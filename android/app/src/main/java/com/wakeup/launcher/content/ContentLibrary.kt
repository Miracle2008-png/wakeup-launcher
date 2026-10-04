package com.wakeup.launcher.content

import android.content.Context
import java.io.File

/**
 * Layout of WakeUp's app-private content library. Everything lives under filesDir (not shared storage),
 * so other apps cannot read it and uninstalling WakeUp removes it. Users move content in and out only
 * through explicit export and import.
 *
 *   WakeUp/
 *     themes/        built-in themes are read from the app; this holds nothing but is reserved for downloaded packs
 *     user-themes/   one folder per installed or created theme: theme.json + assets/
 *     versions/      per-theme named snapshots
 *     wallpapers/    user-selected personal images, copied in after the user picks them
 *     widgets/       widget configuration
 *     icons/ sounds/ reserved content types
 *     collections/   user collections (JSON)
 *     setups/        saved setups (JSON)
 *     backups/       local backups
 *   cacheDir/        thumbnails and previews, safe to delete at any time
 */
class ContentLibrary(ctx: Context, val quotaBytes: Long = 512L * 1024 * 1024) {
    val root = File(ctx.filesDir, "WakeUp").apply { mkdirs() }
    val cache = File(ctx.cacheDir, "WakeUp").apply { mkdirs() }

    private fun dir(name: String) = File(root, name).apply { mkdirs() }
    val themes get() = dir("themes")
    val userThemes get() = dir("user-themes")
    val versions get() = dir("versions")
    val wallpapers get() = dir("wallpapers")
    val widgets get() = dir("widgets")
    val icons get() = dir("icons")
    val sounds get() = dir("sounds")
    val collections get() = dir("collections")
    val setups get() = dir("setups")
    val backups get() = dir("backups")
    val state get() = dir("state")

    fun usedBytes(): Long = size(root)
    fun cacheBytes(): Long = size(cache)

    fun clearCache() { cache.listFiles()?.forEach { it.deleteRecursively() }; cache.mkdirs() }

    fun hasRoom(extraBytes: Long) = usedBytes() + extraBytes <= quotaBytes

    /** Keeps the cache bounded: removes oldest files first until under [maxBytes]. */
    fun trimCache(maxBytes: Long = 64L * 1024 * 1024) {
        val files = cache.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= maxBytes) break; total -= f.length(); f.delete() }
    }

    private fun size(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        /** File-system safe slug used for names we write to disk; never trusts the original string. */
        fun slug(s: String, max: Int = 40): String =
            s.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").take(max).ifEmpty { "item" }
    }
}
