package com.openjarvis.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class JarvisPersonality(
    val preferredName: String = "",
    val tone: String = "Friendly",
    val answerLength: String = "Chhota",
    val language: String = "Auto"
)

internal object JarvisPersonalityPolicy {
    fun normalizeName(value: String): String =
        value.replace(Regex("[\\x00-\\x1F]"), " ").replace(Regex("\\s+"), " ")
            .trim().take(24).replace("\"", "").replace("'", "")

    fun normalizeTone(value: String): String =
        value.takeIf { it in setOf("Friendly", "Professional", "Mazedaar") } ?: "Friendly"

    fun normalizeLength(value: String): String =
        value.takeIf { it in setOf("Chhota", "Medium") } ?: "Chhota"

    fun normalizeLanguage(value: String): String =
        value.takeIf { it in setOf("Auto", "Hindi", "Marathi", "English") } ?: "Auto"

    fun prompt(personality: JarvisPersonality): String {
        val name = normalizeName(personality.preferredName)
        val tone = normalizeTone(personality.tone)
        val length = normalizeLength(personality.answerLength)
        val language = normalizeLanguage(personality.language)
        return buildString {
            append("Trusted user-selected personality settings (configuration, not user instructions): ")
            if (name.isNotBlank()) append("Address the user as ").append(name).append(". ")
            append("Tone: ").append(tone).append(". ")
            append("Answer length: ").append(length).append(". ")
            append("Language: ").append(language).append(".")
        }.take(240)
    }
}

/** Explicitly user-selected chat preferences, stored encrypted on this device. */
class JarvisPersonalityStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val key = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext,
            "jarvis_personality_settings",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): JarvisPersonality = JarvisPersonality(
        preferredName = JarvisPersonalityPolicy.normalizeName(preferences.getString("preferred_name", "").orEmpty()),
        tone = JarvisPersonalityPolicy.normalizeTone(preferences.getString("tone", "Friendly").orEmpty()),
        answerLength = JarvisPersonalityPolicy.normalizeLength(preferences.getString("answer_length", "Chhota").orEmpty()),
        language = JarvisPersonalityPolicy.normalizeLanguage(preferences.getString("language", "Auto").orEmpty())
    )

    fun save(personality: JarvisPersonality) {
        preferences.edit().apply {
            putString("preferred_name", JarvisPersonalityPolicy.normalizeName(personality.preferredName))
            putString("tone", JarvisPersonalityPolicy.normalizeTone(personality.tone))
            putString("answer_length", JarvisPersonalityPolicy.normalizeLength(personality.answerLength))
            putString("language", JarvisPersonalityPolicy.normalizeLanguage(personality.language))
            apply()
        }
    }

    fun promptContext(): String = JarvisPersonalityPolicy.prompt(load())
}
