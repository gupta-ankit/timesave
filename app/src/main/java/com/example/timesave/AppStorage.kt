package com.example.timesave

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

// kotlinx.serialization imports
import kotlinx.serialization.KSerializer // For KSerializer interface
import kotlinx.serialization.builtins.ListSerializer // For List collections
import kotlinx.serialization.builtins.MapSerializer // For Map collections
import kotlinx.serialization.builtins.serializer // For basic type serializers like String.serializer(), Long.serializer()
import kotlinx.serialization.json.Json // The main entry point for JSON serialization
import kotlinx.serialization.encodeToString // Extension function on Json object
// Note: decodeFromString is also an extension function on Json, implicitly available if Json is imported.

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppStorage {
    private const val PREFS_NAME = "com.example.timesave.prefs"
    private const val KEY_BLOCKED_ITEMS = "blocked_items"
    private const val KEY_ITEM_USAGE_MILLIS = "item_usage_millis" // Individual usage for stats
    private const val KEY_LAST_RESET_DATE = "last_reset_date"

    // Group-based keys
    private const val KEY_GROUP_TIME_LIMIT_MINUTES_PREFIX = "group_time_limit_minutes_"
    private const val KEY_GROUP_USAGE_MILLIS_PREFIX = "group_usage_millis_"
    
    const val DEFAULT_GROUP_ID = "default_group" // Made public for access from service/settings

    private val TAG = "AppStorage"

    // Explicit Json configuration (optional, defaults are usually fine)
    private val jsonFormat = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Serializers for complex types
    private val blockedItemListSerializer: KSerializer<List<BlockedItem>> = ListSerializer(BlockedItem.serializer())
    private val usageMapSerializer: KSerializer<Map<String, Long>> = MapSerializer(String.serializer(), Long.serializer())

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Group Time Limit ---
    fun saveGroupTimeLimit(context: Context, groupId: String, limitInMinutes: Long) {
        val prefs = getPreferences(context)
        prefs.edit().putLong(KEY_GROUP_TIME_LIMIT_MINUTES_PREFIX + groupId, limitInMinutes).apply()
        Log.d(TAG, "Saved time limit for group '$groupId': $limitInMinutes minutes")
    }

    fun loadGroupTimeLimit(context: Context, groupId: String): Long {
        val prefs = getPreferences(context)
        // Default to 60 minutes if not set for any group
        val limit = prefs.getLong(KEY_GROUP_TIME_LIMIT_MINUTES_PREFIX + groupId, 60L)
        Log.d(TAG, "Loaded time limit for group '$groupId': $limit minutes")
        return limit
    }

    // --- Group Usage ---
    fun saveGroupUsage(context: Context, groupId: String, usageMillis: Long) {
        val prefs = getPreferences(context)
        prefs.edit().putLong(KEY_GROUP_USAGE_MILLIS_PREFIX + groupId, usageMillis).apply()
        Log.d(TAG, "Saved usage for group '$groupId': $usageMillis ms")
    }

    fun loadGroupUsage(context: Context, groupId: String): Long {
        val prefs = getPreferences(context)
        val usage = prefs.getLong(KEY_GROUP_USAGE_MILLIS_PREFIX + groupId, 0L)
        Log.d(TAG, "Loaded usage for group '$groupId': $usage ms")
        return usage
    }
    
    // --- Blocked Items (List of distracting items, now with groupId) ---
    fun saveBlockedItems(context: Context, items: List<BlockedItem>) {
        val prefs = getPreferences(context)
        try {
            val jsonString = jsonFormat.encodeToString(blockedItemListSerializer, items)
            prefs.edit().putString(KEY_BLOCKED_ITEMS, jsonString).apply()
            Log.d(TAG, "Saved blocked items: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving blocked items", e)
        }
    }

    fun loadBlockedItems(context: Context): List<BlockedItem> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_BLOCKED_ITEMS, null)
        return if (jsonString != null) {
            try {
                val items = jsonFormat.decodeFromString(blockedItemListSerializer, jsonString)
                // Ensure loaded items have a groupId, default if missing (for backward compatibility if needed)
                items.map { if (it.groupId.isBlank()) it.copy(groupId = DEFAULT_GROUP_ID) else it }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked items from JSON: '$jsonString'", e)
                getDefaultBlockedItems() 
            }
        } else {
            Log.d(TAG, "No saved blocked items, returning default.")
            getDefaultBlockedItems() 
        }
    }

    // --- Individual Item Usage (still useful for stats) ---
    fun saveItemUsage(context: Context, usageMap: Map<String, Long>) {
        val prefs = getPreferences(context)
        try {
            val jsonString = jsonFormat.encodeToString(usageMapSerializer, usageMap)
            prefs.edit().putString(KEY_ITEM_USAGE_MILLIS, jsonString).apply()
            Log.d(TAG, "Saved item usage: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving item usage", e)
        }
    }

    fun loadItemUsage(context: Context): MutableMap<String, Long> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_ITEM_USAGE_MILLIS, null)
        return if (jsonString != null) {
            try {
                if (jsonString == "{}") { 
                    mutableMapOf()
                } else {
                    jsonFormat.decodeFromString(usageMapSerializer, jsonString).toMutableMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading item usage from JSON: '$jsonString'", e)
                mutableMapOf() 
            }
        } else {
            mutableMapOf()
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    data class DailyUsageData(
        val itemUsage: MutableMap<String, Long>,
        val groupUsage: Map<String, Long> // Now a map of groupId to usage
    )

    fun clearUsageIfNewDay(context: Context): DailyUsageData {
        val prefs = getPreferences(context)
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val todayDate = getTodayDateString()

        val editor = prefs.edit()
        var resetOccurred = false

        if (lastResetDate != todayDate) {
            Log.i(TAG, "New day detected ($todayDate from $lastResetDate). Clearing daily usage data.")
            editor.putString(KEY_ITEM_USAGE_MILLIS, jsonFormat.encodeToString(usageMapSerializer, emptyMap()))
            
            // Clear known group usages (for now, just default_group)
            // In future, might iterate over known group IDs or find all keys with prefix
            editor.putLong(KEY_GROUP_USAGE_MILLIS_PREFIX + DEFAULT_GROUP_ID, 0L)
            
            editor.putString(KEY_LAST_RESET_DATE, todayDate)
            editor.apply()
            resetOccurred = true
        }

        val groupUsageMap = mutableMapOf<String, Long>()
        // Load usage for default group. If more groups, loop or load all.
        groupUsageMap[DEFAULT_GROUP_ID] = if(resetOccurred) 0L else loadGroupUsage(context, DEFAULT_GROUP_ID)

        return DailyUsageData(
            itemUsage = loadItemUsage(context), // loadItemUsage is fine, it returns empty if reset
            groupUsage = groupUsageMap
        )
    }

    internal fun getDefaultBlockedItems(): List<BlockedItem> {
        return listOf(
            // groupId is defaulted in BlockedItem constructor, timeLimitInMinutes is placeholder
            BlockedItem("com.example.blockedapp", BlockType.APP, 0, "Sample Blocked App"),
            BlockedItem("youtube.com", BlockType.WEBSITE, 0, "YouTube Website"),
            BlockedItem("com.google.android.youtube", BlockType.APP, 0, "YouTube App"),
            BlockedItem("facebook.com", BlockType.WEBSITE, 0, "Facebook Website"),
            BlockedItem("com.facebook.katana", BlockType.APP, 0, "Facebook App")
        )
    }
} 