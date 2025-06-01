package com.example.timesave

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppPickerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AppPickerAdapter
    private var appList: MutableList<AppInfo> = mutableListOf()

    companion object {
        const val EXTRA_APP_NAME = "com.example.timesave.EXTRA_APP_NAME"
        const val EXTRA_PACKAGE_NAME = "com.example.timesave.EXTRA_PACKAGE_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarAppPicker)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewApps)
        progressBar = findViewById(R.id.progressBarAppPicker)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppPickerAdapter(appList) { selectedApp ->
            val resultIntent = Intent()
            resultIntent.putExtra(EXTRA_APP_NAME, selectedApp.appName)
            resultIntent.putExtra(EXTRA_PACKAGE_NAME, selectedApp.packageName)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        recyclerView.adapter = adapter

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val installedApps = getInstalledAppsList()
            appList.clear()
            appList.addAll(installedApps)
            adapter.updateData(installedApps)
            progressBar.visibility = View.GONE
        }
    }

    private suspend fun getInstalledAppsList(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val launchableApps = mutableListOf<AppInfo>()

        for (appInfo in packages) {
            // Filter out system apps or apps without a launch intent, if desired
            if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                 // Optionally filter out system apps further if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                launchableApps.add(AppInfo(appName, appInfo.packageName, icon))
            }
        }
        // Sort apps by name
        launchableApps.sortBy { it.appName.lowercase() }
        return@withContext launchableApps
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 