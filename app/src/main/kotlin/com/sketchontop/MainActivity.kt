package com.sketchontop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - handles permission request and starts the overlay service.
 * This activity only shows when the user needs to grant permission or control the overlay.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var btnStartOverlay: Button
    private lateinit var btnStopOverlay: Button
    private lateinit var btnGrantPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        btnStartOverlay = findViewById(R.id.btnStartOverlay)
        btnStopOverlay = findViewById(R.id.btnStopOverlay)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
    }

    private fun setupClickListeners() {
        btnGrantPermission.setOnClickListener {
            requestOverlayPermission()
        }
        
        btnStartOverlay.setOnClickListener {
            startOverlayService()
        }
        
        btnStopOverlay.setOnClickListener {
            stopOverlayService()
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
    }

    /**
     * Checks if the app has overlay permission.
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Permission not needed before Android M
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
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Starts the overlay service and minimizes the app.
     */
    private fun startOverlayService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "Overlay started!", Toast.LENGTH_SHORT).show()
        
        // Go to home screen so user can draw over other apps
        moveTaskToBack(true)
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
