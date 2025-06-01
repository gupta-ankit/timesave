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
    private val blockedItems = listOf(
        BlockedItem("youtube.com", BlockType.WEBSITE, 1, "YouTube Website"),
        BlockedItem("com.google.android.youtube", BlockType.APP, 1, "YouTube App"),
        BlockedItem("facebook.com", BlockType.WEBSITE, 1, "Facebook Website"),
        BlockedItem("com.facebook.katana", BlockType.APP, 1, "Facebook App"), // Example package name
        BlockedItem("instagram.com", BlockType.WEBSITE, 1, "Instagram Website"),
        BlockedItem("com.instagram.android", BlockType.APP, 1, "Instagram App"), // Example package name
        BlockedItem("twitter.com", BlockType.WEBSITE, 1, "Twitter/X Website"),
        BlockedItem("com.twitter.android", BlockType.APP, 1, "Twitter/X App"), // Example package name
        BlockedItem("reddit.com", BlockType.WEBSITE, 1, "Reddit Website")
        // Add com.reddit.frontpage for the app if needed
    )

    // Stores usage in milliseconds, keyed by item identifier (hostname or package name)
    // TODO: Persist this data
    private val itemUsageTodayMillis = mutableMapOf<String, Long>()

    private var currentlyTrackedIdentifier: String? = null
    private var currentItemType: BlockType? = null
    private var sessionStartTimeMillis: Long? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Log.d(TAG, "onAccessibilityEvent: type -> ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}, package -> ${event?.packageName}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString()
            // Log.d(TAG, "Foreground App: $packageName, Event Source Node: ${event.source?.className}")

            val rootInActiveWindow: AccessibilityNodeInfo? = rootInActiveWindow
            var currentUrl: String? = null

            if (packageName != null) {
                if (isBrowserApp(packageName) && rootInActiveWindow != null) {
                    currentUrl = findUrlInNode(rootInActiveWindow, packageName)
                    // Log.d(TAG, "Current URL (heuristic): $currentUrl from browser $packageName")
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
                packageName.contains("samsung.android.browser") ||
                packageName.contains("webview") // More generic, but could be other apps embedding webviews
                )
    }

    private fun handleTimeTracking(currentUrl: String?, currentPackageName: String?) {
        val previouslyTrackedIdentifier = currentlyTrackedIdentifier
        var newTrackedIdentifier: String? = null
        var newTrackedItemType: BlockType? = null

        // Priority 1: Check for blocked APP by package name
        if (currentPackageName != null) {
            blockedItems.find { it.identifier == currentPackageName && it.type == BlockType.APP }?.let {
                newTrackedIdentifier = it.identifier
                newTrackedItemType = it.type
                // Log.d(TAG, "Matched APP: ${it.displayName ?: it.identifier}")
            }
        }

        // Priority 2: If not an explicitly blocked app, check for blocked WEBSITE by URL (if in a browser)
        if (newTrackedIdentifier == null && currentUrl != null && isBrowserApp(currentPackageName)) {
            blockedItems.find { it.type == BlockType.WEBSITE && currentUrl.contains(it.identifier, ignoreCase = true) }?.let {
                newTrackedIdentifier = it.identifier
                newTrackedItemType = it.type
                // Log.d(TAG, "Matched WEBSITE: ${it.displayName ?: it.identifier} in $currentPackageName")
            }
        }

        if (previouslyTrackedIdentifier != null && previouslyTrackedIdentifier != newTrackedIdentifier) {
            // User navigated away from a tracked item or switched app/URL context
            finalizeSession(previouslyTrackedIdentifier)
        }

        if (newTrackedIdentifier != null && newTrackedIdentifier != previouslyTrackedIdentifier) {
            // User navigated to a new blocked item (app or website)
            startNewSession(newTrackedIdentifier!!, newTrackedItemType!!)
        } else if (newTrackedIdentifier == null && previouslyTrackedIdentifier != null) {
            // User navigated away from any distracting item (e.g., to a non-distracting URL/app)
            finalizeSession(previouslyTrackedIdentifier)
        }
    }

    private fun startNewSession(identifier: String, type: BlockType) {
        Log.i(TAG, "Starting new session for: $identifier (Type: $type)")
        currentlyTrackedIdentifier = identifier
        currentItemType = type
        sessionStartTimeMillis = SystemClock.elapsedRealtime()
    }

    private fun finalizeSession(identifier: String?) {
        if (identifier == null || sessionStartTimeMillis == null) {
            // Log.d(TAG, "FinalizeSession called with no active session for $identifier")
            clearCurrentSession()
            return
        }

        val endTimeMillis = SystemClock.elapsedRealtime()
        val sessionDurationMillis = endTimeMillis - sessionStartTimeMillis!!
        if (sessionDurationMillis <= 0) {
            // Log.d(TAG, "FinalizeSession: session duration is zero or negative for $identifier, skipping update.")
            clearCurrentSession()
            return
        }

        val previousTotalUsage = itemUsageTodayMillis.getOrDefault(identifier, 0L)
        val newTotalUsage = previousTotalUsage + sessionDurationMillis
        itemUsageTodayMillis[identifier] = newTotalUsage

        val itemConfig = blockedItems.find { it.identifier == identifier }
        Log.i(TAG, "Finalized session for ${itemConfig?.displayName ?: identifier}. Duration: ${sessionDurationMillis / 1000}s. Total today: ${newTotalUsage / 1000}s")
        clearCurrentSession()

        checkUsageLimits(identifier, newTotalUsage)
    }
    
    private fun clearCurrentSession() {
        currentlyTrackedIdentifier = null
        currentItemType = null
        sessionStartTimeMillis = null
        // Log.d(TAG, "Current session cleared.")
    }

    private fun checkUsageLimits(identifier: String, totalUsageMillis: Long) {
        val itemConfig = blockedItems.find { it.identifier == identifier }
        if (itemConfig != null) {
            val limitMillis = itemConfig.timeLimitInMinutes * 60 * 1000
            if (limitMillis > 0 && totalUsageMillis >= limitMillis) { // Only block if limit > 0
                Log.w(TAG, "Time limit EXCEEDED for ${itemConfig.displayName ?: identifier}! Usage: ${totalUsageMillis / 1000}s, Limit: ${limitMillis / 1000}s")
                showBlockScreen(itemConfig)
            } else {
                 Log.i(TAG, "Time limit NOT YET exceeded for ${itemConfig.displayName ?: identifier}. Usage: ${totalUsageMillis / 1000}s, Limit: ${limitMillis / 1000}s")
            }
        }
    }

    private fun showBlockScreen(item: BlockedItem) {
        val intent = Intent(this, BlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Clears other instances of BlockActivity if any
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_NAME, item.displayName ?: item.identifier)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_IDENTIFIER, item.identifier)
        try {
            startActivity(intent)
            Log.i(TAG, "BlockActivity launched for ${item.displayName ?: item.identifier}")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching BlockActivity: ", e)
            // Fallback or error handling if activity cannot be started from service
            // This might happen due to background activity start restrictions on newer Android versions
            // Consider posting a high-priority notification instead as a fallback.
        }
    }

    // ... (findUrlInNode and findUrlRecursive methods remain largely the same, ensure they are present) ...
    // findUrlInNode might need minor logging adjustments if any, but core logic should be fine.
    // findUrlRecursive can remain as is.

    // Ensure these methods are present and correct from the previous version.
    // This is a very basic and often unreliable way to get a URL.
    private fun findUrlInNode(nodeInfo: AccessibilityNodeInfo?, packageName: String): String? {
        if (nodeInfo == null) return null

        val urlBarIds = when {
            packageName.contains("chrome") -> listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/location_bar")
            packageName.contains("firefox") -> listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/toolbar_input")
            packageName.contains("duckduckgo") -> listOf("com.duckduckgo.mobile.android:id/omnibarTextInput")
            packageName.contains("brave") -> listOf("com.brave.browser:id/url_bar") // Often similar to Chrome
            packageName.contains("edge") -> listOf("com.microsoft.emmx:id/url_bar")
            packageName.contains("samsung.android.browser") -> listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text")
            else -> emptyList()
        }

        for (id in urlBarIds) {
            val urlBarNodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
            if (urlBarNodes != null && urlBarNodes.isNotEmpty()) {
                for (node in urlBarNodes) {
                    if (node != null && node.text != null) {
                        val text = node.text.toString()
                        if (text.startsWith("http://") || text.startsWith("https://") || text.contains(".")) {
                             Log.d(TAG, "URL found via ID '$id' for $packageName: $text")
                            return text
                        }
                    }
                    node?.recycle() // Recycle node after use
                }
                urlBarNodes.forEach { it?.recycle() } // Ensure all nodes in list are recycled
            }
        }
        
        // Fallback: Recursive search (can be heavy, consider if it's still needed or can be optimized)
        // Log.d(TAG, "No specific URL bar ID match for $packageName, trying recursive search...")
        // return findUrlRecursive(nodeInfo) // Commenting out for now to test ID-based first.
        return null // Preferring specific ID matches for now
    }

    private fun findUrlRecursive(nodeInfo: AccessibilityNodeInfo?): String? {
        if (nodeInfo == null) return null

        if (nodeInfo.text != null) {
            val text = nodeInfo.text.toString().trim()
            if (text.length > 5 && text.contains(".") && !text.contains(" ") &&
                (text.startsWith("http") || text.startsWith("www.") || text.count { it == '.' } >= 1)) {
                
                val viewId = nodeInfo.viewIdResourceName
                val className = nodeInfo.className?.toString() ?: ""

                if (className.contains("EditText", ignoreCase = true) ||
                    className.contains("TextView", ignoreCase = true) ||
                    className.contains("WebView", ignoreCase = true)) {
                    
                    if (text.length < 2048 && !text.contains("\n") && text.isNotBlank()) {
                         // Log.d(TAG, "Potential URL in EditText/TextView/WebView by ID '$viewId' or Class '$className': $text")
                        if (className.contains("EditText", ignoreCase = true) && (text.startsWith("http") || text.startsWith("www."))) {
                            return text
                        } else if (!className.contains("EditText", ignoreCase = true)){ 
                             return text
                        }
                    }
                }
            }
        }

        for (i in 0 until (nodeInfo.childCount ?: 0)) {
            val child = nodeInfo.getChild(i)
            val url = findUrlRecursive(child)
            if (url != null) {
                // child?.recycle() // Do not recycle if URL found, parent might need it
                return url
            }
            child?.recycle()
        }
        return null
    }


    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted. Finalizing any active session.")
        finalizeSession(currentlyTrackedIdentifier)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val currentServiceInfo = this.serviceInfo ?: AccessibilityServiceInfo()
        currentServiceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED // or AccessibilityEvent.TYPE_VIEW_FOCUSED
        currentServiceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        currentServiceInfo.flags = (currentServiceInfo.flags ?: 0) or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS // Ensure flags are ORed correctly
        currentServiceInfo.packageNames = null // Monitor all packages
        
        this.serviceInfo = currentServiceInfo
        Log.i(TAG, "onServiceConnected: WebsiteBlockerService connected.")
        itemUsageTodayMillis.clear()
        Log.d(TAG, "Item usage data cleared on service connect.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: WebsiteBlockerService unbound. Finalizing any active session.")
        finalizeSession(currentlyTrackedIdentifier)
        return super.onUnbind(intent)
    }
} 