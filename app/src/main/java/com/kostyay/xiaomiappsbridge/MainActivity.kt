package com.kostyay.xiaomiappsbridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
    private lateinit var status: TextView
    private lateinit var adbStatus: TextView
    private lateinit var target: TextView
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = label("Starting", 27f, Typeface.BOLD)
        adbStatus = label("●  ADB DISCONNECTED", 17f, Typeface.BOLD)
        target = label(targetLabel(), 19f)

        val statePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(52, 48, 52, 48)
            background = panel(CARD)
            addView(label("REMOTE BRIDGE", 16f, Typeface.BOLD).apply {
                setTextColor(ORANGE)
                letterSpacing = 0.16f
            })
            addView(label("Apps button", 43f, Typeface.BOLD), margins(top = 18))
            addView(status, margins(top = 24))
            addView(adbStatus, margins(top = 28))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(label("CURRENT TARGET", 14f, Typeface.BOLD).apply {
                setTextColor(MUTED)
                letterSpacing = 0.12f
            })
            addView(target, margins(top = 10))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(54, 36, 54, 36)
            addView(label("Controls", 34f, Typeface.BOLD))
            addView(label("Choose what opens, then test the remote button.", 18f).apply {
                setTextColor(MUTED)
            }, margins(top = 8, bottom = 30))
            addView(actionButton("Choose target", ::chooseTarget))
            addView(actionButton("Test Apps button") { startBridge(BridgeService.ACTION_TEST) }, margins(top = 16))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(actionButton("Start bridge") { startBridge() }, LinearLayout.LayoutParams(0, 82, 1f))
                addView(actionButton("Stop bridge") {
                    stopService(BridgeService.intent(this@MainActivity))
                    status.text = "Bridge stopped"
                    showAdb(false)
                }, margins(left = 16, width = 0, height = 82, weight = 1f))
            }, margins(top = 16))
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(62, 62, 62, 62)
            setBackgroundColor(BACKGROUND)
            addView(statePanel, LinearLayout.LayoutParams(0, -1, 0.9f))
            addView(View(this@MainActivity).apply { setBackgroundColor(ORANGE) }, margins(left = 30, width = 5, height = -1))
            addView(controls, LinearLayout.LayoutParams(0, -1, 1.1f))
        })

        refreshStatus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startBridge()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(statusReceiver, IntentFilter(BridgeService.ACTION_STATUS), RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun chooseTarget() {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        val choices = listOf("Projectivy apps panel" to BridgeService.DEFAULT_TARGET) + apps

        AlertDialog.Builder(this)
            .setTitle("Open with the Apps button")
            .setItems(choices.map { it.first }.toTypedArray()) { dialog, which ->
                getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
                    .edit().putString(BridgeService.TARGET, choices[which].second).apply()
                target.text = choices[which].first
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshStatus() {
        val preferences = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        status.text = preferences.getString(BridgeService.STATUS, "Starting")
        showAdb(preferences.getBoolean(BridgeService.ADB_CONNECTED, false))
    }

    private fun showAdb(connected: Boolean) {
        adbStatus.text = if (connected) "●  ADB CONNECTED" else "●  ADB DISCONNECTED"
        adbStatus.setTextColor(if (connected) MINT else AMBER)
    }

    private fun targetLabel(): String {
        val selected = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
            .getString(BridgeService.TARGET, BridgeService.DEFAULT_TARGET)
        if (selected == BridgeService.DEFAULT_TARGET) return "Projectivy apps panel"
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(selected!!, 0)).toString()
        }.getOrDefault(selected ?: "Not selected")
    }

    private fun label(value: String, size: Float, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif", style)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 19f
        isAllCaps = false
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = buttonBackground()
        setOnClickListener { action() }
    }

    private fun buttonBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), panel(ORANGE, 18f))
        addState(intArrayOf(android.R.attr.state_pressed), panel(ORANGE, 18f))
        addState(intArrayOf(), panel(BUTTON, 18f))
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

    companion object {
        private val BACKGROUND = Color.rgb(10, 13, 18)
        private val CARD = Color.rgb(23, 29, 39)
        private val BUTTON = Color.rgb(39, 48, 63)
        private val TEXT = Color.rgb(244, 247, 251)
        private val MUTED = Color.rgb(157, 169, 187)
        private val ORANGE = Color.rgb(255, 105, 0)
        private val MINT = Color.rgb(120, 230, 200)
        private val AMBER = Color.rgb(255, 180, 84)
    }
}
