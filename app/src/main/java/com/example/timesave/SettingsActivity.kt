package com.example.timesave

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts

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
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)
        val editTextIdentifier = dialogView.findViewById<EditText>(R.id.editTextIdentifier)
        val textInputLayoutName = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutName)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val numberPickerTimeLimit = dialogView.findViewById<NumberPicker>(R.id.numberPickerTimeLimit)

        numberPickerTimeLimit.minValue = 1
        numberPickerTimeLimit.maxValue = 300 // Max 5 hours
        numberPickerTimeLimit.value = 60 // Default 60 minutes

        val title: String
        when (type) {
            BlockType.APP -> {
                title = "Block New App"
                textInputLayoutIdentifier.hint = "Package Name (e.g., com.example.app)"
                editTextIdentifier.setText(prefillIdentifier ?: "")
                editTextIdentifier.isEnabled = prefillIdentifier == null // Lock if prefilled from picker
                textInputLayoutName.hint = "App Name (Optional)"
                editTextName.setText(prefillName ?: "")
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
                val timeLimit = numberPickerTimeLimit.value.toLong()

                if (identifier.isEmpty()) {
                    Toast.makeText(this, "Identifier cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (displayName.isEmpty()) displayName = identifier // Default display name to identifier

                val newItem = BlockedItem(identifier, type, timeLimit, displayName)
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
        val textInputLayoutIdentifier = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutIdentifier)
        val editTextIdentifier = dialogView.findViewById<EditText>(R.id.editTextIdentifier)
        val textInputLayoutName = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutName)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val numberPickerTimeLimit = dialogView.findViewById<NumberPicker>(R.id.numberPickerTimeLimit)

        editTextIdentifier.setText(item.identifier)
        editTextIdentifier.isEnabled = false // Cannot edit identifier
        editTextName.setText(item.displayName ?: "")
        
        textInputLayoutIdentifier.hint = if(item.type == BlockType.APP) "Package Name" else "Hostname"
        textInputLayoutName.hint = "Display Name (Optional)"

        numberPickerTimeLimit.minValue = 1
        numberPickerTimeLimit.maxValue = 300 // Max 5 hours, adjust as needed
        numberPickerTimeLimit.value = item.timeLimitInMinutes.toInt().coerceIn(1, 300)

        AlertDialog.Builder(this)
            .setTitle("Edit Time Limit for ${item.displayName ?: item.identifier}")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                var newDisplayName = editTextName.text.toString().trim()
                val newTimeLimit = numberPickerTimeLimit.value.toLong()
                if (newDisplayName.isEmpty()) newDisplayName = item.identifier

                val updatedItem = item.copy(timeLimitInMinutes = newTimeLimit, displayName = newDisplayName)
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
                blockedItemsList.removeAt(position)
                adapter.removeItem(position)
                saveBlockedItems()
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