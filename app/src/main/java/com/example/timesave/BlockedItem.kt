package com.example.timesave

data class BlockedItem(
    val identifier: String, // Can be a hostname (e.g., "youtube.com") or a package name (e.g., "com.google.android.youtube")
    val type: BlockType,
    val timeLimitInMinutes: Long,
    val displayName: String? = null // Optional: for a user-friendly name if identifier is a package name
) 