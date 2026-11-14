package com.openjarvis.accessibility

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class DeviceGateTest {
    @Test fun legacyEntrypointsCannotActOrReadWithoutAnApprovalPermit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val service = JarvisAccessibilityService()
            assertFalse(service.openAppByPackage("com.android.settings"))
            assertFalse(service.openAppByLabel("Settings"))
            assertFalse(service.tapByText("Delete"))
            assertFalse(service.typeText("secret"))
            assertFalse(service.pressBack())
            assertFalse(service.pressHome())
            assertFalse(service.pressRecents())
            assertFalse(service.tapAt(10f, 10f))
            assertFalse(service.longPressAt(10f, 10f))
            assertFalse(service.swipe(10f, 10f, 20f, 20f))
            val reader = ScreenReader(service)
            assertEquals("", reader.extractAllText())
            assertNull(reader.findNodeByText("secret"))
            assertNull(reader.findNodeByHint("PIN"))
            assertNull(reader.getFocusedNode())
        }
    }
}
