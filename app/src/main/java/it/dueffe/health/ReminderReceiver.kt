package it.dueffe.health

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: ""
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("health_reminders", "Promemoria salute", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val note = NotificationCompat.Builder(context, "health_reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(open).build()
            manager.notify(intent.getIntExtra("id", 0), note)
        }
        ReminderScheduler(context).scheduleAll()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ReminderScheduler(context).scheduleAll() }
}

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val store = HealthStore(context)
    private fun pending(id: Int, title: String = "", body: String = "") = PendingIntent.getBroadcast(context, id, Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("body", body), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun schedule(id: Int, whenMillis: Long, title: String, body: String) {
        if (whenMillis <= System.currentTimeMillis()) return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending(id, title, body))
    }
    fun scheduleAll() {
        val known = store.settings.optJSONArray("scheduledIds")
        if (known != null) for (i in 0 until known.length()) alarms.cancel(pending(known.optInt(i)))
        val ids = org.json.JSONArray()
        if (!store.settings.bool("notifications")) { store.setSetting("scheduledIds", ids); return }
        val now = LocalDateTime.now()
        val profiles = store.profiles
        for (p in 0 until profiles.length()) {
            val profileId = profiles.getJSONObject(p).str("id")
            for (med in store.list("medicines", profileId)) {
                if (med.bool("archiviata") || !med.optJSONObject("notifiche").let { it?.bool("attive", true) ?: true }) continue
                for (time in med.strings("orari")) {
                    val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: continue
                    val nextDate = (0L..366L).asSequence().map { LocalDate.now().plusDays(it) }
                        .firstOrNull { date -> medicineScheduledOn(med, date) && LocalDateTime.of(date, localTime).isAfter(now) } ?: continue
                    val at = LocalDateTime.of(nextDate, localTime)
                    val id = "${profileId}:${med.str("id")}:$time".hashCode()
                    ids.put(id)
                    schedule(id, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Farmaco: ${med.str("nome")}", "È ora della dose ${med.str("dosaggio")} ${med.str("unita")}")
                    val advances = med.optJSONObject("notifiche")?.optJSONArray("anticipo")
                    if (advances != null) for (i in 0 until advances.length()) {
                        val pre = at.minusMinutes(advances.optLong(i))
                        if (pre.isAfter(now)) {
                            val preId = "${profileId}:${med.str("id")}:$time:pre${advances.optLong(i)}".hashCode()
                            ids.put(preId)
                            schedule(preId, pre.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Farmaco tra ${advances.optLong(i)} minuti", "${med.str("nome")} alle $time")
                        }
                    }
                }
            }
            for (visit in store.list("appointments", profileId)) {
                val reminder = visit.optJSONObject("promemoria")
                if (!visit.bool("completata") && reminder?.bool("attivo", true) == true && visit.str("data").isNotBlank()) {
                    val at = runCatching { LocalDateTime.of(LocalDate.parse(visit.str("data")), LocalTime.parse(visit.str("ora").ifBlank { "09:00" })) }.getOrNull()
                    if (at != null) {
                        val mins = reminder.optJSONArray("anticipo") ?: org.json.JSONArray().put(1440)
                        for (i in 0 until mins.length()) {
                            val time = at.minusMinutes(mins.optLong(i))
                            val id = "${profileId}:${visit.str("id")}:$i".hashCode()
                            ids.put(id)
                            schedule(id, time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Visita: ${visit.str("tipo")}", "Promemoria visita ${visit.str("data")} ${visit.str("ora")}")
                        }
                    }
                }
                val referti = visit.optJSONObject("refertiReminder")
                if (referti != null && !referti.bool("ritirati") && referti.str("data").isNotBlank()) {
                    val date = runCatching { LocalDate.parse(referti.str("data")) }.getOrNull()
                    if (date != null) {
                        val id = "${profileId}:${visit.str("id")}:referti".hashCode()
                        ids.put(id)
                        schedule(id, date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Ritira i referti", "${visit.str("tipo")} · ${referti.str("nota")}")
                    }
                }
            }
            for (booking in store.list("bookingReminders", profileId)) {
                if (booking.bool("prenotata") || booking.str("dataPromemoria").isBlank()) continue
                val at = runCatching { LocalDateTime.of(LocalDate.parse(booking.str("dataPromemoria")), LocalTime.of(9, 0)) }.getOrNull() ?: continue
                val id = "${profileId}:${booking.str("id")}:booking".hashCode()
                ids.put(id)
                schedule(id, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Prenota una visita", booking.str("tipoVisita"))
            }
        }
        store.setSetting("scheduledIds", ids)
    }
}
