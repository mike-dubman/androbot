package com.androbot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
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
    private lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        policy = TrustedSenderPolicy(this)
        updater = updaterFactory(this)
        if (!skipPermissionRequestForTests) {
            ensureSmsPermission()
        }
        setupTrustedSenderUi()
        renderStatus()
        renderTrustedSenders()
        handleIntentActions(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentActions(intent)
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderTrustedSenders()
    }

    override fun onDestroy() {
        updater.cleanup()
        super.onDestroy()
    }

    private fun setupTrustedSenderUi() {
        val addButton = findViewById<Button>(R.id.addTrustedSenderButton)
        val removeButton = findViewById<Button>(R.id.removeTrustedSenderButton)
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        val checkUpdateButton = findViewById<Button>(R.id.checkUpdateButton)
        val closeButton = findViewById<Button>(R.id.closeTrustedSenderScreenButton)

        addButton.setOnClickListener { showTrustedSenderDialog(isRemove = false) }
        removeButton.setOnClickListener { showTrustedSenderDialog(isRemove = true) }
        aboutButton.setOnClickListener { showAboutDialog() }
        checkUpdateButton.setOnClickListener { checkForUpdates() }
        closeButton.setOnClickListener { finish() }
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        updater.checkForUpdate { result ->
            when (result) {
                is AppUpdater.CheckResult.UpToDate ->
                    AlertDialog.Builder(this)
                        .setTitle("No updates")
                        .setMessage("Already on latest version")
                        .setPositiveButton("OK", null)
                        .show()

                is AppUpdater.CheckResult.Error ->
                    AlertDialog.Builder(this)
                        .setTitle("Update check failed")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()

                is AppUpdater.CheckResult.UpdateAvailable -> showUpdateAvailableDialog(result.metadata)
            }
        }
    }

    private fun handleIntentActions(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TRIGGER_UPDATE_FROM_SMS, false) != true) {
            return
        }
        intent.removeExtra(EXTRA_TRIGGER_UPDATE_FROM_SMS)
        Toast.makeText(this, "SMS requested update check", Toast.LENGTH_SHORT).show()
        checkForUpdates()
    }

    private fun showUpdateAvailableDialog(metadata: AppUpdater.UpdateMetadata) {
        val msg = buildString {
            append("Version ")
            append(metadata.versionName)
            append(" is available.\n\n")
            append("Install update now?")
        }
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(msg)
            .setPositiveButton("Download") { _, _ ->
                updater.downloadAndInstall(metadata) { status ->
                    Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showAboutDialog() {
        val linkText = SpannableString(PROJECT_URL).apply {
            setSpan(URLSpan(PROJECT_URL), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val messageView = TextView(this).apply {
            text = linkText
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
            setPadding(48, 16, 48, 8)
        }

        AlertDialog.Builder(this)
            .setTitle("About")
            .setView(messageView)
            .setNeutralButton("Close") { d, _ -> d.dismiss() }
            .show()
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
            append("- wifi reset\n")
            append("- wifi on\n")
            append("- wifi off\n\n")
            append("- data reset\n")
            append("- data off\n")
            append("- data on\n\n")
            append("- call me back (calls trusted sender, enables speaker)\n\n")
            append("- update software (opens app and checks OTA update)\n")
            append("- info (replies by SMS with version and commands)\n\n")
            append("Trusted sender management:\n")
            append("- Add/Remove/List via this UI\n")
            append("- Optional SMS management from trusted sender:\n")
            append("  trusted add <phone>\n")
            append("  trusted remove <phone>\n")
            append("  trusted list\n")
            append("  sms forwarder on\n")
            append("  sms forwarder off\n\n")
            append("SMS forwarding requires SEND_SMS permission.\n\n")
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
        return AndrobotInfo.versionLabel(this)
    }

    private fun ensureSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val smsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val sendSmsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val callGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            if (!smsGranted || !sendSmsGranted || !callGranted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.CALL_PHONE
                    ),
                    1001
                )
            }
        }
    }

    companion object {
        private const val PROJECT_URL = "https://github.com/mike-dubman/androbot"
        const val EXTRA_TRIGGER_UPDATE_FROM_SMS = "extra_trigger_update_from_sms"
        @Volatile
        var updaterFactory: (AppCompatActivity) -> Updater = { activity -> AppUpdater(activity) }
        @Volatile
        var skipPermissionRequestForTests: Boolean = false
    }
}
