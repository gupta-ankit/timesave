package com.example.timesave

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WebsiteBlockerService : AccessibilityService() {

    private val TAG = "WebsiteBlockerService"

    // TODO: Load this list from storage
    private val distractingWebsites = listOf(
        DistractingWebsite("youtube.com", 0), // 0 minutes for now, for testing
        DistractingWebsite("facebook.com", 0),
        DistractingWebsite("instagram.com", 0),
        DistractingWebsite("twitter.com", 0),
        DistractingWebsite("reddit.com", 0)
    )

    // TODO: Store and update daily usage
    private val websiteUsageToday = mutableMapOf<String, Long>() // Hostname to milliseconds

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent: type -> ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "App in foreground: $packageName")

            val rootInActiveWindow: AccessibilityNodeInfo? = rootInActiveWindow
            if (rootInActiveWindow != null && packageName != null) {
                // More sophisticated URL detection will be needed here.
                // This is a placeholder and might only work for some browsers or specific views.
                val currentUrl = findUrlInNode(rootInActiveWindow)
                Log.d(TAG, "Current URL (heuristic): $currentUrl")

                currentUrl?.let { url ->
                    distractingWebsites.forEach { distractingSite ->
                        if (url.contains(distractingSite.hostname, ignoreCase = true)) {
                            Log.i(TAG, "Distracting site detected: ${distractingSite.hostname}")
                            // TODO: Start timer for this site
                            // TODO: Check if usage exceeds limit
                            // TODO: If limit exceeded, show block screen
                        }
                    }
                }
            }
        }
    }

    // This is a very basic and often unreliable way to get a URL.
    // More robust solutions might involve looking for specific view IDs for URL bars in known browsers,
    // or using browser extensions if developing for a specific browser.
    private fun findUrlInNode(nodeInfo: AccessibilityNodeInfo): String? {
        // Try to find EditText views, as URL bars are often EditTexts
        val editTextNodes = nodeInfo.findAccessibilityNodeInfosByViewId("android:id/edit") // Common for web views, but not guaranteed
        if (editTextNodes != null && editTextNodes.isNotEmpty()) {
            for (editTextNode in editTextNodes) {
                if (editTextNode != null && editTextNode.text != null) {
                    val text = editTextNode.text.toString()
                    if (text.startsWith("http://") || text.startsWith("https://")) {
                        return text
                    }
                }
            }
        }
        // Fallback: A more generic search for text that looks like a URL (less reliable)
        return findUrlRecursive(nodeInfo)
    }

    private fun findUrlRecursive(nodeInfo: AccessibilityNodeInfo?): String? {
        if (nodeInfo == null) return null

        if (nodeInfo.text != null) {
            val text = nodeInfo.text.toString()
            // Basic check for a URL-like pattern (very simplistic)
            if (text.contains("." ) && (text.startsWith("http") || text.contains("/")) && !text.contains(" ") && text.length > 5) {
                 // More specific checks for common browser package names' URL bar IDs
                val viewId = nodeInfo.viewIdResourceName
                if (viewId != null) {
                    if ( (nodeInfo.className == "android.widget.EditText" || nodeInfo.className == "android.webkit.WebView") ) {
                        Log.d(TAG, "Potential URL in EditText/WebView by ID $viewId : $text")
                        return text
                    }
                }
            }
        }

        for (i in 0 until (nodeInfo.childCount ?: 0)) {
            val child = nodeInfo.getChild(i)
            val url = findUrlRecursive(child)
            if (url != null) return url
            child?.recycle()
        }
        return null
    }


    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted")
        // TODO: Handle interruption, maybe save current state
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val serviceInfo = AccessibilityServiceInfo()
        serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo.notificationTimeout = 100
        this.serviceInfo = serviceInfo
        Log.i(TAG, "onServiceConnected: WebsiteBlockerService connected.")
        // TODO: Initialize usage data, load distracting sites from storage
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: WebsiteBlockerService unbound.")
        // TODO: Clean up resources, save final state
        return super.onUnbind(intent)
    }
} 