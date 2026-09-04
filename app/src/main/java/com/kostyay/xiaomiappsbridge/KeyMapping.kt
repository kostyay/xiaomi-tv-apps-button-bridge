package com.kostyay.xiaomiappsbridge

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

enum class MappingKind { PROJECTIVY, APP, INTENT }

data class KeyMapping(
    val key: String,
    val label: String,
    val kind: MappingKind,
    val action: String = "",
    val data: String = "",
    val component: String = ""
) {
    fun command(): String? = when (kind) {
        MappingKind.PROJECTIVY ->
            "am start -a tv.projectivy.ALL_APPS -c ${Intent.CATEGORY_DEFAULT}"

        MappingKind.APP -> action.takeIf { it.matches(NAME) }
            ?.let { "monkey -p $it -c ${Intent.CATEGORY_LEANBACK_LAUNCHER} 1" }

        MappingKind.INTENT -> buildList {
            add("am start")
            action.takeIf { it.matches(NAME) }?.let { add("-a ${shellArg(it)}") }
            data.takeIf { it.isNotBlank() }?.let { add("-d ${shellArg(it)}") }
            component.takeIf { it.matches(COMPONENT_NAME) }?.let { add("-n ${shellArg(it)}") }
        }.takeIf { it.size > 1 }?.joinToString(" ")
    }

    fun toJson() = JSONObject()
        .put("key", key)
        .put("label", label)
        .put("kind", kind.name.lowercase())
        .put("action", action)
        .put("data", data)
        .put("component", component)

    companion object {
        private val NAME = Regex("[A-Za-z0-9._]+")
        private val COMPONENT_NAME = Regex("[A-Za-z0-9._]+/[A-Za-z0-9._$]+")

        fun fromJson(json: JSONObject) = KeyMapping(
            json.getString("key"),
            json.getString("label"),
            MappingKind.valueOf(json.getString("kind").uppercase()),
            json.optString("action"),
            json.optString("data"),
            json.optString("component")
        )

        private fun shellArg(value: String) = "'${value.replace("'", "'\\''")}'"
    }
}

fun Context.loadMappings(): List<KeyMapping> {
    val preferences = getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
    val saved = preferences.getString(BridgeService.MAPPINGS, null) ?: return listOf(
        KeyMapping("KEY_CHAT", "Projectivy apps panel", MappingKind.PROJECTIVY)
    )
    return runCatching {
        val json = JSONArray(saved)
        List(json.length()) { KeyMapping.fromJson(json.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}

fun Context.saveMapping(mapping: KeyMapping) {
    val mappings = loadMappings().filterNot { it.key == mapping.key } + mapping
    saveMappings(mappings)
}

fun Context.removeMapping(key: String) {
    saveMappings(loadMappings().filterNot { it.key == key })
}

private fun Context.saveMappings(mappings: List<KeyMapping>) {
    getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE).edit()
        .putString(BridgeService.MAPPINGS, JSONArray(mappings.map { it.toJson() }).toString())
        .apply()
}
