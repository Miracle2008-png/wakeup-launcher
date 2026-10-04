package com.wakeup.launcher.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.wakeup.dna.BuiltInThemes
import com.wakeup.dna.DnaValidator
import com.wakeup.dna.PackageResult
import com.wakeup.dna.ThemeDna
import com.wakeup.dna.ThemePackage
import com.wakeup.dna.ThemePackageContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

data class InstalledTheme(val dna: ThemeDna, val builtIn: Boolean)

@Serializable
data class VersionEnvelope(val name: String, val savedMs: Long, val dna: ThemeDna)

data class VersionEntry(val file: File, val name: String, val savedMs: Long)

/** Built-in themes (read-only, from the app) plus user themes (installed, remixed, duplicated) in the library. */
class ThemeRepository(private val lib: ContentLibrary) {
    private val _themes = MutableStateFlow<List<InstalledTheme>>(emptyList())
    val themes: StateFlow<List<InstalledTheme>> get() = _themes
    private val bitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val env = Json { ignoreUnknownKeys = false; encodeDefaults = true; prettyPrint = true }

    init { reload() }

    fun find(id: String): InstalledTheme? = _themes.value.firstOrNull { it.dna.id == id }
    fun ids() = _themes.value.map { it.dna.id }.toSet()

    fun reload() {
        val built = runCatching { BuiltInThemes.all().map { InstalledTheme(it, true) } }.getOrDefault(emptyList())
        val users = lib.userThemes.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { d ->
            // a corrupt or invalid user theme is skipped, never allowed to break the launcher
            runCatching {
                val dna = ThemeDna.parse(File(d, "theme.json").readText())
                if (dna.id != d.name || DnaValidator.errors(dna).isNotEmpty() || dna.id in built.map { it.dna.id }) null else InstalledTheme(dna, false)
            }.getOrNull()
        }
        _themes.value = built + users
    }

    sealed class InstallResult {
        class Installed(val theme: ThemeDna) : InstallResult()
        class Failed(val reason: String) : InstallResult()
    }

    fun import(stream: InputStream, knownSizeHint: Long = -1): InstallResult {
        if (knownSizeHint > 0 && !lib.hasRoom(knownSizeHint * 2)) return InstallResult.Failed("Not enough room in the WakeUp library")
        return when (val r = ThemePackage.read(stream, existingIds = ids())) {
            is PackageResult.Rejected -> InstallResult.Failed(listOf(r.reason, r.detail).filter { it.isNotBlank() }.joinToString(": "))
            is PackageResult.Ok -> install(r.content)
        }
    }

    private fun install(c: ThemePackageContent): InstallResult {
        val need = c.assets.values.sumOf { it.size.toLong() }
        if (!lib.hasRoom(need + 64 * 1024)) return InstallResult.Failed("Not enough room in the WakeUp library")
        // imported content is third-party until proven otherwise: mark it personal-use unless the author declared a licence
        val dna = if (c.dna.provenance.license.isBlank()) c.dna.copy(provenance = c.dna.provenance.copy(personalUseOnly = true)) else c.dna
        return try {
            write(dna, c.assets)
            reload()
            InstallResult.Installed(dna)
        } catch (e: Exception) {
            File(lib.userThemes, dna.id).deleteRecursively()
            InstallResult.Failed("Could not save the theme: ${e.javaClass.simpleName}")
        }
    }

    private fun write(dna: ThemeDna, assets: Map<String, ByteArray>) {
        val dir = File(lib.userThemes, dna.id)
        val staging = File(lib.userThemes, ".${dna.id}.staging")
        staging.deleteRecursively(); staging.mkdirs()
        File(staging, "theme.json").writeText(dna.encode())
        for ((p, b) in assets) {
            val f = File(staging, "assets/$p")
            // defence in depth: the validator already rejected traversal, confirm the final path stays inside
            require(f.canonicalPath.startsWith(File(staging, "assets").canonicalPath + File.separator)) { "path escapes theme folder" }
            f.parentFile?.mkdirs(); f.writeBytes(b)
        }
        // keep existing assets when saving a theme that has no new ones
        if (assets.isEmpty() && File(dir, "assets").exists()) File(dir, "assets").copyRecursively(File(staging, "assets"), true)
        dir.deleteRecursively()
        check(staging.renameTo(dir)) { "rename failed" }
    }

    /** Saves a created or remixed theme as a user theme. Never overwrites a built-in. */
    fun saveUserTheme(dna: ThemeDna): Result<ThemeDna> = runCatching {
        require(BuiltInThemes.IDS.none { it == dna.id }) { "built-in themes are immutable; save as a new theme" }
        val errs = DnaValidator.errors(dna)
        require(errs.isEmpty()) { errs.first().toString() }
        require(lib.hasRoom(256 * 1024)) { "Not enough room in the WakeUp library" }
        write(dna, emptyMap()); reload(); dna
    }

    fun uniqueId(base: String): String {
        var id = ContentLibrary.slug(base, 48); var n = 2
        while (find(id) != null) { id = ContentLibrary.slug(base, 44) + "-" + n++ }
        return id
    }

    /** Copies any theme (built-in included) into a new editable user theme. The original is untouched. */
    fun duplicate(sourceId: String, newName: String): Result<ThemeDna> = runCatching {
        val src = find(sourceId)?.dna ?: error("theme not found")
        val id = uniqueId(newName)
        val copy = src.copy(id = id, name = newName.take(60), parent = "${src.id}@${src.version}", version = 1)
        val assets = HashMap<String, ByteArray>()
        File(lib.userThemes, "${src.id}/assets").takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.forEach {
            assets[it.relativeTo(File(lib.userThemes, "${src.id}/assets")).invariantSeparatorsPath] = it.readBytes()
        }
        write(copy, assets); reload(); copy
    }

    fun delete(id: String): Boolean {
        val t = find(id) ?: return false
        if (t.builtIn) return false
        File(lib.userThemes, id).deleteRecursively()
        File(lib.versions, id).deleteRecursively()
        reload(); return true
    }

    fun exportPackage(id: String): ByteArray? {
        val t = find(id)?.dna ?: return null
        val assets = HashMap<String, ByteArray>()
        File(lib.userThemes, "$id/assets").takeIf { it.exists() }?.let { root ->
            root.walkTopDown().filter { it.isFile }.forEach { assets[it.relativeTo(root).invariantSeparatorsPath] = it.readBytes() }
        }
        return ThemePackage.write(t, assets)
    }

    fun asset(themeId: String, ref: String): Bitmap? {
        val key = "$themeId/$ref"
        bitmaps.get(key)?.let { return it }
        val f = File(lib.userThemes, "$themeId/assets/$ref")
        val root = File(lib.userThemes, "$themeId/assets")
        if (!f.exists() || !f.canonicalPath.startsWith(root.canonicalPath + File.separator)) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = runCatching { BitmapFactory.decodeFile(f.path, opts) }.getOrNull() ?: return null
        bitmaps.put(key, bmp); return bmp
    }

    // ---- version history ----
    fun snapshot(dna: ThemeDna, name: String): VersionEntry? = runCatching {
        val dir = File(lib.versions, dna.id).apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val f = File(dir, "$ts-${ContentLibrary.slug(name, 30)}.json")
        f.writeText(env.encodeToString(VersionEnvelope.serializer(), VersionEnvelope(name.take(60), ts, dna)))
        VersionEntry(f, name.take(60), ts)
    }.getOrNull()

    fun versions(id: String): List<VersionEntry> =
        File(lib.versions, id).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { val e = env.decodeFromString(VersionEnvelope.serializer(), f.readText()); VersionEntry(f, e.name, e.savedMs) }.getOrNull()
        }.sortedByDescending { it.savedMs }

    fun readVersion(v: VersionEntry): ThemeDna? = runCatching { env.decodeFromString(VersionEnvelope.serializer(), v.file.readText()).dna }.getOrNull()

    /** Restore snapshots the current state first so restoring is itself undoable. Built-in themes are restored as a duplicate. */
    fun restore(currentId: String, v: VersionEntry): Result<ThemeDna> = runCatching {
        val old = readVersion(v) ?: error("version unreadable")
        val cur = find(currentId)
        if (cur != null && !cur.builtIn) snapshot(cur.dna, "Before restoring ${v.name}")
        val target = if (cur == null || cur.builtIn) old.copy(id = uniqueId(old.name + " restored"), name = old.name + " (restored)", version = 1)
        else old.copy(id = cur.dna.id, version = cur.dna.version + 1)
        write(target, emptyMap()); reload(); target
    }
}
