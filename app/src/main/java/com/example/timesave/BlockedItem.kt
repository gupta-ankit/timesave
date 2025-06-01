package com.example.timesave

import kotlinx.serialization.Serializable

@Serializable
data class BlockedItem(
    val identifier: String, // hostname for website, package name for app
    val type: BlockType,
    var timeLimitInMinutes: Long, // Placeholder if using group limits, actual limit if per-item (not used for now)
    var displayName: String? = null,
    val groupId: String = "default_group" // All items belong to a group
) {
    // Optional: secondary constructor if you often create items without specifying groupId initially
    // constructor(identifier: String, type: BlockType, timeLimitInMinutes: Long, displayName: String? = null)
    // : this(identifier, type, timeLimitInMinutes, displayName, "default_group")
} 