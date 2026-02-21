package com.androbot.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var policy: TrustedSenderPolicy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        policy = TrustedSenderPolicy(this)
        ensureSmsPermission()
        setupTrustedSenderUi()
        renderStatus()
        renderTrustedSenders()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderTrustedSenders()
    }

    private fun setupTrustedSenderUi() {
        val input = findViewById<EditText>(R.id.trustedSenderInput)
        val addButton = findViewById<Button>(R.id.addTrustedSenderButton)
        val removeButton = findViewById<Button>(R.id.removeTrustedSenderButton)

        addButton.setOnClickListener {
            val raw = input.text.toString()
            val added = policy.addTrustedSender(raw)
            if (added) {
                Toast.makeText(this, "Trusted sender added", Toast.LENGTH_SHORT).show()
                input.text.clear()
                renderStatus()
                renderTrustedSenders()
            } else {
                Toast.makeText(this, "Invalid number or already trusted", Toast.LENGTH_SHORT).show()
            }
        }

        removeButton.setOnClickListener {
            val raw = input.text.toString()
            val removed = policy.removeTrustedSender(raw)
            if (removed) {
                Toast.makeText(this, "Trusted sender removed", Toast.LENGTH_SHORT).show()
                input.text.clear()
                renderStatus()
                renderTrustedSenders()
            } else {
                Toast.makeText(this, "Number not found in trusted list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderStatus() {
        val trusted = policy.trustedSenders()

        findViewById<TextView>(R.id.statusText).text = buildString {
            append("Androbot ")
            append(appVersionLabel())
            append(" is running.\n\n")
            append("Allowed commands:\n")
            append("- volume max\n")
            append("- volume min\n")
            append("- volume <0-100>\n\n")
            append("Trusted sender management:\n")
            append("- Add/Remove/List via this UI\n")
            append("- Optional SMS management from trusted sender:\n")
            append("  trusted add <phone>\n")
            append("  trusted remove <phone>\n")
            append("  trusted list\n\n")
            if (trusted.isEmpty()) {
                append("Setup required: add your first trusted sender below.\n")
            }
        }
    }

    private fun renderTrustedSenders() {
        val trustedText = policy.trustedSenders().joinToString("\n")
        findViewById<TextView>(R.id.trustedSendersList).text = trustedText.ifBlank { "(none configured)" }
    }

    private fun appVersionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val name = info.versionName ?: "unknown"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "v$name ($code)"
    }

    private fun ensureSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), 1001)
            }
        }
    }
}
