package dev.vis.weather

import android.app.Application
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

class WeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        L.init(this)
    }
}

/**
 * Localized strings for code that has no Context or Composable scope (data, alerts, enums, formatting).
 * Reads the application's resources on every call, so it follows the system / per-app language.
 * In @Composable code prefer stringResource / pluralStringResource.
 */
object L {
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun s(@StringRes id: Int): String = app.getString(id)

    fun s(@StringRes id: Int, vararg args: Any?): String = app.getString(id, *args)

    fun p(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
        if (args.isEmpty()) app.resources.getQuantityString(id, count, count)
        else app.resources.getQuantityString(id, count, *args)
}

/**
 * A label used in the middle of a sentence ("High" → "high"). Languages that capitalise nouns
 * (German) keep the original casing.
 */
fun String.inSentence(): String {
    val locale = java.util.Locale.getDefault()
    if (locale.language in setOf("de", "lb")) return this
    // Acronyms like "UV" or "AQI" keep their capitals.
    return split(' ').joinToString(" ") { w -> if (w.length > 1 && w.all { !it.isLetter() || it.isUpperCase() }) w else w.lowercase(locale) }
}
