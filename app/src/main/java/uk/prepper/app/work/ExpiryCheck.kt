package uk.prepper.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import uk.prepper.app.MainActivity
import uk.prepper.app.R
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.Readiness
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Runs once a week (Saturday morning) - no other background work in the app. */
class ExpiryWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Repo.init(applicationContext)
        val soon = Readiness.expiringSoon(Repo.items(), LocalDate.now())
        if (soon.isNotEmpty()) ExpiryNotifier.show(applicationContext, soon.map { "${it.name} (${it.expiry})" })
        return Result.success()
    }
}

object ExpiryScheduler {
    private const val WORK = "weekly-expiry-check"

    /** Delay until the next Saturday 08:00 local time. */
    fun delayToNextSaturdayMorning(now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(LocalTime.of(8, 0))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next)
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ExpiryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delayToNextSaturdayMorning().toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // KEEP: once scheduled it stays on its Saturday cadence across app launches.
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

object ExpiryNotifier {
    const val CHANNEL = "expiry"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Weekly expiry check", NotificationManager.IMPORTANCE_DEFAULT)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission") // checked on the first line
    fun show(ctx: Context, lines: List<String>) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText("+${lines.size - 6} more")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${lines.size} item(s) expire within 6 weeks")
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, n)
    }
}
