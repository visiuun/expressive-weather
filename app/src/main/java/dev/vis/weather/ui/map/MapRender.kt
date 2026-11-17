package dev.vis.weather.ui.map

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.roundToInt

/** Integer tile level drawn for the camera's fractional zoom (tiles shown at 0.78×–1.56× scale). */
internal fun tileZoomFor(camera: MapCamera, source: TileSource): Int =
    floor(camera.zoom + source.zoomBias).toInt().coerceIn(source.minZoom, source.maxZoom)

/**
 * Calls [block] for every tile of [source] intersecting the viewport, with its wrapped x and its
 * on-screen pixel rect (edges snapped so neighbouring tiles share them exactly — no seams).
 */
internal inline fun forEachVisibleTile(
    camera: MapCamera,
    source: TileSource,
    block: (z: Int, x: Int, y: Int, l: Int, t: Int, r: Int, b: Int) -> Unit,
) {
    val w = camera.width
    val h = camera.height
    if (w == 0 || h == 0) return
    val z = tileZoomFor(camera, source)
    val n = 1 shl z
    val ws = camera.worldSize
    val span = ws / n
    val ox = camera.x * ws - w / 2.0
    val oy = camera.y * ws - h / 2.0
    val tx0 = floor(ox / span).toInt()
    val tx1 = floor((ox + w) / span).toInt()
    val ty0 = floor(oy / span).toInt().coerceAtLeast(0)
    val ty1 = floor((oy + h) / span).toInt().coerceAtMost(n - 1)
    for (ty in ty0..ty1) {
        val t = floor(ty * span - oy).toInt()
        val b = floor((ty + 1) * span - oy).toInt()
        for (tx in tx0..tx1) {
            val l = floor(tx * span - ox).toInt()
            val r = floor((tx + 1) * span - ox).toInt()
            block(z, tx.mod(n), ty, l, t, r, b)
        }
    }
}

/**
 * Draws a tile layer. Missing tiles are requested and covered by the nearest cached ancestor
 * (scaled up) or, when zooming out, by cached children.
 */
internal fun DrawScope.drawTiles(
    camera: MapCamera,
    source: TileSource,
    alpha: Float,
    priority: Int,
    colorFilter: ColorFilter? = null,
) {
    forEachVisibleTile(camera, source) { z, x, y, l, t, r, b ->
        val img = TileLoader.get(source, z, x, y)
        if (img != null) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(l, t), IntSize(r - l, b - t), alpha, colorFilter = colorFilter)
            return@forEachVisibleTile
        }
        TileLoader.request(source, z, x, y, priority)
        var pz = z - 1
        val stop = maxOf(source.minZoom, z - 8)
        while (pz >= stop) {
            val d = z - pz
            val px = x shr d
            val py = y shr d
            val p = TileLoader.get(source, pz, px, py)
            if (p != null) {
                val sub = 1 shl d
                val sw = p.width / sub
                val sh = p.height / sub
                if (sw > 0 && sh > 0) {
                    drawImage(
                        p,
                        IntOffset((x - (px shl d)) * sw, (y - (py shl d)) * sh), IntSize(sw, sh),
                        IntOffset(l, t), IntSize(r - l, b - t), alpha, colorFilter = colorFilter,
                    )
                    return@forEachVisibleTile
                }
            }
            pz--
        }
        if (z + 1 <= source.maxZoom) {
            val mx = (l + r) / 2
            val my = (t + b) / 2
            for (cy in 0..1) for (cx in 0..1) {
                val c = TileLoader.get(source, z + 1, x * 2 + cx, y * 2 + cy) ?: continue
                val dl = if (cx == 0) l else mx
                val dt = if (cy == 0) t else my
                val dr = if (cx == 0) mx else r
                val db = if (cy == 0) my else b
                drawImage(c, IntOffset.Zero, IntSize(c.width, c.height), IntOffset(dl, dt), IntSize(dr - dl, db - dt), alpha, colorFilter = colorFilter)
            }
        }
    }
}

/**
 * Night version of the light OSM style: CSS-like invert + hue-rotate(180°), dimmed so land goes
 * near-black (OLED friendly) while water stays blue and roads/labels stay legible.
 */
internal val DarkMapFilter: ColorFilter by lazy {
    // Hue rotation by 180° (luminance-preserving), applied to the inverted colour (255 - c).
    val h = floatArrayOf(
        -0.574f, 1.430f, 0.144f,
        0.426f, 0.430f, 0.144f,
        0.426f, 1.430f, -0.856f,
    )
    val k = 0.88f
    val off = 255f * k
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -h[0] * k, -h[1] * k, -h[2] * k, 0f, off,
                -h[3] * k, -h[4] * k, -h[5] * k, 0f, off,
                -h[6] * k, -h[7] * k, -h[8] * k, 0f, off,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** Requests (without drawing) the visible tiles of [source], e.g. upcoming radar frames. */
internal fun prefetchTiles(camera: MapCamera, source: TileSource, priority: Int) {
    forEachVisibleTile(camera, source) { z, x, y, _, _, _, _ ->
        TileLoader.request(source, z, x, y, priority)
    }
}

/** Number of tiles of [source] covering the viewport. */
internal fun visibleTileCount(camera: MapCamera, source: TileSource): Int {
    var n = 0
    forEachVisibleTile(camera, source) { _, _, _, _, _, _, _ -> n++ }
    return n
}

/** Number of visible tiles of [source] not yet in memory. */
internal fun missingTiles(camera: MapCamera, source: TileSource): Int {
    var missing = 0
    forEachVisibleTile(camera, source) { z, x, y, _, _, _, _ ->
        if (!TileLoader.isLoaded(source, z, x, y)) missing++
    }
    return missing
}

/**
 * Places a child so the point ([ax], [ay]) of its bounds (fractions) sits on the world point,
 * shifted by [dy] px. Reads the camera only during placement, so panning never recomposes.
 * The parent must fill the map (children start at its top-left corner).
 */
internal fun Modifier.anchorAt(
    camera: MapCamera,
    wx: Double,
    wy: Double,
    ax: Float = 0.5f,
    ay: Float = 0.5f,
    dy: Float = 0f,
): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val sx = camera.screenX(wx) - p.width * ax
        val sy = camera.screenY(wy) - p.height * ay + dy
        // Park far-off-screen children just outside the viewport to keep layer sizes sane.
        val cx = sx.coerceIn(-4f * p.width - 64f, camera.width + 64f)
        val cy = sy.coerceIn(-4f * p.height - 64f, camera.height + 64f)
        p.place(cx.roundToInt(), cy.roundToInt())
    }
}
