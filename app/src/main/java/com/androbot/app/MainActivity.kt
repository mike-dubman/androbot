package com.androbot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        val addButton = findViewById<Button>(R.id.addTrustedSenderButton)
        val removeButton = findViewById<Button>(R.id.removeTrustedSenderButton)
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        val closeButton = findViewById<Button>(R.id.closeTrustedSenderScreenButton)

        addButton.setOnClickListener { showTrustedSenderDialog(isRemove = false) }
        removeButton.setOnClickListener { showTrustedSenderDialog(isRemove = true) }
        aboutButton.setOnClickListener { openProjectLink() }
        closeButton.setOnClickListener { finish() }
    }

    private fun openProjectLink() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open project link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTrustedSenderDialog(isRemove: Boolean) {
        val input = EditText(this).apply {
            hint = "+15551234567"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            maxLines = 1
        }

        val title = if (isRemove) "Remove trusted sender" else "Add trusted sender"
        val positive = if (isRemove) "Remove" else "Add"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(positive, null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .setNeutralButton("Close") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString()
                val changed = if (isRemove) {
                    policy.removeTrustedSender(raw)
                } else {
                    policy.addTrustedSender(raw)
                }

                if (changed) {
                    val message = if (isRemove) "Trusted sender removed" else "Trusted sender added"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    renderStatus()
                    renderTrustedSenders()
                    dialog.dismiss()
                } else {
                    val message = if (isRemove) {
                        "Number not found in trusted list"
                    } else {
                        "Invalid number or already trusted"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
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
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
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

    companion object {
        private const val PROJECT_URL = "https://github.com/mike-dubman/androbot"
    }
}
