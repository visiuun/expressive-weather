package dev.vis.weather.ui.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import dev.vis.weather.data.Sky
import dev.vis.weather.ui.theme.WeatherMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle simulation behind [WeatherSky]. All state lives in preallocated primitive arrays;
 * [step] and [draw] allocate nothing per frame. Units: px and seconds.
 */
internal class SkyEngine {
    companion object {
        /** Radius of the soft radial brushes; shapes are scaled from it. */
        const val UNIT = 100f
        private const val MAX_DROPS = 360
        private const val MAX_CLOUDS = 9
        private const val MAX_STARS = 120
        private const val MAX_FOG = 6
        private const val BOLT = 14
        private const val BRANCH = 6

        // Puff layout of one cloud, in cloud-width units: x, y, radius.
        private val PUFFS = floatArrayOf(
            -0.55f, 0.10f, 0.42f,
            -0.18f, -0.14f, 0.58f,
            0.26f, -0.06f, 0.50f,
            0.62f, 0.12f, 0.36f,
            0.04f, 0.20f, 0.52f,
        )

        const val RAIN: Byte = 0
        const val DRIZZLE: Byte = 1
        const val SNOW: Byte = 2
        const val HAIL: Byte = 3
        const val PELLET: Byte = 4
    }

    var density = 1f
    var width = 0f
    var height = 0f

    private val rnd = Random.Default
    private var t = 0f

    // ---- configuration (rebuilt when the mood changes)
    private var cfgCode = Int.MIN_VALUE
    private var cfgDay = true
    private var cfgWind = -1.0
    private var cfgPrecip = -1.0
    private var seededW = 0f
    private var seededH = 0f
    private var needsSeed = true

    private var sky = Sky.CLEAR
    private var isDay = true
    private var intensity = 0.5f
    private var windTilt = 0f      // vx / vy for falling particles
    private var dropCount = 0
    private var dropKind: Byte = RAIN
    private var extraCount = 0     // hail / ice pellets appended after the drops
    private var extraKind: Byte = HAIL
    private var cloudCount = 0
    private var cloudDark = false
    private var starCount = 0
    private var fogCount = 0
    private var sun = 0f           // 0..1 strength
    private var moon = 0f
    private var lightning = false
    private var shooting = false

    // ---- drops / flakes
    private val dx = FloatArray(MAX_DROPS)
    private val dy = FloatArray(MAX_DROPS)
    private val dvy = FloatArray(MAX_DROPS)
    private val dlen = FloatArray(MAX_DROPS)
    private val da = FloatArray(MAX_DROPS)
    private val dphase = FloatArray(MAX_DROPS)
    private val dsway = FloatArray(MAX_DROPS)
    private val dkind = ByteArray(MAX_DROPS)

    // ---- clouds
    private val cx = FloatArray(MAX_CLOUDS)
    private val cy = FloatArray(MAX_CLOUDS)
    private val cw = FloatArray(MAX_CLOUDS)
    private val cv = FloatArray(MAX_CLOUDS)
    private val ca = FloatArray(MAX_CLOUDS)

    // ---- stars (fractions of the canvas)
    private val sx = FloatArray(MAX_STARS)
    private val sy = FloatArray(MAX_STARS)
    private val sr = FloatArray(MAX_STARS)
    private val sp = FloatArray(MAX_STARS)
    private val ss = FloatArray(MAX_STARS)

    // ---- fog banks
    private val fx = FloatArray(MAX_FOG)
    private val fy = FloatArray(MAX_FOG)
    private val fw = FloatArray(MAX_FOG)
    private val fh = FloatArray(MAX_FOG)
    private val fv = FloatArray(MAX_FOG)
    private val fa = FloatArray(MAX_FOG)

    // ---- lightning
    private var nextStrike = 3f
    private var strikeAge = 99f
    private var strikePower = 1f
    private val boltX = FloatArray(BOLT)
    private val boltY = FloatArray(BOLT)
    private val branchX = FloatArray(BRANCH)
    private val branchY = FloatArray(BRANCH)
    private var branchFrom = 0

    // ---- shooting star
    private var nextShoot = 6f
    private var shootAge = 99f
    private var shootX = 0f
    private var shootY = 0f
    private var shootVx = 0f
    private var shootVy = 0f

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private val dp get() = density

    fun configure(mood: WeatherMood, windKmh: Double, precipMm: Double) {
        val wind = if (windKmh.isNaN()) 0.0 else windKmh
        val precip = if (precipMm.isNaN()) 0.0 else precipMm
        if (mood.code == cfgCode && mood.isDay == cfgDay && wind == cfgWind && precip == cfgPrecip) return
        cfgCode = mood.code; cfgDay = mood.isDay; cfgWind = wind; cfgPrecip = precip
        sky = mood.sky
        isDay = mood.isDay

        val code = mood.code
        val codeBase = when (code) {
            51, 61, 71, 77, 80, 85, 56, 66 -> 0.3f
            53, 63, 73, 81 -> 0.55f
            55, 65, 75, 82, 86, 57, 67 -> 0.85f
            95 -> 0.65f
            96, 99 -> 0.8f
            else -> 0.3f
        }
        val fromPrecip = (ln(1.0 + precip) / ln(11.0)).toFloat().coerceIn(0f, 1f)
        intensity = max(codeBase, fromPrecip)
        windTilt = (wind / 70.0).toFloat().coerceIn(0f, 0.5f)

        dropCount = 0; extraCount = 0; cloudCount = 0; starCount = 0; fogCount = 0
        sun = 0f; moon = 0f; lightning = false; shooting = false; cloudDark = false
        when (sky) {
            Sky.CLEAR -> if (isDay) sun = 1f else { starCount = MAX_STARS; moon = 1f; shooting = true }
            Sky.PARTLY -> {
                cloudCount = 4
                if (isDay) sun = 0.7f else { starCount = 70; moon = 0.7f; shooting = true }
            }
            Sky.CLOUDY -> cloudCount = 8
            Sky.FOG -> { fogCount = MAX_FOG; cloudCount = 2 }
            Sky.DRIZZLE -> { cloudCount = 5; dropKind = DRIZZLE; dropCount = (60 + 80 * intensity).toInt() }
            Sky.RAIN -> { cloudCount = 6; cloudDark = true; dropKind = RAIN; dropCount = (50 + 200 * intensity).toInt() }
            Sky.FREEZING -> {
                cloudCount = 5; dropKind = DRIZZLE; dropCount = (50 + 90 * intensity).toInt()
                extraKind = PELLET; extraCount = (16 + 30 * intensity).toInt()
            }
            Sky.SNOW -> { cloudCount = 4; dropKind = SNOW; dropCount = (40 + 140 * intensity).toInt() }
            Sky.THUNDER -> {
                cloudCount = 7; cloudDark = true; lightning = true
                dropKind = RAIN; dropCount = (90 + 170 * intensity).toInt()
                if (mood.hail) { extraKind = HAIL; extraCount = 40 }
            }
        }
        needsSeed = true
    }

    /** Advances the simulation. Returns the strength (0..1) of a lightning strike that began this frame, else 0. */
    fun step(dt: Float): Float {
        val w = width
        val h = height
        if (w <= 0f || h <= 0f) return 0f
        if (needsSeed || w != seededW || h != seededH) seed(w, h)
        t += dt
        if (t > 3600f) t -= 3600f

        // Falling particles.
        val total = activeDrops()
        for (i in 0 until total) {
            val vy = dvy[i]
            dy[i] += vy * dt
            dx[i] += vy * windTilt * dt * (if (dkind[i] == SNOW) 0.6f else 1f)
            val len = dlen[i]
            if (dy[i] - len > h || dx[i] - len > w) respawnDrop(i, w, h, top = true)
        }

        // Clouds drift right (with the wind) and wrap.
        for (i in 0 until cloudCount) {
            cx[i] += cv[i] * dt
            if (cx[i] - cw[i] > w) cx[i] = -cw[i] * r(0.9f, 1.4f)
        }
        for (i in 0 until fogCount) {
            fx[i] += fv[i] * dt
            if (fx[i] - fw[i] > w) fx[i] = -fw[i]
            if (fx[i] + fw[i] < 0f) fx[i] = w + fw[i]
        }

        // Shooting stars.
        if (shooting) {
            shootAge += dt
            nextShoot -= dt
            if (nextShoot <= 0f) {
                nextShoot = r(7f, 16f)
                shootAge = 0f
                val toLeft = rnd.nextBoolean()
                shootX = w * r(0.15f, 0.85f)
                shootY = h * r(0.03f, 0.25f)
                val speed = r(520f, 760f) * dp
                val angle = r(0.35f, 0.6f)
                shootVx = (if (toLeft) -1f else 1f) * speed * cos(angle)
                shootVy = speed * sin(angle)
            }
            if (shootAge < 1f) {
                shootX += shootVx * dt
                shootY += shootVy * dt
            }
        }

        // Lightning.
        var strike = 0f
        if (lightning) {
            strikeAge += dt
            nextStrike -= dt
            if (nextStrike <= 0f) {
                nextStrike = r(3.5f, 10f)
                strikeAge = 0f
                strikePower = r(0.55f, 1f)
                makeBolt(w, h)
                strike = strikePower
            }
        }
        return strike
    }

    private fun activeDrops() = min(MAX_DROPS, scaled(dropCount) + scaled(extraCount))

    /** Particle counts are tuned for a ~400 dp wide sky; scale with the actual width. */
    private fun scaled(n: Int): Int = (n * ((width / dp) / 400f).coerceIn(0.6f, 2.2f)).toInt()

    private fun seed(w: Float, h: Float) {
        needsSeed = false
        seededW = w
        seededH = h
        val drops = min(MAX_DROPS, scaled(dropCount))
        val total = activeDrops()
        for (i in 0 until total) {
            dkind[i] = if (i < drops) dropKind else extraKind
            respawnDrop(i, w, h, top = false)
        }
        for (i in 0 until cloudCount) {
            val layer = i % 3 // 0 far, 2 near
            cw[i] = r(70f, 110f) * dp * (0.75f + layer * 0.3f) * (if (sky == Sky.CLOUDY || cloudDark) 1.25f else 1f)
            cx[i] = r(-0.2f, 1.1f) * w
            cy[i] = h * (if (sky == Sky.CLOUDY || cloudDark) r(0.02f, 0.42f) else r(0.06f, 0.36f))
            cv[i] = (r(4f, 9f) + layer * 5f) * dp * (1f + windTilt * 3f)
            ca[i] = when {
                cloudDark -> r(0.18f, 0.32f)
                sky == Sky.CLOUDY -> r(0.22f, 0.4f)
                else -> r(0.32f, 0.55f)
            } * (0.7f + layer * 0.15f)
        }
        for (i in 0 until starCount) {
            sx[i] = rnd.nextFloat()
            sy[i] = rnd.nextFloat() * rnd.nextFloat() * 0.8f // denser near the top
            sr[i] = r(0.5f, 1.6f) * dp
            sp[i] = r(0f, 2f * PI.toFloat())
            ss[i] = r(0.8f, 3.2f)
        }
        for (i in 0 until fogCount) {
            fw[i] = w * r(0.6f, 1.1f)
            fh[i] = r(28f, 60f) * dp
            fx[i] = r(-0.3f, 1.3f) * w
            fy[i] = h * (0.12f + 0.75f * i / max(1, fogCount - 1).toFloat()) + r(-12f, 12f) * dp
            fv[i] = (if (i % 2 == 0) 1f else -1f) * r(5f, 12f) * dp
            fa[i] = r(0.22f, 0.4f)
        }
        nextStrike = r(1.5f, 4f)
        strikeAge = 99f
        nextShoot = r(3f, 9f)
        shootAge = 99f
    }

    private fun respawnDrop(i: Int, w: Float, h: Float, top: Boolean) {
        val k = dkind[i]
        val depth = rnd.nextFloat() // 0 far … 1 near
        when (k) {
            RAIN -> {
                dvy[i] = (900f + 450f * depth) * dp
                dlen[i] = (12f + 14f * depth) * dp
                da[i] = 0.18f + 0.4f * depth
            }
            DRIZZLE -> {
                dvy[i] = (420f + 220f * depth) * dp
                dlen[i] = (5f + 5f * depth) * dp
                da[i] = 0.18f + 0.35f * depth
            }
            SNOW -> {
                dvy[i] = (26f + 52f * depth) * dp
                dlen[i] = (1.1f + 2.6f * depth) * dp // radius
                da[i] = 0.45f + 0.5f * depth
                dsway[i] = (6f + 14f * depth) * dp
            }
            HAIL -> {
                dvy[i] = (620f + 300f * depth) * dp
                dlen[i] = (1.5f + 1.4f * depth) * dp
                da[i] = 0.6f + 0.35f * depth
            }
            else -> { // PELLET
                dvy[i] = (480f + 220f * depth) * dp
                dlen[i] = (0.9f + 0.8f * depth) * dp
                da[i] = 0.5f + 0.4f * depth
            }
        }
        dphase[i] = r(0f, 2f * PI.toFloat())
        // Spawn upwind so tilted rain still covers the whole width.
        val drift = h * windTilt
        dx[i] = r(-drift, w)
        dy[i] = if (top) -r(0f, h * 0.25f) - dlen[i] else r(-h * 0.1f, h)
    }

    private fun makeBolt(w: Float, h: Float) {
        var x = w * r(0.15f, 0.85f)
        val end = h * r(0.4f, 0.7f)
        for (i in 0 until BOLT) {
            boltX[i] = x
            boltY[i] = end * i / (BOLT - 1)
            x += r(-16f, 16f) * dp
        }
        branchFrom = 3 + rnd.nextInt(5)
        var bx = boltX[branchFrom]
        var by = boltY[branchFrom]
        val dir = if (rnd.nextBoolean()) 1f else -1f
        for (i in 0 until BRANCH) {
            branchX[i] = bx
            branchY[i] = by
            bx += dir * r(6f, 16f) * dp
            by += r(8f, 16f) * dp
        }
    }

    private fun flashAlpha(): Float {
        val a = strikeAge
        return when {
            a < 0.06f -> 1f
            a < 0.10f -> 0.25f
            a < 0.16f -> 0.85f
            a < 0.6f -> 0.85f * (1f - (a - 0.16f) / 0.44f)
            else -> 0f
        } * strikePower
    }

    fun draw(
        scope: DrawScope,
        particle: Color,
        glow: Color,
        cloudBrush: Brush,
        glowBrush: Brush,
        strength: Float,
        dark: Boolean,
    ) = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || needsSeed) return@with
        // Light added to a dark sky should brighten it, not muddy it (yellow over navy = olive).
        val blend = if (dark) BlendMode.Screen else BlendMode.SrcOver

        // Sun: soft glow + slowly turning rays, top end.
        if (sun > 0f) {
            val cxs = w * 0.86f
            val cys = h * 0.07f
            val pulse = 1f + 0.05f * sin(t * 0.7f)
            val rad = min(190f * dp, w * 0.7f) * pulse
            softDisc(glowBrush, cxs, cys, rad, rad, (if (dark) 0.38f else 0.75f) * sun * strength, blend)
            // Soft beams: long, narrow soft ellipses fanned around the sun.
            val rot = t * 2.5f
            for (i in 0 until 10) {
                val shimmer = 0.5f + 0.5f * sin(t * 0.9f + i * 1.7f)
                val len = min((100f + 60f * shimmer) * dp, w * 0.45f)
                rotate(rot + i * 36f, Offset(cxs, cys)) {
                    softDisc(
                        glowBrush, cxs + 60f * dp + len, cys, len, (10f + 6f * shimmer) * dp,
                        (0.08f + 0.08f * shimmer) * sun * strength * (if (dark) 0.7f else 1f), blend,
                    )
                }
            }
        }

        // Moon glow + stars.
        if (moon > 0f) {
            softDisc(glowBrush, w * 0.84f, h * 0.09f, 120f * dp, 120f * dp, 0.28f * moon * strength, blend)
        }
        for (i in 0 until starCount) {
            val y = sy[i] * h
            val tw = 0.55f + 0.45f * sin(t * ss[i] + sp[i])
            val fade = 1f - (y / h) * 1.1f
            if (fade <= 0f) continue
            drawCircle(Color.White, sr[i], Offset(sx[i] * w, y), alpha = (0.35f + 0.6f * tw) * fade * strength)
        }
        if (shooting && shootAge < 1f) {
            val life = 1f - shootAge
            val a = life * life * strength
            for (seg in 0 until 4) {
                val k0 = seg * 0.03f
                val k1 = k0 + 0.03f
                drawLine(
                    Color.White,
                    Offset(shootX - shootVx * k0, shootY - shootVy * k0),
                    Offset(shootX - shootVx * k1, shootY - shootVy * k1),
                    strokeWidth = (1.8f - seg * 0.35f) * dp, cap = StrokeCap.Round, alpha = a * (1f - seg * 0.24f),
                )
            }
        }

        // Clouds: clusters of soft puffs. On dark skies they stay a hint, so text on top stays readable.
        val cloudK = if (dark) 0.42f else 1f
        for (i in 0 until cloudCount) {
            val u = cw[i]
            val bob = sin(t * 0.25f + i) * 3f * dp
            for (p in 0 until 5) {
                val pr = PUFFS[p * 3 + 2] * u
                softDisc(cloudBrush, cx[i] + PUFFS[p * 3] * u, cy[i] + bob + PUFFS[p * 3 + 1] * u, pr, pr, ca[i] * strength * cloudK)
            }
        }

        // Fog banks: wide, flat soft ellipses.
        for (i in 0 until fogCount) {
            val bob = sin(t * 0.3f + i * 1.3f) * 6f * dp
            softDisc(cloudBrush, fx[i], fy[i] + bob, fw[i], fh[i], fa[i] * strength * (if (dark) 0.6f else 1f))
        }

        // Lightning: sky flash, then the bolt.
        if (lightning && strikeAge < 0.6f) {
            val fa = flashAlpha()
            drawRect(glow, alpha = fa * 0.22f * strength)
            drawRect(Color.White, alpha = fa * 0.12f * strength)
            if (strikeAge < 0.35f) {
                for (pass in 0 until 2) {
                    val sw = if (pass == 0) 7f * dp else 1.8f * dp
                    val col = if (pass == 0) glow else Color(0xFFFFFCE8)
                    val al = if (pass == 0) fa * 0.35f else fa
                    for (i in 0 until BOLT - 1) {
                        drawLine(col, Offset(boltX[i], boltY[i]), Offset(boltX[i + 1], boltY[i + 1]), sw, StrokeCap.Round, alpha = al)
                    }
                    for (i in 0 until BRANCH - 1) {
                        drawLine(col, Offset(branchX[i], branchY[i]), Offset(branchX[i + 1], branchY[i + 1]), sw * 0.7f, StrokeCap.Round, alpha = al * 0.8f)
                    }
                }
            }
        }

        // Precipitation on top.
        val total = activeDrops()
        for (i in 0 until total) {
            val x = dx[i]
            val y = dy[i]
            when (dkind[i]) {
                RAIN, DRIZZLE -> {
                    val len = dlen[i]
                    drawLine(
                        particle, Offset(x, y), Offset(x - len * windTilt, y - len),
                        strokeWidth = (if (dkind[i] == RAIN) 1.3f else 1f) * dp, cap = StrokeCap.Round,
                        alpha = da[i] * strength,
                    )
                }
                SNOW -> {
                    val sway = sin(t * (0.6f + dlen[i] / dp * 0.2f) + dphase[i]) * dsway[i]
                    drawCircle(particle, dlen[i], Offset(x + sway, y), alpha = da[i] * strength)
                }
                else -> drawCircle(particle, dlen[i], Offset(x, y), alpha = da[i] * strength)
            }
        }
    }

    /** Draws the unit soft brush as an ellipse with radii [rx], [ry] centred at ([x], [y]). */
    private fun DrawScope.softDisc(brush: Brush, x: Float, y: Float, rx: Float, ry: Float, alpha: Float, blend: BlendMode = BlendMode.SrcOver) {
        if (alpha <= 0.003f) return
        translate(x, y) {
            scale(rx / UNIT, ry / UNIT, pivot = Offset.Zero) {
                drawCircle(brush, UNIT, Offset.Zero, alpha = alpha.coerceAtMost(1f), blendMode = blend)
            }
        }
    }
}
