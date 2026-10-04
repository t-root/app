package com.example.nexora.data

import android.content.Context
import com.example.nexora.model.AppGroup
import com.example.nexora.model.GroupColors
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What an export file holds: the groups and the centre app. */
data class GroupConfig(val groups: List<AppGroup>, val center: String?)

/**
 * Stores the app groups as JSON in SharedPreferences, so the launcher
 * screen and the live wallpaper (same process) see the same groups.
 * Also reads / writes the export file format.
 */
class GroupRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<AppGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return try {
            parseGroups(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(groups: List<AppGroup>) {
        val array = JSONArray()
        for (group in groups) {
            array.put(
                JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("color", group.color)
                    put("packages", JSONArray(group.packages))
                }
            )
        }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "nexora_groups"
        private const val KEY_GROUPS = "groups"
        private const val FORMAT = "nexora-groups"
        private const val FORMAT_VERSION = 1

        /** The export file: readable colours (#RRGGBB) and a format tag. */
        fun exportJson(groups: List<AppGroup>, center: String?): String {
            val array = JSONArray()
            for (group in groups) {
                array.put(
                    JSONObject().apply {
                        put("name", group.name)
                        put("color", String.format("#%06X", group.color and 0xFFFFFF))
                        put("packages", JSONArray(group.packages))
                    }
                )
            }
            return JSONObject().apply {
                put("format", FORMAT)
                put("version", FORMAT_VERSION)
                if (center != null) put("center", center)
                put("groups", array)
            }.toString(2)
        }

        /**
         * Reads an export file (or a bare array of groups). Groups get fresh ids;
         * an app listed in several groups stays in the first. Throws on bad JSON.
         */
        fun importJson(text: String): GroupConfig {
            val trimmed = text.trim()
            val root = if (trimmed.startsWith("[")) null else JSONObject(trimmed)
            val array = if (root == null) {
                JSONArray(trimmed)
            } else {
                root.optJSONArray("groups") ?: throw IllegalArgumentException("Không có mục \"groups\"")
            }
            val center = root?.optString("center")?.ifBlank { null }
            // The centre app is in no group; an app listed twice stays in the first.
            val claimed = HashSet<String>().apply { center?.let(::add) }
            val groups = parseGroups(array).map { group ->
                group.copy(id = UUID.randomUUID().toString(), packages = group.packages.filter { claimed.add(it) })
            }
            return GroupConfig(groups, center)
        }

        private fun parseGroups(array: JSONArray): List<AppGroup> = List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val packages = obj.optJSONArray("packages") ?: JSONArray()
            AppGroup(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name").ifBlank { "Nhóm ${i + 1}" },
                color = parseColor(obj.opt("color"), i),
                packages = List(packages.length()) { packages.getString(it) }.distinct(),
            )
        }

        private fun parseColor(value: Any?, index: Int): Int {
            val fallback = GroupColors.PALETTE[index % GroupColors.PALETTE.size]
            return when (value) {
                is Number -> value.toInt()
                is String -> value.removePrefix("#").toLongOrNull(16)?.let { (it or 0xFF000000).toInt() } ?: fallback
                else -> fallback
            }
        }
    }
}
