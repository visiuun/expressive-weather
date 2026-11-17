package dev.vis.weather.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dev.vis.weather.MainActivity
import dev.vis.weather.R
import java.time.LocalTime

/** A notification ready to be shown. */
data class Note(
    val type: AlertType,
    /** Stable key: re-posting with the same key replaces the notification. */
    val key: String,
    val title: String,
    val text: String,
    val bigText: String? = null,
    val placeKey: String? = null,
    val placeName: String = "",
    val icon: Int = R.drawable.ic_stat_bell,
    /** Severe things may break through quiet hours. */
    val urgent: Boolean = false,
    /** Where tapping goes: "alerts", "warnings", "friends", "metric:PRECIP", "day:1"… */
    val open: String? = null,
    val color: Int? = null,
    val actions: List<Pair<String, String>> = emptyList(),
    val log: Boolean = true,
)

object Notifier {
    const val EXTRA_OPEN = "dev.vis.weather.OPEN"
    const val EXTRA_PLACE = "dev.vis.weather.NOTIFY_PLACE"

    private const val ONGOING_ID = 1
    private const val NOWCAST_ID = 2

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach { c ->
            val ch = NotificationChannel(c.id, c.title, c.importance).apply {
                description = c.description
                if (c == Channel.ONGOING) {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                if (c == Channel.WARNINGS) enableLights(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun permitted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channelEnabled(context: Context, channel: Channel): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val nm = context.getSystemService(NotificationManager::class.java)
        return (nm.getNotificationChannel(channel.id)?.importance ?: 1) != NotificationManager.IMPORTANCE_NONE
    }

    private fun inQuietHours(prefs: AlertPrefs): Boolean {
        if (!prefs.quietHours) return false
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val s = prefs.quietStart
        val e = prefs.quietEnd
        return if (s <= e) now in s until e else now >= s || now < e
    }

    fun openIntent(context: Context, open: String?, placeKey: String?, requestCode: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, open)
        if (placeKey != null) i.putExtra(EXTRA_PLACE, placeKey)
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun id(key: String) = key.hashCode() and 0x7FFFFFFF or 0x100

    fun post(context: Context, note: Note) {
        if (!permitted(context)) return
        ensureChannels(context)
        val prefs = AlertPrefs.get(context)
        val quiet = inQuietHours(prefs) && !note.urgent
        val b = NotificationCompat.Builder(context, note.type.channel.id)
            .setSmallIcon(note.icon)
            .setContentTitle(note.title)
            .setContentText(note.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.bigText ?: note.text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(quiet)
            .setCategory(if (note.type == AlertType.WARNINGS || note.type == AlertType.FRIENDS) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openIntent(context, note.open, note.placeKey, id(note.key)))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
        if (note.placeName.isNotBlank()) b.setSubText(note.placeName)
        note.color?.let { b.setColor(it) }
        if (note.urgent) b.setPriority(NotificationCompat.PRIORITY_HIGH)
        note.actions.forEachIndexed { i, (label, open) ->
            b.addAction(0, label, openIntent(context, open, note.placeKey, id(note.key) + i + 1))
        }
        runCatching { NotificationManagerCompat.from(context).notify(note.key, id(note.key), b.build()) }
        if (note.log) prefs.log(AlertLog(System.currentTimeMillis(), note.type, note.title, note.text, note.placeName))
    }

    fun cancel(context: Context, key: String) = NotificationManagerCompat.from(context).cancel(key, id(key))

    // ---- Live rain countdown (Android 16 Live Updates; a normal ongoing notification elsewhere)

    /** One step of the nowcast bar: [mm] per 15 minutes. */
    data class NowcastStep(val minutes: Int, val mm: Double)

    fun postNowcast(
        context: Context,
        title: String,
        text: String,
        chip: String,
        /** Epoch millis to count down to (rain start, or rain end while it's raining), shown live. */
        countdownTo: Long?,
        steps: List<NowcastStep>,
        snow: Boolean,
        placeKey: String?,
        placeName: String,
        silent: Boolean,
    ) {
        if (!permitted(context)) return
        ensureChannels(context)
        val maxMinutes = (steps.maxOfOrNull { it.minutes } ?: 60) + 15
        val style = NotificationCompat.ProgressStyle().setStyledByProgress(false)
        // One coloured segment per 15-minute slot: grey when dry, deeper blue (or icy) as intensity rises.
        steps.forEach { s ->
            val c = when {
                s.mm < 0.1 -> Color.parseColor("#5F6B7A")
                snow -> if (s.mm < 0.5) Color.parseColor("#BFE6FF") else Color.parseColor("#7CC8FF")
                s.mm < 0.5 -> Color.parseColor("#7FB8FF")
                s.mm < 2.0 -> Color.parseColor("#2F7BFF")
                else -> Color.parseColor("#123FD1")
            }
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(15).setColor(c))
        }
        style.setProgress(0)
        style.setProgressTrackerIcon(IconCompat.createWithResource(context, if (snow) R.drawable.ic_stat_snow else R.drawable.ic_stat_rain))
        val b = NotificationCompat.Builder(context, Channel.NOWCAST.id)
            .setSmallIcon(if (snow) R.drawable.ic_stat_snow else R.drawable.ic_stat_rain)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(placeName.ifBlank { null })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent || inQuietHours(AlertPrefs.get(context)))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(context, "metric:PRECIP", placeKey, NOWCAST_ID))
            .setTimeoutAfter(maxMinutes * 60_000L)
            .setRequestPromotedOngoing(true)
        if (countdownTo != null && countdownTo > System.currentTimeMillis()) {
            b.setWhen(countdownTo).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShortCriticalText(chip)
        }
        if (steps.isNotEmpty()) b.setStyle(style) else b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        runCatching { NotificationManagerCompat.from(context).notify(NOWCAST_ID, b.build()) }
    }

    fun cancelNowcast(context: Context) = NotificationManagerCompat.from(context).cancel(NOWCAST_ID)

    // ---- Status bar temperature

    fun postOngoing(context: Context, temp: String, title: String, text: String, bigText: String, placeKey: String?, placeName: String) {
        if (!permitted(context)) return
        ensureChannels(context)
        val b = NotificationCompat.Builder(context, Channel.ONGOING.id)
            .setSmallIcon(IconCompat.createWithBitmap(tempBitmap(temp)))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSubText(placeName.ifBlank { null })
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openIntent(context, null, placeKey, ONGOING_ID))
        runCatching { NotificationManagerCompat.from(context).notify(ONGOING_ID, b.build()) }
    }

    fun cancelOngoing(context: Context) = NotificationManagerCompat.from(context).cancel(ONGOING_ID)

    /** White digits on transparent, drawn big and condensed so "-12°" still fits the status bar slot. */
    private fun tempBitmap(text: String): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.95f
        }
        while (p.measureText(text) > size * 1.02f && p.textSize > 20f) p.textSize -= 2f
        val fm = p.fontMetrics
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, size / 2f, y, p)
        return bmp
    }
}
