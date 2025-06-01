package com.example.timesave

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_ITEM_NAME = "com.example.timesave.BLOCKED_ITEM_NAME"
        const val EXTRA_BLOCKED_ITEM_IDENTIFIER = "com.example.timesave.BLOCKED_ITEM_IDENTIFIER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        val blockedItemName = intent.getStringExtra(EXTRA_BLOCKED_ITEM_NAME) ?: "Selected App/Website"
        // val blockedItemIdentifier = intent.getStringExtra(EXTRA_BLOCKED_ITEM_IDENTIFIER)

        val textViewMessage: TextView = findViewById(R.id.textViewBlockMessage)
        val textViewItemName: TextView = findViewById(R.id.textViewBlockedItemName)
        val buttonClose: Button = findViewById(R.id.buttonCloseBlocker)

        textViewMessage.text = "Time's up for:"
        textViewItemName.text = blockedItemName

        buttonClose.setOnClickListener {
            finish() // Close this activity
            // Optionally, navigate to home screen or a "safe" app
            // val homeIntent = Intent(Intent.ACTION_MAIN)
            // homeIntent.addCategory(Intent.CATEGORY_HOME)
            // homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // startActivity(homeIntent)
        }
    }

    override fun onBackPressed() {
        // Prevent going back to the blocked app easily by just pressing back.
        // Instead, effectively treat back press like the close button.
        super.onBackPressed()
        finish()
        // Or send to home:
        // val homeIntent = Intent(Intent.ACTION_MAIN)
        // homeIntent.addCategory(Intent.CATEGORY_HOME)
        // homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        // startActivity(homeIntent)
    }
} 