package com.screenmirror.sender

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var adminLockCard: LinearLayout
    private lateinit var statusLabel: TextView

    // Pre-configured Supabase Credentials
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
            val intent = Intent(this, ScreenShareService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
                putExtra("SB_URL", SB_URL)
                putExtra("SB_KEY", SB_KEY)
                putExtra("ROOM_ID", ROOM_ID)
            }
            ContextCompat.startForegroundService(this, intent)
            showAdminLockedState()
        } else {
            Toast.makeText(this, "Screen permission is required to connect", Toast.LENGTH_SHORT).show()
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
            text = "Screen Share Client"
            textSize = 24f
            setTextColor(Color.parseColor("#38BDF8"))
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
            text = "Server: Connected (Supabase Cloud)\nRoom ID: $ROOM_ID\nMode: Admin Handshake Control"
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

        val lockDesc = TextView(this).apply {
            text = "\nOnly the connected Admin Panel can initiate, pause, or disconnect this stream. Local stop controls are locked.\n\n✓ MediaProjection Active\n✓ Foreground Service Active\n✓ Waiting for Admin Commands"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        adminLockCard.addView(lockTitle)
        adminLockCard.addView(lockDesc)

        root.addView(header)
        root.addView(statusLabel)
        root.addView(infoBox)
        root.addView(setupBtn)
        root.addView(adminLockCard)

        setContentView(root)
    }

    private fun showAdminLockedState() {
        setupBtn.visibility = View.GONE
        adminLockCard.visibility = View.VISIBLE
        statusLabel.text = "● ONLINE & CONTROLLED BY ADMIN"
        statusLabel.setTextColor(Color.parseColor("#10B981"))
    }
}
