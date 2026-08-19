package dev.crossfeed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

enum class Dest(val label: String) {
    HOME("listening"),
    AUX("auxshare"),
    PLAYER("player"),
    SETTINGS("settings"),
    ROUTE("route"),
}

val Roots = listOf(Dest.HOME, Dest.AUX, Dest.PLAYER, Dest.SETTINGS)

@Stable
class Nav(initial: List<Dest>) {

    private val stack = mutableStateListOf<Dest>().also { it.addAll(initial) }

    val current: Dest get() = stack.last()

    val root: Dest get() = stack.first()

    val canPop: Boolean get() = stack.size > 1

    fun select(dest: Dest) {
        if (root == dest && !canPop) return
        stack.clear()
        stack.add(dest)
    }

    fun push(dest: Dest) {
        if (current != dest) stack.add(dest)
    }

    fun pop() {
        if (canPop) stack.removeAt(stack.lastIndex)
    }

    internal fun trail(): List<String> = stack.map { it.name }
}

private val NavSaver = listSaver<Nav, String>(
    save = { it.trail() },
    restore = { names ->
        val trail = names.mapNotNull { name -> runCatching { Dest.valueOf(name) }.getOrNull() }
        Nav(trail.ifEmpty { listOf(Dest.HOME) })
    },
)

@Composable
fun rememberNav(): Nav = rememberSaveable(saver = NavSaver) { Nav(listOf(Dest.HOME)) }
