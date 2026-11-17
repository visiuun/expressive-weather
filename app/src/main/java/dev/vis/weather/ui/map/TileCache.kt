package dev.vis.weather.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.collection.MutableLongObjectMap
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.vis.weather.BuildConfig
import dev.vis.weather.data.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal val MAP_USER_AGENT = "ExpressiveWeather/${BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"

/**
 * A raster XYZ tile set. [dir] is the folder name inside the disk cache; [template] uses
 * {s} (subdomain a-d), {z}, {x}, {y}.
 */
class TileSource(
    val id: Int,
    val dir: String,
    val minZoom: Int,
    val maxZoom: Int,
    val opaque: Boolean,
    val maxAgeMs: Long,
    private val template: String,
    /** Added to the camera zoom before flooring to pick a tile level (higher = sharper, more tiles). */
    val zoomBias: Double = 0.35,
) {
    fun url(z: Int, x: Int, y: Int): String = template
        .replace("{s}", SUBDOMAINS[(x + y).mod(SUBDOMAINS.size)])
        .replace("{z}", z.toString())
        .replace("{x}", x.toString())
        .replace("{y}", y.toString())

    fun key(z: Int, x: Int, y: Int): Long =
        (id.toLong() shl 39) or (z.toLong() shl 34) or (x.toLong() shl 17) or y.toLong()

    companion object {
        private val SUBDOMAINS = arrayOf("a", "b", "c", "d")
        private const val DAY = 24L * 3600 * 1000
        private val nextId = AtomicInteger(16)
        private val radarSources = HashMap<String, TileSource>()

        /**
         * Standard OpenStreetMap tiles: the keyless fallback while CARTO only serves "API key required"
         * watermarks. Follows the OSM tile usage policy: identifying User-Agent, local caching, no bulk
         * prefetching, visible attribution. 256 px only, so a slightly higher zoom bias keeps it crisp.
         */
        val Osm = TileSource(
            3, "osm", 0, 19, true, 7 * DAY,
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            zoomBias = 0.5,
        )

        val CartoLight = TileSource(
            1, "carto-light", 0, 20, true, 30 * DAY,
            "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}@2x.png",
        )
        val CartoDark = TileSource(
            2, "carto-dark", 0, 20, true, 30 * DAY,
            "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        )

        /**
         * One RainViewer radar frame. RainViewer only renders up to zoom 7 (higher levels return a
         * "zoom not supported" placeholder), so [maxZoom] is 7 and the map overscales above that.
         * Colour scheme 2 (Universal Blue), smoothing on, snow colours on.
         */
        @Synchronized
        fun radar(host: String, path: String): TileSource = radarSources.getOrPut(path) {
            TileSource(
                nextId.getAndIncrement(),
                "radar/" + path.trimEnd('/').substringAfterLast('/'),
                0, 7, false, 6 * 3600 * 1000L,
                "$host$path/256/{z}/{x}/{y}/2/1_1.png",
            )
        }
    }
}

/** Byte-bounded LRU of decoded tiles keyed by [TileSource.key]; no boxing on lookups. */
internal class TileMemoryCache(private val maxBytes: Long) {
    private class Entry(val image: ImageBitmap, val bytes: Int, var stamp: Long)

    private val map = MutableLongObjectMap<Entry>()
    private var size = 0L
    private var clock = 0L

    @Synchronized
    fun get(key: Long): ImageBitmap? {
        val e = map[key] ?: return null
        e.stamp = ++clock
        return e.image
    }

    @Synchronized
    fun contains(key: Long): Boolean = map.containsKey(key)

    @Synchronized
    fun put(key: Long, image: ImageBitmap, bytes: Int) {
        map.put(key, Entry(image, bytes, ++clock))?.let { size -= it.bytes }
        size += bytes
        trim(maxBytes)
    }

    @Synchronized
    fun trimTo(fraction: Float) = trim((maxBytes * fraction).toLong())

    /**
     * Evicts least-recently-used tiles. Bitmaps are simply dropped (not recycle()d): a frame that is
     * still being rendered may hold a reference, and the native allocation is freed by the GC.
     */
    private fun trim(limit: Long) {
        while (size > limit && map.isNotEmpty()) {
            var oldestKey = 0L
            var oldest = Long.MAX_VALUE
            map.forEach { k, e ->
                if (e.stamp < oldest) {
                    oldest = e.stamp
                    oldestKey = k
                }
            }
            map.remove(oldestKey)?.let { size -= it.bytes }
        }
    }
}

/**
 * Loads tiles from memory → disk (cacheDir/map-tiles) → network. Network requests run on at most
 * [MAX_CONCURRENT] workers, highest priority first. Callers mark the tiles they need during a draw
 * pass ([request]) and finish with [endPass], which cancels whatever wasn't asked for again.
 */
object TileLoader {
    private const val MAX_CONCURRENT = 6
    private const val DISK_CAP = 50L * 1024 * 1024
    private const val FAIL_BACKOFF_MS = 30_000L

    /** Bumped (on the main thread, coalesced) whenever a tile arrives; read it in draw to repaint. */
    val version = mutableIntStateOf(0)

    private val maxMem = Runtime.getRuntime().maxMemory()
    private val baseCache = TileMemoryCache(minOf(maxMem / 10, 40L * 1024 * 1024))
    private val overlayBytes = minOf(maxMem / 7, 48L * 1024 * 1024)
    private val overlayCache = TileMemoryCache(overlayBytes)

    /** How many 256 px ARGB overlay tiles fit comfortably in memory (used to bound radar preloading). */
    val overlayTileCapacity: Int get() = (overlayBytes * 8 / 10 / (256 * 256 * 4)).toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val main = Handler(Looper.getMainLooper())
    private val bumpPosted = AtomicBoolean(false)

    private class Request(val key: Long, val source: TileSource, val z: Int, val x: Int, val y: Int) {
        @Volatile var priority = 0
        @Volatile var pass = 0
        @Volatile var cancelled = false
        @Volatile var conn: HttpURLConnection? = null
        var queued = false
        var seq = 0L
    }

    private val lock = Any()
    private val requests = MutableLongObjectMap<Request>()
    private val failures = MutableLongObjectMap<Long>()
    private var pass = 0
    private var seq = 0L
    private var writes = 0

    private var dir: File? = null
    private var prefs: android.content.SharedPreferences? = null

    /**
     * True once CARTO answered with its "API key required" watermark (ETag "wm-…"). Remembered for a week,
     * then CARTO is tried again. While true the map falls back to [TileSource.Osm].
     */
    val cartoBlocked = mutableStateOf(false)

    private const val CARTO_RECHECK_MS = 7L * 24 * 3600 * 1000

    /** The basemap to draw: CARTO Positron / Dark Matter, or OpenStreetMap when CARTO is unavailable. */
    fun basemap(dark: Boolean): TileSource = when {
        cartoBlocked.value -> TileSource.Osm
        dark -> TileSource.CartoDark
        else -> TileSource.CartoLight
    }

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.cacheDir, "map-tiles")
        dir = d
        val p = context.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)
        prefs = p
        cartoBlocked.value = System.currentTimeMillis() - p.getLong("carto_blocked_at", 0L) < CARTO_RECHECK_MS
        repeat(MAX_CONCURRENT) { scope.launch { worker() } }
        scope.launch { trimDisk(d) }
    }

    private fun cache(source: TileSource) = if (source.opaque) baseCache else overlayCache

    fun get(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? = cache(source).get(source.key(z, x, y))

    fun isLoaded(source: TileSource, z: Int, x: Int, y: Int): Boolean = cache(source).contains(source.key(z, x, y))

    /** Asks for a tile (no-op if cached or already on its way). Lower [priority] loads first. */
    fun request(source: TileSource, z: Int, x: Int, y: Int, priority: Int) {
        val key = source.key(z, x, y)
        if (cache(source).contains(key)) return
        val r = synchronized(lock) {
            val existing = requests[key]
            if (existing != null) {
                if (existing.pass != Int.MAX_VALUE) existing.pass = pass
                if (priority < existing.priority) existing.priority = priority
                return
            }
            val failedAt = failures[key]
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAIL_BACKOFF_MS) return
            Request(key, source, z, x, y).also {
                it.priority = priority
                it.pass = pass
                requests[key] = it
            }
        }
        scope.launch(diskDispatcher) { loadFromDisk(r) }
    }

    /** Ends a draw pass: requests not renewed since the previous pass are cancelled. */
    fun endPass() {
        synchronized(lock) {
            // One pass of grace so a request made outside a draw isn't dropped immediately.
            val stale = pass - 1
            requests.removeIf { _, r ->
                val drop = r.pass < stale
                if (drop) {
                    r.cancelled = true
                    r.conn?.disconnect()
                }
                drop
            }
            pass++
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            requests.forEachValue { it.cancelled = true; it.conn?.disconnect() }
            requests.clear()
        }
    }

    /** Frees memory when the map isn't on screen. */
    fun trimMemory() {
        overlayCache.trimTo(0.25f)
        baseCache.trimTo(0.5f)
    }

    private fun fileFor(r: Request): File? = dir?.let { File(it, "${r.source.dir}/${r.z}/${r.x}_${r.y}.png") }

    private fun loadFromDisk(r: Request) {
        if (r.cancelled) return
        val f = fileFor(r)
        if (f != null && f.isFile) {
            val fresh = System.currentTimeMillis() - f.lastModified() < r.source.maxAgeMs
            val ok = try {
                decodeAndStore(r, f.readBytes())
            } catch (_: Exception) {
                false
            }
            if (ok && fresh) {
                finish(r)
                return
            }
            if (ok) {
                // The stale tile is on screen now, so nobody will ask for it again: refresh it in the
                // background at low priority without tying it to draw passes.
                r.pass = Int.MAX_VALUE
                r.priority = 9
            }
        }
        synchronized(lock) {
            if (r.cancelled) return
            r.queued = true
            r.seq = ++seq
        }
        wake.trySend(Unit)
    }

    private fun next(): Request? = synchronized(lock) {
        var best: Request? = null
        requests.forEachValue { r ->
            if (r.queued && !r.cancelled) {
                val b = best
                if (b == null || r.priority < b.priority || (r.priority == b.priority && r.seq < b.seq)) best = r
            }
        }
        best?.queued = false
        if (best != null) wake.trySend(Unit)
        best
    }

    private suspend fun worker() {
        while (true) {
            val r = next()
            if (r == null) {
                wake.receive()
                continue
            }
            fetch(r)
        }
    }

    private fun fetch(r: Request) {
        if (r.cancelled) return
        var conn: HttpURLConnection? = null
        var ok = false
        try {
            conn = URL(r.source.url(r.z, r.x, r.y)).openConnection() as HttpURLConnection
            r.conn = conn
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", MAP_USER_AGENT)
            if (r.cancelled) return
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if (r.source.id == 1 || r.source.id == 2) {
                val etag = conn.getHeaderField("ETag")?.trim('"', ' ', 'W', '/').orEmpty()
                if (etag.startsWith("wm-")) {
                    prefs?.edit()?.putLong("carto_blocked_at", System.currentTimeMillis())?.apply()
                    main.post { cartoBlocked.value = true }
                    // Watermarks may already sit in the disk cache from before; drop CARTO's folders.
                    dir?.let { d -> scope.launch { File(d, TileSource.CartoLight.dir).deleteRecursively(); File(d, TileSource.CartoDark.dir).deleteRecursively() } }
                    throw IOException("CARTO requires an API key")
                }
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            if (!decodeAndStore(r, bytes)) throw IOException("Undecodable tile")
            fileFor(r)?.let { writeFile(it, bytes) }
            finish(r)
            ok = true
        } catch (e: Exception) {
            if (!r.cancelled) {
                synchronized(lock) {
                    failures[r.key] = System.currentTimeMillis()
                    requests.remove(r.key)
                }
            }
        } finally {
            r.conn = null
            // On success the drained stream hands the socket back to the keep-alive pool; only tear
            // the connection down when something went wrong.
            if (!ok) conn?.disconnect()
        }
    }

    private fun decodeAndStore(r: Request, bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = if (r.source.opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
        bmp.prepareToDraw()
        cache(r.source).put(r.key, bmp.asImageBitmap(), bmp.allocationByteCount)
        bump()
        return true
    }

    private fun finish(r: Request) {
        synchronized(lock) {
            if (requests[r.key] === r) requests.remove(r.key)
            failures.remove(r.key)
        }
    }

    private fun bump() {
        if (bumpPosted.compareAndSet(false, true)) {
            main.post {
                bumpPosted.set(false)
                version.intValue++
            }
        }
    }

    private fun writeFile(f: File, bytes: ByteArray) {
        try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
            val d = dir
            if (d != null && synchronized(lock) { ++writes % 300 == 0 }) scope.launch { trimDisk(d) }
        } catch (_: IOException) {
        }
    }

    /** Drops expired tiles, then the oldest ones until the cache is under [DISK_CAP]. */
    private fun trimDisk(root: File) {
        if (!root.isDirectory) return
        val now = System.currentTimeMillis()
        val files = ArrayList<File>()
        root.walkBottomUp().forEach { f ->
            if (f.isFile) {
                val radar = f.path.contains("${File.separator}radar${File.separator}")
                val maxAge = if (radar) 6 * 3600 * 1000L else 30L * 24 * 3600 * 1000
                if (now - f.lastModified() > maxAge || f.name.endsWith(".tmp")) f.delete() else files += f
            } else if (f != root && f.list()?.isEmpty() == true) {
                f.delete()
            }
        }
        var total = files.sumOf { it.length() }
        if (total <= DISK_CAP) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= DISK_CAP * 9 / 10) break
            total -= f.length()
            f.delete()
        }
    }
}
