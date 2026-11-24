package dev.vis.weather.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.vis.weather.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps background work in line with the alert settings:
 *  - a periodic job (every 15 min when rain nowcasts are on, otherwise 30) for warnings, nowcasts, storms,
 *    pressure, friends and the status-bar temperature;
 *  - one alarm per daily alert at its exact time (when the system allows exact alarms; ±10 min otherwise).
 */
object AlertScheduler {
    private const val WATCH = "alerts-watch"
    const val ACTION_DAILY = "dev.vis.weather.action.DAILY_ALERT"
    const val ACTION_REPOST_ONGOING = "dev.vis.weather.action.REPOST_ONGOING"
    const val EXTRA_TYPE = "type"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(context: Context) {
        val app = context.applicationContext
        Notifier.ensureChannels(app)
        val cfg = AlertPrefs.get(app).configs.value
        val dynamicIcon = dev.vis.weather.data.Prefs(app).settings.value.appIcon == dev.vis.weather.data.AppIcon.DYNAMIC
        val watchNeeded = dynamicIcon || cfg.any { (t, c) -> c.enabled && (t.trigger != Trigger.DAILY || (t == AlertType.RAIN && c.allDay)) }
        val wm = WorkManager.getInstance(app)
        if (watchNeeded) {
            val fast = cfg.getValue(AlertType.NOWCAST).enabled || cfg.getValue(AlertType.RAIN).let { it.enabled && it.allDay }
            val minutes = if (fast) 15L else 30L
            val sp = app.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            val policy = if (sp.getLong("watch_period", 0L) == minutes) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE
            sp.edit().putLong("watch_period", minutes).apply()
            wm.enqueueUniquePeriodicWork(
                WATCH, policy,
                PeriodicWorkRequestBuilder<AlertWatchWorker>(minutes, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        } else {
            wm.cancelUniqueWork(WATCH)
            Notifier.cancelNowcast(app)
        }
        if (!cfg.getValue(AlertType.ONGOING).enabled) Notifier.cancelOngoing(app)
        AlertType.entries.filter { it.trigger == Trigger.DAILY }.forEach { t ->
            val c = cfg.getValue(t)
            if (c.enabled && !(t == AlertType.RAIN && c.allDay)) scheduleDaily(app, t, c.time) else cancelDaily(app, t)
        }
    }

    /** Run the watcher right now (e.g. after enabling an alert or opening the app). */
    fun runWatchNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WATCH-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AlertWatchWorker>().setConstraints(network).build(),
        )
    }

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pending(context: Context, type: AlertType): PendingIntent {
        val i = Intent(context, AlertReceiver::class.java).setAction(ACTION_DAILY).putExtra(EXTRA_TYPE, type.name)
        return PendingIntent.getBroadcast(context, 1000 + type.ordinal, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextTrigger(minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = LocalDateTime.now(zone)
        var t = LocalDate.now(zone).atTime(minutes / 60, minutes % 60)
        if (!t.isAfter(now.plusSeconds(30))) t = t.plusDays(1)
        return t.atZone(zone).toInstant().toEpochMilli()
    }

    fun scheduleDaily(context: Context, type: AlertType, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = nextTrigger(minutes)
        val pi = pending(context, type)
        runCatching {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
        }
    }

    fun cancelDaily(context: Context, type: AlertType) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, type))
    }

    fun runDailyNow(context: Context, type: AlertType) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily-${type.name}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyAlertWorker>()
                .setInputData(workDataOf(EXTRA_TYPE to type.name))
                .setConstraints(network)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }
}

/** Alarm fired / device rebooted / clock changed / app updated. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlertScheduler.ACTION_DAILY -> {
                val type = AlertType.entries.firstOrNull { it.name == intent.getStringExtra(AlertScheduler.EXTRA_TYPE) } ?: return
                val cfg = AlertPrefs.get(context).config(type)
                if (!cfg.enabled) return
                AlertScheduler.runDailyNow(context, type)
                AlertScheduler.scheduleDaily(context, type, cfg.time)
            }
            AlertScheduler.ACTION_REPOST_ONGOING -> {
                if (!AlertPrefs.get(context).config(AlertType.ONGOING).enabled) return
                Notifier.postOngoing(
                    context,
                    intent.getStringExtra("temp") ?: return,
                    intent.getStringExtra("title").orEmpty(),
                    intent.getStringExtra("text").orEmpty(),
                    intent.getStringExtra("big").orEmpty(),
                    intent.getStringExtra("place"),
                    intent.getStringExtra("placeName").orEmpty(),
                )
            }
            else -> AlertScheduler.sync(context)
        }
    }
}

private fun workingInfo(context: Context): ForegroundInfo {
    Notifier.ensureChannels(context)
    val n = NotificationCompat.Builder(context, Channel.ONGOING.id)
        .setSmallIcon(R.drawable.ic_stat_cloud)
        .setContentTitle(context.getString(R.string.al_checking_weather))
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
    // Only used below Android 12, where expedited work runs as a short foreground service.
    return ForegroundInfo(42, n)
}

class AlertWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AlertEngine.runWatch(applicationContext)
        runCatching { BackgroundHooks.afterWatch(applicationContext) }
        return Result.success()
    }
}

class DailyAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = workingInfo(applicationContext)

    override suspend fun doWork(): Result {
        val type = AlertType.entries.firstOrNull { it.name == inputData.getString(AlertScheduler.EXTRA_TYPE) } ?: return Result.failure()
        val result = runCatching { AlertEngine.runDaily(applicationContext, type) }.getOrNull()
        // null = the forecast couldn't be loaded (network still waking up?): retry a couple of times.
        return if (result == null && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

/** Extension point for other background tasks that should run with the watcher (e.g. dynamic app icon). */
object BackgroundHooks {
    var afterWatch: suspend (Context) -> Unit = { dev.vis.weather.data.AppIcons.sync(it) }
    /** Called after the app or a background job loaded a fresh forecast for the selected place. */
    var afterRefresh: suspend (Context, dev.vis.weather.data.Settings, dev.vis.weather.data.Forecast) -> Unit = { _, _, _ -> }
}
