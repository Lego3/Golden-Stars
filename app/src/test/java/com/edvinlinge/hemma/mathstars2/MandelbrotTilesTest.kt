package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MandelbrotTilesTest {

    @Test
    fun `zoom steps are powers of two matching double-tap jumps`() {
        assertEquals(0, MandelbrotTiles.zoomStep(1.0))
        assertEquals(1, MandelbrotTiles.zoomStep(2.0))
        assertEquals(2, MandelbrotTiles.zoomStep(4.0))
        assertEquals(-1, MandelbrotTiles.zoomStep(0.5))
        assertEquals(2, MandelbrotTiles.zoomStep(3.0))
        assertEquals(1.0, MandelbrotTiles.discreteZoom(0), 0.0)
        assertEquals(8.0, MandelbrotTiles.discreteZoom(3), 0.0)
        assertEquals(0.5, MandelbrotTiles.discreteZoom(-1), 0.0)
    }

    @Test
    fun `tile world size halves when zoom step increases`() {
        val parent = MandelbrotTiles.tileWorldSize(0, 256, 800, 600)
        val child = MandelbrotTiles.tileWorldSize(1, 256, 800, 600)
        assertEquals(parent, child * 2.0, 1e-12)
    }

    @Test
    fun `large screens pick 512 pixel tiles so fewer of them cover the view`() {
        assertEquals(256, MandelbrotTiles.tilePixelSize(800, 600))
        assertEquals(512, MandelbrotTiles.tilePixelSize(1080, 2340))
    }

    @Test
    fun `parent and child indices stay aligned for negative tiles`() {
        assertEquals(-1L, MandelbrotTiles.parentTileX(-1L))
        assertEquals(-1L, MandelbrotTiles.parentTileX(-2L))
        assertEquals(0L, MandelbrotTiles.parentTileX(0L))
        assertEquals(0L, MandelbrotTiles.parentTileX(1L))
        assertEquals(-2L, MandelbrotTiles.childTileX(-1L, 0))
        assertEquals(-1L, MandelbrotTiles.childTileX(-1L, 1))
    }

    @Test
    fun `source rect inside a parent is the matching quadrant`() {
        val parent = MandelbrotTiles.sourceRectInAncestor(
            tileX = 5,
            tileY = 8,
            tileStep = 3,
            ancestorX = 2,
            ancestorY = 4,
            ancestorStep = 2,
            ancestorPixelSize = 256,
        )
        assertEquals(128, parent.left)
        assertEquals(0, parent.top)
        assertEquals(256, parent.right)
        assertEquals(128, parent.bottom)
    }

    @Test
    fun `source rect inside a grandparent uses a 64 pixel window`() {
        val src = MandelbrotTiles.sourceRectInAncestor(
            tileX = 5,
            tileY = 8,
            tileStep = 3,
            ancestorX = 1,
            ancestorY = 2,
            ancestorStep = 1,
            ancestorPixelSize = 256,
        )
        assertEquals(64, src.right - src.left)
        assertEquals(64, src.bottom - src.top)
        assertEquals((5 - 1 * 4).toInt() * 64, src.left)
        assertEquals((8 - 2 * 4).toInt() * 64, src.top)
    }

    @Test
    fun `source rect for a negative child is the right half of its parent`() {
        val src = MandelbrotTiles.sourceRectInAncestor(
            tileX = -1,
            tileY = -2,
            tileStep = 1,
            ancestorX = -1,
            ancestorY = -1,
            ancestorStep = 0,
            ancestorPixelSize = 256,
        )
        assertEquals(128, src.left)
        assertEquals(0, src.top)
        assertEquals(256, src.right)
        assertEquals(128, src.bottom)
    }

    @Test
    fun `visible tile range covers the viewport and stays finite at deep zoom`() {
        val range = MandelbrotTiles.visibleTileRange(
            offsetX = -0.5,
            offsetY = 0.0,
            zoom = 1.0,
            viewWidth = 800,
            viewHeight = 600,
            zoomStep = 0,
            tilePixelSize = 256,
        )
        assertTrue(range.tileCount in 4L..24L)
        assertTrue(range.x0 <= range.x1)
        assertTrue(range.y0 <= range.y1)

        val deep = MandelbrotTiles.visibleTileRange(
            offsetX = -0.75,
            offsetY = 0.1,
            zoom = 1.0e12,
            viewWidth = 800,
            viewHeight = 600,
            zoomStep = MandelbrotTiles.zoomStep(1.0e12),
            tilePixelSize = 256,
        )
        assertTrue(deep.tileCount in 4L..24L)
    }

    @Test
    fun `tile screen rect maps the tile center to the view center when they share a focus`() {
        val zoom = 1.0
        val step = 0
        val tilePx = 256
        val world = MandelbrotTiles.tileWorldSize(step, tilePx, 800, 600)
        val tileX = -1L
        val tileY = 0L
        val offsetX = (tileX + 0.5) * world
        val offsetY = (tileY + 0.5) * world
        val dest = MandelbrotTiles.tileScreenRect(
            tileX, tileY, step, tilePx, zoom, offsetX, offsetY, 800, 600,
        )
        val centerX = (dest.left + dest.right) / 2f
        val centerY = (dest.top + dest.bottom) / 2f
        assertEquals(400f, centerX, 0.5f)
        assertEquals(300f, centerY, 0.5f)
    }

    @Test
    fun `a parent full tile outranks a same-step preview because it contributes more pixels`() {
        val targetStep = 2
        val preview = MandelbrotTiles.TileKey(2, 0, 0, 256, 600, preview = true)
        val parentFull = MandelbrotTiles.TileKey(1, 0, 0, 256, 600, preview = false)
        val selfFull = MandelbrotTiles.TileKey(2, 0, 0, 256, 600, preview = false)
        assertTrue(MandelbrotTiles.coverageSamples(parentFull, targetStep) > MandelbrotTiles.coverageSamples(preview, targetStep))
        assertTrue(MandelbrotTiles.coverageSamples(selfFull, targetStep) > MandelbrotTiles.coverageSamples(parentFull, targetStep))
    }

    @Test
    fun `compose draw list scales a parent tile into the missing child`() {
        val parent = MandelbrotTiles.TileKey(0, -1, -1, 256, 600, preview = false)
        val draws = MandelbrotTiles.composeDrawList(
            zoom = 2.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            available = setOf(parent),
        )
        assertTrue(draws.isNotEmpty())
        assertTrue(draws.all { it.key == parent })
        assertTrue(draws.any { it.src.right - it.src.left == 128 })
    }

    @Test
    fun `compose draw list paints sharper children on top of a parent`() {
        val parent = MandelbrotTiles.TileKey(0, -1, 0, 256, 600, preview = false)
        val child = MandelbrotTiles.TileKey(
            zoomStep = 1,
            tileX = MandelbrotTiles.childTileX(-1, 0),
            tileY = MandelbrotTiles.childTileY(0, 0),
            tilePixelSize = 256,
            viewMinEdge = 600,
            preview = false,
        )
        val draws = MandelbrotTiles.composeDrawList(
            zoom = 1.5,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            available = setOf(parent, child),
        )
        val lastChild = draws.indexOfLast { it.key == child }
        val lastParent = draws.indexOfLast { it.key == parent }
        assertTrue(lastChild >= 0)
        assertTrue(lastParent >= 0)
        assertTrue(lastChild > lastParent)
    }

    @Test
    fun `render plan fills visible tiles first then pan direction then the next zoom step`() {
        val cached = mutableSetOf<MandelbrotTiles.TileKey>()
        val plan = MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 1,
            panSignY = 0,
            zoomSign = 1,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { it in cached },
        )
        assertTrue(plan.visibleFull.isNotEmpty())
        assertTrue(plan.prefetch.isNotEmpty())
        val visible = plan.visibleFull.toSet()
        assertTrue(plan.prefetch.none { it in visible })

        val visibleRange = MandelbrotTiles.visibleTileRange(
            -0.5, 0.0, 1.0, 800, 600, 0, 256,
        )
        val firstPrefetch = plan.prefetch.first()
        assertTrue(firstPrefetch.tileX > visibleRange.x1 || firstPrefetch.tileX < visibleRange.x0 || firstPrefetch.zoomStep == 1)

        cached.addAll(plan.visibleFull)
        val afterVisible = MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 1,
            panSignY = 0,
            zoomSign = 1,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { it in cached },
        )
        assertTrue(afterVisible.visibleFull.isEmpty())
        assertTrue(afterVisible.prefetch.any { it.zoomStep == 1 })
        assertTrue(afterVisible.prefetch.any { it.tileX > visibleRange.x1 })
    }

    @Test
    fun `visible tiles complete only when every on-screen full tile is cached`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val keys = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false)
        val cached = keys.toMutableSet()
        assertTrue(
            MandelbrotTiles.visibleTilesComplete(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in cached },
        )
        cached.remove(keys.first())
        assertFalse(
            MandelbrotTiles.visibleTilesComplete(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in cached },
        )
    }

    @Test
    fun `viewport is covered by a parent even when exact tiles are missing`() {
        val zoom = 2.0
        val step = MandelbrotTiles.zoomStep(zoom)
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, zoom, 800, 600, step, 256)
        val parents = HashSet<MandelbrotTiles.TileKey>()
        range.forEach { x, y ->
            parents += MandelbrotTiles.TileKey(
                zoomStep = step - 1,
                tileX = MandelbrotTiles.parentTileX(x),
                tileY = MandelbrotTiles.parentTileY(y),
                tilePixelSize = 256,
                viewMinEdge = 600,
                preview = false,
            )
        }
        assertTrue(
            MandelbrotTiles.visibleViewportCovered(
                zoom, -0.5, 0.0, 800, 600, 256,
            ) { it in parents },
        )
        assertFalse(
            MandelbrotTiles.visibleTilesComplete(
                zoom, -0.5, 0.0, 800, 600, 256,
            ) { it in parents },
        )
    }

    @Test
    fun `viewport is covered by same-step previews`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val previews = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = true).toSet()
        assertTrue(
            MandelbrotTiles.visibleViewportCovered(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in previews },
        )
        assertFalse(
            MandelbrotTiles.visibleTilesComplete(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in previews },
        )
    }

    @Test
    fun `viewport is covered when every visible tile has all four children`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val children = HashSet<MandelbrotTiles.TileKey>()
        range.forEach { x, y ->
            for (ly in 0..1) {
                for (lx in 0..1) {
                    children += MandelbrotTiles.TileKey(
                        zoomStep = 1,
                        tileX = MandelbrotTiles.childTileX(x, lx),
                        tileY = MandelbrotTiles.childTileY(y, ly),
                        tilePixelSize = 256,
                        viewMinEdge = 600,
                        preview = false,
                    )
                }
            }
        }
        assertTrue(
            MandelbrotTiles.visibleViewportCovered(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in children },
        )
        children.remove(children.first())
        assertFalse(
            MandelbrotTiles.visibleViewportCovered(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { it in children },
        )
    }

    @Test
    fun `viewport is not covered when the cache is empty`() {
        assertFalse(
            MandelbrotTiles.visibleViewportCovered(
                1.0, -0.5, 0.0, 800, 600, 256,
            ) { false },
        )
    }

    @Test
    fun `a two times zoom at the same center stays covered by parent tiles`() {
        val tilePx = 256
        val minEdge = 600
        val range1 = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, tilePx)
        val cached = MandelbrotTiles.keysForRange(range1, 0, tilePx, minEdge, preview = false).toSet()
        assertTrue(
            MandelbrotTiles.visibleViewportCovered(
                1.0, -0.5, 0.0, 800, 600, tilePx,
            ) { it in cached },
        )
        assertTrue(
            MandelbrotTiles.visibleViewportCovered(
                2.0, -0.5, 0.0, 800, 600, tilePx,
            ) { it in cached },
        )
        assertFalse(
            MandelbrotTiles.visibleTilesComplete(
                2.0, -0.5, 0.0, 800, 600, tilePx,
            ) { it in cached },
        )
    }

    @Test
    fun `protectable keys keep parent tiles that still paint the zoomed view`() {
        val zoom = 2.0
        val keys = MandelbrotTiles.protectableKeys(
            zoom = zoom,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
        )
        val step = MandelbrotTiles.zoomStep(zoom)
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, zoom, 800, 600, step, 256)
        range.forEach { x, y ->
            val exact = MandelbrotTiles.TileKey(step, x, y, 256, 600, preview = false)
            val parent = MandelbrotTiles.TileKey(
                step - 1,
                MandelbrotTiles.parentTileX(x),
                MandelbrotTiles.parentTileY(y),
                256,
                600,
                preview = false,
            )
            assertTrue(exact in keys)
            assertTrue(parent in keys)
        }
    }

    @Test
    fun `a full visible range is a dense bbox so it can render in one pass`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val keys = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false)
        val bbox = MandelbrotTiles.bboxOf(keys)!!
        assertEquals(keys.size.toLong(), bbox.tileCount)
        assertTrue(keys.size <= MandelbrotTiles.MAX_VISIBLE_BATCH)
    }

    @Test
    fun `expanding a range in the pan direction only grows that edge`() {
        val base = MandelbrotTiles.TileRange(0, 2, 0, 1)
        val right = MandelbrotTiles.expandRange(base, panSignX = 1, panSignY = 0, extra = 1)
        assertEquals(0L, right.x0)
        assertEquals(3L, right.x1)
        assertEquals(0L, right.y0)
        assertEquals(1L, right.y1)

        val ring = MandelbrotTiles.expandRange(base, extra = 1)
        assertEquals(-1L, ring.x0)
        assertEquals(3L, ring.x1)
        assertEquals(-1L, ring.y0)
        assertEquals(2L, ring.y1)
    }

    @Test
    fun `sort toward focus puts the tile under the pointer first`() {
        val keys = listOf(
            MandelbrotTiles.TileKey(0, 4, 4, 256, 600, false),
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 1, 0, 256, 600, false),
        )
        val world = MandelbrotTiles.tileWorldSize(0, 256, 800, 600)
        val sorted = MandelbrotTiles.sortTowardFocus(
            keys,
            focusX = 0.5 * world,
            focusY = 0.5 * world,
            zoomStep = 0,
            tilePixelSize = 256,
            viewWidth = 800,
            viewHeight = 600,
        )
        assertEquals(0L, sorted.first().tileX)
        assertEquals(0L, sorted.first().tileY)
    }

    @Test
    fun `preview pixel size is a quarter of a full tile`() {
        val full = MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = false)
        val preview = full.copy(preview = true)
        assertEquals(256, MandelbrotTiles.pixelSize(full))
        assertEquals(64, MandelbrotTiles.pixelSize(preview))
    }

    @Test
    fun `tile is covered by an exact full tile in the cache`() {
        val key = MandelbrotTiles.TileKey(0, -1, 0, 256, 600, preview = false)
        assertTrue(
            MandelbrotTiles.tileIsCovered(
                tileX = -1,
                tileY = 0,
                tileStep = 0,
                tilePixelSize = 256,
                viewMinEdge = 600,
            ) { it == key },
        )
    }

    @Test
    fun `zero sized views have no protectable keys and an uncovered viewport`() {
        assertTrue(
            MandelbrotTiles.protectableKeys(
                zoom = 1.0,
                offsetX = -0.5,
                offsetY = 0.0,
                viewWidth = 0,
                viewHeight = 600,
                tilePixelSize = 256,
            ).isEmpty(),
        )
        assertFalse(
            MandelbrotTiles.visibleViewportCovered(
                zoom = 1.0,
                offsetX = -0.5,
                offsetY = 0.0,
                viewWidth = 800,
                viewHeight = 0,
                tilePixelSize = 256,
            ) { true },
        )
    }

    @Test
    fun `screen rect intersection rejects tiles fully outside the view`() {
        val offscreen = MandelbrotTiles.ScreenRect(-300f, 0f, -50f, 200f)
        assertFalse(offscreen.intersectsView(viewWidth = 800, viewHeight = 600))

        val partial = MandelbrotTiles.ScreenRect(-10f, 100f, 10f, 200f)
        assertTrue(partial.intersectsView(viewWidth = 800, viewHeight = 600))
    }

    @Test
    fun `render plan skips preview work when full tile is already cached`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val cached = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false).toSet()
        val plan = renderPlanWithEmptyGesture(cached)
        assertTrue(plan.visibleFull.isEmpty())
        assertTrue(plan.visiblePreview.isEmpty())
    }

    @Test
    fun `render plan requests preview when neither preview nor full tile is cached`() {
        val plan = renderPlanWithEmptyGesture(emptySet())
        assertTrue(plan.visibleFull.isNotEmpty())
        assertTrue(plan.visiblePreview.isNotEmpty())
        assertTrue(plan.visiblePreview.all { it.preview })
        assertTrue(plan.visiblePreview.none { isCachedFull(it, emptySet()) })
    }

    @Test
    fun `render plan omits preview keys that are already cached`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val previewOnly = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = true).toSet()
        val plan = renderPlanWithEmptyGesture(previewOnly)
        assertTrue(plan.visibleFull.isNotEmpty())
        assertTrue(plan.visiblePreview.isEmpty())
    }

    @Test
    fun `tile is covered by ancestor preview when full resolution is still rendering`() {
        val parentPreview = MandelbrotTiles.TileKey(0, -1, 0, 256, 600, preview = true)
        assertTrue(
            MandelbrotTiles.tileIsCovered(
                tileX = MandelbrotTiles.childTileX(-1, 0),
                tileY = MandelbrotTiles.childTileY(0, 0),
                tileStep = 1,
                tilePixelSize = 256,
                viewMinEdge = 600,
            ) { it == parentPreview },
        )
    }

    @Test
    fun `child tile contributes more samples than its parent at the same zoom step`() {
        val parent = MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = false)
        val child = MandelbrotTiles.TileKey(1, 0, 0, 256, 600, preview = false)
        assertTrue(
            MandelbrotTiles.coverageSamples(child, targetStep = 1) >
                MandelbrotTiles.coverageSamples(parent, targetStep = 1),
        )
    }

    @Test
    fun `source rect returns the full ancestor when zoom steps match`() {
        val src = MandelbrotTiles.sourceRectInAncestor(
            tileX = 3,
            tileY = 4,
            tileStep = 2,
            ancestorX = 3,
            ancestorY = 4,
            ancestorStep = 2,
            ancestorPixelSize = 256,
        )
        assertEquals(0, src.left)
        assertEquals(0, src.top)
        assertEquals(256, src.right)
        assertEquals(256, src.bottom)
    }

    private fun renderPlanWithEmptyGesture(cached: Set<MandelbrotTiles.TileKey>): MandelbrotTiles.RenderPlan =
        MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 0,
            panSignY = 0,
            zoomSign = 0,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { key -> isCachedFull(key, cached) },
        )

    private fun isCachedFull(key: MandelbrotTiles.TileKey, cached: Set<MandelbrotTiles.TileKey>): Boolean {
        val full = if (key.preview) key.copy(preview = false) else key
        return full in cached || key in cached
    }
}
