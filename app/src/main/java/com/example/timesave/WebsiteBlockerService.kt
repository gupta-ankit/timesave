package com.example.timesave

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WebsiteBlockerService : AccessibilityService() {

    private val TAG = "WebsiteBlockerService"

    // TODO: Load this list from storage
    private val distractingWebsites = listOf(
        DistractingWebsite("youtube.com", 1), // 1 minute for testing
        DistractingWebsite("facebook.com", 1),
        DistractingWebsite("instagram.com", 1),
        DistractingWebsite("twitter.com", 1),
        DistractingWebsite("reddit.com", 1)
    )

    // TODO: Store and update daily usage, and persist this
    private val websiteUsageTodayMillis = mutableMapOf<String, Long>() // Hostname to milliseconds

    private var currentlyTrackedHostname: String? = null
    private var sessionStartTimeMillis: Long? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent: type -> ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "Foreground App: $packageName, Event Source Package: ${event.source?.packageName}")

            val rootInActiveWindow: AccessibilityNodeInfo? = rootInActiveWindow
            var currentUrl: String? = null

            if (rootInActiveWindow != null && packageName != null) {
                // Only try to get URL if it's a known browser or a WebView container
                // Add more browser package names as needed
                if (isBrowserApp(packageName)) {
                    currentUrl = findUrlInNode(rootInActiveWindow, packageName)
                    Log.d(TAG, "Current URL (heuristic): $currentUrl from package $packageName")
                } else {
                    Log.d(TAG, "Not a tracked browser: $packageName. Clearing current URL.")
                }
            }
            handleTimeTracking(currentUrl, packageName)
        }
    }

    private fun isBrowserApp(packageName: String?): Boolean {
        return packageName != null && (
                packageName.contains("chrome") ||
                packageName.contains("firefox") ||
                packageName.contains("opera") ||
                packageName.contains("duckduckgo") ||
                packageName.contains("brave") ||
                packageName.contains("edge") ||
                packageName.contains("webview") // Generic for apps embedding webviews
                // Add other browser package names if necessary
                )
    }


    private fun handleTimeTracking(currentUrl: String?, currentPackageName: String?) {
        val previouslyTrackedHostname = currentlyTrackedHostname
        var newTrackedHostname: String? = null

        if (currentUrl != null && isBrowserApp(currentPackageName)) {
            distractingWebsites.find { currentUrl.contains(it.hostname, ignoreCase = true) }?.let {
                newTrackedHostname = it.hostname
            }
        }

        if (previouslyTrackedHostname != null && previouslyTrackedHostname != newTrackedHostname) {
            // User navigated away from a tracked site or switched app
            finalizeSession(previouslyTrackedHostname)
        }

        if (newTrackedHostname != null && previouslyTrackedHostname != newTrackedHostname) {
            // User navigated to a new distracting site
            startNewSession(newTrackedHostname!!)
        } else if (newTrackedHostname == null && previouslyTrackedHostname != null) {
            // User navigated away from any distracting site (e.g. to a non-distracting URL or different app)
             finalizeSession(previouslyTrackedHostname)
        }
    }

    private fun startNewSession(hostname: String) {
        Log.i(TAG, "Starting new session for: $hostname")
        currentlyTrackedHostname = hostname
        sessionStartTimeMillis = SystemClock.elapsedRealtime()
    }

    private fun finalizeSession(hostname: String?) {
        if (hostname == null || sessionStartTimeMillis == null) {
            Log.d(TAG, "FinalizeSession called with no active session for $hostname")
            clearCurrentSession()
            return
        }

        val endTimeMillis = SystemClock.elapsedRealtime()
        val sessionDurationMillis = endTimeMillis - sessionStartTimeMillis!!
        if (sessionDurationMillis <= 0) {
             Log.d(TAG, "FinalizeSession: session duration is zero or negative for $hostname, skipping update.")
             clearCurrentSession()
             return
        }


        val previousTotalUsage = websiteUsageTodayMillis.getOrDefault(hostname, 0L)
        val newTotalUsage = previousTotalUsage + sessionDurationMillis

        websiteUsageTodayMillis[hostname] = newTotalUsage

        Log.i(TAG, "Finalized session for $hostname. Duration: ${sessionDurationMillis / 1000}s. Total today: ${newTotalUsage / 1000}s")
        clearCurrentSession()

        // TODO: Check if usage exceeds limit
        checkUsageLimits(hostname, newTotalUsage)
    }
    
    private fun clearCurrentSession() {
        currentlyTrackedHostname = null
        sessionStartTimeMillis = null
        Log.d(TAG, "Current session cleared.")
    }

    private fun checkUsageLimits(hostname: String, totalUsageMillis: Long) {
        val siteConfig = distractingWebsites.find { it.hostname == hostname }
        if (siteConfig != null) {
            val limitMillis = siteConfig.timeLimitInMinutes * 60 * 1000
            if (totalUsageMillis >= limitMillis) {
                Log.w(TAG, "Time limit EXCEEDED for $hostname! Usage: ${totalUsageMillis / 1000}s, Limit: ${limitMillis / 1000}s")
                // TODO: Implement blocking action!
                // For now, just log. We will add the blocking screen next.
                // showBlockScreen(hostname)
            } else {
                 Log.i(TAG, "Time limit NOT exceeded for $hostname. Usage: ${totalUsageMillis / 1000}s, Limit: ${limitMillis / 1000}s")
            }
        }
    }


    // This is a very basic and often unreliable way to get a URL.
    // More robust solutions might involve looking for specific view IDs for URL bars in known browsers,
    // or using browser extensions if developing for a specific browser.
    private fun findUrlInNode(nodeInfo: AccessibilityNodeInfo?, packageName: String): String? {
        if (nodeInfo == null) return null

        // Attempt to find URL bar by common resource IDs for specific browsers
        // These IDs can change with browser updates and vary between browser versions.
        val urlBarIds = when {
            packageName.contains("chrome") -> listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/location_bar")
            packageName.contains("firefox") -> listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view") // Example, actual IDs may vary
            // Add more browser specific IDs here
            else -> emptyList()
        }

        for (id in urlBarIds) {
            val urlBarNodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
            if (urlBarNodes != null && urlBarNodes.isNotEmpty()) {
                for (node in urlBarNodes) {
                    if (node != null && node.text != null) {
                        val text = node.text.toString()
                        if (text.startsWith("http://") || text.startsWith("https://") || text.contains(".")) {
                             Log.d(TAG, "URL found via ID '$id': $text")
                            return text
                        }
                    }
                }
            }
        }
        
        // Fallback: A more generic search for text that looks like a URL (less reliable)
        // This recursive search can be heavy. Use with caution or optimize.
        return findUrlRecursive(nodeInfo)
    }

    private fun findUrlRecursive(nodeInfo: AccessibilityNodeInfo?): String? {
        if (nodeInfo == null) return null

        if (nodeInfo.text != null) {
            val text = nodeInfo.text.toString().trim()
            // Basic check for a URL-like pattern (very simplistic and can have false positives)
            if (text.length > 5 && text.contains(".") && !text.contains(" ") &&
                (text.startsWith("http") || text.startsWith("www.") || text.count { it == '.' } >= 1)) {
                
                val viewId = nodeInfo.viewIdResourceName
                val className = nodeInfo.className?.toString() ?: ""

                // Prioritize EditTexts or views that are likely to hold URLs
                if (className.contains("EditText", ignoreCase = true) ||
                    className.contains("TextView", ignoreCase = true) || // Some browsers display URL in TextView when not focused
                    className.contains("WebView", ignoreCase = true)) {
                    
                    // Additional heuristics: avoid excessively long text, text with newlines, or non-ASCII heavy text
                    if (text.length < 2048 && !text.contains("\n") && text.isNotBlank()) {
                         Log.d(TAG, "Potential URL in EditText/TextView/WebView by ID '$viewId' or Class '$className': $text")
                        // More stringent check if it's an EditText
                        if (className.contains("EditText", ignoreCase = true) && (text.startsWith("http") || text.startsWith("www."))) {
                            return text
                        } else if (!className.contains("EditText", ignoreCase = true)){ // Be a bit more lenient for TextView/WebView
                             return text
                        }
                    }
                }
            }
        }

        for (i in 0 until (nodeInfo.childCount ?: 0)) {
            val child = nodeInfo.getChild(i)
            val url = findUrlRecursive(child) // Recursive call
            if (url != null) {
                // Don't recycle child if URL found, as nodeInfo might be part of that URL path
                return url
            }
            child?.recycle() // Recycle if no URL found in this branch
        }
        return null
    }


    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted. Finalizing any active session.")
        finalizeSession(currentlyTrackedHostname)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val serviceInfo = this.serviceInfo ?: AccessibilityServiceInfo() // Use existing or create new
        serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED
        serviceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo.packageNames = null // Monitor all packages initially
        // serviceInfo.packageNames = arrayOf("com.android.chrome", "org.mozilla.firefox") // Example: To restrict to specific browsers

        this.serviceInfo = serviceInfo
        Log.i(TAG, "onServiceConnected: WebsiteBlockerService connected.")
        // TODO: Initialize usage data, load distracting sites from storage
        // For now, usage is reset when service restarts.
        websiteUsageTodayMillis.clear()
        Log.d(TAG, "Website usage data cleared on service connect.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: WebsiteBlockerService unbound. Finalizing any active session.")
        finalizeSession(currentlyTrackedHostname)
        // TODO: Clean up resources, save final state
        return super.onUnbind(intent)
    }
} 