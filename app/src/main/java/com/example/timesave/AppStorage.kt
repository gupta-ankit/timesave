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
    private const val KEY_ITEM_USAGE_MILLIS = "item_usage_millis"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"

    private val TAG = "AppStorage"

    // Explicit Json configuration (optional, defaults are usually fine)
    private val jsonFormat = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Serializers for complex types
    private val blockedItemListSerializer: KSerializer<List<BlockedItem>> = ListSerializer(BlockedItem.serializer())
    private val usageMapSerializer: KSerializer<Map<String, Long>> = MapSerializer(String.serializer(), Long.serializer())

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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
                Log.d(TAG, "Loaded blocked items: $items")
                items
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked items from JSON: '$jsonString'", e)
                getDefaultBlockedItems() // Fallback to default
            }
        } else {
            Log.d(TAG, "No saved blocked items, returning default.")
            getDefaultBlockedItems() // Return default if nothing saved
        }
    }

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
                if (jsonString == "{}") { // Handle empty map explicitly if saved as such
                    Log.d(TAG, "Loaded empty item usage map string.")
                    mutableMapOf()
                } else {
                    val map = jsonFormat.decodeFromString(usageMapSerializer, jsonString)
                    Log.d(TAG, "Loaded item usage: $map")
                    map.toMutableMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading item usage from JSON: '$jsonString'", e)
                mutableMapOf() // Fallback to empty
            }
        } else {
            Log.d(TAG, "No saved item usage, returning empty map.")
            mutableMapOf()
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun clearUsageIfNewDay(context: Context): MutableMap<String, Long> {
        val prefs = getPreferences(context)
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val todayDate = getTodayDateString()

        if (lastResetDate != todayDate) {
            Log.i(TAG, "New day detected ($todayDate from $lastResetDate). Clearing daily usage data.")
            // Explicitly save an empty JSON map string
            prefs.edit().putString(KEY_ITEM_USAGE_MILLIS, jsonFormat.encodeToString(usageMapSerializer, emptyMap()))
                .putString(KEY_LAST_RESET_DATE, todayDate)
                .apply()
            return mutableMapOf()
        } else {
            Log.d(TAG, "Same day ($todayDate). Loading existing usage data.")
            return loadItemUsage(context)
        }
    }

    internal fun getDefaultBlockedItems(): List<BlockedItem> {
        return listOf(
            BlockedItem("com.example.blockedapp", BlockType.APP, 0, "InstaBlock Test App (0 min)"), // For testing 0 min block
            BlockedItem("youtube.com", BlockType.WEBSITE, 60, "YouTube Website"),
            BlockedItem("com.google.android.youtube", BlockType.APP, 60, "YouTube App"),
            BlockedItem("facebook.com", BlockType.WEBSITE, 30, "Facebook Website"),
            BlockedItem("com.facebook.katana", BlockType.APP, 30, "Facebook App")
            // Add more defaults as needed
        )
    }
} 