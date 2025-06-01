package com.example.timesave

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import android.widget.EditText
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater

class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BlockedItemAdapter
    private var blockedItemsList: MutableList<BlockedItem> = mutableListOf()
    private val TAG = "SettingsActivity"

    // Activity result launcher for AppPickerActivity
    private val appPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val appName = data?.getStringExtra(AppPickerActivity.EXTRA_APP_NAME)
            val packageName = data?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME)

            if (appName != null && packageName != null) {
                Log.d(TAG, "App selected: $appName ($packageName)")
                showAddItemDialog(BlockType.APP, packageName, appName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Blocked Items Settings"

        recyclerView = findViewById(R.id.recyclerViewBlockedItems)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadBlockedItems()

        adapter = BlockedItemAdapter(this, blockedItemsList,
            onEditClicked = { item, position -> showEditItemDialog(item, position) },
            onDeleteClicked = { item, position -> confirmDeleteItem(item, position) }
        )
        recyclerView.adapter = adapter

        val fab: FloatingActionButton = findViewById(R.id.fabAddBlockedItem)
        fab.setOnClickListener {
            showAddItemTypeDialog()
        }
    }

    private fun loadBlockedItems() {
        blockedItemsList.clear()
        blockedItemsList.addAll(AppStorage.loadBlockedItems(this))
        if (::adapter.isInitialized) {
            adapter.updateData(blockedItemsList)
        }
        Log.d(TAG, "Loaded items: ${blockedItemsList.size}")
    }

    private fun saveBlockedItems() {
        AppStorage.saveBlockedItems(this, blockedItemsList)
        Log.d(TAG, "Saved items: ${blockedItemsList.size}")
        // Notify service if running? For now, service reloads on its connect.
    }

    private fun showAddItemTypeDialog() {
        val options = arrayOf("Block an App", "Block a Website")
        AlertDialog.Builder(this)
            .setTitle("Add New Blocked Item")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Block an App
                        val intent = Intent(this, AppPickerActivity::class.java)
                        appPickerLauncher.launch(intent)
                    }
                    1 -> showAddItemDialog(BlockType.WEBSITE) // Block a Website
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddItemDialog(type: BlockType, prefillIdentifier: String? = null, prefillName: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_item, null)
        val editTextIdentifier = dialogView.findViewById<EditText>(R.id.editTextIdentifier)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val numberPickerHours = dialogView.findViewById<NumberPicker>(R.id.numberPickerHours)
        val numberPickerMinutes = dialogView.findViewById<NumberPicker>(R.id.numberPickerMinutes)
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)
        val textInputLayoutName = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutName)

        numberPickerHours.minValue = 0
        numberPickerHours.maxValue = 23 // Max 23 hours
        numberPickerHours.value = 1 // Default 1 hour

        numberPickerMinutes.minValue = 0 // Allow 0 minutes
        numberPickerMinutes.maxValue = 59 // Max 59 minutes
        numberPickerMinutes.value = 0 // Default 0 minutes

        val title: String
        when (type) {
            BlockType.APP -> {
                title = "Block New App"
                editTextIdentifier.setText(prefillIdentifier ?: "")
                editTextIdentifier.isEnabled = prefillIdentifier == null // Lock if prefilled from picker
                editTextName.setText(prefillName ?: "")
                textInputLayoutIdentifier.hint = "Package Name"
                textInputLayoutName.hint = "App Name (Optional)"
            }
            BlockType.WEBSITE -> {
                title = "Block New Website"
                textInputLayoutIdentifier.hint = "Hostname (e.g., example.com)"
                textInputLayoutName.hint = "Website Name (Optional)"
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val identifier = editTextIdentifier.text.toString().trim()
                var displayName = editTextName.text.toString().trim()
                val hours = numberPickerHours.value
                val minutes = numberPickerMinutes.value
                val totalTimeLimitInMinutes = (hours * 60 + minutes).toLong()

                if (identifier.isEmpty()) {
                    Toast.makeText(this, "Identifier cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (displayName.isEmpty()) displayName = identifier // Default display name to identifier

                val newItem = BlockedItem(identifier, type, totalTimeLimitInMinutes, displayName)
                blockedItemsList.add(newItem)
                adapter.notifyItemInserted(blockedItemsList.size - 1)
                saveBlockedItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditItemDialog(item: BlockedItem, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_item, null)
        val editTextIdentifier = dialogView.findViewById<EditText>(R.id.editTextIdentifier)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val numberPickerHours = dialogView.findViewById<NumberPicker>(R.id.numberPickerHours)
        val numberPickerMinutes = dialogView.findViewById<NumberPicker>(R.id.numberPickerMinutes)
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)

        editTextIdentifier.setText(item.identifier)
        editTextIdentifier.isEnabled = false // Cannot edit identifier
        editTextName.setText(item.displayName ?: "")
        
        textInputLayoutIdentifier.hint = if(item.type == BlockType.APP) "Package Name" else "Hostname"

        val currentTotalMinutes = item.timeLimitInMinutes
        val currentHours = (currentTotalMinutes / 60).toInt()
        val currentMinutes = (currentTotalMinutes % 60).toInt()

        numberPickerHours.minValue = 0
        numberPickerHours.maxValue = 23
        numberPickerHours.value = currentHours.coerceIn(0, 23)

        numberPickerMinutes.minValue = 0
        numberPickerMinutes.maxValue = 59
        numberPickerMinutes.value = currentMinutes.coerceIn(0, 59)

        AlertDialog.Builder(this)
            .setTitle("Edit Time Limit for ${item.displayName ?: item.identifier}")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                var newDisplayName = editTextName.text.toString().trim()
                val hours = numberPickerHours.value
                val minutes = numberPickerMinutes.value
                val newTotalTimeLimit = (hours * 60 + minutes).toLong()

                if (newDisplayName.isEmpty()) newDisplayName = item.identifier

                val updatedItem = item.copy(timeLimitInMinutes = newTotalTimeLimit, displayName = newDisplayName)
                blockedItemsList[position] = updatedItem
                adapter.updateItem(position, updatedItem)
                saveBlockedItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteItem(item: BlockedItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete '${item.displayName ?: item.identifier}' from the blocklist?")
            .setPositiveButton("Delete") { dialog, _ ->
                // Ensure position is valid before removing
                if (position >= 0 && position < blockedItemsList.size) {
                    Log.d(TAG, "Deleting item at position: $position, item: ${blockedItemsList[position].identifier}")
                    blockedItemsList.removeAt(position) // Modify the list
                    
                    // Notify the adapter about the removal
                    adapter.notifyItemRemoved(position)
                    
                    // Notify that items after the removed one have shifted
                    // The range starts at 'position' and covers the rest of the items in the (newly sized) list
                    if (position < blockedItemsList.size) { // Check if there are items after the removed one
                        adapter.notifyItemRangeChanged(position, blockedItemsList.size - position)
                    }
                    
                    saveBlockedItems() // Save the updated list
                    Log.d(TAG, "Item deleted. New list size: ${blockedItemsList.size}")
                } else {
                    Log.w(TAG, "Attempted to delete item at invalid position: $position. List size: ${blockedItemsList.size}")
                    Toast.makeText(this, "Error deleting item. Please try again.", Toast.LENGTH_SHORT).show()
                    loadBlockedItems() // Resync adapter with blockedItemsList as a fallback
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 