package com.example.timesave

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppPickerAdapter(
    private var apps: List<AppInfo>,
    private val onItemClicked: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppPickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        holder.appIcon.setImageDrawable(app.icon)

        holder.itemView.setOnClickListener {
            onItemClicked(app)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged() // Consider DiffUtil for large lists
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.imageViewAppIcon)
        val appName: TextView = view.findViewById(R.id.textViewAppName)
        val packageName: TextView = view.findViewById(R.id.textViewPackageName)
    }
} 