package com.example.timesave

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

// Note: Drawable is not directly serializable with kotlinx.serialization by default.
// If you needed to serialize AppInfo (e.g., pass it complexly), you'd handle Drawable separately (e.g., store path or skip).
// For this picker, we only use it in memory for display.

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
) 