package com.wakeup.dna

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * `.wakeuptheme` is a zip containing manifest.json, theme.json and image assets only.
 * Packages are untrusted input: every limit below is enforced while streaming, never from
 * the sizes the archive claims about itself.
 */
@Serializable
data class PackageManifest(
    val format: Int,
    val id: String,
    val name: String,
    val version: Int,
    val author: String = "",
    val license: String = "",
    val files: List<ManifestFile> = emptyList(),
)

@Serializable
data class ManifestFile(val path: String, val sha256: String, val size: Long)

data class PackageLimits(
    val maxEntries: Int = 120,
    val maxTotalBytes: Long = 48L * 1024 * 1024,
    val maxFileBytes: Long = 12L * 1024 * 1024,
    val maxThemeJsonBytes: Long = 256L * 1024,
    val maxPathLength: Int = 120,
    val maxDepth: Int = 4,
    /** Compressed-to-expanded ratio guard against decompression bombs. */
    val maxRatio: Long = 200,
)

class ThemePackageContent(val manifest: PackageManifest, val dna: ThemeDna, val assets: Map<String, ByteArray>, val warnings: List<Issue>)

sealed class PackageResult {
    class Ok(val content: ThemePackageContent) : PackageResult()
    class Rejected(val reason: String, val detail: String = "") : PackageResult()
}

object ThemePackage {
    const val FORMAT = 1
    /** Only still images. No fonts, audio, executables, scripts, archives or native libraries. */
    val ALLOWED_EXT = setOf("png", "webp", "jpg", "jpeg")
    private val SAFE_PATH = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*$")
    private val manifestJson = Json { ignoreUnknownKeys = false; isLenient = false }

    fun read(input: InputStream, limits: PackageLimits = PackageLimits(), existingIds: Set<String> = emptySet()): PackageResult {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        var count = 0
        var compressedSeen = 0L
        try {
            ZipInputStream(input).use { zin ->
                while (true) {
                    val e: ZipEntry = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (++count > limits.maxEntries) return PackageResult.Rejected("too many entries", "limit ${limits.maxEntries}")
                    val name = e.name
                    safePathProblem(name, limits)?.let { return PackageResult.Rejected("unsafe path", "$name: $it") }
                    if (name in files) return PackageResult.Rejected("duplicate entry", name)
                    val isJson = name == "manifest.json" || name == "theme.json"
                    if (!isJson) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext !in ALLOWED_EXT) return PackageResult.Rejected("file type not allowed", name)
                    }
                    val cap = if (isJson) limits.maxThemeJsonBytes else limits.maxFileBytes
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var n = 0L
                    while (true) {
                        val r = zin.read(chunk)
                        if (r < 0) break
                        n += r
                        total += r
                        if (n > cap) return PackageResult.Rejected("file too large", name)
                        if (total > limits.maxTotalBytes) return PackageResult.Rejected("package too large when expanded", "limit ${limits.maxTotalBytes} bytes")
                        buf.write(chunk, 0, r)
                    }
                    val comp = if (e.compressedSize >= 0) e.compressedSize else 0
                    compressedSeen += comp
                    if (comp > 0 && n / comp > limits.maxRatio) return PackageResult.Rejected("suspicious compression ratio", name)
                    files[name] = buf.toByteArray()
                }
            }
        } catch (ex: java.io.IOException) {
            return PackageResult.Rejected("unreadable archive", ex.message ?: "")
        }

        val mBytes = files["manifest.json"] ?: return PackageResult.Rejected("manifest.json missing")
        val tBytes = files["theme.json"] ?: return PackageResult.Rejected("theme.json missing")
        val manifest = try {
            manifestJson.decodeFromString(PackageManifest.serializer(), mBytes.decodeToString())
        } catch (ex: SerializationException) {
            return PackageResult.Rejected("manifest.json invalid", ex.message?.take(200) ?: "")
        } catch (ex: IllegalArgumentException) {
            return PackageResult.Rejected("manifest.json invalid", ex.message?.take(200) ?: "")
        }
        if (manifest.format != FORMAT) return PackageResult.Rejected("unsupported package format ${manifest.format}")

        // every payload file must be declared with a matching hash and size, and nothing undeclared may ride along
        val declared = manifest.files.associateBy { it.path }
        val payload = files.keys.filter { it != "manifest.json" && it != "theme.json" }
        for (p in payload) if (p !in declared) return PackageResult.Rejected("undeclared file", p)
        for ((p, mf) in declared) {
            val bytes = files[p] ?: return PackageResult.Rejected("declared file missing", p)
            if (bytes.size.toLong() != mf.size) return PackageResult.Rejected("size mismatch", p)
            if (sha256(bytes) != mf.sha256.lowercase()) return PackageResult.Rejected("hash mismatch", p)
        }

        val dna = try {
            ThemeDna.parse(tBytes.decodeToString())
        } catch (ex: SerializationException) {
            return PackageResult.Rejected("theme.json invalid", ex.message?.take(300) ?: "")
        } catch (ex: IllegalArgumentException) {
            return PackageResult.Rejected("theme.json invalid", ex.message?.take(300) ?: "")
        }
        if (dna.id != manifest.id) return PackageResult.Rejected("id mismatch between manifest and theme")
        val issues = DnaValidator.validate(dna)
        val errs = issues.filter { it.severity == Issue.Severity.ERROR }
        if (errs.isNotEmpty()) return PackageResult.Rejected("theme failed validation", errs.take(5).joinToString("; "))
        // bitmap layers may only reference files that were declared
        for (l in dna.world.layers) if (l.type == "bitmap") {
            val ref = l.str("ref")
            if (ref !in declared) return PackageResult.Rejected("bitmap layer references an undeclared file", ref)
        }
        if (dna.id in existingIds) return PackageResult.Rejected("a theme with this id is already installed", "import as a copy instead")
        return PackageResult.Ok(ThemePackageContent(manifest, dna, files.filterKeys { it in declared }, issues))
    }

    private fun safePathProblem(name: String, limits: PackageLimits): String? {
        if (name.isEmpty() || name.length > limits.maxPathLength) return "length"
        if (name.contains('\\') || name.startsWith("/") || name.contains(':') || name.contains("\u0000")) return "absolute or non-portable"
        if (name.split('/').any { it == ".." || it == "." || it.isEmpty() }) return "traversal segment"
        if (name.count { it == '/' } >= limits.maxDepth) return "too deep"
        if (!SAFE_PATH.matches(name)) return "characters"
        return null
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Build a package. Used by export and by tests. */
    fun write(dna: ThemeDna, assets: Map<String, ByteArray>, author: String = dna.provenance.author): ByteArray {
        val files = assets.map { (p, b) -> ManifestFile(p, sha256(b), b.size.toLong()) }
        val manifest = PackageManifest(FORMAT, dna.id, dna.name, dna.version, author, dna.provenance.license, files)
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            put("manifest.json", Json { prettyPrint = true; encodeDefaults = true }.encodeToString(PackageManifest.serializer(), manifest).toByteArray())
            put("theme.json", dna.encode().toByteArray())
            assets.forEach { (p, b) -> put(p, b) }
        }
        return out.toByteArray()
    }
}
