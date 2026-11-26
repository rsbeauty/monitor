package com.screenmirror.sender

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var accBtn: Button
    private lateinit var setupBtn: Button
    private lateinit var reverifyBtn: Button
    private lateinit var adminLockCard: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var lockDesc: TextView

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            getSharedPreferences("admin_lock_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("is_paired_lifetime", true)
                .apply()

            val intent = Intent(this, ScreenShareService::class.java).apply {
                action = "START_WITH_PROJECTION"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            requestIgnoreBatteryOptimizations()
            updateUIState()
        } else {
            Toast.makeText(this, "Screen permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val uniqueDeviceId = "fire_${androidId.take(8)}"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#060B19"))
            setPadding(48, 84, 48, 48)
        }

        val header = TextView(this).apply {
            text = "Fire"
            textSize = 28f
            setTextColor(Color.parseColor("#FB923C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        statusLabel = TextView(this).apply {
            text = "● Ready to Pair with Admin"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 36)
        }

        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#1E293B"))
            }
        }

        val infoText = TextView(this).apply {
            text = "Device ID: $uniqueDeviceId\nModel: ${Build.MANUFACTURER} ${Build.MODEL}\nMode: 4-Grid Fleet & Remote Touch"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(10f, 1.1f)
        }
        infoBox.addView(infoText)

        // Step 1: Enable Remote Touch (Accessibility)
        accBtn = Button(this).apply {
            text = "1. ENABLE REMOTE TOUCH ACCESS"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 32, 28, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 22f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 36 }
            layoutParams = params

            setOnClickListener {
                Toast.makeText(context, "Find 'Fire' in Installed Apps and turn ON", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Step 2: Grant Screen Access & Go Online (Permanent Lock)
        setupBtn = Button(this).apply {
            text = "2. GRANT SCREEN & GO ONLINE"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 36, 28, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = 22f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            layoutParams = params

            setOnClickListener {
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }

        // Admin Locked Card
        adminLockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(36, 36, 36, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 28f
                setStroke(3, Color.parseColor("#38BDF8"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 36 }
            layoutParams = params
        }

        val lockTitle = TextView(this).apply {
            text = "🔒 SESSION MANAGED BY ADMIN"
            textSize = 16f
            setTextColor(Color.parseColor("#38BDF8"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        lockDesc = TextView(this).apply {
            text = "\nConnected to Fire Fleet Admin.\nLocal stop buttons are permanently locked."
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        reverifyBtn = Button(this).apply {
            text = "↻ RE-VERIFY CONNECTION (AFTER RESTART)"
            textSize = 12f
            visibility = View.GONE
            setTextColor(Color.parseColor("#FDE047"))
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 28, 24, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#422006"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#EAB308"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 28 }
            layoutParams = params

            setOnClickListener {
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }

        adminLockCard.addView(lockTitle)
        adminLockCard.addView(lockDesc)
        adminLockCard.addView(reverifyBtn)

        root.addView(header)
        root.addView(statusLabel)
        root.addView(infoBox)
        root.addView(accBtn)
        root.addView(setupBtn)
        root.addView(adminLockCard)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun updateUIState() {
        val prefs = getSharedPreferences("admin_lock_prefs", MODE_PRIVATE)
        val isPairedLifetime = prefs.getBoolean("is_paired_lifetime", false)
        val accActive = FireAccessibilityService.instance != null

        accBtn.visibility = if (accActive) View.GONE else View.VISIBLE

        if (!isPairedLifetime) {
            setupBtn.visibility = View.VISIBLE
            adminLockCard.visibility = View.GONE
            statusLabel.text = "● Ready to Pair with Admin"
            statusLabel.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            setupBtn.visibility = View.GONE
            adminLockCard.visibility = View.VISIBLE

            if (ScreenShareService.hasActiveProjectionToken) {
                reverifyBtn.visibility = View.GONE
                statusLabel.text = "● ONLINE & CONTROLLED BY ADMIN (24/7)"
                statusLabel.setTextColor(Color.parseColor("#10B981"))
                lockDesc.text = "\nConnected to Fire Fleet Admin.\n✓ Screen Stream Ready\n✓ Remote Touch: ${if (accActive) "ACTIVE" else "Pending"}\n✓ Local Stop Locked"
            } else {
                reverifyBtn.visibility = View.VISIBLE
                statusLabel.text = "● DEVICE RESTARTED — RE-VERIFY REQUIRED"
                statusLabel.setTextColor(Color.parseColor("#F59E0B"))
                lockDesc.text = "\nPhone restarted. Tap below once to resume 24/7 Admin screen access."
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
