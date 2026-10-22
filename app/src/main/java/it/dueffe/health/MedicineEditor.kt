package it.dueffe.health

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val weekdayLabels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicineEditor(initial: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var data by remember(initial) { mutableStateOf(JSONObject(initial.toString())) }
    var step by remember(initial) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var times by remember(initial) { mutableStateOf(initial.strings("orari").distinct().sortedBy(::timeToMinutes)) }
    var frequency by remember(initial) { mutableStateOf(if (initial.str("id").isBlank() && initial.str("frequenza").isBlank()) "" else medicineFrequencyType(initial)) }
    var intervalDays by remember(initial) { mutableIntStateOf(initial.optInt("intervalloGiorni", 2).coerceIn(2, 30)) }
    var endByDays by remember(initial) { mutableStateOf(initial.optInt("durataGiorni", 0) > 0) }
    var durationDays by remember(initial) { mutableIntStateOf(initial.optInt("durataGiorni", 7).coerceAtLeast(1)) }
    var weekdays by remember(initial) { mutableStateOf(initial.optJSONArray("giorniSettimana")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()) }
    var advances by remember(initial) { mutableStateOf(initial.optJSONObject("notifiche")?.optJSONArray("anticipo")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 }.toSet() } ?: emptySet()) }
    var timeIndex by remember { mutableIntStateOf(-2) }
    fun field(name: String, value: String) { data = JSONObject(data.toString()).put(name, value) }
    fun flag(name: String, value: Boolean) { data = JSONObject(data.toString()).put(name, value) }
    fun endAfter(days: Int) {
        durationDays = days.coerceAtLeast(1)
        val start = runCatching { LocalDate.parse(data.str("dataInizio").ifBlank { today() }) }.getOrDefault(LocalDate.now())
        data = JSONObject(data.toString()).put("durataGiorni", durationDays).put("dataFine", start.plusDays(durationDays.toLong()).toString())
    }
    val steps = listOf("Che farmaco è?", "Dose e frequenza", "Durata e promemoria", "Ultimi dettagli")
    val frequencyOptions = linkedMapOf("daily" to "Ogni giorno", "interval" to "Ogni N giorni", "weekdays" to "Giorni della settimana", "as_needed" to "Al bisogno")
    fun validateCurrentStep(): String = when (step) {
        0 -> if (data.str("nome").isBlank()) "Inserisci il nome del farmaco" else ""
        1 -> when {
            data.str("dosaggio").isBlank() -> "Indica la quantità da assumere a ogni dose"
            data.str("unita").isBlank() -> "Seleziona l'unità della dose"
            frequency.isBlank() -> "Scegli ogni quanto assumere il farmaco"
            frequency == "weekdays" && weekdays.isEmpty() -> "Scegli almeno un giorno della settimana"
            frequency != "as_needed" && times.isEmpty() -> "Aggiungi almeno un orario"
            else -> ""
        }
        2 -> when {
            !data.bool("senzaFine", true) && data.str("dataFine").isBlank() -> "Seleziona la data di fine"
            !data.bool("senzaFine", true) && data.str("dataFine") < data.str("dataInizio").ifBlank { today() } -> "La data di fine precede quella di inizio"
            else -> ""
        }
        else -> ""
    }
    fun save() {
        data.put("frequenzaTipo", frequency)
        data.put("frequenza", frequencyOptions[frequency] ?: "")
        data.put("intervalloGiorni", intervalDays)
        data.put("giorniSettimana", JSONArray(weekdays.sorted()))
        data.put("orari", JSONArray(if (frequency == "as_needed") emptyList<String>() else times.sortedBy(::timeToMinutes)))
        if (data.str("dataInizio").isBlank()) data.put("dataInizio", today())
        if (!data.has("senzaFine")) data.put("senzaFine", true)
        val notifications = data.optJSONObject("notifiche") ?: JSONObject()
        notifications.put("attive", frequency != "as_needed" && notifications.bool("attive", true))
        notifications.put("anticipo", JSONArray(advances.sorted()))
        data.put("notifiche", notifications)
        onSave(data)
    }
    fun back() { if (step > 0) { step--; error = "" } else onDismiss() }
    BackHandler(onBack = ::back)
    StepScaffold(
        title = if (initial.str("id").isBlank()) "Nuovo farmaco" else "Modifica farmaco",
        steps = steps, step = step, error = error,
        onBack = ::back,
        onNext = {
            error = validateCurrentStep()
            if (error.isBlank()) { if (step < steps.lastIndex) { step++; timeIndex = -2 } else save() }
        }
    ) { current ->
        when (current) {
            0 -> {
                FormHint("Inserisci i dati come riportati sulla confezione.")
                FormText("Nome farmaco *", data.str("nome")) { field("nome", it) }
                FormRadioChoice("Formulazione", data.str("formulazione"), listOf("Compressa", "Capsula", "Gocce", "Sciroppo", "Soluzione", "Iniezione", "Crema", "Cerotto", "Spray", "Supposta"), { field("formulazione", it) }, optional = true)
            }
            1 -> {
                FormHint("Indica quanto assumi ogni volta, seguendo la prescrizione.")
                NumberStepper("Quantità per dose *", data.str("dosaggio"), { field("dosaggio", it) }, min = 0.1)
                FormRadioChoice("Unità *", data.str("unita"), listOf("mg", "g", "mcg", "ml", "UI", "gocce", "compresse", "capsule"), { field("unita", it) })
                FormRadioChoice("Ogni quanto? *", frequencyOptions[frequency] ?: "", frequencyOptions.values.toList(), { choice -> frequency = frequencyOptions.entries.firstOrNull { it.value == choice }?.key ?: "" }, custom = false)
                AnimatedVisibility(frequency == "interval") {
                    NumberStepper("Ripeti ogni quanti giorni?", intervalDays.toString(), { intervalDays = (it.toIntOrNull() ?: 2).coerceIn(2, 30) }, min = 2.0)
                }
                AnimatedVisibility(frequency == "weekdays") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormLabel("Quali giorni?")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            weekdayLabels.forEachIndexed { index, day ->
                                val number = index + 1
                                SelectChip(day, number in weekdays) { weekdays = if (number in weekdays) weekdays - number else weekdays + number }
                            }
                        }
                    }
                }
                if (frequency == "as_needed") FormHint("Al bisogno: non saranno creati orari né promemoria automatici. Segnerai le assunzioni da Terapie.")
                AnimatedVisibility(frequency.isNotBlank() && frequency != "as_needed") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormLabel("A che ora?")
                        FormHint("Aggiungi un orario per ogni dose prevista nella giornata.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            times.forEachIndexed { index, time ->
                                InputChip(
                                    selected = true, onClick = { timeIndex = index },
                                    label = { Text(doseTimeLabel(time), fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                                    trailingIcon = { Icon(Icons.Rounded.Close, "Rimuovi $time", Modifier.size(18.dp).clipToCircleClick { times = times.filterIndexed { i, _ -> i != index } }) },
                                    shape = CircleShape, modifier = Modifier.height(42.dp)
                                )
                            }
                            AssistChip(onClick = { timeIndex = -1 }, label = { Text("Aggiungi orario") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, shape = CircleShape, modifier = Modifier.height(42.dp))
                        }
                    }
                }
                if (timeIndex >= -1) TimeSelectDialog(times.getOrNull(timeIndex) ?: "08:00", onDismiss = { timeIndex = -2 }) { value ->
                    times = (if (timeIndex >= 0) times.filterIndexed { i, _ -> i != timeIndex } + value else times + value).distinct().sortedBy(::timeToMinutes)
                    timeIndex = -2
                }
            }
            2 -> {
                FormHint("Scegli quando inizia la terapia e se ha una data di fine.")
                FormDate("Data inizio", data.str("dataInizio").ifBlank { today() }) { start ->
                    field("dataInizio", start)
                    if (endByDays) endAfter(durationDays)
                }
                FormSwitch("Terapia continuativa", data.bool("senzaFine", true), "Nessuna data di fine prevista.") { flag("senzaFine", it); if (!it && endByDays) endAfter(durationDays) }
                AnimatedVisibility(!data.bool("senzaFine", true)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FormRadioChoice("Come termina la terapia?", if (endByDays) "Dopo N giorni" else "Data precisa", listOf("Data precisa", "Dopo N giorni"), { choice ->
                            endByDays = choice == "Dopo N giorni"
                            if (endByDays) endAfter(durationDays) else field("durataGiorni", "")
                        }, custom = false)
                        if (endByDays) {
                            NumberStepper("Giorni dopo l'inizio", durationDays.toString(), { endAfter(it.toIntOrNull() ?: 1) }, min = 1.0)
                            FormHint("Ultimo giorno: ${displayDate(data.str("dataFine"))}")
                        } else FormDate("Data fine", data.str("dataFine")) { field("dataFine", it) }
                    }
                }
                if (frequency != "as_needed") {
                    val enabled = data.optJSONObject("notifiche")?.bool("attive", true) ?: true
                    FormSwitch("Promemoria all'orario della dose", enabled, "Una notifica ti ricorda ogni assunzione.") {
                        data = JSONObject(data.toString()).put("notifiche", (data.optJSONObject("notifiche") ?: JSONObject()).put("attive", it))
                    }
                    AnimatedVisibility(enabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormLabel("Vuoi anche un avviso prima?")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                (setOf(5, 10, 15, 30, 60) + advances).sorted().forEach { minutes ->
                                    SelectChip(if (minutes == 60) "1 ora" else "$minutes min", minutes in advances) { advances = if (minutes in advances) advances - minutes else advances + minutes }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val frequencySummary = when (frequency) {
                    "interval" -> "Ogni $intervalDays giorni"
                    "weekdays" -> weekdays.sorted().joinToString(", ") { weekdayLabels[it - 1] }
                    else -> frequencyOptions[frequency].orEmpty()
                }
                HeroSurface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        CapsuleIcon(androidx.compose.ui.graphics.Color.White, Modifier.size(40.dp), highlight = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(data.str("nome"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color.White)
                            Text("${data.str("dosaggio")} ${data.str("unita")} · $frequencySummary", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
                            if (frequency != "as_needed") Text(times.joinToString("  ·  ") { doseTimeLabel(it) }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
                FormHint("Questi dettagli sono facoltativi: servono per avvisarti quando la scorta sta finendo.")
                NumberStepper("Quantità in scorta", data.str("quantita"), { field("quantita", it) })
                FormRadioChoice("Unità scorta", data.str("quantitaUnita"), listOf("compresse", "capsule", "fiale", "flaconi", "gocce", "ml", "g", "cerotti"), { field("quantitaUnita", it) }, optional = true)
                NumberStepper("Avvisami sotto quota", data.str("soglia"), { field("soglia", it) })
                FormText("Note", data.str("note")) { field("note", it) }
            }
        }
    }
}

private fun Modifier.clipToCircleClick(onClick: () -> Unit): Modifier =
    this.then(Modifier.clip(CircleShape).clickable(onClick = onClick))
