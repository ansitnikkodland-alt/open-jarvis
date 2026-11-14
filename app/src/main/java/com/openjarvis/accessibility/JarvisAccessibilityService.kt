package com.openjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.os.Handler
import android.os.Looper
import com.openjarvis.ui.OverlayService
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.openjarvis.agent.Action
import com.openjarvis.agent.ActionApprovalCoordinator
import com.openjarvis.agent.ActionApprovalPolicy
import com.openjarvis.agent.DeviceSession
import android.graphics.Rect
import java.security.MessageDigest

class JarvisAccessibilityService : AccessibilityService() {


    private val buttonCallback =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                startService(
    Intent(this@JarvisAccessibilityService, OverlayService::class.java).apply {
        action = "com.openjarvis.TOGGLE_PANEL"
    }
)
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DeviceSession.start()
        instance = this
        accessibilityButtonController.registerAccessibilityButtonCallback(
            buttonCallback,
            Handler(Looper.getMainLooper())
        )
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or 
                   AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    private fun revoke() {
        DeviceSession.stop()
        ActionApprovalCoordinator.cancelAll()
        instance = null
    }

    override fun onInterrupt() { revoke() }

    override fun onUnbind(intent: Intent?): Boolean {
        revoke()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)

        revoke()
        super.onDestroy()
    }

    private var permitted = false
    private fun permittedNow(): Boolean =
        Looper.myLooper() == Looper.getMainLooper() && permitted && instance === this &&
            DeviceSession.current() != null

    fun openAppByPackage(packageName: String): Boolean {
        if (!permittedNow()) return false
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppByLabel(label: String): Boolean {
        if (!permittedNow()) return false
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(intent, 0)
        val normalizedLabel = label.lowercase()
        
        for (app in apps) {
            val appLabel = app.loadLabel(pm).toString().lowercase()
            if (appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel)) {
                return openAppByPackage(app.activityInfo.packageName)
            }
        }
        return false
    }

    fun tapByText(text: String): Boolean {
        if (!permittedNow()) return false
        val rootNode = rootInActiveWindow ?: return false
        val node = findNodeByText(rootNode, text)
        if (node != null) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            rootNode.recycle()
            return result
        }
        rootNode.recycle()
        return false
    }

    fun typeText(text: String): Boolean {
        if (!permittedNow()) return false
        val rootNode = rootInActiveWindow ?: return false
        val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            // Never inject text into Android password/PIN fields, even if an
            // upstream planner or approval UI mistakenly permits this action.
            if (focused.isPassword) {
                focused.recycle()
                rootNode.recycle()
                return false
            }
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focused.recycle()
            rootNode.recycle()
            return result
        }
        rootNode.recycle()
        return false
    }

    fun pressBack() = permittedNow() && performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = permittedNow() && performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = permittedNow() && performGlobalAction(GLOBAL_ACTION_RECENTS)

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isPassword) return
            if (node.isClickable && node.isVisibleToUser &&
                (node.text?.toString() == text || node.contentDescription?.toString() == text)) {
                matches.add(AccessibilityNodeInfo.obtain(node))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let {
                try { visit(it) } finally { it.recycle() }
            }
        }
        visit(root)
        if (matches.size == 1) return matches.single()
        matches.forEach { it.recycle() }
        return null
    }


    fun tapAt(x: Float, y: Float): Boolean =
        performGesture(x, y, x, y, 100L)

    fun longPressAt(x: Float, y: Float): Boolean =
        performGesture(x, y, x, y, 900L)

    fun swipe(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        durationMs: Long = 450L
    ): Boolean =
        performGesture(x1, y1, x2, y2, durationMs)

    private fun performGesture(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        durationMs: Long
    ): Boolean {
        if (!permittedNow()) return false
        if (durationMs <= 0L || durationMs > 60000L) return false

        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) lineTo(x2, y2)
        }

        val stroke = GestureDescription.StrokeDescription(
            path, 0L, durationMs
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private data class WindowState(val pkg: String, val id: Int, val digest: String)

    // Metadata only until reading is explicitly approved. No screen text is
    // read, retained, logged or sent to the model by this snapshot.
    private fun snapshot(): WindowState? {
        if (instance !== this || DeviceSession.current() == null) return null
        val root = rootInActiveWindow ?: return null
        return try {
            val pkg = root.packageName?.toString() ?: return null
            if (pkg == packageName) return null
            val metadata = StringBuilder()
            var count = 0
            fun visit(node: AccessibilityNodeInfo): Boolean {
                if (++count > 2000 || node.isPassword) return false
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                metadata.append(node.viewIdResourceName).append(node.className)
                    .append(bounds).append(node.isFocused).append(node.isClickable)
                    .append(node.isEnabled).append(node.isVisibleToUser).append(node.childCount)
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: return false
                    val safe = try { visit(child) } finally { child.recycle() }
                    if (!safe) return false
                }
                return true
            }
            if (!visit(root)) return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(metadata.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            WindowState(pkg, root.windowId, digest)
        } finally { root.recycle() }
    }

    private suspend fun <T> approvedOperation(
        details: String, denied: T, operation: () -> T
    ): T {
        val token = DeviceSession.current() ?: return denied
        val before = withContext(Dispatchers.Main.immediate) { snapshot() } ?: return denied
        if (!ActionApprovalCoordinator.awaitDetails(this,
                "$details\nCurrent application: ${before.pkg}\nWindow: ${before.id}")) return denied
        return withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (!DeviceSession.valid(token) || snapshot() != before) return@withContext denied
            // No suspension between the final check and the device call.
            permitted = true
            try { operation() } finally { permitted = false }
        }
    }

    suspend fun executeApproved(action: Action): Boolean = approvedOperation(
        ActionApprovalPolicy.describe(action), false
    ) {
        when (action.action) {
            Action.OPEN_APP -> action.packageName?.let { openAppByPackage(it) } ?: false
            Action.TAP -> action.text?.takeIf { it.isNotBlank() }?.let { tapByText(it) } ?: false
            Action.TYPE -> action.value?.let { typeText(it) } ?: false
            Action.PRESS_BACK -> pressBack()
            Action.PRESS_HOME -> pressHome()
            Action.PRESS_RECENTS -> pressRecents()
            // Coordinates cannot identify a stable UI target: fail closed.
            else -> false
        }
    }

    suspend fun readApproved(): String = approvedOperation(
        "Read this application's screen once and use its text in the AI request. " +
            "Deny on confidential screens, PIN keypads or passwords.", ""
    ) { ScreenReader(this).extractAllText() }

    internal fun mayReadScreen(): Boolean = permittedNow()

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
    }
}