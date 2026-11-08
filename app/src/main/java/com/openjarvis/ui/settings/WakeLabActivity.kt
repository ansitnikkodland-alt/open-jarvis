package com.openjarvis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openjarvis.voice.WakeWordConfig
import com.openjarvis.voice.voicute.WakeWordEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

class WakeLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WakeLabScreen(onBack = { finish() }) } }
    }
}

private data class WakeClip(val file: File, val label: String, val createdAt: Long)
private data class WakeReplay(
    val clipName: String,
    val label: String,
    val modeAScores: Map<String, Float>,
    val modeBScores: Map<String, Float>,
    val modeAHit: Boolean,
    val modeBHit: Boolean
)

@Composable
private fun WakeLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { File(context.filesDir, "wake_lab").apply { mkdirs() } }
    val prefs = remember { context.getSharedPreferences("wake_lab", 0) }
    var optedIn by remember { mutableStateOf(prefs.getBoolean("recording_opt_in", false)) }
    var label by remember { mutableStateOf("Jarvis") }
    var clips by remember { mutableStateOf(loadClips(root)) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Local-only • Voicute • 16 kHz mono PCM") }
    var results by remember { mutableStateOf<List<WakeReplay>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val threshold = remember {
        val p = context.getSharedPreferences(WakeWordConfig.SENSITIVITY_PREFS, 0)
        WakeWordConfig.Sensitivity.fromPreference(
            p.getString(WakeWordConfig.SENSITIVITY_KEY, WakeWordConfig.Sensitivity.HIGH.name)
        ).threshold
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Microphone permission granted" else "Microphone permission denied"
    }

    fun refresh() { clips = loadClips(root) }
    fun recordClip() {
        if (!optedIn) { status = "Enable local recording opt-in first"; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        if (recording || busy) return
        recording = true
        status = "Recording 4 seconds…"
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(AudioRecord.getMinBufferSize(
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                    ), 8192)
                )
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
                    val pcm = ShortArray(16000 * 4)
                    recorder.startRecording()
                    var offset = 0
                    while (offset < pcm.size) {
                        val n = recorder.read(pcm, offset, pcm.size - offset)
                        check(n > 0) { "AudioRecord read failed: $n" }
                        offset += n
                    }
                    recorder.stop()
                    val stamp = System.currentTimeMillis()
                    val safe = label.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val wav = File(root, "wake_${stamp}_${safe}.wav")
                    writeWav(wav, pcm)
                    File(root, wav.nameWithoutExtension + ".label").writeText(label)
                    wav
                } finally {
                    runCatching { recorder.release() }
                }
            }
            recording = false
            if (saved != null) {
                refresh()
                status = "Saved locally: ${saved.name}"
            } else status = "Recording failed"
        }
    }

    fun replayAll() {
        if (clips.isEmpty() || busy) return
        busy = true
        status = "Replaying clips in Mode A and Mode B…"
        scope.launch {
            val replayed = withContext(Dispatchers.IO) {
                val model = WakeWordEngine(context)
                try {
                    check(model.isLoaded) { model.errorMessage ?: "Voicute model failed to load" }
                    clips.map { clip -> replayClip(model, clip, threshold) }
                } finally { model.close() }
            }
            results = replayed
            busy = false
            status = "Replay complete • threshold=${"%.3f".format(Locale.US, threshold)}"
        }
    }

    val positive = results.filter { it.label != "negative" }
    val negative = results.filter { it.label == "negative" }
    val aHits = positive.count { it.modeAHit }
    val bHits = positive.count { it.modeBHit }
    val aFalse = negative.count { !it.modeAScores.values.all { score -> score < threshold } }
    val bFalse = negative.count { !it.modeBScores.values.all { score -> score < threshold } }
    val report = buildString {
        appendLine("JARVIS WAKE LAB — VOICUTE ONLY")
        appendLine("Sample rate: 16000 Hz mono PCM16")
        appendLine("Threshold: $threshold")
        appendLine("Mode A: raw int16-scale (same preprocessing as live)")
        appendLine("Mode B: short / 32768f (experimental only; never used by live listener)")
        appendLine("Aggregation: maximum score per phrase across overlapping 1.01s windows, 100ms hop")
        appendLine("Mode A hit rate: ${if (positive.isEmpty()) "N/A" else "$aHits/${positive.size} = ${100.0*aHits/positive.size}%"}")
        appendLine("Mode B hit rate: ${if (positive.isEmpty()) "N/A" else "$bHits/${positive.size} = ${100.0*bHits/positive.size}%"}")
        appendLine("Mode A false-trigger rate: ${if (negative.isEmpty()) "N/A (no negative clips)" else "$aFalse/${negative.size} = ${100.0*aFalse/negative.size}%"}")
        appendLine("Mode B false-trigger rate: ${if (negative.isEmpty()) "N/A (no negative clips)" else "$bFalse/${negative.size} = ${100.0*bFalse/negative.size}%"}")
        results.forEach { r ->
            appendLine()
            appendLine("Clip: ${r.clipName} label=${r.label}")
            appendLine("A: ${r.modeAScores} result=${if (r.modeAHit) "HIT" else "MISS"}")
            appendLine("B: ${r.modeBScores} result=${if (r.modeBHit) "HIT" else "MISS"}")
        }
        appendLine()
        appendLine("Privacy: clips stay in app-private local storage. No audio is included in diagnostic reports.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) }
                ?: error("Cannot write export")
        }.onFailure { status = "Export failed: ${it.message}" }
            .onSuccess { status = "Wake Lab TXT exported" }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Wake Lab • Voicute") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(status, style = MaterialTheme.typography.bodySmall) }
            item {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local recording opt-in", style = MaterialTheme.typography.titleMedium)
                        Text("Recording is off by default. Audio stays only in this app's private storage.")
                        Row {
                            Switch(checked = optedIn, onCheckedChange = {
                                optedIn = it
                                prefs.edit().putBoolean("recording_opt_in", it).apply()
                                if (!it) status = "Recording opt-in disabled"
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(if (optedIn) "Enabled" else "Disabled")
                        }
                        Text("Expected phrase / label")
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(value = label, onValueChange = {}, readOnly = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Jarvis", "Hey Jarvis", "Hi Jarvis", "negative").forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { label = item; expanded = false })
                                }
                            }
                        }
                        Button(onClick = { recordClip() }, enabled = optedIn && !recording && !busy) {
                            Text(if (recording) "Recording…" else "Record 4-second clip")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { replayAll() }, enabled = clips.isNotEmpty() && !busy) { Text("Replay A + B") }
                    OutlinedButton(onClick = { exportLauncher.launch("Wake_Lab_${System.currentTimeMillis()}.txt") }, enabled = results.isNotEmpty()) { Text("Export TXT") }
                }
            }
            item { Text("Saved local clips (${clips.size})", style = MaterialTheme.typography.titleMedium) }
            items(clips, key = { it.file.name }) { clip ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(clip.file.name)
                        Text("Label: ${clip.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    root.listFiles()?.forEach { it.delete() }
                    results = emptyList()
                    refresh()
                    status = "All Wake Lab clips deleted"
                }, enabled = !recording && !busy && clips.isNotEmpty()) { Text("Delete all clips") }
            }
            item { Text("Replay results", style = MaterialTheme.typography.titleMedium) }
            items(results, key = { it.clipName }) { result ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("${result.clipName} • ${result.label}")
                        Text("Mode A: ${result.modeAScores} • ${if (result.modeAHit) "HIT" else "MISS"}")
                        Text("Mode B: ${result.modeBScores} • ${if (result.modeBHit) "HIT" else "MISS"}")
                    }
                }
            }
        }
    }
}

private fun loadClips(root: File): List<WakeClip> = root.listFiles()
    ?.filter { it.extension.equals("wav", true) }
    ?.sortedByDescending { it.lastModified() }
    ?.map { wav ->
        val labelFile = File(root, wav.nameWithoutExtension + ".label")
        WakeClip(wav, labelFile.takeIf { it.exists() }?.readText()?.trim() ?: "negative", wav.lastModified())
    }.orEmpty()

private fun writeWav(file: File, pcm: ShortArray) {
    FileOutputStream(file).use { out ->
        val dataBytes = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)); header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII)); header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16); header.putShort(1); header.putShort(1); header.putInt(16000)
        header.putInt(32000); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)); header.putInt(dataBytes)
        out.write(header.array())
        val bytes = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        out.write(bytes.array())
    }
}

private fun readWav(file: File): ShortArray {
    val bytes = file.readBytes()
    require(bytes.size >= 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid WAV: ${file.name}" }
    val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
    return ShortArray((bytes.size - 44) / 2) { pcm.short }
}

private fun replayClip(model: WakeWordEngine, clip: WakeClip, threshold: Float): WakeReplay {
    val pcm = readWav(clip.file)
    val windowSize = model.audioSamplesNeeded
    require(windowSize > 0) { "Voicute window size unavailable" }
    val keys = listOf("Jarvis", "Hey Jarvis", "Hi Jarvis")
    val a = keys.associateWith { 0f }.toMutableMap()
    val b = keys.associateWith { 0f }.toMutableMap()
    val hop = 1600
    var start = 0
    while (start < pcm.size) {
        val window = ShortArray(windowSize)
        val count = minOf(windowSize, pcm.size - start)
        System.arraycopy(pcm, start, window, 0, count)
        val resultA = model.processForWakeLab(window, false)
        val resultB = model.processForWakeLab(window, true)
        parseScores(resultA?.scoreSummary).forEach { (k, v) -> if (a.containsKey(k)) a[k] = max(a[k] ?: 0f, v) }
        parseScores(resultB?.scoreSummary).forEach { (k, v) -> if (b.containsKey(k)) b[k] = max(b[k] ?: 0f, v) }
        if (start + windowSize >= pcm.size) break
        start += hop
    }
    val expected = clip.label
    val hitA = if (expected == "negative") a.values.any { it >= threshold } else (a[expected] ?: 0f) >= threshold
    val hitB = if (expected == "negative") b.values.any { it >= threshold } else (b[expected] ?: 0f) >= threshold
    return WakeReplay(clip.file.name, expected, a, b, hitA, hitB)
}

private fun parseScores(summary: String?): Map<String, Float> = summary.orEmpty().split(",")
    .mapNotNull { token ->
        val parts = token.trim().split("=", limit = 2)
        if (parts.size != 2) null else parts[1].toFloatOrNull()?.let { parts[0] to it }
    }.toMap()
