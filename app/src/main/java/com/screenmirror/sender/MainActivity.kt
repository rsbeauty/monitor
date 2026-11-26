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

    private lateinit var setupBtn: Button
    private lateinit var reverifyBtn: Button
    private lateinit var adminLockCard: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var lockDesc: TextView

    private val SB_URL = "https://pigsylxeeoztnyuydqtr.supabase.co"
    private val SB_KEY = "sb_publishable_JrDxSeFli4NkkVhopnwCoQ_aa7HFsgl"
    private val ROOM_ID = "device_1"

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
                putExtra("SB_URL", SB_URL)
                putExtra("SB_KEY", SB_KEY)
                putExtra("ROOM_ID", ROOM_ID)
            }
            ContextCompat.startForegroundService(this, intent)
            requestIgnoreBatteryOptimizations()
            updateUIState()
        } else {
            Toast.makeText(this, "Screen permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#060B19"))
            setPadding(48, 96, 48, 48)
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
            setPadding(0, 16, 0, 48)
        }

        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1428"))
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#1E293B"))
            }
        }

        val infoText = TextView(this).apply {
            text = "App: Fire\nRoom ID: $ROOM_ID\nPersistence: 24/7 Background Lock"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(12f, 1.1f)
        }
        infoBox.addView(infoText)

        setupBtn = Button(this).apply {
            text = "GRANT ACCESS & GO ONLINE"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(32, 36, 32, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = 24f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 48 }
            layoutParams = params

            setOnClickListener {
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }

        adminLockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#38BDF8"))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 48 }
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
            text = "\nOnly the connected Admin Panel can initiate, pause, or disconnect this stream. Local controls are permanently locked.\n\n✓ MediaProjection Active\n✓ 24/7 Background Service Active\n✓ Battery Optimization Bypassed"
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
            ).apply { topMargin = 32 }
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
                lockDesc.text = "\nOnly the connected Admin Panel can initiate, pause, or disconnect this stream. Local controls are permanently locked.\n\n✓ MediaProjection Token Ready\n✓ 24/7 Background Service Running\n✓ Waiting for Admin Connect"
            } else {
                reverifyBtn.visibility = View.VISIBLE
                statusLabel.text = "● DEVICE RESTARTED — RE-VERIFY REQUIRED"
                statusLabel.setTextColor(Color.parseColor("#F59E0B"))
                lockDesc.text = "\nPhone was restarted. Android OS requires a quick one-tap re-verification to resume 24/7 Admin screen access."
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
