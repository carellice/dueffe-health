package it.dueffe.health

import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GuidedEntityEditor(
    entityKey: String,
    initial: JSONObject,
    store: HealthStore,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit
) {
    val context = LocalContext.current
    var data by remember(initial) { mutableStateOf(JSONObject(initial.toString())) }
    var step by remember(initial) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var stagedAttachment by remember { mutableStateOf("") }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    fun discardStaged() {
        if (stagedAttachment.isNotBlank()) store.attachmentPath(stagedAttachment).delete()
        stagedAttachment = ""
    }
    fun dismiss() { discardStaged(); pendingCameraFile?.delete(); onDismiss() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            val file = store.attachmentFile("draft_${UUID.randomUUID()}", extension)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("File non leggibile")
            discardStaged()
            stagedAttachment = file.name
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "Allegato"
            data = JSONObject(data.toString()).put("attachment", file.name).put("fileName", name).put("fileType", mime)
            error = ""
        }.onFailure { error = "Allegato non leggibile" }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCameraFile
        if (saved && file != null) {
            discardStaged()
            stagedAttachment = file.name
            data = JSONObject(data.toString()).put("attachment", file.name).put("fileName", "Foto ${today()}.jpg").put("fileType", "image/jpeg")
            error = ""
        } else file?.delete()
        pendingCameraFile = null
    }
    var visitAdvances by remember(initial) {
        mutableStateOf(initial.optJSONObject("promemoria")?.optJSONArray("anticipo")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 }.toSet()
        } ?: setOf(1440))
    }
    fun field(name: String, value: String) { data = JSONObject(data.toString()).put(name, value) }
    fun reminderField(name: String, value: String) {
        data = JSONObject(data.toString()).put("refertiReminder", (data.optJSONObject("refertiReminder") ?: JSONObject()).put(name, value))
    }
    val title = when (entityKey) { "appointments" -> "visita"; "bookingReminders" -> "promemoria prenotazione"; else -> "documento" }
    val steps = when (entityKey) {
        "appointments" -> if (data.bool("completata")) listOf("Tipo di visita", "Quando e dove") else listOf("Tipo di visita", "Quando e dove", "Promemoria e note")
        "bookingReminders" -> listOf("Visita da prenotare", "Quando ricordartelo")
        else -> listOf("Documento", "Collegamenti e allegato")
    }
    fun validateStep(): String = when (entityKey) {
        "appointments" -> when (step) {
            0 -> if (data.str("tipo").isBlank()) "Indica il tipo di visita" else ""
            1 -> if (runCatching { LocalDate.parse(data.str("data")) }.isFailure) "Scegli la data della visita" else ""
            else -> when {
                (data.optJSONObject("promemoria")?.bool("attivo", true) ?: true) && visitAdvances.isEmpty() -> "Scegli almeno un anticipo o disattiva il promemoria"
                data.optJSONObject("refertiReminder")?.str("data")?.isNotBlank() == true && data.optJSONObject("refertiReminder")!!.str("data") < data.str("data") -> "Il ritiro referti precede la visita"
                else -> ""
            }
        }
        "bookingReminders" -> when (step) {
            0 -> when {
                data.str("tipoVisita").isBlank() -> "Indica la visita da prenotare"
                data.str("periodo").isNotBlank() && runCatching { YearMonth.parse(data.str("periodo")) }.isFailure -> "Scegli un periodo valido"
                else -> ""
            }
            else -> when {
                runCatching { LocalDate.parse(data.str("dataPromemoria")) }.isFailure -> "Scegli la data del promemoria"
                data.str("dataPromemoria") < today() -> "Scegli una data da oggi in poi"
                else -> ""
            }
        }
        else -> if (step == 0 && data.str("nome").isBlank()) "Inserisci il nome del documento" else ""
    }
    fun save() {
        when (entityKey) {
            "appointments" -> {
                if (data.str("ora").isBlank()) data.put("ora", "09:00")
                data.put("medico", listOf(data.str("medicoNome"), data.str("medicoCognome")).filter { it.isNotBlank() }.joinToString(" "))
                val reminder = data.optJSONObject("promemoria") ?: JSONObject().put("attivo", true)
                if (data.bool("completata")) reminder.put("attivo", false)
                data.put("promemoria", reminder.put("anticipo", JSONArray(visitAdvances.sorted())))
            }
            "documents" -> {
                if (data.str("data").isBlank()) data.put("data", today())
                if (data.str("categoria").isBlank()) data.put("categoria", "Altro")
            }
        }
        onSave(data)
    }
    BackHandler { if (step > 0) { step--; error = "" } else dismiss() }
    StepScaffold(
        title = if (initial.str("id").isBlank()) "Nuovo $title" else "Modifica $title",
        steps = steps, step = step, error = error,
        onBack = { if (step > 0) { step--; error = "" } else dismiss() },
        onNext = {
            error = validateStep()
            if (error.isBlank()) { if (step < steps.lastIndex) step++ else save() }
        }
    ) { step ->
            when (entityKey) {
                "appointments" -> when (step) {
                    0 -> {
                        FormHint("Descrivi la visita o l'esame da fare.")
                        FormRadioChoice("Tipo visita *", data.str("tipo"), listOf("Visita medica", "Visita specialistica", "Controllo", "Esame del sangue", "Ecografia", "Radiografia", "Vaccinazione"), { field("tipo", it) })
                        FormRadioChoice("Categoria", data.str("categoria"), listOf("Visita", "Esame", "Controllo", "Analisi", "Altro"), { field("categoria", it) }, optional = true)
                    }
                    1 -> {
                        FormHint("Seleziona data e ora. Puoi aggiungere il luogo e il medico se li conosci.")
                        FormDate("Data visita *", data.str("data")) { field("data", it) }
                        FormTime("Ora visita", data.str("ora").ifBlank { "09:00" }) { field("ora", it) }
                        FormText("Luogo", data.str("luogo")) { field("luogo", it) }
                        FormText("Nome medico", data.str("medicoNome")) { field("medicoNome", it) }
                        FormText("Cognome medico", data.str("medicoCognome")) { field("medicoCognome", it) }
                        FormSwitch("Visita già effettuata", data.bool("completata"), "Non riceverai promemoria per questa visita.") { data = JSONObject(data.toString()).put("completata", it) }
                    }
                    else -> {
                        val enabled = data.optJSONObject("promemoria")?.bool("attivo", true) ?: true
                        FormSwitch("Ricordami la visita", enabled, "Ricevi una notifica prima dell'appuntamento.") { data = JSONObject(data.toString()).put("promemoria", (data.optJSONObject("promemoria") ?: JSONObject()).put("attivo", it)) }
                        AnimatedVisibility(enabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FormLabel("Quanto tempo prima?")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (setOf(15, 60, 1440, 2880, 10080) + visitAdvances).sorted().forEach { minutes ->
                                        val label = when (minutes) { 60 -> "1 ora"; 1440 -> "1 giorno"; 2880 -> "2 giorni"; 10080 -> "1 settimana"; else -> "$minutes min" }
                                        SelectChip(label, minutes in visitAdvances) { visitAdvances = if (minutes in visitAdvances) visitAdvances - minutes else visitAdvances + minutes }
                                    }
                                }
                            }
                        }
                        FormDate("Ritiro referti", data.optJSONObject("refertiReminder")?.str("data") ?: "") { reminderField("data", it) }
                        FormText("Note ritiro referti", data.optJSONObject("refertiReminder")?.str("nota") ?: "") { reminderField("nota", it) }
                        FormText("Note visita", data.str("note")) { field("note", it) }
                    }
                }
                "bookingReminders" -> if (step == 0) {
                    FormHint("Indica cosa devi prenotare e il mese in cui vorresti farlo.")
                    FormRadioChoice("Tipo visita *", data.str("tipoVisita"), listOf("Visita medica", "Visita specialistica", "Controllo", "Esame del sangue", "Ecografia", "Radiografia"), { field("tipoVisita", it) })
                    FormMonth("Periodo desiderato", data.str("periodo")) { field("periodo", it) }
                } else {
                    FormHint("Scegli il giorno in cui vuoi ricevere il promemoria.")
                    FormDate("Data promemoria", data.str("dataPromemoria")) { field("dataPromemoria", it) }
                    FormText("Note", data.str("nota")) { field("nota", it) }
                }
                "documents" -> if (step == 0) {
                    FormHint("Dai un nome al documento e scegli la sua categoria.")
                    FormText("Nome documento *", data.str("nome")) { field("nome", it) }
                    FormRadioChoice("Categoria", data.str("categoria").ifBlank { "Altro" }, listOf("Analisi", "Referti", "Ricette", "Altro"), { field("categoria", it) })
                    FormDate("Data documento", data.str("data").ifBlank { today() }) { field("data", it) }
                } else {
                    FormHint("Se vuoi, collega il documento a una visita e aggiungi un allegato.")
                    val visits = store.list("appointments")
                    if (visits.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = it }) {
                            OutlinedTextField(visits.firstOrNull { it.str("id") == data.str("visitaId") }?.str("tipo") ?: "Nessuna visita collegata", {}, readOnly = true, label = { Text("Visita collegata") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(18.dp), colors = formFieldColors(), modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true))
                            ExposedDropdownMenu(expanded, { expanded = false }, shadowElevation = 0.dp) {
                                DropdownMenuItem(text = { Text("Nessuna") }, onClick = { field("visitaId", ""); expanded = false })
                                visits.forEach { visit -> DropdownMenuItem(text = { Text("${visit.str("tipo")} · ${displayDate(visit.str("data"))}") }, onClick = { field("visitaId", visit.str("id")); expanded = false }) }
                            }
                        }
                    }
                    FormText("Note", data.str("note")) { field("note", it) }
                    FormLabel("Allegato")
                    AnimatedVisibility(data.str("attachment").isNotBlank()) {
                        FieldTile(Icons.Rounded.AttachFile, "Allegato pronto", data.str("fileName").ifBlank { "File" }, onClick = {}, trailing = {
                            IconButton(onClick = { discardStaged(); data = JSONObject(data.toString()).put("attachment", "").put("fileName", "").put("fileType", "") }) { Icon(Icons.Rounded.Close, "Rimuovi allegato") }
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AttachOption(Icons.Rounded.UploadFile, "Scegli un file", Modifier.weight(1f)) { filePicker.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }
                        AttachOption(Icons.Rounded.PhotoCamera, "Scatta una foto", Modifier.weight(1f)) {
                            val file = store.attachmentFile("draft_${UUID.randomUUID()}", "jpg")
                            pendingCameraFile = file
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        }
                    }
                }
            }
    }
}

@Composable
private fun FormMonth(label: String, value: String, onChange: (String) -> Unit) {
    val months = listOf("Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")
    val parts = value.split("-")
    val year = parts.firstOrNull()?.takeIf { it.matches(Regex("\\d{1,4}")) } ?: LocalDate.now().year.toString()
    val month = parts.getOrNull(1)?.toIntOrNull()
    FormLabel(label)
    FormChoice("Mese", month?.let { months.getOrNull(it - 1) } ?: "", months) { selected ->
        val number = months.indexOf(selected) + 1
        onChange(if (number > 0) "$year-${number.toString().padStart(2, '0')}" else "")
    }
    FormText("Anno", year, KeyboardType.Number) { typed ->
        if (typed.all(Char::isDigit) && typed.length <= 4) onChange(if (month != null) "$typed-${month.toString().padStart(2, '0')}" else typed)
    }
}

@Composable
private fun AttachOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = areaColors(Area.Document)
    HealthCard(modifier, onClick = onClick, padding = PaddingValues(vertical = 18.dp, horizontal = 12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(icon, c.accent, c.container, 48.dp, 16.dp)
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
