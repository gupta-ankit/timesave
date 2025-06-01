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
import android.widget.Button

class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BlockedItemAdapter
    private var blockedItemsList: MutableList<BlockedItem> = mutableListOf()
    private val TAG = "SettingsActivity"

    private lateinit var numberPickerGroupHours: NumberPicker
    private lateinit var numberPickerGroupMinutes: NumberPicker
    private lateinit var buttonSaveGroupLimit: Button

    private var currentGroupTimeLimitInMinutes: Long = 60L // For the "default_group"

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
        supportActionBar?.title = "Distraction Settings"

        // Group Limit UI (for "default_group")
        numberPickerGroupHours = findViewById(R.id.numberPickerGlobalHours)
        numberPickerGroupMinutes = findViewById(R.id.numberPickerGlobalMinutes)
        buttonSaveGroupLimit = findViewById(R.id.buttonSaveGlobalLimit)

        numberPickerGroupHours.minValue = 0
        numberPickerGroupHours.maxValue = 23
        numberPickerGroupMinutes.minValue = 0
        numberPickerGroupMinutes.maxValue = 59

        loadDefaultGroupLimit()

        buttonSaveGroupLimit.setOnClickListener {
            val hours = numberPickerGroupHours.value
            val minutes = numberPickerGroupMinutes.value
            currentGroupTimeLimitInMinutes = (hours * 60 + minutes).toLong()
            AppStorage.saveGroupTimeLimit(this, AppStorage.DEFAULT_GROUP_ID, currentGroupTimeLimitInMinutes)
            Toast.makeText(this, "Daily limit saved: ${formatTimeLimit(currentGroupTimeLimitInMinutes)}", Toast.LENGTH_SHORT).show()
        }
        buttonSaveGroupLimit.text = "Save Daily Limit"

        // Blocked Items List UI
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

    private fun loadDefaultGroupLimit(){
        currentGroupTimeLimitInMinutes = AppStorage.loadGroupTimeLimit(this, AppStorage.DEFAULT_GROUP_ID)
        val hours = (currentGroupTimeLimitInMinutes / 60).toInt()
        val minutes = (currentGroupTimeLimitInMinutes % 60).toInt()
        numberPickerGroupHours.value = hours
        numberPickerGroupMinutes.value = minutes
        Log.d(TAG, "Loaded limit for group '${AppStorage.DEFAULT_GROUP_ID}': ${formatTimeLimit(currentGroupTimeLimitInMinutes)}")
    }
    
    private fun formatTimeLimit(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun loadBlockedItems() {
        blockedItemsList.clear()
        blockedItemsList.addAll(AppStorage.loadBlockedItems(this))
        if (::adapter.isInitialized) {
            adapter.updateData(blockedItemsList)
        }
        Log.d(TAG, "Loaded distracting items: ${blockedItemsList.size}")
    }

    private fun saveBlockedItems() {
        AppStorage.saveBlockedItems(this, blockedItemsList)
        Log.d(TAG, "Saved distracting items: ${blockedItemsList.size}")
    }

    private fun showAddItemTypeDialog() {
        val options = arrayOf("Add Distracting App", "Add Distracting Website")
        AlertDialog.Builder(this)
            .setTitle("Add Item to Distraction List")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, AppPickerActivity::class.java)
                        appPickerLauncher.launch(intent)
                    }
                    1 -> showAddItemDialog(BlockType.WEBSITE) 
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
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)
        val textInputLayoutName = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutName)

        val title: String
        when (type) {
            BlockType.APP -> {
                title = "Add Distracting App"
                editTextIdentifier.setText(prefillIdentifier ?: "")
                editTextIdentifier.isEnabled = prefillIdentifier == null 
                editTextName.setText(prefillName ?: "")
                textInputLayoutIdentifier.hint = "Package Name"
                textInputLayoutName.hint = "App Name (Optional)"
            }
            BlockType.WEBSITE -> {
                title = "Add Distracting Website"
                textInputLayoutIdentifier.hint = "Hostname (e.g., example.com)"
                textInputLayoutName.hint = "Website Name (Optional)"
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Add to List") { dialog, _ ->
                val identifier = editTextIdentifier.text.toString().trim()
                var displayName = editTextName.text.toString().trim()
                
                // timeLimitInMinutes is placeholder (0L), groupId is AppStorage.DEFAULT_GROUP_ID
                val newItem = BlockedItem(identifier, type, 0L, displayName, AppStorage.DEFAULT_GROUP_ID)

                if (identifier.isEmpty()) {
                    Toast.makeText(this, "Identifier cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (displayName.isEmpty()) displayName =
                    newItem.copy(displayName = identifier).displayName.toString() // ensure display name is set
                
                // Ensure updated display name is used if it was defaulted
                val finalNewItem = newItem.copy(displayName = displayName)

                blockedItemsList.add(finalNewItem)
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
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)

        editTextIdentifier.setText(item.identifier)
        editTextIdentifier.isEnabled = false 
        editTextName.setText(item.displayName ?: "")
        
        textInputLayoutIdentifier.hint = if(item.type == BlockType.APP) "Package Name" else "Hostname"

        AlertDialog.Builder(this)
            .setTitle("Edit Display Name")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                var newDisplayName = editTextName.text.toString().trim()
                if (newDisplayName.isEmpty()) newDisplayName = item.identifier

                // Only display name can be changed. groupId and placeholder timeLimit remain.
                val updatedItem = item.copy(displayName = newDisplayName)
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
            .setTitle("Remove Item")
            .setMessage("Are you sure you want to remove '${item.displayName ?: item.identifier}' from the distraction list?")
            .setPositiveButton("Remove") { dialog, _ ->
                if (position >= 0 && position < blockedItemsList.size) {
                    Log.d(TAG, "Removing item at position: $position, item: ${blockedItemsList[position].identifier}")
                    blockedItemsList.removeAt(position) 
                    adapter.notifyItemRemoved(position)
                    if (position < blockedItemsList.size) { 
                        adapter.notifyItemRangeChanged(position, blockedItemsList.size - position)
                    }
                    saveBlockedItems() 
                    Log.d(TAG, "Item removed. New list size: ${blockedItemsList.size}")
                } else {
                    Log.w(TAG, "Attempted to remove item at invalid position: $position. List size: ${blockedItemsList.size}")
                    Toast.makeText(this, "Error removing item.", Toast.LENGTH_SHORT).show()
                    loadBlockedItems() 
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