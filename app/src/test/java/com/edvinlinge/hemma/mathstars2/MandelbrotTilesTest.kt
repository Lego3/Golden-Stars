package com.edvinlinge.hemma.mathstars2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `view min edge uses the shorter dimension and never drops below one`() {
        assertEquals(600, MandelbrotTiles.viewMinEdge(800, 600))
        assertEquals(400, MandelbrotTiles.viewMinEdge(400, 800))
        assertEquals(1, MandelbrotTiles.viewMinEdge(0, 0))
    }

    @Test
    fun `tile install skips when full resolution already covers the slot`() {
        val full = MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = false)
        val preview = full.copy(preview = true)
        val cached = setOf(full)
        assertTrue(MandelbrotTiles.shouldSkipTileCacheInstall(preview) { it in cached })
        assertTrue(MandelbrotTiles.shouldSkipTileCacheInstall(full) { it in cached })
    }

    @Test
    fun `tile install skips duplicate full writes but still accepts preview when only preview exists`() {
        val full = MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = false)
        val preview = full.copy(preview = true)
        val previewOnly = setOf(preview)
        assertFalse(MandelbrotTiles.shouldSkipTileCacheInstall(preview) { it in previewOnly })
        assertFalse(MandelbrotTiles.shouldSkipTileCacheInstall(full) { it in previewOnly })
        assertTrue(MandelbrotTiles.shouldSkipTileCacheInstall(full) { it == full })
    }

    @Test
    fun `view geometry matches only when width and height are unchanged`() {
        assertTrue(MandelbrotTiles.viewGeometryMatches(800, 600, 800, 600))
        assertFalse(MandelbrotTiles.viewGeometryMatches(800, 600, 600, 800))
        assertFalse(MandelbrotTiles.viewGeometryMatches(800, 600, 900, 600))
    }

    @Test
    fun `rendered range pixel count scales with tile count and preview downscale`() {
        val single = MandelbrotTiles.TileRange(0, 0, 0, 0)
        val pair = MandelbrotTiles.TileRange(0, 1, 0, 0)
        assertEquals(256 * 256, MandelbrotTiles.renderedRangePixelCount(single, 256, preview = false))
        assertEquals(64 * 64, MandelbrotTiles.renderedRangePixelCount(single, 256, preview = true))
        assertEquals(256 * 256 * 2, MandelbrotTiles.renderedRangePixelCount(pair, 256, preview = false))
    }

    @Test
    fun `truncated render buffers are rejected before tile install`() {
        val range = MandelbrotTiles.TileRange(0, 1, 0, 0)
        val expected = MandelbrotTiles.renderedRangePixelCount(range, 256, preview = false)
        val truncated = ByteArray(expected - 1)
        assertFalse(
            MandelbrotTiles.hasEnoughPixelsForRange(range, 256, preview = false, pixels = truncated),
        )
        assertFalse(
            MandelbrotTiles.shouldInstallRenderedRange(
                renderViewWidth = 800,
                renderViewHeight = 600,
                viewWidth = 800,
                viewHeight = 600,
                range = range,
                tilePixelSize = 256,
                preview = false,
                pixels = truncated,
            ),
        )
    }

    @Test
    fun `geometry mismatch rejects install even with a valid pixel buffer`() {
        val range = MandelbrotTiles.TileRange(0, 0, 0, 0)
        val pixels = ByteArray(MandelbrotTiles.renderedRangePixelCount(range, 256, preview = false))
        assertFalse(
            MandelbrotTiles.shouldInstallRenderedRange(
                renderViewWidth = 800,
                renderViewHeight = 600,
                viewWidth = 900,
                viewHeight = 600,
                range = range,
                tilePixelSize = 256,
                preview = false,
                pixels = pixels,
            ),
        )
        assertFalse(
            MandelbrotTiles.shouldInstallRenderedRange(
                renderViewWidth = 800,
                renderViewHeight = 600,
                viewWidth = 0,
                viewHeight = 600,
                range = range,
                tilePixelSize = 256,
                preview = false,
                pixels = pixels,
            ),
        )
    }

    @Test
    fun `valid geometry and buffer allow rendered range install`() {
        val range = MandelbrotTiles.TileRange(0, 0, 0, 0)
        val pixels = ByteArray(MandelbrotTiles.renderedRangePixelCount(range, 256, preview = false))
        assertTrue(
            MandelbrotTiles.shouldInstallRenderedRange(
                renderViewWidth = 800,
                renderViewHeight = 600,
                viewWidth = 800,
                viewHeight = 600,
                range = range,
                tilePixelSize = 256,
                preview = false,
                pixels = pixels,
            ),
        )
    }

    @Test
    fun `accepts tile pixel payload only for exact square size`() {
        val full = MandelbrotTiles.TileKey(
            zoomStep = 0,
            tileX = 0,
            tileY = 0,
            tilePixelSize = 256,
            viewMinEdge = 600,
            preview = false,
        )
        val preview = full.copy(preview = true)
        assertTrue(MandelbrotTiles.acceptsTilePixelPayload(full, ByteArray(256 * 256)))
        assertFalse(MandelbrotTiles.acceptsTilePixelPayload(full, ByteArray(256 * 255)))
        assertTrue(MandelbrotTiles.acceptsTilePixelPayload(preview, ByteArray(64 * 64)))
        assertFalse(MandelbrotTiles.acceptsTilePixelPayload(preview, ByteArray(256 * 256)))
    }

    @Test
    fun `tile world size changes when view min edge changes`() {
        val narrow = MandelbrotTiles.tileWorldSize(0, 256, 500, 800)
        val wider = MandelbrotTiles.tileWorldSize(0, 256, 700, 800)
        assertTrue(narrow > wider)
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
    fun `compose draw list is empty when nothing is available`() {
        val draws = MandelbrotTiles.composeDrawList(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            available = emptySet(),
        )
        assertTrue(draws.isEmpty())
    }

    @Test
    fun `visible tile progress reaches full count as on-screen tiles finish sharpening`() {
        val visible = listOf(
            MandelbrotTiles.TileKey(1, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 1, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 2, 0, 256, 600, false),
        )
        val cached = mutableSetOf<MandelbrotTiles.TileKey>()
        val initial = MandelbrotTiles.mergeVisibleTileQueue(emptySet(), visible) { it in cached }
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 0, queued = 3),
            MandelbrotTiles.visibleTileProgress(initial) { it in cached },
        )

        cached += visible[0]
        val partial = MandelbrotTiles.mergeVisibleTileQueue(initial, visible) { it in cached }
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 1, queued = 3),
            MandelbrotTiles.visibleTileProgress(partial) { it in cached },
        )

        cached += visible
        val complete = MandelbrotTiles.mergeVisibleTileQueue(partial, visible) { it in cached }
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 3, queued = 3),
            MandelbrotTiles.visibleTileProgress(complete) { it in cached },
        )
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
    fun `visible full keys match the current zoom step range`() {
        val keys = MandelbrotTiles.visibleFullKeys(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
        )
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        assertEquals(
            MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false),
            keys,
        )
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.none { it.preview })
        assertTrue(keys.all { it.zoomStep == 0 })
    }

    @Test
    fun `visible tile queue keeps finished tiles and drops ones that left the view`() {
        val visible = listOf(
            MandelbrotTiles.TileKey(1, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 1, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 2, 0, 256, 600, false),
        )
        val cached = mutableSetOf<MandelbrotTiles.TileKey>()
        val initial = MandelbrotTiles.mergeVisibleTileQueue(emptySet(), visible) { it in cached }
        assertEquals(visible.toSet(), initial)

        cached += visible[0]
        cached += visible[1]
        val afterSomeDone = MandelbrotTiles.mergeVisibleTileQueue(initial, visible) { it in cached }
        assertEquals(visible.toSet(), afterSomeDone)
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 2, queued = 3),
            MandelbrotTiles.visibleTileProgress(afterSomeDone) { it in cached },
        )

        val afterPan = listOf(visible[1], visible[2], MandelbrotTiles.TileKey(1, 3, 0, 256, 600, false))
        val mergedAfterPan = MandelbrotTiles.mergeVisibleTileQueue(afterSomeDone, afterPan) { it in cached }
        assertEquals(
            setOf(visible[1], visible[2], afterPan[2]),
            mergedAfterPan,
        )
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 1, queued = 3),
            MandelbrotTiles.visibleTileProgress(mergedAfterPan) { it in cached },
        )
    }

    @Test
    fun `visible tile queue resets when the zoom step changes`() {
        val oldStep = setOf(MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false))
        val newStep = listOf(MandelbrotTiles.TileKey(1, 0, 0, 256, 600, false))
        val merged = MandelbrotTiles.mergeVisibleTileQueue(oldStep, newStep) { false }
        assertEquals(newStep.toSet(), merged)
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 0, queued = 1),
            MandelbrotTiles.visibleTileProgress(merged) { false },
        )
    }

    @Test
    fun `visible tile queue drops stale keys after view geometry changes`() {
        val oldGeometry = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 1, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 2, 0, 256, 600, false),
        )
        val cached = mutableSetOf<MandelbrotTiles.TileKey>()
        var tracked = MandelbrotTiles.mergeVisibleTileQueue(emptySet(), oldGeometry) { it in cached }
        cached += oldGeometry[0]
        cached += oldGeometry[1]
        tracked = MandelbrotTiles.mergeVisibleTileQueue(tracked, oldGeometry) { it in cached }
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 2, queued = 3),
            MandelbrotTiles.visibleTileProgress(tracked) { it in cached },
        )

        val newGeometry = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 512, 1080, false),
            MandelbrotTiles.TileKey(0, 1, 0, 512, 1080, false),
            MandelbrotTiles.TileKey(0, 2, 0, 512, 1080, false),
        )
        val merged = MandelbrotTiles.mergeVisibleTileQueue(tracked, newGeometry) { it in cached }
        assertEquals(newGeometry.toSet(), merged)
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 0, queued = 3),
            MandelbrotTiles.visibleTileProgress(merged) { it in cached },
        )
    }

    @Test
    fun `visible tile queue ignores tracked keys that only match by tile coordinates`() {
        val tracked = setOf(MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false))
        val resized = listOf(MandelbrotTiles.TileKey(0, 0, 0, 512, 1080, false))
        val merged = MandelbrotTiles.mergeVisibleTileQueue(tracked, resized) { false }
        assertEquals(resized.toSet(), merged)
    }

    @Test
    fun `visible tile progress is zero when nothing is queued`() {
        assertEquals(
            MandelbrotTiles.VisibleTileProgress(finished = 0, queued = 0),
            MandelbrotTiles.visibleTileProgress(emptySet()) { true },
        )
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
    fun `diagonal pan expansion grows both axes without a uniform ring`() {
        val base = MandelbrotTiles.TileRange(0, 2, 0, 1)
        val diagonal = MandelbrotTiles.expandRange(base, panSignX = 1, panSignY = -1, extra = 1)
        assertEquals(0L, diagonal.x0)
        assertEquals(3L, diagonal.x1)
        assertEquals(-1L, diagonal.y0)
        assertEquals(1L, diagonal.y1)
    }

    @Test
    fun `render plan prefetches along both pan axes during diagonal gestures`() {
        val visibleRange = MandelbrotTiles.visibleTileRange(
            -0.5, 0.0, 1.0, 800, 600, 0, 256,
        )
        val plan = MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 1,
            panSignY = -1,
            zoomSign = 0,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { false },
        )
        val visible = plan.visibleFull.toSet()
        assertTrue(plan.prefetch.none { it in visible })
        assertTrue(plan.prefetch.any { it.tileX > visibleRange.x1 })
        assertTrue(plan.prefetch.any { it.tileY < visibleRange.y0 })
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
        assertTrue(
            MandelbrotTiles.visibleFullKeys(
                zoom = 1.0,
                offsetX = -0.5,
                offsetY = 0.0,
                viewWidth = 0,
                viewHeight = 600,
                tilePixelSize = 256,
            ).isEmpty(),
        )
        assertFalse(
            MandelbrotTiles.visibleTilesComplete(
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
    fun `render plan prefetch never queues on-screen tiles even when they are already cached`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val allVisible = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false)
        val cached = allVisible.take(1).toSet()
        val plan = renderPlanWithEmptyGesture(cached)
        val visibleSet = allVisible.toSet()
        assertTrue(plan.prefetch.none { it in visibleSet })
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

    @Test
    fun `compose draw list uses preview child when full child is missing`() {
        val childPreview = MandelbrotTiles.TileKey(
            zoomStep = 1,
            tileX = MandelbrotTiles.childTileX(-1, 0),
            tileY = MandelbrotTiles.childTileY(0, 0),
            tilePixelSize = 256,
            viewMinEdge = 600,
            preview = true,
        )
        val draws = MandelbrotTiles.composeDrawList(
            zoom = 1.5,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            available = setOf(childPreview),
        )
        assertTrue(draws.any { it.key == childPreview })
    }

    @Test
    fun `compose draw list paints full child over preview parent`() {
        val parentPreview = MandelbrotTiles.TileKey(0, -1, 0, 256, 600, preview = true)
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
            available = setOf(parentPreview, child),
        )
        val lastChild = draws.indexOfLast { it.key == child }
        val lastParent = draws.indexOfLast { it.key == parentPreview }
        assertTrue(lastChild >= 0)
        assertTrue(lastParent >= 0)
        assertTrue(lastChild > lastParent)
    }

    @Test
    fun `tile is not covered when only three of four children are cached`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val tx = range.x0
        val ty = range.y0
        val children = (0..1).flatMap { ly ->
            (0..1).map { lx ->
                MandelbrotTiles.TileKey(
                    zoomStep = 1,
                    tileX = MandelbrotTiles.childTileX(tx, lx),
                    tileY = MandelbrotTiles.childTileY(ty, ly),
                    tilePixelSize = 256,
                    viewMinEdge = 600,
                    preview = false,
                )
            }
        }
        val threeChildren = children.drop(1).toSet()
        assertFalse(
            MandelbrotTiles.tileIsCovered(
                tileX = tx,
                tileY = ty,
                tileStep = 0,
                tilePixelSize = 256,
                viewMinEdge = 600,
            ) { it in threeChildren },
        )
    }

    @Test
    fun `dense tile batch covers most of its bounding box`() {
        val range = MandelbrotTiles.visibleTileRange(-0.5, 0.0, 1.0, 800, 600, 0, 256)
        val denseKeys = MandelbrotTiles.keysForRange(range, 0, 256, 600, preview = false)
        assertTrue(MandelbrotTiles.isDenseTileBatch(denseKeys))

        val sparseKeys = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 2, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 0, 2, 256, 600, false),
            MandelbrotTiles.TileKey(0, 2, 2, 256, 600, false),
        )
        assertFalse(MandelbrotTiles.isDenseTileBatch(sparseKeys))

        val boundaryKeys = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 1, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 0, 1, 256, 600, false),
            MandelbrotTiles.TileKey(0, 1, 1, 256, 600, false),
        )
        assertTrue(MandelbrotTiles.isDenseTileBatch(boundaryKeys))
        assertTrue(MandelbrotTiles.isDenseTileBatch(emptyList()).not())
    }

    @Test
    fun `copy subgrid extracts each tile from a rendered bbox buffer`() {
        val size = 2
        val sourceWidth = 4
        val source = ByteArray(16) { it.toByte() }
        val topLeft = ByteArray(4)
        val bottomRight = ByteArray(4)
        MandelbrotTiles.copySubgrid(source, sourceWidth, srcX = 0, srcY = 0, size = size, dest = topLeft)
        MandelbrotTiles.copySubgrid(source, sourceWidth, srcX = 2, srcY = 2, size = size, dest = bottomRight)
        assertArrayEquals(byteArrayOf(0, 1, 4, 5), topLeft)
        assertArrayEquals(byteArrayOf(10, 11, 14, 15), bottomRight)
    }

    @Test
    fun `select next work prioritizes visible full tiles when idle`() {
        val visible = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 1, 0, 256, 600, false),
        )
        val plan = MandelbrotTiles.RenderPlan(
            visibleFull = visible,
            visiblePreview = listOf(visible[0].copy(preview = true)),
            prefetch = listOf(MandelbrotTiles.TileKey(1, 0, 0, 256, 600, false)),
        )
        val work = MandelbrotTiles.selectNextWork(
            plan = plan,
            isInteracting = false,
            viewportCovered = false,
            memoryBytes = 0L,
            maxMemoryBytes = 64L * 1024L * 1024L,
        )
        assertEquals(
            MandelbrotTiles.WorkSelection(keys = visible, preview = false, prefetch = false),
            work,
        )
    }

    @Test
    fun `select next work serves preview tiles during gestures when the viewport is uncovered`() {
        val preview = listOf(
            MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = true),
        )
        val plan = MandelbrotTiles.RenderPlan(
            visibleFull = emptyList(),
            visiblePreview = preview,
            prefetch = emptyList(),
        )
        val work = MandelbrotTiles.selectNextWork(
            plan = plan,
            isInteracting = true,
            viewportCovered = false,
            memoryBytes = 0L,
            maxMemoryBytes = 64L * 1024L * 1024L,
        )
        assertEquals(
            MandelbrotTiles.WorkSelection(keys = preview, preview = true, prefetch = false),
            work,
        )
    }

    @Test
    fun `select next work stays idle during gestures when the viewport is already covered`() {
        val plan = MandelbrotTiles.RenderPlan(
            visibleFull = emptyList(),
            visiblePreview = listOf(MandelbrotTiles.TileKey(0, 0, 0, 256, 600, preview = true)),
            prefetch = emptyList(),
        )
        assertNull(
            MandelbrotTiles.selectNextWork(
                plan = plan,
                isInteracting = true,
                viewportCovered = true,
                memoryBytes = 0L,
                maxMemoryBytes = 64L * 1024L * 1024L,
            ),
        )
    }

    @Test
    fun `select next work prefetches homogeneous neighbours only when memory allows`() {
        val prefetch = listOf(
            MandelbrotTiles.TileKey(1, 0, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 1, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 2, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 3, 0, 256, 600, false),
            MandelbrotTiles.TileKey(1, 4, 0, 256, 600, false),
            MandelbrotTiles.TileKey(0, 5, 0, 256, 600, false),
        )
        val plan = MandelbrotTiles.RenderPlan(
            visibleFull = emptyList(),
            visiblePreview = emptyList(),
            prefetch = prefetch,
        )
        val work = MandelbrotTiles.selectNextWork(
            plan = plan,
            isInteracting = false,
            viewportCovered = true,
            memoryBytes = 0L,
            maxMemoryBytes = 64L * 1024L * 1024L,
        )
        assertEquals(
            MandelbrotTiles.WorkSelection(
                keys = prefetch.take(4),
                preview = false,
                prefetch = true,
            ),
            work,
        )
        assertNull(
            MandelbrotTiles.selectNextWork(
                plan = plan,
                isInteracting = false,
                viewportCovered = true,
                memoryBytes = 64L * 1024L * 1024L,
                maxMemoryBytes = 64L * 1024L * 1024L,
            ),
        )
    }

    @Test
    fun `render plan prefetches zoom in before zoom out when pinching in`() {
        val plan = MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 0,
            panSignY = 0,
            zoomSign = 1,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { false },
        )
        val firstZoomIn = plan.prefetch.indexOfFirst { it.zoomStep == 1 }
        val firstZoomOut = plan.prefetch.indexOfFirst { it.zoomStep == -1 }
        assertTrue(firstZoomIn >= 0)
        assertTrue(firstZoomOut >= 0)
        assertTrue(firstZoomIn < firstZoomOut)
    }

    @Test
    fun `render plan prefetches zoom out before zoom in when pinching out`() {
        val plan = MandelbrotTiles.renderPlan(
            zoom = 1.0,
            offsetX = -0.5,
            offsetY = 0.0,
            viewWidth = 800,
            viewHeight = 600,
            tilePixelSize = 256,
            panSignX = 0,
            panSignY = 0,
            zoomSign = -1,
            focusX = -0.5,
            focusY = 0.0,
            minZoom = 0.5,
            maxZoom = 1.0e13,
            isCached = { false },
        )
        val firstZoomIn = plan.prefetch.indexOfFirst { it.zoomStep == 1 }
        val firstZoomOut = plan.prefetch.indexOfFirst { it.zoomStep == -1 }
        assertTrue(firstZoomIn >= 0)
        assertTrue(firstZoomOut >= 0)
        assertTrue(firstZoomOut < firstZoomIn)
    }

    @Test
    fun `discrete zoom clamps extreme steps to avoid pow2 overflow`() {
        val maxStep = 62
        assertEquals((1L shl maxStep).toDouble(), MandelbrotTiles.discreteZoom(100), 0.0)
        assertEquals(1.0 / (1L shl maxStep), MandelbrotTiles.discreteZoom(-100), 0.0)
        assertTrue(MandelbrotTiles.discreteZoom(-100) > 0.0)
        assertTrue(MandelbrotTiles.discreteZoom(100).isFinite())
    }

    @Test
    fun `active work action cancels prefetch during gestures but not visible sharpening`() {
        assertEquals(
            MandelbrotTiles.ActiveWorkAction.Launch,
            MandelbrotTiles.activeWorkAction(
                workActive = false,
                cancelPrefetch = true,
                workIsPrefetch = true,
            ),
        )
        assertEquals(
            MandelbrotTiles.ActiveWorkAction.CancelPrefetchAndLaunch,
            MandelbrotTiles.activeWorkAction(
                workActive = true,
                cancelPrefetch = true,
                workIsPrefetch = true,
            ),
        )
        assertEquals(
            MandelbrotTiles.ActiveWorkAction.Skip,
            MandelbrotTiles.activeWorkAction(
                workActive = true,
                cancelPrefetch = true,
                workIsPrefetch = false,
            ),
        )
        assertEquals(
            MandelbrotTiles.ActiveWorkAction.Skip,
            MandelbrotTiles.activeWorkAction(
                workActive = true,
                cancelPrefetch = false,
                workIsPrefetch = true,
            ),
        )
    }

    @Test
    fun `post work action reschedules when new work lands before the job exits`() {
        assertEquals(
            MandelbrotTiles.PostWorkAction.None,
            MandelbrotTiles.postWorkAction(epochMatches = false, hasPendingWork = true),
        )
        assertEquals(
            MandelbrotTiles.PostWorkAction.Reschedule,
            MandelbrotTiles.postWorkAction(epochMatches = true, hasPendingWork = true),
        )
        assertEquals(
            MandelbrotTiles.PostWorkAction.ForceIdle,
            MandelbrotTiles.postWorkAction(epochMatches = true, hasPendingWork = false),
        )
    }

    @Test
    fun `spinner hud resets progress to zero when idle`() {
        val progress = MandelbrotTiles.VisibleTileProgress(finished = 3, queued = 10)
        val idle = MandelbrotTiles.spinnerHudState(
            forceIdle = false,
            workActive = false,
            workIsPrefetch = false,
            visibleTilesComplete = false,
            progress = progress,
        )
        assertFalse(idle.visible)
        assertEquals(0, idle.finished)
        assertEquals(0, idle.queued)
        assertTrue(idle.clearTrackedQueue)
    }

    @Test
    fun `spinner hud passes through progress while visible sharpening runs`() {
        val progress = MandelbrotTiles.VisibleTileProgress(finished = 2, queued = 5)
        val busy = MandelbrotTiles.spinnerHudState(
            forceIdle = false,
            workActive = true,
            workIsPrefetch = false,
            visibleTilesComplete = false,
            progress = progress,
        )
        assertTrue(busy.visible)
        assertEquals(2, busy.finished)
        assertEquals(5, busy.queued)
        assertFalse(busy.clearTrackedQueue)
    }

    @Test
    fun `spinner hud hides prefetch and forces idle when requested`() {
        val progress = MandelbrotTiles.VisibleTileProgress(finished = 1, queued = 4)
        val prefetch = MandelbrotTiles.spinnerHudState(
            forceIdle = false,
            workActive = true,
            workIsPrefetch = true,
            visibleTilesComplete = false,
            progress = progress,
        )
        assertFalse(prefetch.visible)
        assertEquals(0, prefetch.finished)
        assertTrue(prefetch.clearTrackedQueue)

        val forced = MandelbrotTiles.spinnerHudState(
            forceIdle = true,
            workActive = true,
            workIsPrefetch = false,
            visibleTilesComplete = false,
            progress = progress,
        )
        assertFalse(forced.visible)
        assertEquals(0, forced.finished)
        assertTrue(forced.clearTrackedQueue)
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
