package com.kitap.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.ZoneId

/**
 * Kişisel notlar için hatırlatıcı: notun gün ve saatinde telefona bildirim gelir
 * (saat yazılmamışsa o gün 09:00'da). Uygulama her tazelendiğinde yeniden kurulur.
 */
object Reminders {
    private const val PREFS = "kitap_reminders"
    const val CHANNEL = "kitap_reminders"
    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private fun pending(c: Context, plan: Plan, flags: Int): PendingIntent? {
        val i = Intent(c, ReminderReceiver::class.java)
            .putExtra("text", plan.text)
            .putExtra("time", plan.time?.let { Format.time(it) } ?: "")
            .putExtra("id", plan.reqCode)
        return PendingIntent.getBroadcast(c, plan.reqCode, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(c: Context, plans: List<Plan>) {
        if (BuildConfig.ADMIN) return
        val app = c.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = sp.getStringSet("codes", emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()
        val next = HashSet<String>()
        plans.forEach { p ->
            val at = p.date.atTime(p.time ?: DEFAULT_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now || at > now + 60L * 86_400_000L) return@forEach
            val pi = pending(app, p, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                next.add(p.reqCode.toString())
            } catch (_: Throwable) {
            }
        }
        // Silinen notların hatırlatıcılarını kaldır
        (old - next).forEach { code ->
            val pi = PendingIntent.getBroadcast(app, code.toInt(), Intent(app, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        sp.edit().putStringSet("codes", next).apply()
    }

    fun cancelAll(c: Context) = schedule(c, emptyList())

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        }
    }
}

/** Saati gelen hatırlatıcıyı bildirim olarak gösterir. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val text = intent.getStringExtra("text").orEmpty()
        if (text.isEmpty()) return
        Reminders.ensureChannel(c)
        val time = intent.getStringExtra("time").orEmpty()
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(c.getString(R.string.reminder_title) + if (time.isNotEmpty()) " · $time" else "")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(intent.getIntExtra("id", 1), n)
        } catch (_: SecurityException) {
        }
    }
}

/** Telefon yeniden başlayınca hatırlatıcıları son kaydedilen notlardan tekrar kur. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Account.cachedHome(c)?.let { Reminders.schedule(c, it.plans) }
    }
}
