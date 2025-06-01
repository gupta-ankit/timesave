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
    private val CHECK_INTERVAL_MS = 30 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageCheckRunnable: Runnable

    private lateinit var distractingItems: List<BlockedItem>
    private lateinit var itemUsageTodayMillis: MutableMap<String, Long> // For individual stats, if needed
    
    private var defaultGroupTimeLimitMillis: Long = 0L
    private var defaultGroupUsageTodayMillis: Long = 0L

    private var currentlyTrackedIdentifier: String? = null
    private var currentlyTrackedItemConfig: BlockedItem? = null // Store the full item for groupId access
    private var sessionStartTimeMillis: Long? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString()
            val rootInActiveWindow: AccessibilityNodeInfo? = rootInActiveWindow
            var currentUrl: String? = null
            if (packageName != null && isBrowserApp(packageName) && rootInActiveWindow != null) {
                currentUrl = findUrlInNode(rootInActiveWindow, packageName)
            }
            handleTimeTracking(currentUrl, packageName)
        }
    }

    private fun isBrowserApp(packageName: String?): Boolean {
        return packageName != null && (packageName.contains("chrome") || packageName.contains("firefox") || packageName.contains("opera") || packageName.contains("duckduckgo") || packageName.contains("brave") || packageName.contains("edge") || packageName.contains("samsung.android.browser") || packageName.contains("webview"))
    }

    private fun handleTimeTracking(currentUrl: String?, currentPackageName: String?) {
        val previouslyTrackedItem = currentlyTrackedItemConfig
        var newTrackedItem: BlockedItem? = null

        if (currentPackageName != null) {
            newTrackedItem = distractingItems.find { it.identifier == currentPackageName && it.type == BlockType.APP && it.groupId == AppStorage.DEFAULT_GROUP_ID }
        }
        if (newTrackedItem == null && currentUrl != null && isBrowserApp(currentPackageName)) {
            newTrackedItem = distractingItems.find { it.type == BlockType.WEBSITE && currentUrl.contains(it.identifier, ignoreCase = true) && it.groupId == AppStorage.DEFAULT_GROUP_ID }
        }

        if (previouslyTrackedItem != null && previouslyTrackedItem.identifier != newTrackedItem?.identifier) {
            finalizeSession(previouslyTrackedItem)
        }

        if (newTrackedItem != null && newTrackedItem.identifier != previouslyTrackedItem?.identifier) {
            startNewSession(newTrackedItem)
        } else if (newTrackedItem == null && previouslyTrackedItem != null) {
            finalizeSession(previouslyTrackedItem)
        }
    }

    private fun startNewSession(item: BlockedItem) {
        Log.i(TAG, "Starting new session for distracting item: ${item.displayName ?: item.identifier} (Group: ${item.groupId})")
        currentlyTrackedIdentifier = item.identifier // Keep for compatibility if needed elsewhere
        currentlyTrackedItemConfig = item
        sessionStartTimeMillis = SystemClock.elapsedRealtime()

        if (item.groupId == AppStorage.DEFAULT_GROUP_ID && defaultGroupTimeLimitMillis == 0L) {
            Log.w(TAG, "Immediate block: Default group limit is 0. Blocking ${item.displayName ?: item.identifier}")
            showBlockScreen(item)
        }
    }

    private fun finalizeSession(itemConfigToFinalize: BlockedItem?, preemptiveBlock: Boolean = false) {
        if (itemConfigToFinalize == null || sessionStartTimeMillis == null) {
            clearCurrentSession()
            return
        }

        val endTimeMillis = SystemClock.elapsedRealtime()
        val sessionDurationMillis = endTimeMillis - sessionStartTimeMillis!!

        if (sessionDurationMillis > 0 && itemConfigToFinalize.groupId == AppStorage.DEFAULT_GROUP_ID) {
            // Update individual item usage (for stats)
            val previousIndividualUsage = itemUsageTodayMillis.getOrDefault(itemConfigToFinalize.identifier, 0L)
            val newIndividualUsage = previousIndividualUsage + sessionDurationMillis
            itemUsageTodayMillis[itemConfigToFinalize.identifier] = newIndividualUsage
            AppStorage.saveItemUsage(this, itemUsageTodayMillis)

            // Update default group usage
            defaultGroupUsageTodayMillis += sessionDurationMillis
            AppStorage.saveGroupUsage(this, AppStorage.DEFAULT_GROUP_ID, defaultGroupUsageTodayMillis)
            
            Log.i(TAG, "Finalized session for ${itemConfigToFinalize.displayName ?: itemConfigToFinalize.identifier}. Group: ${itemConfigToFinalize.groupId}. Duration: ${sessionDurationMillis / 1000}s. Group total: ${defaultGroupUsageTodayMillis / 1000}s")
            
            if (!preemptiveBlock) {
                 checkGroupUsageLimitAndBlockIfNeeded(itemConfigToFinalize, defaultGroupUsageTodayMillis, defaultGroupTimeLimitMillis)
            }
        } else if (!preemptiveBlock && itemConfigToFinalize.groupId == AppStorage.DEFAULT_GROUP_ID) {
             checkGroupUsageLimitAndBlockIfNeeded(itemConfigToFinalize, defaultGroupUsageTodayMillis, defaultGroupTimeLimitMillis)
        }
        
        if (preemptiveBlock) { // itemConfigToFinalize must be non-null if preemptiveBlock is true from periodic check
            showBlockScreen(itemConfigToFinalize)
        }
        clearCurrentSession()
    }
    
    private fun clearCurrentSession() {
        currentlyTrackedIdentifier = null
        currentlyTrackedItemConfig = null
        sessionStartTimeMillis = null
    }

    // Updated signature to take group usage and limit
    private fun checkGroupUsageLimitAndBlockIfNeeded(currentItem: BlockedItem, groupUsage: Long, groupLimit: Long, isPeriodicCheck: Boolean = false) {
        if (currentItem.groupId != AppStorage.DEFAULT_GROUP_ID) return // Only act on default group for now

        if (groupLimit == 0L && groupUsage > 0) { 
            Log.w(TAG, "Group '${currentItem.groupId}' limit is 0. Blocking ${currentItem.displayName ?: currentItem.identifier} due to usage.")
            showBlockScreen(currentItem)
            return
        }

        if (groupLimit > 0 && groupUsage >= groupLimit) {
            val logPrefix = if (isPeriodicCheck) "Periodic Check: " else ""
            Log.w(TAG, "${logPrefix}Group '${currentItem.groupId}' time limit EXCEEDED! Total: ${groupUsage/1000}s, Limit: ${groupLimit/1000}s. Blocking ${currentItem.displayName ?: currentItem.identifier}")
            showBlockScreen(currentItem)
        } else {
             Log.i(TAG, "Group '${currentItem.groupId}' time limit NOT YET exceeded. Total: ${groupUsage/1000}s, Limit: ${groupLimit/1000}s")
        }
    }

    private fun showBlockScreen(item: BlockedItem) {
        val intent = Intent(this, BlockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_NAME, item.displayName ?: item.identifier)
        intent.putExtra(BlockActivity.EXTRA_BLOCKED_ITEM_IDENTIFIER, item.identifier)
        try {
            startActivity(intent)
            Log.i(TAG, "BlockActivity launched for ${item.displayName ?: item.identifier} (Group: ${item.groupId}).")
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching BlockActivity or performing home action: ", e)
            performGlobalAction(GLOBAL_ACTION_HOME) 
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
                            node.recycle() // Recycle node after use
                            urlBarNodes.forEach { it?.recycle() } // Recycle list nodes
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
        Log.i(TAG, "onServiceConnected: WebsiteBlockerService connecting...")
        distractingItems = AppStorage.loadBlockedItems(this)
        defaultGroupTimeLimitMillis = AppStorage.loadGroupTimeLimit(this, AppStorage.DEFAULT_GROUP_ID) * 60 * 1000

        val dailyData = AppStorage.clearUsageIfNewDay(this)
        itemUsageTodayMillis = dailyData.itemUsage
        defaultGroupUsageTodayMillis = dailyData.groupUsage[AppStorage.DEFAULT_GROUP_ID] ?: 0L

        if (distractingItems.isEmpty()) {
            distractingItems = AppStorage.getDefaultBlockedItems()
            AppStorage.saveBlockedItems(this, distractingItems)
            Log.i(TAG, "No saved distracting items. Loaded and saved default items.")
        }

        val currentServiceInfo = this.serviceInfo ?: AccessibilityServiceInfo()
        currentServiceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        currentServiceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        currentServiceInfo.flags = (currentServiceInfo.flags ?: 0) or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        currentServiceInfo.packageNames = null 
        this.serviceInfo = currentServiceInfo
        
        Log.i(TAG, "Service connected. Monitoring ${distractingItems.size} items in group '${AppStorage.DEFAULT_GROUP_ID}'.")
        Log.i(TAG, "Default group limit: ${defaultGroupTimeLimitMillis / (60*1000)} minutes. Current group usage: ${defaultGroupUsageTodayMillis / 1000}s.")

        setupPeriodicCheck()
        handler.post(usageCheckRunnable) 
    }

    private fun setupPeriodicCheck() {
        usageCheckRunnable = Runnable {
            checkCurrentItemGroupUsageLimit()
            handler.postDelayed(usageCheckRunnable, CHECK_INTERVAL_MS)
        }
    }

    private fun checkCurrentItemGroupUsageLimit() {
        val currentItem = currentlyTrackedItemConfig
        val sessionStart = sessionStartTimeMillis

        if (currentItem != null && currentItem.groupId == AppStorage.DEFAULT_GROUP_ID && sessionStart != null) {
            if (defaultGroupTimeLimitMillis == 0L) { 
                Log.w(TAG, "Periodic Check: Default group limit is 0. Blocking active item ${currentItem.displayName ?: currentItem.identifier}")
                finalizeSession(currentItem, true) 
                return
            }

            if (defaultGroupTimeLimitMillis > 0) { 
                val currentTime = SystemClock.elapsedRealtime()
                val currentSessionDuration = currentTime - sessionStart
                val hypotheticalTotalGroupUsage = defaultGroupUsageTodayMillis + currentSessionDuration

                if (hypotheticalTotalGroupUsage >= defaultGroupTimeLimitMillis) {
                    Log.w(TAG, "Periodic Check: Default group time limit EXCEEDED for active item ${currentItem.displayName ?: currentItem.identifier}! Hypo Usage: ${hypotheticalTotalGroupUsage/1000}s")
                    finalizeSession(currentItem, true)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted.")
        handler.removeCallbacks(usageCheckRunnable)
        finalizeSession(currentlyTrackedItemConfig) 
        AppStorage.saveGroupUsage(this, AppStorage.DEFAULT_GROUP_ID, defaultGroupUsageTodayMillis) 
        AppStorage.saveItemUsage(this, itemUsageTodayMillis) 
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: WebsiteBlockerService unbound.")
        handler.removeCallbacks(usageCheckRunnable)
        finalizeSession(currentlyTrackedItemConfig) 
        AppStorage.saveGroupUsage(this, AppStorage.DEFAULT_GROUP_ID, defaultGroupUsageTodayMillis)
        AppStorage.saveItemUsage(this, itemUsageTodayMillis)
        return super.onUnbind(intent)
    }
} 