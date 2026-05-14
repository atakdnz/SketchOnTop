package com.sketchontop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - handles permission request and starts the overlay service.
 * 
 * On launch: if permission granted, immediately starts overlay and closes.
 * Only stays open if permission is needed or opened from overlay settings button.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        const val EXTRA_FROM_OVERLAY = "from_overlay"
    }

    private lateinit var statusText: TextView
    private lateinit var btnStartOverlay: Button
    private lateinit var btnStopOverlay: Button
    private lateinit var btnGrantPermission: Button
    private lateinit var switchAccessibilityStylusInput: Switch
    private lateinit var switchCompactToolbar: Switch
    private lateinit var switchSPenButtonMode: Switch
    private lateinit var switchHoverArmDrawing: Switch
    private lateinit var inputFingerResetDelay: EditText
    private lateinit var inputButtonReenableDelay: EditText
    private lateinit var inputHoverExitDelay: EditText
    private lateinit var btnSaveSPenSettings: Button
    private lateinit var btnOpenAccessibilitySettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // If permission granted and NOT opened from overlay settings, start immediately
        val fromOverlay = intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false)
        if (hasOverlayPermission() && !fromOverlay) {
            startOverlayService()
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        initViews()
        setupClickListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            updateUI()
        }
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        btnStartOverlay = findViewById(R.id.btnStartOverlay)
        btnStopOverlay = findViewById(R.id.btnStopOverlay)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        switchAccessibilityStylusInput = findViewById(R.id.switchAccessibilityStylusInput)
        switchCompactToolbar = findViewById(R.id.switchCompactToolbar)
        switchSPenButtonMode = findViewById(R.id.switchSPenButtonMode)
        switchHoverArmDrawing = findViewById(R.id.switchHoverArmDrawing)
        inputFingerResetDelay = findViewById(R.id.inputFingerResetDelay)
        inputButtonReenableDelay = findViewById(R.id.inputButtonReenableDelay)
        inputHoverExitDelay = findViewById(R.id.inputHoverExitDelay)
        btnSaveSPenSettings = findViewById(R.id.btnSaveSPenSettings)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
    }

    private fun setupClickListeners() {
        btnGrantPermission.setOnClickListener {
            requestOverlayPermission()
        }
        
        btnStartOverlay.setOnClickListener {
            startOverlayService()
            finish() // Close after starting
        }
        
        btnStopOverlay.setOnClickListener {
            stopOverlayService()
        }

        btnSaveSPenSettings.setOnClickListener {
            saveSPenSettings()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    /**
     * Updates UI based on permission status and service state.
     */
    private fun updateUI() {
        val hasPermission = hasOverlayPermission()
        
        if (hasPermission) {
            statusText.text = "✓ Overlay permission granted\n\nTap 'Start Overlay' to begin drawing on screen."
            btnGrantPermission.isEnabled = false
            btnGrantPermission.alpha = 0.5f
            btnStartOverlay.isEnabled = true
            btnStopOverlay.isEnabled = true
        } else {
            statusText.text = "⚠ Overlay permission required\n\nSketchOnTop needs the 'Display over other apps' permission to draw on your screen."
            btnGrantPermission.isEnabled = true
            btnGrantPermission.alpha = 1f
            btnStartOverlay.isEnabled = false
            btnStopOverlay.isEnabled = false
        }

        updateSPenSettingsUI()
    }

    private fun updateSPenSettingsUI() {
        switchSPenButtonMode.isChecked = SPenSettings.sPenButtonTogglesDrawMode(this)
        switchAccessibilityStylusInput.isChecked = SPenSettings.accessibilityStylusInput(this)
        switchCompactToolbar.isChecked = SPenSettings.compactToolbar(this)
        switchHoverArmDrawing.isChecked = SPenSettings.hoverArmsDrawing(this)
        inputFingerResetDelay.setText(SPenSettings.fingerPassthroughResetDelayMs(this).toString())
        inputButtonReenableDelay.setText(SPenSettings.sPenButtonReenableDelayMs(this).toString())
        inputHoverExitDelay.setText(SPenSettings.hoverExitDelayMs(this).toString())
    }

    private fun saveSPenSettings() {
        val fingerDelay = parseDelay(
            inputFingerResetDelay.text?.toString(),
            SPenSettings.DEFAULT_FINGER_PASSTHROUGH_RESET_DELAY_MS
        )
        val buttonDelay = parseDelay(
            inputButtonReenableDelay.text?.toString(),
            SPenSettings.DEFAULT_SPEN_BUTTON_REENABLE_DELAY_MS
        )
        val hoverExitDelay = parseDelay(
            inputHoverExitDelay.text?.toString(),
            SPenSettings.DEFAULT_HOVER_EXIT_DELAY_MS
        )

        SPenSettings.prefs(this).edit()
            .putBoolean(SPenSettings.KEY_ACCESSIBILITY_STYLUS_INPUT, switchAccessibilityStylusInput.isChecked)
            .putBoolean(SPenSettings.KEY_COMPACT_TOOLBAR, switchCompactToolbar.isChecked)
            .putBoolean(SPenSettings.KEY_SPEN_BUTTON_TOGGLES_DRAW_MODE, switchSPenButtonMode.isChecked)
            .putBoolean(SPenSettings.KEY_HOVER_ARM_DRAWING, switchHoverArmDrawing.isChecked)
            .putLong(SPenSettings.KEY_FINGER_PASSTHROUGH_RESET_DELAY_MS, fingerDelay)
            .putLong(SPenSettings.KEY_SPEN_BUTTON_REENABLE_DELAY_MS, buttonDelay)
            .putLong(SPenSettings.KEY_HOVER_EXIT_DELAY_MS, hoverExitDelay)
            .apply()

        updateSPenSettingsUI()
        Toast.makeText(this, "S Pen settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun parseDelay(value: String?, fallback: Long): Long {
        return SPenSettings.sanitizeDelay(value?.toLongOrNull() ?: fallback)
    }

    /**
     * Checks if the app has overlay permission.
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    /**
     * Opens system settings to grant overlay permission.
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                startOverlayService()
                finish()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }
    }

    /**
     * Starts the overlay service.
     */
    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Stops the overlay service.
     */
    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
}
