package dev.vis.weather.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import java.time.LocalDate

/** Little bits of personal history, kept only on this device ("Your activity"). */
data class WeatherStats(
    val installedSince: LocalDate?,
    val daysCheckedIn: Int,
    val streak: Int,
    val bestStreak: Int,
    val hottest: Double,
    val hottestWhere: String,
    val coldest: Double,
    val coldestWhere: String,
    val conditions: Set<Int>,
    val places: Set<String>,
)

object Stats {
    private fun sp(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    /** Record a successful look at the weather. Cheap; call after each refresh. */
    fun record(context: Context, place: Place, f: Forecast) {
        val sp = sp(context)
        val today = LocalDate.now()
        val last = sp.getString("last_day", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        sp.edit {
            if (!sp.contains("since")) putString("since", today.toString())
            if (last != today) {
                putInt("days", sp.getInt("days", 0) + 1)
                val streak = if (last == today.minusDays(1)) sp.getInt("streak", 0) + 1 else 1
                putInt("streak", streak)
                putInt("best", maxOf(streak, sp.getInt("best", 0)))
                putString("last_day", today.toString())
            }
            val t = f.current.temp
            if (!t.isNaN()) {
                if (!sp.contains("hot") || t > sp.getFloat("hot", 0f)) { putFloat("hot", t.toFloat()); putString("hot_where", place.name) }
                if (!sp.contains("cold") || t < sp.getFloat("cold", 0f)) { putFloat("cold", t.toFloat()); putString("cold_where", place.name) }
            }
            val conds = JSONArray(sp.getString("conds", "[]")).let { a -> (0 until a.length()).map { a.getInt(it) }.toMutableSet() }
            conds += WeatherInfo.sky(f.current.code).ordinal
            putString("conds", JSONArray(conds.toList()).toString())
            val places = JSONArray(sp.getString("places", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() }
            places += place.name
            putString("places", JSONArray(places.toList()).toString())
        }
    }

    fun read(context: Context): WeatherStats {
        val sp = sp(context)
        val last = sp.getString("last_day", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val streakAlive = last != null && !last.isBefore(LocalDate.now().minusDays(1))
        return WeatherStats(
            installedSince = sp.getString("since", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            daysCheckedIn = sp.getInt("days", 0),
            streak = if (streakAlive) sp.getInt("streak", 0) else 0,
            bestStreak = sp.getInt("best", 0),
            hottest = if (sp.contains("hot")) sp.getFloat("hot", 0f).toDouble() else Double.NaN,
            hottestWhere = sp.getString("hot_where", "") ?: "",
            coldest = if (sp.contains("cold")) sp.getFloat("cold", 0f).toDouble() else Double.NaN,
            coldestWhere = sp.getString("cold_where", "") ?: "",
            conditions = JSONArray(sp.getString("conds", "[]")).let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            places = JSONArray(sp.getString("places", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
        )
    }
}
