package dev.vis.weather.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun and moon astronomy computed on the device (no network).
 * Sun: NOAA / Meeus low-precision solar coordinates (~0.01°). Moon: Meeus ch. 47 main terms (~0.05°).
 * Times are epoch seconds; 0 means "doesn't happen" (e.g. polar day/night).
 */
object Astro {
    private const val RAD = PI / 180
    private const val DEG = 180 / PI
    const val SYNODIC = 29.530588853
    private const val AU_KM = 149_597_870.7

    private fun jd(epochSec: Long): Double = epochSec / 86400.0 + 2440587.5
    private fun jd(epochSec: Double): Double = epochSec / 86400.0 + 2440587.5
    private fun epochOf(jd: Double): Long = ((jd - 2440587.5) * 86400).toLong()
    private fun norm(d: Double): Double = ((d % 360) + 360) % 360
    private fun sinD(d: Double) = sin(d * RAD)
    private fun cosD(d: Double) = cos(d * RAD)

    // ---------------------------------------------------------------- Sun

    private class SunCoords(val ra: Double, val dec: Double, val lon: Double, val distKm: Double, val eotMin: Double)

    private fun sun(jd: Double): SunCoords {
        val t = (jd - 2451545.0) / 36525
        val l0 = norm(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sinD(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sinD(2 * m) * (0.019993 - 0.000101 * t) + sinD(3 * m) * 0.000289
        val trueLon = l0 + c
        val v = m + c
        val r = 1.000001018 * (1 - e * e) / (1 + e * cosD(v))
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLon - 0.00569 - 0.00478 * sinD(omega)
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cosD(omega)
        val ra = norm(atan2(cosD(eps) * sinD(lambda), cosD(lambda)) * DEG)
        val dec = asin(sinD(eps) * sinD(lambda)) * DEG
        // Equation of time (minutes)
        val y = tan(eps / 2 * RAD).let { it * it }
        val l0r = l0 * RAD; val mr = m * RAD
        val eot = 4 * DEG * (y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
            0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr))
        return SunCoords(ra, dec, norm(lambda), r * AU_KM, eot)
    }

    private fun gmst(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525
        return norm(280.46061837 + 360.98564736629 * (jd - 2451545.0) + t * t * (0.000387933 - t / 38710000))
    }

    data class Position(val altitude: Double, val azimuth: Double)

    private fun horizontal(ra: Double, dec: Double, jd: Double, lat: Double, lon: Double): Position {
        val h = norm(gmst(jd) + lon - ra)
        val alt = asin(sinD(lat) * sinD(dec) + cosD(lat) * cosD(dec) * cosD(h)) * DEG
        // Azimuth from north, clockwise.
        val az = norm(atan2(sinD(h), cosD(h) * sinD(lat) - tan(dec * RAD) * cosD(lat)) * DEG + 180)
        return Position(alt, az)
    }

    /** Sun altitude/azimuth (degrees) at [epochSec]. Altitude not corrected for refraction. */
    fun sunPosition(epochSec: Long, lat: Double, lon: Double): Position {
        val j = jd(epochSec)
        val s = sun(j)
        return horizontal(s.ra, s.dec, j, lat, lon)
    }

    data class SunDay(
        val date: LocalDate,
        /** Civil dawn (sun 6° below the horizon): first light. */
        val firstLight: Long,
        val sunrise: Long,
        val solarNoon: Long,
        val sunset: Long,
        /** Civil dusk: last light. */
        val lastLight: Long,
        /** Sun height above the horizon at solar noon, degrees. */
        val noonElevation: Double,
        /** Compass bearing of sunrise / sunset, degrees from north. */
        val riseAzimuth: Double,
        val setAzimuth: Double,
        /** Seconds between sunrise and sunset (0 in polar night, 86400 in polar day). */
        val daylight: Double,
        val polarDay: Boolean,
        val polarNight: Boolean,
    )

    /**
     * Time when the sun's centre reaches altitude [h0] (deg) on the [rising] or setting side of solar noon [noonJd].
     * Returns null if it never does (sun stays above/below).
     */
    private fun sunEvent(noonJd: Double, lat: Double, lon: Double, h0: Double, rising: Boolean): Double? {
        var t = noonJd
        repeat(3) {
            val s = sun(t)
            val cosH = (sinD(h0) - sinD(lat) * sinD(s.dec)) / (cosD(lat) * cosD(s.dec))
            if (cosH < -1 || cosH > 1) return null
            val hDeg = acos(cosH) * DEG
            t = noonJd + (if (rising) -hDeg else hDeg) / 360.0
        }
        return t
    }

    private fun solarNoonJd(date: LocalDate, lon: Double): Double {
        val midnight = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC).toDouble()
        var noon = midnight + (720 - 4 * lon) * 60
        repeat(2) { noon = midnight + (720 - 4 * lon - sun(jd(noon)).eotMin) * 60 }
        return jd(noon)
    }

    /** Sun events for the local calendar [date] at [lat]/[lon]. */
    fun sunDay(date: LocalDate, lat: Double, lon: Double): SunDay {
        val noon = solarNoonJd(date, lon)
        val s = sun(noon)
        val noonPos = horizontal(s.ra, s.dec, noon, lat, lon)
        val rise = sunEvent(noon, lat, lon, -0.833, true)
        val set = sunEvent(noon, lat, lon, -0.833, false)
        val dawn = sunEvent(noon, lat, lon, -6.0, true)
        val dusk = sunEvent(noon, lat, lon, -6.0, false)
        val polarDay = rise == null && noonPos.altitude > 0
        val polarNight = rise == null && noonPos.altitude <= 0
        fun az(j: Double?) = j?.let { val c = sun(it); horizontal(c.ra, c.dec, it, lat, lon).azimuth } ?: Double.NaN
        val daylight = when {
            polarDay -> 86400.0
            polarNight || rise == null || set == null -> 0.0
            else -> (set - rise) * 86400
        }
        return SunDay(
            date = date,
            firstLight = dawn?.let { epochOf(it) } ?: 0L,
            sunrise = rise?.let { epochOf(it) } ?: 0L,
            solarNoon = epochOf(noon),
            sunset = set?.let { epochOf(it) } ?: 0L,
            lastLight = dusk?.let { epochOf(it) } ?: 0L,
            noonElevation = noonPos.altitude,
            riseAzimuth = az(rise),
            setAzimuth = az(set),
            daylight = daylight,
            polarDay = polarDay,
            polarNight = polarNight,
        )
    }

    /** Daylight length in seconds (fast path for charts). */
    fun daylight(date: LocalDate, lat: Double, lon: Double): Double {
        val noon = solarNoonJd(date, lon)
        val s = sun(noon)
        val cosH = (sinD(-0.833) - sinD(lat) * sinD(s.dec)) / (cosD(lat) * cosD(s.dec))
        return when {
            cosH <= -1 -> 86400.0
            cosH >= 1 -> 0.0
            else -> 2 * acos(cosH) * DEG / 360.0 * 86400
        }
    }

    /** Daylight for every day of [year] (index 0 = 1 January). */
    fun yearDaylight(year: Int, lat: Double, lon: Double): List<Double> {
        val start = LocalDate.of(year, 1, 1)
        return (0 until start.lengthOfYear()).map { daylight(start.plusDays(it.toLong()), lat, lon) }
    }

    enum class SeasonEvent(val label: String) {
        MARCH_EQUINOX("March equinox"), JUNE_SOLSTICE("June solstice"),
        SEPTEMBER_EQUINOX("September equinox"), DECEMBER_SOLSTICE("December solstice"),
    }

    /** Exact time of a solstice/equinox in [year] (Meeus ch. 27 start values, refined on the sun's longitude). */
    fun seasonEvent(year: Int, e: SeasonEvent): Long {
        val y = (year - 2000) / 1000.0
        var j = when (e) {
            SeasonEvent.MARCH_EQUINOX -> 2451623.80984 + 365242.37404 * y + 0.05169 * y * y - 0.00411 * y * y * y - 0.00057 * y * y * y * y
            SeasonEvent.JUNE_SOLSTICE -> 2451716.56767 + 365241.62603 * y + 0.00325 * y * y + 0.00888 * y * y * y - 0.00030 * y * y * y * y
            SeasonEvent.SEPTEMBER_EQUINOX -> 2451810.21715 + 365242.01767 * y - 0.11575 * y * y + 0.00337 * y * y * y + 0.00078 * y * y * y * y
            SeasonEvent.DECEMBER_SOLSTICE -> 2451900.05952 + 365242.74049 * y - 0.06223 * y * y - 0.00823 * y * y * y + 0.00032 * y * y * y * y
        }
        val target = e.ordinal * 90.0
        repeat(4) {
            val diff = ((target - sun(j).lon + 540) % 360) - 180
            j += 58 * sinD(diff)
        }
        return epochOf(j)
    }

    /** The next solstice or equinox after [fromEpoch]. */
    fun nextSeasonEvent(fromEpoch: Long, zone: ZoneId): Pair<SeasonEvent, Long> {
        val year = Instant.ofEpochSecond(fromEpoch).atZone(zone).year
        for (yr in year..year + 1) for (e in SeasonEvent.entries) {
            val t = seasonEvent(yr, e)
            if (t > fromEpoch) return e to t
        }
        return SeasonEvent.MARCH_EQUINOX to seasonEvent(year + 1, SeasonEvent.MARCH_EQUINOX)
    }

    /** Hemisphere-aware name: "Summer solstice", "Spring equinox"... */
    fun seasonName(e: SeasonEvent, lat: Double): String {
        val north = lat >= 0
        return when (e) {
            SeasonEvent.MARCH_EQUINOX -> if (north) "Spring equinox" else "Autumn equinox"
            SeasonEvent.JUNE_SOLSTICE -> if (north) "Summer solstice" else "Winter solstice"
            SeasonEvent.SEPTEMBER_EQUINOX -> if (north) "Autumn equinox" else "Spring equinox"
            SeasonEvent.DECEMBER_SOLSTICE -> if (north) "Winter solstice" else "Summer solstice"
        }
    }

    // ---------------------------------------------------------------- Moon

    private class MoonCoords(val lon: Double, val lat: Double, val distKm: Double, val ra: Double, val dec: Double)

    // D, M, M', F, Σl coefficient (1e-6 °), Σr coefficient (1e-3 km)
    private val LR = arrayOf(
        intArrayOf(0, 0, 1, 0, 6288774, -20905355), intArrayOf(2, 0, -1, 0, 1274027, -3699111),
        intArrayOf(2, 0, 0, 0, 658314, -2955968), intArrayOf(0, 0, 2, 0, 213618, -569925),
        intArrayOf(0, 1, 0, 0, -185116, 48888), intArrayOf(0, 0, 0, 2, -114332, -3149),
        intArrayOf(2, 0, -2, 0, 58793, 246158), intArrayOf(2, -1, -1, 0, 57066, -152138),
        intArrayOf(2, 0, 1, 0, 53322, -170733), intArrayOf(2, -1, 0, 0, 45758, -204586),
        intArrayOf(0, 1, -1, 0, -40923, -129620), intArrayOf(1, 0, 0, 0, -34720, 108743),
        intArrayOf(0, 1, 1, 0, -30383, 104755), intArrayOf(2, 0, 0, -2, 15327, 10321),
        intArrayOf(0, 0, 1, 2, -12528, 0), intArrayOf(0, 0, 1, -2, 10980, 79661),
        intArrayOf(4, 0, -1, 0, 10675, -34782), intArrayOf(0, 0, 3, 0, 10034, -23210),
        intArrayOf(4, 0, -2, 0, 8548, -21636), intArrayOf(2, 1, -1, 0, -7888, 24208),
        intArrayOf(2, 1, 0, 0, -6766, 30824), intArrayOf(1, 0, -1, 0, -5163, -8379),
        intArrayOf(1, 1, 0, 0, 4987, -16675), intArrayOf(2, -1, 1, 0, 4036, -12831),
        intArrayOf(2, 0, 2, 0, 3994, -10445), intArrayOf(4, 0, 0, 0, 3861, -11650),
        intArrayOf(2, 0, -3, 0, 3665, 14403), intArrayOf(0, 1, -2, 0, -2689, -7003),
    )

    // D, M, M', F, Σb coefficient (1e-6 °)
    private val B = arrayOf(
        intArrayOf(0, 0, 0, 1, 5128122), intArrayOf(0, 0, 1, 1, 280602), intArrayOf(0, 0, 1, -1, 277693),
        intArrayOf(2, 0, 0, -1, 173237), intArrayOf(2, 0, -1, 1, 55413), intArrayOf(2, 0, -1, -1, 46271),
        intArrayOf(2, 0, 0, 1, 32573), intArrayOf(0, 0, 2, 1, 17198), intArrayOf(2, 0, 1, -1, 9266),
        intArrayOf(0, 0, 2, -1, 8822), intArrayOf(2, -1, 0, -1, 8216), intArrayOf(2, 0, -2, -1, 4324),
        intArrayOf(2, 0, 1, 1, 4200),
    )

    private fun moon(jd: Double): MoonCoords {
        val t = (jd - 2451545.0) / 36525
        val lp = norm(218.3164477 + 481267.88123421 * t)
        val d = norm(297.8501921 + 445267.1114034 * t)
        val m = norm(357.5291092 + 35999.0502909 * t)
        val mp = norm(134.9633964 + 477198.8675055 * t)
        val f = norm(93.2720950 + 483202.0175233 * t)
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        var sl = 0.0; var sr = 0.0; var sb = 0.0
        for (r in LR) {
            val arg = r[0] * d + r[1] * m + r[2] * mp + r[3] * f
            val ef = when (abs(r[1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sl += r[4] * ef * sinD(arg)
            sr += r[5] * ef * cosD(arg)
        }
        for (r in B) {
            val arg = r[0] * d + r[1] * m + r[2] * mp + r[3] * f
            val ef = when (abs(r[1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sb += r[4] * ef * sinD(arg)
        }
        sl += 3958 * sinD(a1) + 1962 * sinD(lp - f) + 318 * sinD(a2)
        sb += -2235 * sinD(lp) + 382 * sinD(a3) + 175 * sinD(a1 - f) + 175 * sinD(a1 + f) + 127 * sinD(lp - mp) - 115 * sinD(lp + mp)
        val lambda = norm(lp + sl / 1e6)
        val beta = sb / 1e6
        val dist = 385000.56 + sr / 1000
        val eps = 23.439291 - 0.0130042 * t
        val ra = norm(atan2(sinD(lambda) * cosD(eps) - tan(beta * RAD) * sinD(eps), cosD(lambda)) * DEG)
        val dec = asin(sinD(beta) * cosD(eps) + cosD(beta) * sinD(eps) * sinD(lambda)) * DEG
        return MoonCoords(lambda, beta, dist, ra, dec)
    }

    data class MoonState(
        /** 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
        val phase: Double,
        /** Illuminated fraction 0..1. */
        val illumination: Double,
        val name: String,
        val waxing: Boolean,
        val distanceKm: Double,
        val altitude: Double,
        val azimuth: Double,
    )

    fun phaseName(p: Double): String = when {
        p < 0.0339 -> "New moon"
        p < 0.216 -> "Waxing crescent"
        p < 0.284 -> "First quarter"
        p < 0.466 -> "Waxing gibbous"
        p < 0.534 -> "Full moon"
        p < 0.716 -> "Waning gibbous"
        p < 0.784 -> "Last quarter"
        p < 0.966 -> "Waning crescent"
        else -> "New moon"
    }

    /** Elongation of the moon east of the sun, 0..360°. */
    private fun elongation(jd: Double): Double = norm(moon(jd).lon - sun(jd).lon)

    fun moonState(epochSec: Long, lat: Double = 0.0, lon: Double = 0.0): MoonState {
        val j = jd(epochSec)
        val mc = moon(j)
        val s = sun(j)
        val elong = norm(mc.lon - s.lon)
        // Phase angle (Meeus 48.2/48.3)
        val cosPsi = cosD(mc.lat) * cosD(mc.lon - s.lon)
        val psi = acos(cosPsi.coerceIn(-1.0, 1.0))
        val i = atan2(s.distKm * sin(psi), mc.distKm - s.distKm * cosPsi)
        val k = (1 + cos(i)) / 2
        val p = elong / 360
        val pos = horizontal(mc.ra, mc.dec, j, lat, lon)
        return MoonState(p, k, phaseName(p), elong < 180, mc.distKm, pos.altitude, pos.azimuth)
    }

    /** Topocentric-ish altitude used for rise/set (centre, with the standard h0 for the moon). */
    private fun moonAltMinusH0(epochSec: Double, lat: Double, lon: Double): Double {
        val j = jd(epochSec)
        val mc = moon(j)
        val parallax = asin(6378.14 / mc.distKm) * DEG
        val h0 = 0.7275 * parallax - 0.5667
        return horizontal(mc.ra, mc.dec, j, lat, lon).altitude - h0
    }

    data class MoonDay(val rise: Long, val set: Long, val alwaysUp: Boolean, val alwaysDown: Boolean)

    /** Moonrise and moonset during the local calendar [date] (0 when it doesn't happen that day). */
    fun moonDay(date: LocalDate, zone: ZoneId, lat: Double, lon: Double): MoonDay {
        val start = date.atStartOfDay(zone).toEpochSecond().toDouble()
        val end = date.plusDays(1).atStartOfDay(zone).toEpochSecond().toDouble()
        val step = 600.0
        var rise = 0L; var set = 0L
        var t0 = start
        var a0 = moonAltMinusH0(t0, lat, lon)
        val first = a0
        var anyUp = a0 > 0
        while (t0 < end) {
            val t1 = minOf(t0 + step, end)
            val a1 = moonAltMinusH0(t1, lat, lon)
            if (a1 > 0) anyUp = true
            if ((a0 <= 0) != (a1 <= 0)) {
                // Bisection for a crisp time.
                var lo = t0; var hi = t1; var alo = a0
                repeat(12) {
                    val mid = (lo + hi) / 2
                    val am = moonAltMinusH0(mid, lat, lon)
                    if ((am <= 0) == (alo <= 0)) { lo = mid; alo = am } else hi = mid
                }
                val at = ((lo + hi) / 2).toLong()
                if (a0 <= 0 && a1 > 0) { if (rise == 0L) rise = at } else { if (set == 0L) set = at }
            }
            t0 = t1; a0 = a1
        }
        return MoonDay(rise, set, alwaysUp = rise == 0L && set == 0L && first > 0, alwaysDown = rise == 0L && set == 0L && !anyUp)
    }

    /** Next time after [fromEpoch] when the moon/sun elongation reaches [targetDeg] (0 new, 90 first quarter, 180 full, 270 last quarter). */
    fun nextPhase(fromEpoch: Long, targetDeg: Double): Long {
        var j = jd(fromEpoch)
        // Coarse step forward until we pass the target, then refine.
        fun diff(x: Double) = ((elongation(x) - targetDeg + 540) % 360) - 180 // -180..180, crosses 0 upward at the event
        var prev = diff(j)
        var steps = 0
        while (steps < 140) {
            val nj = j + 0.25
            val d = diff(nj)
            if (prev < 0 && d >= 0) {
                var lo = j; var hi = nj
                repeat(20) {
                    val mid = (lo + hi) / 2
                    if (diff(mid) < 0) lo = mid else hi = mid
                }
                return epochOf((lo + hi) / 2)
            }
            prev = d; j = nj; steps++
        }
        return epochOf(j)
    }

    fun nextFullMoon(fromEpoch: Long): Long = nextPhase(fromEpoch, 180.0)
    fun nextNewMoon(fromEpoch: Long): Long = nextPhase(fromEpoch, 0.0)

    /** Perigee ~356 500 km, apogee ~406 700 km. A full moon closer than ~360 000 km is popularly called a supermoon. */
    fun isSupermoon(distanceKm: Double): Boolean = distanceKm < 360_000
}
