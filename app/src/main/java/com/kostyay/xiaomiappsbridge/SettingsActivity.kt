package com.kostyay.xiaomiappsbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private val colors = TvTheme
    private lateinit var mappings: LinearLayout
    private var captureDialog: AlertDialog? = null
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = consumeCapturedKey()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mappings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(colors.BACKGROUND)
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(72, 42, 72, 42)
                        addView(label("KEY MAPPINGS", 13f, colors.ORANGE, Typeface.BOLD))
                        addView(label("Remote button settings", 28f, colors.TEXT, Typeface.BOLD), margins(top = 10))
                        addView(
                            label("Select a mapping to change its action.", 15f, colors.MUTED),
                            margins(top = 6, bottom = 22)
                        )
                        addView(mappings)
                        addView(button("Add mapping", ::captureKey), margins(top = 22))
                        addView(button("What can I open?", ::showIntentHelp), margins(top = 14))
                        addView(button("Back", ::finish), margins(top = 14, bottom = 32))
                    }
                )
            }
        )
        renderMappings()
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver(statusReceiver)
        consumeCapturedKey()
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun renderMappings() {
        mappings.removeAllViews()
        val savedMappings = loadMappings()
        savedMappings.forEach { mapping ->
            mappings.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = panel(colors.CARD)
                    setPadding(22, 14, 14, 14)
                    addView(
                        button("${mapping.key}  →  ${mapping.label}") { chooseAction(mapping.key) },
                        LinearLayout.LayoutParams(0, 96, 1f)
                    )
                    addView(
                        button("Delete") {
                            removeMapping(mapping.key)
                            renderMappings()
                        },
                        margins(left = 14, width = 210, height = 96)
                    )
                },
                margins(bottom = 12)
            )
        }
        if (savedMappings.isEmpty()) {
            mappings.addView(label("No mappings", 19f, colors.MUTED))
        }
    }

    private fun captureKey() {
        getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).edit()
            .remove(BridgeService.CAPTURED_KEY).apply()
        startBridge(BridgeService.ACTION_CAPTURE)
        captureDialog = AlertDialog.Builder(this)
            .setTitle("Press a remote button")
            .setMessage("The next detected button will be used.")
            .setNegativeButton("Cancel") { _, _ -> startBridge(BridgeService.ACTION_CANCEL_CAPTURE) }
            .create().apply {
                setOnCancelListener { startBridge(BridgeService.ACTION_CANCEL_CAPTURE) }
                show()
            }
    }

    private fun consumeCapturedKey() {
        val preferences = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        val key = preferences.getString(BridgeService.CAPTURED_KEY, null) ?: return
        preferences.edit().remove(BridgeService.CAPTURED_KEY).apply()
        captureDialog?.dismiss()
        captureDialog = null
        chooseAction(key)
    }

    private fun chooseAction(key: String) {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                KeyMapping(
                    key,
                    it.loadLabel(packageManager).toString(),
                    MappingKind.APP,
                    action = it.activityInfo.packageName
                )
            }
            .distinctBy { it.action }
            .sortedBy { it.label.lowercase() }
        val presets = listOf(
            KeyMapping(key, "Projectivy apps panel", MappingKind.PROJECTIVY),
            intentMapping(key, "Android settings", Settings.ACTION_SETTINGS),
            intentMapping(key, "Wi-Fi settings", Settings.ACTION_WIFI_SETTINGS),
            intentMapping(key, "Bluetooth settings", Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        val labels = presets.map { it.label } + "Custom intent…" + apps.map { it.label }

        AlertDialog.Builder(this)
            .setTitle("Action for $key")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < presets.size -> saveMapping(presets[which])
                    which == presets.size -> editCustomIntent(key)
                    else -> saveMapping(apps[which - presets.size - 1])
                }
                if (which != presets.size) renderMappings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editCustomIntent(key: String) {
        val action = field("Action, for example android.settings.SETTINGS")
        val data = field("Optional data URI, for example https://example.com")
        val component = field("Optional component, package/.Activity")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 4, 28, 0)
            addView(action)
            addView(data)
            addView(component)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom intent for $key")
            .setView(fields)
            .setPositiveButton("Save") { _, _ ->
                val actionValue = action.text.toString().trim()
                val dataValue = data.text.toString().trim()
                val componentValue = component.text.toString().trim()
                val mapping = KeyMapping(
                    key,
                    listOf(actionValue, componentValue, dataValue).firstOrNull { it.isNotBlank() }
                        ?: "Custom intent",
                    MappingKind.INTENT,
                    actionValue,
                    dataValue,
                    componentValue
                )
                if (mapping.command() != null) saveMapping(mapping)
                renderMappings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showIntentHelp() {
        AlertDialog.Builder(this)
            .setTitle("Available actions")
            .setMessage(
                "Installed TV apps and common settings are listed automatically. " +
                    "For a deep link or a special activity, select Custom intent and copy the " +
                    "action, data URI, or exported component from that app's documentation. " +
                    "Android does not publish one complete list of intents."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun intentMapping(key: String, label: String, action: String) =
        KeyMapping(key, label, MappingKind.INTENT, action)

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(colors.MUTED)
        setTextColor(colors.TEXT)
        textSize = 17f
        isSingleLine = true
    }

    private fun label(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", style)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 16f
        isAllCaps = false
        setTextColor(colors.TEXT)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), panel(colors.ORANGE, 16f))
            addState(intArrayOf(android.R.attr.state_pressed), panel(colors.ORANGE, 16f))
            addState(intArrayOf(), panel(colors.BUTTON, 16f))
        }
        setOnClickListener { action() }
    }

    private fun panel(color: Int, radius: Float = 24f) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun margins(left: Int = 0, top: Int = 0, width: Int = -1, height: Int = -2, bottom: Int = 0) =
        LinearLayout.LayoutParams(width, height).apply { setMargins(left, top, 0, bottom) }
}
