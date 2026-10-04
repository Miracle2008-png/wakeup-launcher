package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOpsTest {
    private fun app(n: Int) = AppRef("p$n", "c$n")
    private fun item(n: Int, page: Int = 0, col: Int = 0, row: Int = 0) = AppItem("i$n", Cell(page, col, row), app(n))
    private val empty = HomeState(columns = 4, rows = 6, pages = 2)

    @Test fun addPlacesAtWishAndAvoidsOverlap() {
        var s = HomeOps.add(empty, item(1))
        s = HomeOps.add(s, item(2))
        assertEquals(2, s.items.size)
        assertFalse(HomeOps.overlaps(s.items[0].cell, s.items[1].cell))
    }

    @Test fun widgetsRespectSpanAndBounds() {
        val w = WakeWidgetItem("w", Cell(0, 3, 0, 2, 2), "clock")
        val s = HomeOps.add(empty, w)
        val c = s.items.single().cell
        assertTrue(c.col + c.spanX <= 4)
    }

    @Test fun fullPageSpillsToNextThenGrows() {
        var s = empty.copy(pages = 1)
        for (i in 0 until 25) s = HomeOps.add(s, item(i))
        assertEquals(2, s.pages)
        assertEquals(25, s.items.size)
    }

    @Test fun moveSnapsToNearestFreeCell() {
        var s = HomeOps.add(empty, item(1, 0, 0, 0))
        s = HomeOps.add(s, item(2, 0, 1, 0))
        val moved = HomeOps.move(s, "i1", Cell(0, 1, 0))
        val c = moved.items.first { it.id == "i1" }.cell
        assertTrue(HomeOps.fits(moved.copy(items = moved.items.filterNot { it.id == "i1" }), c))
        assertFalse(c.col == 1 && c.row == 0)
    }

    @Test fun resizeOnlyWhenSpaceIsFree() {
        var s = HomeOps.add(empty, WakeWidgetItem("w", Cell(0, 0, 0, 2, 2), "clock"))
        s = HomeOps.add(s, item(1, 0, 2, 0))
        val blocked = HomeOps.resize(s, "w", 3, 2)
        assertEquals(2, blocked.items.first { it.id == "w" }.cell.spanX)
        val ok = HomeOps.resize(s, "w", 2, 3)
        assertEquals(3, ok.items.first { it.id == "w" }.cell.spanY)
    }

    @Test fun folderLifecycle() {
        var s = HomeOps.add(empty, item(1, 0, 0, 0)); s = HomeOps.add(s, item(2, 0, 1, 0)); s = HomeOps.add(s, item(3, 0, 2, 0))
        s = HomeOps.dropOnto(s, "i1", "i2", "f1")!!
        val f = s.items.filterIsInstance<FolderItem>().single()
        assertEquals(2, f.apps.size)
        s = HomeOps.dropOnto(s, "i3", "f1", "x")!!
        assertEquals(3, (s.items.filterIsInstance<FolderItem>().single()).apps.size)
        assertEquals(1, s.items.size)
        s = HomeOps.removeFromFolder(s, "f1", app(1), "n1")
        assertEquals(2, s.items.size)
        s = HomeOps.removeFromFolder(s, "f1", app(2), "n2")
        // folder with one app left collapses to a plain app
        assertTrue(s.items.none { it is FolderItem })
        assertEquals(3, s.items.size)
    }

    @Test fun dropOntoRejectsNonApps() {
        val s = HomeOps.add(HomeOps.add(empty, item(1)), WakeWidgetItem("w", Cell(0, 0, 2, 2, 2), "clock"))
        assertNull(HomeOps.dropOnto(s, "i1", "w", "f"))
        assertNull(HomeOps.dropOnto(s, "i1", "i1", "f"))
    }

    @Test fun compactRemovesEmptyPagesButKeepsFirst() {
        val s = HomeState(pages = 4, items = listOf(item(1, 0), item(2, 2)))
        val c = HomeOps.compact(s)
        assertEquals(2, c.pages)
        assertEquals(1, c.items.first { it.id == "i2" }.cell.page)
        assertEquals(1, HomeOps.compact(HomeState(pages = 3)).pages)
    }

    @Test fun pruneRemovesUninstalledApps() {
        val f = FolderItem("f", Cell(0, 0, 0), "F", listOf(app(1), app(2), app(3)))
        val s = HomeState(items = listOf(item(4), f), dock = listOf(app(1), app(9)))
        val p = HomeOps.prune(s, setOf(app(1).key, app(2).key))
        assertEquals(1, p.items.size)
        assertEquals(2, (p.items.single() as FolderItem).apps.size)
        assertEquals(listOf(app(1)), p.dock)
    }

    @Test fun serializationRoundTripIncludingPolymorphicItems() {
        val s = HomeState(items = listOf(item(1), FolderItem("f", Cell(0, 1, 0), "F", listOf(app(2), app(3))),
            WakeWidgetItem("w", Cell(0, 0, 1, 2, 2), "horizon", mapOf("a" to "b")), SystemWidgetItem("s", Cell(1, 0, 0, 4, 2), 7, "x/y")), dock = listOf(app(5)))
        assertEquals(s, HomeState.decode(s.encode()))
    }

    @Test fun findFreeReturnsNullWhenImpossible() {
        val full = HomeState(columns = 4, rows = 6, pages = 1, items = (0 until 24).map { item(it, 0, it % 4, it / 4) })
        assertNull(HomeOps.findFree(full, Cell(0, 0, 0)))
        assertNotNull(HomeOps.findFree(full.copy(pages = 2), Cell(0, 0, 0)))
    }
}
