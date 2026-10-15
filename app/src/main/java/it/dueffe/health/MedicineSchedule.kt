package it.dueffe.health

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Le terapie vecchie senza frequenza strutturata continuano a usare gli orari quotidiani. */
fun medicineFrequencyType(med: JSONObject): String = med.str("frequenzaTipo").ifBlank {
    when (med.str("frequenza").lowercase()) {
        "al bisogno" -> "as_needed"
        "a giorni alterni" -> "interval"
        "ogni settimana" -> "weekdays"
        else -> "daily"
    }
}

fun medicineScheduledOn(med: JSONObject, date: LocalDate): Boolean {
    val start = runCatching { LocalDate.parse(med.str("dataInizio")) }.getOrNull()
    val end = if (med.bool("senzaFine", true)) null else runCatching { LocalDate.parse(med.str("dataFine")) }.getOrNull()
    if (start != null && date.isBefore(start)) return false
    if (end != null && date.isAfter(end)) return false
    return when (medicineFrequencyType(med)) {
        "as_needed" -> false
        "interval" -> {
            val days = med.optInt("intervalloGiorni", if (med.str("frequenza") == "A giorni alterni") 2 else 1).coerceAtLeast(1)
            val anchor = start ?: date
            ChronoUnit.DAYS.between(anchor, date) % days == 0L
        }
        "weekdays" -> {
            val selected = med.optJSONArray("giorniSettimana")
            if (selected == null || selected.length() == 0) date.dayOfWeek == (start ?: date).dayOfWeek
            else (0 until selected.length()).any { selected.optInt(it) == date.dayOfWeek.value }
        }
        else -> true
    }
}

fun medicineTimesOn(med: JSONObject, date: LocalDate): List<String> =
    if (medicineScheduledOn(med, date)) med.strings("orari") else emptyList()

fun medicineFrequencyDescription(med: JSONObject): String = when (medicineFrequencyType(med)) {
    "as_needed" -> "Al bisogno"
    "interval" -> "Ogni ${med.optInt("intervalloGiorni", 2).coerceAtLeast(2)} giorni"
    "weekdays" -> {
        val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
        val selected = med.optJSONArray("giorniSettimana")
        if (selected == null || selected.length() == 0) "Ogni settimana"
        else (0 until selected.length()).mapNotNull { days.getOrNull(selected.optInt(it) - 1) }.joinToString(", ")
    }
    else -> "Ogni giorno"
}
