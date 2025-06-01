package com.example.timesave

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class BlockedItemAdapter(
    private val context: Context,
    private var items: MutableList<BlockedItem>,
    private val onEditClicked: (BlockedItem, Int) -> Unit, // item, position
    private val onDeleteClicked: (BlockedItem, Int) -> Unit // item, position
) : RecyclerView.Adapter<BlockedItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemName.text = item.displayName ?: item.identifier
        holder.itemIdentifier.text = when(item.type) {
            BlockType.APP -> "App: ${item.identifier}"
            BlockType.WEBSITE -> "Site: ${item.identifier}"
        }
        
        if (item.timeLimitInMinutes == 0L) {
            holder.timeLimit.text = "Blocked"
        } else {
            val hours = item.timeLimitInMinutes / 60
            val minutes = item.timeLimitInMinutes % 60
            var timeString = ""
            if (hours > 0) {
                timeString += "${hours}h "
            }
            if (minutes > 0 || hours.toInt() == 0) { // Show minutes if they exist, or if hours is 0 (e.g. "0h 30min" or just "30min")
                timeString += "${minutes}m"
            }
            holder.timeLimit.text = timeString.trim()
        }

        // Load app icon if it's an app
        if (item.type == BlockType.APP) {
            try {
                val icon: Drawable = context.packageManager.getApplicationIcon(item.identifier)
                holder.itemIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                holder.itemIcon.setImageResource(R.mipmap.ic_launcher) // Fallback icon
            }
        } else {
            holder.itemIcon.setImageResource(R.drawable.ic_website) // Generic website icon
        }

        holder.editButton.setOnClickListener {
            onEditClicked(item, position)
        }
        holder.deleteButton.setOnClickListener {
            onDeleteClicked(item, position)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<BlockedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged() // Consider using DiffUtil for better performance later
    }
    
    fun updateItem(position: Int, item: BlockedItem) {
        if (position >= 0 && position < items.size) {
            items[position] = item
            notifyItemChanged(position)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemIcon: ImageView = view.findViewById(R.id.imageViewItemIcon)
        val itemName: TextView = view.findViewById(R.id.textViewItemName)
        val itemIdentifier: TextView = view.findViewById(R.id.textViewItemIdentifier)
        val timeLimit: TextView = view.findViewById(R.id.textViewTimeLimit)
        val editButton: ImageButton = view.findViewById(R.id.buttonEditLimit)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteItem)
    }
} 