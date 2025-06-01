package com.example.timesave

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var textViewStatus: TextView
    private lateinit var buttonEnableService: Button
    private lateinit var buttonOpenSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewStatus = findViewById(R.id.textViewStatus)
        buttonEnableService = findViewById(R.id.buttonEnableService)
        buttonOpenSettings = findViewById(R.id.buttonOpenSettings)

        buttonEnableService.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: Open general settings if accessibility settings are not directly accessible
                // This might happen on some custom Android ROMs
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        buttonOpenSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusText()
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices == null || enabledServices.isEmpty()) {
            return false
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(getPackageName() + "/" + service.name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun updateServiceStatusText() {
        if (isAccessibilityServiceEnabled(this, WebsiteBlockerService::class.java)) {
            textViewStatus.text = "Service Status: Enabled"
            textViewStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            buttonEnableService.text = "Accessibility Settings"
        } else {
            textViewStatus.text = "Service Status: Disabled - Please enable the service"
            textViewStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            buttonEnableService.text = "Enable Website Blocker Service"
        }
    }
} 