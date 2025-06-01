package com.example.timesave

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WebsiteBlockerService : AccessibilityService() {

    private val TAG = "WebsiteBlockerService"
    private val CHECK_INTERVAL_MS = 30 * 1000L // 30 seconds

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageCheckRunnable: Runnable

    private lateinit var blockedItems: List<BlockedItem> // Now loaded from storage
    private lateinit var itemUsageTodayMillis: MutableMap<String, Long> // Now loaded from storage

    private var currentlyTrackedIdentifier: String? = null
    private var currentItemType: BlockType? = null
    private var sessionStartTimeMillis: Long? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            val rootInActiveWindow: AccessibilityNodeInfo? = rootInActiveWindow
            var currentUrl: String? = null
            if (packageName != null) {
                if (isBrowserApp(packageName) && rootInActiveWindow != null) {
                    currentUrl = findUrlInNode(rootInActiveWindow, packageName)
                }
            }
            handleTimeTracking(currentUrl, packageName)
        }
    }

    private fun isBrowserApp(packageName: String?): Boolean {
        return packageName != null && (packageName.contains("chrome") || packageName.contains("firefox") || packageName.contains("opera") || packageName.contains("duckduckgo") || packageName.contains("brave") || packageName.contains("edge") || packageName.contains("samsung.android.browser") || packageName.contains("webview"))
    }

    private fun handleTimeTracking(currentUrl: String?, currentPackageName: String?) {
        val previouslyTrackedIdentifier = currentlyTrackedIdentifier
        var newTrackedIdentifier: String? = null
        var newTrackedItemType: BlockType? = null

        if (currentPackageName != null) {
            blockedItems.find { it.identifier == currentPackageName && it.type == BlockType.APP }?.let {
                newTrackedIdentifier = it.identifier
                newTrackedItemType = it.type
            }
        }
        if (newTrackedIdentifier == null && currentUrl != null && isBrowserApp(currentPackageName)) {
            blockedItems.find { it.type == BlockType.WEBSITE && currentUrl.contains(it.identifier, ignoreCase = true) }?.let {
                newTrackedIdentifier = it.identifier
                newTrackedItemType = it.type
            }
        }
        if (previouslyTrackedIdentifier != null && previouslyTrackedIdentifier != newTrackedIdentifier) {
            finalizeSession(previouslyTrackedIdentifier)
        }
        if (newTrackedIdentifier != null && newTrackedIdentifier != previouslyTrackedIdentifier) {
            startNewSession(newTrackedIdentifier!!, newTrackedItemType!!)
        } else if (newTrackedIdentifier == null && previouslyTrackedIdentifier != null) {
            finalizeSession(previouslyTrackedIdentifier)
        }
    }

    private fun startNewSession(identifier: String, type: BlockType) {
        Log.i(TAG, "Starting new session for: $identifier (Type: $type)")
        currentlyTrackedIdentifier = identifier
        currentItemType = type
        sessionStartTimeMillis = SystemClock.elapsedRealtime()

        // Check for immediate block if time limit is 0
        val itemConfig = blockedItems.find { it.identifier == identifier }
        if (itemConfig != null && itemConfig.timeLimitInMinutes == 0L) {
            Log.w(TAG, "Immediate block triggered for ${itemConfig.displayName ?: identifier} (0 min limit).")
            // We don't need to finalize a session with duration, just show block screen.
            // The periodic checker will also catch this, but this is faster.
            showBlockScreen(itemConfig) 
            // We might want to clear the current session here as well, 
            // as showBlockScreen might not inherently stop further tracking if the user somehow gets back.
            // However, GLOBAL_ACTION_HOME should prevent that.
            // clearCurrentSession() // Consider if this is needed if GLOBAL_ACTION_HOME fails
        }
    }

    private fun finalizeSession(identifier: String?, preemptiveBlock: Boolean = false) {
        if (identifier == null || sessionStartTimeMillis == null) {
            clearCurrentSession()
            return
        }
        val endTimeMillis = SystemClock.elapsedRealtime()
        val sessionDurationMillis = endTimeMillis - sessionStartTimeMillis!!
        if (sessionDurationMillis > 0) {
            val previousTotalUsage = itemUsageTodayMillis.getOrDefault(identifier, 0L)
            val newTotalUsage = previousTotalUsage + sessionDurationMillis
            itemUsageTodayMillis[identifier] = newTotalUsage
            AppStorage.saveItemUsage(this, itemUsageTodayMillis) // Save usage
            val itemConfigForLog = blockedItems.find { it.identifier == identifier }
            Log.i(TAG, "Finalized session for ${itemConfigForLog?.displayName ?: identifier}. Duration: ${sessionDurationMillis / 1000}s. Total today: ${newTotalUsage / 1000}s")
            if (!preemptiveBlock) {
                 checkUsageLimitsAndBlockIfNeeded(identifier, newTotalUsage, false)
            }
        } else if (!preemptiveBlock) {
             val recordedUsageMillis = itemUsageTodayMillis.getOrDefault(identifier, 0L)
             checkUsageLimitsAndBlockIfNeeded(identifier, recordedUsageMillis, false)
        }
        
        val itemConfig = blockedItems.find { it.identifier == identifier }
        if (preemptiveBlock && itemConfig != null) {
            showBlockScreen(itemConfig)
        }
        clearCurrentSession()
    }
    
    private fun clearCurrentSession() {
        currentlyTrackedIdentifier = null
        currentItemType = null
        sessionStartTimeMillis = null
    }

    private fun checkUsageLimitsAndBlockIfNeeded(identifier: String, totalUsageMillis: Long, isPeriodicCheck: Boolean) {
        val itemConfig = blockedItems.find { it.identifier == identifier }
        if (itemConfig != null) {
            val limitMillis = itemConfig.timeLimitInMinutes * 60 * 1000

            // If limit is 0, it means block immediately. This should be caught by startNewSession or periodic check.
            // This function is typically called after a session, so if limit is 0, it implies it should have been blocked already.
            if (itemConfig.timeLimitInMinutes == 0L) {
                 if (!isPeriodicCheck) { // Avoid double logging if periodic check also caught it
                    Log.w(TAG, "Normal Check: Item '${itemConfig.displayName ?: identifier}' has 0 min limit and was accessed. Should be blocked.")
                 }
                 showBlockScreen(itemConfig) // Ensure block screen is shown
                 return
            }

            if (limitMillis > 0 && totalUsageMillis >= limitMillis) {
                if (!isPeriodicCheck) {
                    Log.w(TAG, "Normal Check: Time limit EXCEEDED for ${itemConfig.displayName ?: identifier}!")
                }
                showBlockScreen(itemConfig)
            } else {
                 Log.i(TAG, "Time limit NOT YET exceeded for ${itemConfig.displayName ?: identifier}.")
            }
        }
    }

    private fun showBlockScreen(item: BlockedItem) {
        val intent = Intent(this, BlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_NAME, item.displayName ?: item.identifier)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_IDENTIFIER, item.identifier)
        try {
            startActivity(intent)
            Log.i(TAG, "BlockActivity launched for ${item.displayName ?: item.identifier}")
            val homeActionSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.i(TAG, "Attempted GLOBAL_ACTION_HOME. Success: $homeActionSuccess")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching BlockActivity or performing home action: ", e)
            performGlobalAction(GLOBAL_ACTION_HOME) // Fallback attempt
        }
    }

    private fun findUrlInNode(nodeInfo: AccessibilityNodeInfo?, packageName: String): String? {
        if (nodeInfo == null) return null
        val urlBarIds = when {
            packageName.contains("chrome") -> listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/location_bar")
            packageName.contains("firefox") -> listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/toolbar_input")
            packageName.contains("duckduckgo") -> listOf("com.duckduckgo.mobile.android:id/omnibarTextInput")
            packageName.contains("brave") -> listOf("com.brave.browser:id/url_bar") 
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
                            return text
                        }
                    }
                    node?.recycle()
                }
                urlBarNodes.forEach { it?.recycle() } 
            }
        }
        return null 
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Load configuration and usage data
        blockedItems = AppStorage.loadBlockedItems(this)
        itemUsageTodayMillis = AppStorage.clearUsageIfNewDay(this) // Also handles daily reset

        if (blockedItems.isEmpty()) {
            // This means AppStorage returned its default because nothing was saved.
            // Let's save these defaults now so they persist for next time.
            blockedItems = AppStorage.getDefaultBlockedItems() // Get them again
            AppStorage.saveBlockedItems(this, blockedItems)
            Log.i(TAG, "No saved blocked items found. Loaded and saved default items.")
        }

        val currentServiceInfo = this.serviceInfo ?: AccessibilityServiceInfo()
        currentServiceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        currentServiceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        currentServiceInfo.flags = (currentServiceInfo.flags ?: 0) or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        currentServiceInfo.packageNames = null
        this.serviceInfo = currentServiceInfo
        Log.i(TAG, "onServiceConnected: WebsiteBlockerService connected. Loaded ${blockedItems.size} items.")
        Log.d(TAG, "Initial usage data: $itemUsageTodayMillis")

        setupPeriodicCheck()
        handler.post(usageCheckRunnable) 
    }

    private fun setupPeriodicCheck() {
        usageCheckRunnable = Runnable {
            checkCurrentItemUsageLimit()
            handler.postDelayed(usageCheckRunnable, CHECK_INTERVAL_MS)
        }
    }

    private fun checkCurrentItemUsageLimit() {
        val trackedId = currentlyTrackedIdentifier
        val sessionStart = sessionStartTimeMillis

        if (trackedId != null && sessionStart != null) {
            val itemConfig = blockedItems.find { it.identifier == trackedId }
            if (itemConfig != null) { // Check if itemConfig is found
                val limitMillis = itemConfig.timeLimitInMinutes * 60 * 1000
                
                if (limitMillis == 0L) { // Handle 0 minute limit separately for periodic check
                    Log.w(TAG, "Periodic Check: Immediate block condition for ${itemConfig.displayName ?: trackedId} (0 min limit).")
                    finalizeSession(trackedId, true) // true for preemptive block
                    return // No further duration check needed
                }

                // Only proceed with duration checks if limit > 0
                if (itemConfig.timeLimitInMinutes > 0) { 
                    val currentTimeMillis = SystemClock.elapsedRealtime()
                    val currentSessionDurationMillis = currentTimeMillis - sessionStart
                    val recordedUsageMillis = itemUsageTodayMillis.getOrDefault(trackedId, 0L)
                    val hypotheticalTotalUsageMillis = recordedUsageMillis + currentSessionDurationMillis

                    if (hypotheticalTotalUsageMillis >= limitMillis) {
                        Log.w(TAG, "Periodic Check: Time limit EXCEEDED for ${itemConfig.displayName ?: trackedId}!")
                        finalizeSession(trackedId, true)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted.")
        handler.removeCallbacks(usageCheckRunnable)
        finalizeSession(currentlyTrackedIdentifier) // This will save current session usage
        AppStorage.saveItemUsage(this, itemUsageTodayMillis) // Ensure all usage is saved
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: WebsiteBlockerService unbound.")
        handler.removeCallbacks(usageCheckRunnable)
        finalizeSession(currentlyTrackedIdentifier) // This will save current session usage
        AppStorage.saveItemUsage(this, itemUsageTodayMillis) // Ensure all usage is saved
        return super.onUnbind(intent)
    }
} 