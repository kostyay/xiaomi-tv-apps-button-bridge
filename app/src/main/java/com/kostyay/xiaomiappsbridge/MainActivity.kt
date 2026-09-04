package com.kostyay.xiaomiappsbridge

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val colors = TvTheme
    private lateinit var status: TextView
    private lateinit var adbStatus: TextView
    private lateinit var mappingCount: TextView
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = label("Starting", 23f, Typeface.BOLD)
        adbStatus = label("●  ADB DISCONNECTED", 15f, Typeface.BOLD)
        mappingCount = label(mappingCountLabel(), 17f)

        val statePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(52, 48, 52, 48)
            background = panel(colors.CARD)
            addView(
                label("REMOTE BRIDGE", 14f, Typeface.BOLD).apply {
                    setTextColor(colors.ORANGE)
                    letterSpacing = 0.16f
                }
            )
            addView(label("TV Key Mapper", 34f, Typeface.BOLD), margins(top = 18))
            addView(status, margins(top = 24))
            addView(adbStatus, margins(top = 28))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(
                label("KEY MAPPINGS", 12f, Typeface.BOLD).apply {
                    setTextColor(colors.MUTED)
                    letterSpacing = 0.12f
                }
            )
            addView(mappingCount, margins(top = 10))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(54, 36, 54, 36)
            addView(label("Controls", 28f, Typeface.BOLD))
            addView(
                label("Choose a remote key and what it must open.", 16f).apply {
                    setTextColor(colors.MUTED)
                },
                margins(top = 8, bottom = 30)
            )
            addView(
                actionButton("Key mappings") {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            )
            addView(actionButton("Test a mapped button") { startBridge(BridgeService.ACTION_TEST) }, margins(top = 16))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(actionButton("Start bridge") { startBridge() }, LinearLayout.LayoutParams(0, 82, 1f))
                    addView(
                        actionButton("Stop bridge") {
                            stopService(BridgeService.intent(this@MainActivity))
                            status.text = "Bridge stopped"
                            showAdb(false)
                        },
                        margins(left = 16, width = 0, height = 82, weight = 1f)
                    )
                },
                margins(top = 16)
            )
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(62, 62, 62, 62)
                setBackgroundColor(colors.BACKGROUND)
                addView(statePanel, LinearLayout.LayoutParams(0, -1, 0.9f))
                addView(
                    View(this@MainActivity).apply {
                        setBackgroundColor(colors.ORANGE)
                    },
                    margins(left = 30, width = 5, height = -1)
                )
                addView(controls, LinearLayout.LayoutParams(0, -1, 1.1f))
            }
        )

        refreshStatus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startBridge()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(statusReceiver, IntentFilter(BridgeService.ACTION_STATUS), RECEIVER_NOT_EXPORTED)
        mappingCount.text = mappingCountLabel()
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun refreshStatus() {
        val preferences = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        status.text = preferences.getString(BridgeService.STATUS, "Starting")
        showAdb(preferences.getBoolean(BridgeService.ADB_CONNECTED, false))
    }

    private fun showAdb(connected: Boolean) {
        adbStatus.text = if (connected) "●  ADB CONNECTED" else "●  ADB DISCONNECTED"
        adbStatus.setTextColor(if (connected) colors.MINT else colors.AMBER)
    }

    private fun mappingCountLabel() = "${loadMappings().size} configured"

    private fun label(value: String, size: Float, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(colors.TEXT)
        typeface = Typeface.create("sans-serif", style)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        setTextColor(colors.TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = buttonBackground()
        setOnClickListener { action() }
    }

    private fun buttonBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), panel(colors.ORANGE, 18f))
        addState(intArrayOf(android.R.attr.state_pressed), panel(colors.ORANGE, 18f))
        addState(intArrayOf(), panel(colors.BUTTON, 18f))
    }

    private fun panel(color: Int, radius: Float = 28f) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun margins(
        left: Int = 0,
        top: Int = 0,
        width: Int = -1,
        height: Int = -2,
        bottom: Int = 0,
        weight: Float = 0f
    ) = LinearLayout.LayoutParams(width, height, weight).apply {
        setMargins(left, top, 0, bottom)
    }
}
