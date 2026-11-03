package com.openjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
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
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                lastWindowStatePackage = packageName
                lastWindowStateAtMs = System.currentTimeMillis()
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun openAppByPackage(packageName: String): Boolean {
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
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(intent, 0)
        val normalizedLabel = label.lowercase()
        
        for (app in apps) {
            val appLabel = app.loadLabel(pm).toString().lowercase()
            if (appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel)) {
                openAppByPackage(app.activityInfo.packageName)
                return true
            }
        }
        return false
    }

    fun tapByText(text: String): Boolean {
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
        val rootNode = rootInActiveWindow ?: return false
        val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
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

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Scroll the first visible scrollable container in the requested direction. */
    fun scrollScreen(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && node.isScrollable) {
                val forward = direction.equals("down", ignoreCase = true) ||
                    direction.equals("forward", ignoreCase = true)
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                val success = node.performAction(action)
                node.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
                return success
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
            node.recycle()
        }
        return false
    }

    fun isPackageActive(packageName: String): Boolean {
        // Prefer the live active window. Floating assistant overlays can temporarily
        // make rootInActiveWindow null or expose the overlay instead of the launched app.
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        root?.recycle()
        if (rootPackage == packageName) return true

        // Fall back to the most recent Android window-state event, but only briefly
        // so a stale previous app cannot make a later launch look successful.
        val eventPackage = lastWindowStatePackage
        val eventAgeMs = System.currentTimeMillis() - lastWindowStateAtMs
        return eventPackage == packageName && eventAgeMs in 0..5000
    }

    fun containsVisibleText(expected: String): Boolean {
        if (expected.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val matches = node.isVisibleToUser && (
                node.text?.toString()?.contains(expected, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(expected, ignoreCase = true) == true
            )
            if (matches) {
                node.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
                return true
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
            node.recycle()
        }
        return false
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val normalizedText = text.lowercase()
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            
            val nodeText = node.text?.toString()?.lowercase()
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            
            if (nodeText?.contains(normalizedText) == true || contentDesc?.contains(normalizedText) == true) {
                if (node.isClickable && node.isVisibleToUser) {
                    return node
                }
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    queue.add(child)
                }
            }
            node.recycle()
        }
        
        return null
    }

    @Volatile
    private var lastWindowStatePackage: String? = null

    @Volatile
    private var lastWindowStateAtMs: Long = 0L

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
    }
}