package dev.crossfeed.core

import org.json.JSONArray
import org.json.JSONObject

object Json {

    fun findObject(root: Any?, depth: Int = 0, predicate: (JSONObject) -> Boolean): JSONObject? {
        if (depth > 12) return null
        when (root) {
            is JSONObject -> {
                if (predicate(root)) return root
                for (key in root.keys()) {
                    findObject(root.opt(key), depth + 1, predicate)?.let { return it }
                }
            }

            is JSONArray -> {
                for (i in 0 until root.length()) {
                    findObject(root.opt(i), depth + 1, predicate)?.let { return it }
                }
            }
        }
        return null
    }

    fun firstName(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { firstName(value.opt(it)) }
        else -> null
    }

    fun collectStrings(root: Any?, depth: Int = 0, into: MutableList<String> = mutableListOf()): List<String> {
        if (depth > 14) return into
        when (root) {
            is String -> into.add(root)
            is JSONObject -> for (key in root.keys()) collectStrings(root.opt(key), depth + 1, into)
            is JSONArray -> for (i in 0 until root.length()) collectStrings(root.opt(i), depth + 1, into)
        }
        return into
    }

    fun parse(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()
}
