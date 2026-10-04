package com.wakeup.dna

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An installed activity on a particular profile. [user] is the profile serial number, not a personal identifier. */
@Serializable
data class AppRef(val pkg: String, val cls: String, val user: Long = 0L) {
    val key: String get() = "$pkg/$cls/$user"
}

@Serializable
data class Cell(val page: Int, val col: Int, val row: Int, val spanX: Int = 1, val spanY: Int = 1)

@Serializable
sealed class HomeItem {
    abstract val id: String
    abstract val cell: Cell
    abstract fun at(c: Cell): HomeItem
}

@Serializable @SerialName("app")
data class AppItem(override val id: String, override val cell: Cell, val app: AppRef) : HomeItem() {
    override fun at(c: Cell) = copy(cell = c)
}

@Serializable @SerialName("folder")
data class FolderItem(override val id: String, override val cell: Cell, val title: String, val apps: List<AppRef>) : HomeItem() {
    override fun at(c: Cell) = copy(cell = c)
}

/** A WakeUp-native widget; [kind] names a definition in the widget registry. */
@Serializable @SerialName("wake")
data class WakeWidgetItem(override val id: String, override val cell: Cell, val kind: String, val config: Map<String, String> = emptyMap()) : HomeItem() {
    override fun at(c: Cell) = copy(cell = c)
}

/** A real Android AppWidget bound through AppWidgetHost. */
@Serializable @SerialName("system")
data class SystemWidgetItem(override val id: String, override val cell: Cell, val appWidgetId: Int, val provider: String) : HomeItem() {
    override fun at(c: Cell) = copy(cell = c)
}

@Serializable
data class HomeState(
    val columns: Int = 4,
    val rows: Int = 6,
    val pages: Int = 2,
    val items: List<HomeItem> = emptyList(),
    val dock: List<AppRef> = emptyList(),
    val version: Int = 1,
) {
    fun encode(): String = HomeOps.json.encodeToString(serializer(), this)
    companion object { fun decode(s: String): HomeState = HomeOps.json.decodeFromString(serializer(), s) }
}

/** Pure operations on the Home Screen model. Everything returns a new state; nothing mutates. */
object HomeOps {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    const val MAX_PAGES = 10
    const val MAX_FOLDER = 12

    fun fits(s: HomeState, c: Cell, ignoreId: String? = null): Boolean {
        if (c.col < 0 || c.row < 0 || c.page < 0 || c.page >= s.pages) return false
        if (c.col + c.spanX > s.columns || c.row + c.spanY > s.rows) return false
        return s.items.none { it.id != ignoreId && overlaps(it.cell, c) }
    }

    fun overlaps(a: Cell, b: Cell) =
        a.page == b.page && a.col < b.col + b.spanX && b.col < a.col + a.spanX && a.row < b.row + b.spanY && b.row < a.row + a.spanY

    fun itemAt(s: HomeState, page: Int, col: Int, row: Int, ignoreId: String? = null): HomeItem? =
        s.items.firstOrNull { it.id != ignoreId && it.cell.page == page && col in it.cell.col until it.cell.col + it.cell.spanX && row in it.cell.row until it.cell.row + it.cell.spanY }

    /** Nearest free cell to the wish on its page, then later pages; adds a page if everything is full. */
    fun findFree(s: HomeState, wish: Cell, ignoreId: String? = null): Cell? {
        var best: Cell? = null; var bestD = Int.MAX_VALUE
        for (p in wish.page until s.pages) {
            for (r in 0..(s.rows - wish.spanY)) for (c in 0..(s.columns - wish.spanX)) {
                val cand = wish.copy(page = p, col = c, row = r)
                if (fits(s, cand, ignoreId)) {
                    val d = (p - wish.page) * 1000 + Math.abs(c - wish.col) + Math.abs(r - wish.row)
                    if (d < bestD) { bestD = d; best = cand }
                }
            }
            if (best != null) return best
        }
        return null
    }

    fun add(s: HomeState, item: HomeItem): HomeState {
        val spot = findFree(s, item.cell) ?: run {
            if (s.pages >= MAX_PAGES) return s
            val grown = s.copy(pages = s.pages + 1)
            return add(grown, item.at(item.cell.copy(page = grown.pages - 1, col = 0, row = 0)))
        }
        return s.copy(items = s.items + item.at(spot))
    }

    fun remove(s: HomeState, id: String) = s.copy(items = s.items.filterNot { it.id == id })

    /** Moves an item to the nearest free cell around [to]. Dropping an app on an app or folder is handled by [dropOnto]. */
    fun move(s: HomeState, id: String, to: Cell): HomeState {
        val it = s.items.firstOrNull { x -> x.id == id } ?: return s
        val target = to.copy(spanX = it.cell.spanX, spanY = it.cell.spanY)
        val spot = findFree(s, target, ignoreId = id) ?: return s
        return s.copy(items = s.items.map { x -> if (x.id == id) x.at(spot) else x })
    }

    fun resize(s: HomeState, id: String, spanX: Int, spanY: Int): HomeState {
        val it = s.items.firstOrNull { x -> x.id == id } ?: return s
        val c = it.cell.copy(spanX = spanX.coerceIn(1, s.columns), spanY = spanY.coerceIn(1, s.rows))
        return if (fits(s, c, ignoreId = id)) s.copy(items = s.items.map { x -> if (x.id == id) x.at(c) else x }) else s
    }

    /** Drop [id] on the item under it: app+app makes a folder, app+folder joins it. Returns null if not applicable. */
    fun dropOnto(s: HomeState, id: String, targetId: String, newId: String): HomeState? {
        val a = s.items.firstOrNull { it.id == id } as? AppItem ?: return null
        val t = s.items.firstOrNull { it.id == targetId } ?: return null
        return when (t) {
            is AppItem -> if (t.id == a.id) null else s.copy(items = s.items.filterNot { it.id == a.id || it.id == t.id } +
                FolderItem(newId, t.cell.copy(spanX = 1, spanY = 1), "Folder", listOf(t.app, a.app)))
            is FolderItem -> if (t.apps.size >= MAX_FOLDER) null else s.copy(items = s.items.filterNot { it.id == a.id }.map { if (it.id == t.id) t.copy(apps = t.apps + a.app) else it })
            else -> null
        }
    }

    fun removeFromFolder(s: HomeState, folderId: String, app: AppRef, newItemId: String): HomeState {
        val f = s.items.firstOrNull { it.id == folderId } as? FolderItem ?: return s
        val rest = f.apps - app
        val without = s.items.filterNot { it.id == folderId }
        val base = if (rest.size >= 2) s.copy(items = without + f.copy(apps = rest))
        else if (rest.size == 1) s.copy(items = without + AppItem(f.id, f.cell, rest[0]))
        else s.copy(items = without)
        return add(base, AppItem(newItemId, f.cell, app))
    }

    fun rename(s: HomeState, folderId: String, title: String) =
        s.copy(items = s.items.map { if (it.id == folderId && it is FolderItem) it.copy(title = title.take(30)) else it })

    fun setDock(s: HomeState, dock: List<AppRef>, max: Int) = s.copy(dock = dock.distinct().take(max))

    /** Drops pages that became empty (except the first) and renumbers. */
    fun compact(s: HomeState): HomeState {
        val used = s.items.map { it.cell.page }.toSortedSet()
        val keep = (0 until s.pages).filter { it == 0 || it in used }
        val remap = keep.withIndex().associate { (n, old) -> old to n }
        return s.copy(pages = keep.size.coerceAtLeast(1), items = s.items.map { it.at(it.cell.copy(page = remap.getValue(it.cell.page))) })
    }

    /** Removes references to apps that are no longer installed. */
    fun prune(s: HomeState, installed: Set<String>): HomeState {
        fun ok(a: AppRef) = a.key in installed
        val items = s.items.mapNotNull {
            when (it) {
                is AppItem -> if (ok(it.app)) it else null
                is FolderItem -> {
                    val keep = it.apps.filter(::ok)
                    when { keep.size >= 2 -> it.copy(apps = keep); keep.size == 1 -> AppItem(it.id, it.cell, keep[0]); else -> null }
                }
                else -> it
            }
        }
        return s.copy(items = items, dock = s.dock.filter(::ok))
    }
}
