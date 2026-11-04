package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetResolverTest {
    private val apps = listOf(
        InstalledAppTarget("Chrome", "com.android.chrome"),
        InstalledAppTarget("WhatsApp", "com.whatsapp"),
        InstalledAppTarget("YouTube", "com.google.android.youtube"),
        InstalledAppTarget("Settings", "com.android.settings")
    )

    @Test fun resolvesAppsExplicitlyNamedInCommandInSpokenOrder() {
        val result = AppTargetResolver.findMentionedApps("Open Chrome, then WhatsApp kholo", apps)
        assertEquals(listOf("com.android.chrome", "com.whatsapp"), result?.map { it.packageName })
    }

    @Test fun matchesHindiAndMarathiFriendlyAliases() {
        val result = AppTargetResolver.findMentionedApps("व्हॉट्सअॅप kholo", apps)
        assertEquals("com.whatsapp", result?.singleOrNull()?.packageName)
    }

    @Test fun rejectsGenericModelPlaceholderLabels() {
        assertTrue(AppTargetResolver.isPlaceholder("App"))
        assertTrue(AppTargetResolver.isPlaceholder("Target App"))
        assertFalse(AppTargetResolver.isPlaceholder("WhatsApp"))
    }

    @Test fun returnsNoTargetIfCommandDoesNotNameInstalledApp() {
        assertEquals(emptyList<InstalledAppTarget>(), AppTargetResolver.findMentionedApps("do something useful", apps))
        assertNull(AppTargetResolver.findMentionedApps("open gallery", listOf(
            InstalledAppTarget("Photos", "com.google.photos"),
            InstalledAppTarget("Gallery", "com.oem.gallery")
        )))
    }

    @Test fun prefersLongestSpecificAppAlias() {
        val result = AppTargetResolver.findMentionedApps("Open Google Chrome", listOf(
            InstalledAppTarget("Google", "com.google.android.googlequicksearchbox"),
            InstalledAppTarget("Chrome", "com.android.chrome")
        ))
        assertEquals("com.android.chrome", result?.singleOrNull()?.packageName)
    }
}
