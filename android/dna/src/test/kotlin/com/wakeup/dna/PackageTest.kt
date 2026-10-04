package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return o.toByteArray()
    }

    private fun read(b: ByteArray, limits: PackageLimits = PackageLimits(), ids: Set<String> = emptySet()) = ThemePackage.read(ByteArrayInputStream(b), limits, ids)
    private fun rejected(r: PackageResult) = (r as? PackageResult.Rejected) ?: throw AssertionError("expected rejection, got $r")

    @Test fun roundTripAcceptsAValidPackage() {
        val dna = BuiltInThemes.load("fjordlys")
        val r = read(ThemePackage.write(dna, mapOf("previews/p.png" to png)))
        assertTrue(r is PackageResult.Ok)
        assertEquals(dna, (r as PackageResult.Ok).content.dna)
    }

    @Test fun zipSlipRejected() {
        for (name in listOf("../evil.png", "a/../../evil.png", "/abs.png", "C:/x.png", "a\\b.png")) {
            val m = rejected(read(zip(name to png)))
            assertTrue(name, m.reason.contains("unsafe path"))
        }
    }

    @Test fun executableAndScriptContentRejected() {
        for (name in listOf("lib.so", "run.sh", "a.apk", "x.dex", "payload.js", "evil.exe", "theme.kts", "fonts.ttf", "ambient.mp3")) {
            assertTrue(name, rejected(read(zip(name to byteArrayOf(1, 2, 3)))).reason.contains("not allowed"))
        }
    }

    @Test fun nestedArchiveRejected() {
        assertTrue(rejected(read(zip("inner.zip" to zip("a.png" to png)))).reason.contains("not allowed"))
    }

    @Test fun decompressionBombRejected() {
        val big = ByteArray(8 * 1024 * 1024) // compresses to a few KB
        val r = rejected(read(zip("manifest.json" to "{}".toByteArray(), "huge.png" to big), PackageLimits(maxFileBytes = 1024 * 1024)))
        assertTrue(r.reason.contains("too large"))
        val r2 = rejected(read(zip("a.png" to big, "b.png" to big), PackageLimits(maxTotalBytes = 10L * 1024 * 1024, maxFileBytes = 9L * 1024 * 1024)))
        assertTrue(r2.reason, r2.reason.contains("too large") || r2.reason.contains("ratio"))
    }

    @Test fun tooManyEntriesRejected() {
        val files = (0 until 30).map { "f$it.png" to png }.toTypedArray()
        assertTrue(rejected(read(zip(*files), PackageLimits(maxEntries = 10))).reason.contains("too many"))
    }

    @Test fun hashMismatchAndUndeclaredFilesRejected() {
        val dna = BuiltInThemes.load("linen")
        val good = ThemePackage.write(dna, mapOf("previews/p.png" to png))
        // tamper: rewrite with an extra undeclared image
        val manifest = zipEntries(good)["manifest.json"]!!
        val theme = zipEntries(good)["theme.json"]!!
        val extra = zip("manifest.json" to manifest, "theme.json" to theme, "previews/p.png" to png, "previews/q.png" to png)
        assertEquals("undeclared file", rejected(read(extra)).reason)
        val wrong = zip("manifest.json" to manifest, "theme.json" to theme, "previews/p.png" to png + byteArrayOf(9))
        assertTrue(rejected(read(wrong)).reason.let { it == "size mismatch" || it == "hash mismatch" })
    }

    @Test fun invalidThemeRejectedWithReason() {
        val dna = BuiltInThemes.load("linen")
        val bad = dna.copy(sound = dna.sound.copy(whileLocked = true))
        assertTrue(rejected(read(ThemePackage.write(bad, emptyMap()))).reason.contains("validation"))
    }

    @Test fun unknownFieldsRejected() {
        val dna = BuiltInThemes.load("linen")
        val json = dna.encode().replaceFirst("\"id\"", "\"onLoad\": \"rm -rf /\", \"id\"")
        val m = ThemePackage.write(dna, emptyMap())
        val e = zipEntries(m)
        val tampered = zip("manifest.json" to e["manifest.json"]!!, "theme.json" to json.toByteArray())
        assertTrue(rejected(read(tampered)).reason.contains("invalid"))
    }

    @Test fun duplicateIdRejected() {
        val dna = BuiltInThemes.load("linen")
        assertTrue(rejected(read(ThemePackage.write(dna, emptyMap()), ids = setOf("linen"))).reason.contains("already installed"))
    }

    @Test fun bitmapLayerMustReferenceDeclaredFile() {
        val dna = BuiltInThemes.load("linen")
        val layer = LayerSpec("pic", "bitmap", params = kotlinx.serialization.json.JsonObject(mapOf("ref" to kotlinx.serialization.json.JsonPrimitive("layers/missing.png"))))
        val t = dna.copy(world = dna.world.copy(layers = dna.world.layers + layer))
        assertTrue(rejected(read(ThemePackage.write(t, emptyMap()))).reason.contains("undeclared"))
    }

    @Test fun garbageIsRejectedNotThrown() {
        rejected(read(ByteArray(64) { it.toByte() }))
        rejected(read(ByteArray(0)))
    }

    private fun zipEntries(b: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(b)).use { z -> while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes() } }
        return out
    }
}
