package com.openjarvis.skills

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.openjarvis.agent.ContactCommandParser
import com.openjarvis.agent.SearchCommandParser

/** Android effects live here; SkillRegistry remains JVM-testable and side-effect free. */
class AndroidSkillExecutor(private val context: Context) : SkillRegistry.SkillExecutor {
    override suspend fun execute(request: SkillRegistry.Request): Result<Unit> = runCatching {
        val args = request.args
        when (request.kind) {
            SkillRegistry.Kind.OPEN_APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(resolvePackage(args.getValue("app")))
                    ?: throw IllegalArgumentException("App not installed: ${args["app"]}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent)
            }
            SkillRegistry.Kind.SETTINGS -> {
                val action = when (args.getValue("screen")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                launch(Intent(action))
            }
            SkillRegistry.Kind.CALL -> {
                val parsed = ContactCommandParser.parse(args["text"].orEmpty())
                val name = parsed?.name ?: args["contact"]
                val uri = if (name != null) Uri.parse("tel:").buildUpon().appendPath(name).build()
                    else Uri.parse("tel:${args["number"] ?: ""}")
                launch(Intent(Intent.ACTION_DIAL, uri))
            }
            SkillRegistry.Kind.SEARCH -> {
                val raw = args["text"].orEmpty()
                val query = SearchCommandParser.parseYouTubeQuery(raw)
                    ?: SearchCommandParser.parseWebQuery(raw) ?: raw
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            }
            SkillRegistry.Kind.WHATSAPP_MESSAGE -> {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, args.getValue("text"))
                    setPackage("com.whatsapp")
                }; launch(i)
            }
            SkillRegistry.Kind.SMS_MESSAGE -> launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", args.getValue("text")))
            SkillRegistry.Kind.ALARM -> launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, args.getValue("hour").toInt()).putExtra(AlarmClock.EXTRA_MINUTES, args.getValue("minute").toInt()).putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis alarm"))
            SkillRegistry.Kind.TIMER -> launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, args.getValue("seconds").toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
            SkillRegistry.Kind.TORCH -> {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    ?: throw IllegalStateException("No torch available")
                manager.setTorchMode(cameraId, args.getValue("enabled").toBoolean())
            }
            SkillRegistry.Kind.VOLUME -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (args.getValue("direction") == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            SkillRegistry.Kind.MAPS -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(args.getValue("destination")))).setPackage("com.google.android.apps.maps"))
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun resolvePackage(label: String): String = when (label.lowercase()) {
        "youtube" -> "com.google.android.youtube"
        "whatsapp" -> "com.whatsapp"
        "chrome" -> "com.android.chrome"
        "maps", "google maps" -> "com.google.android.apps.maps"
        else -> label
    }
}
