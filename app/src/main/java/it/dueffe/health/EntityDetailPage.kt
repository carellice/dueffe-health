package it.dueffe.health

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private data class DetailField(val icon: ImageVector, val label: String, val value: String, val wide: Boolean = false)

@Composable
fun EntityDetailPage(
    key: String, item: JSONObject?, store: HealthStore,
    onBack: () -> Unit, onEdit: () -> Unit, onOpenAttachment: () -> Unit
) {
    BackHandler(onBack = onBack)
    val s = MaterialTheme.colorScheme
    val area = when (key) { "medicines" -> Area.Therapy; "documents" -> Area.Document; else -> Area.Visit }
    val c = areaColors(area)
    if (item == null) {
        Column(Modifier.fillMaxSize().background(s.background).statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack)
            EmptyState(Icons.Rounded.SearchOff, "Elemento non disponibile", "Potrebbe essere stato eliminato.")
        }
        return
    }
    val title: String
    val eyebrow: String
    val pills = mutableListOf<Triple<String, Color, Color>>()
    val fields = mutableListOf<DetailField>()
    var note = ""
    var heroIcon: @Composable () -> Unit = {}
    when (key) {
        "medicines" -> {
            title = item.str("nome").ifBlank { "Terapia" }
            eyebrow = "Terapia"
            val active = isMedicineActive(item)
            pills += when {
                item.bool("archiviata") -> Triple("Archiviata", s.onSurfaceVariant, s.surfaceContainerHigh)
                isMedicineExpired(item) -> Triple("Terminata", s.error, s.errorContainer)
                active -> Triple("Attiva", HealthStatus.success, HealthStatus.successContainer)
                else -> Triple("Non ancora iniziata", s.primary, s.primaryContainer)
            }
            val photo = remember(item.str("photo")) { item.str("photo").takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(store.attachmentPath(it).path) }.getOrNull() } }
            heroIcon = { if (photo != null) Image(photo.asImageBitmap(), "Foto di $title", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else CapsuleIcon(Color.White, Modifier.size(46.dp), highlight = c.accent) }
            val prn = medicineFrequencyType(item) == "as_needed"
            fields += DetailField(Icons.Rounded.Scale, "Dose", "${item.str("dosaggio")} ${item.str("unita")}".trim().ifBlank { "—" })
            fields += DetailField(Icons.Rounded.Repeat, "Frequenza", medicineFrequencyDescription(item))
            fields += DetailField(Icons.Rounded.Schedule, "Orari", if (prn) "Al bisogno" else item.strings("orari").joinToString("  ·  ") { doseTimeLabel(it) }.ifBlank { "—" }, wide = true)
            fields += DetailField(Icons.Rounded.Medication, "Formulazione", item.str("formulazione").ifBlank { "—" })
            fields += DetailField(Icons.Rounded.Inventory2, "Scorta", "${item.str("quantita")} ${item.str("quantitaUnita").ifBlank { item.str("unita") }}".trim().ifBlank { "—" })
            fields += DetailField(Icons.Rounded.PlayCircle, "Inizio", item.str("dataInizio").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "—")
            fields += DetailField(Icons.Rounded.Flag, "Fine", if (item.bool("senzaFine", true)) "Senza scadenza" else item.str("dataFine").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "—")
            if (!prn) {
                val notifications = item.optJSONObject("notifiche")
                val enabled = notifications?.bool("attive", true) ?: true
                val advances = notifications?.optJSONArray("anticipo")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) } }.orEmpty()
                fields += DetailField(Icons.Rounded.NotificationsActive, "Promemoria",
                    if (!enabled) "Disattivati" else "All'orario della dose" + if (advances.isNotEmpty()) " e ${advances.sorted().joinToString(", ") { if (it == 60) "1 ora" else "$it min" }} prima" else "", wide = true)
            }
            note = item.str("note")
        }
        "appointments" -> {
            title = item.str("tipo").ifBlank { "Visita medica" }
            eyebrow = item.str("categoria").ifBlank { "Visita" }
            pills += if (item.bool("completata")) Triple("Effettuata", HealthStatus.success, HealthStatus.successContainer)
            else Triple(relativeDayLabel(item.str("data")), Color.White, Color.White.copy(alpha = 0.22f))
            heroIcon = { StethoscopeIcon(Color.White, Modifier.size(44.dp), hole = c.accent) }
            val doctor = item.str("medico").ifBlank { listOf(item.str("medicoNome"), item.str("medicoCognome")).filter { it.isNotBlank() }.joinToString(" ") }
            fields += DetailField(Icons.Rounded.CalendarMonth, "Data", item.str("data").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "—")
            fields += DetailField(Icons.Rounded.Schedule, "Ora", item.str("ora").ifBlank { "—" })
            fields += DetailField(Icons.Rounded.LocationOn, "Luogo", item.str("luogo").ifBlank { "Non indicato" }, wide = true)
            fields += DetailField(Icons.Rounded.Person, "Medico", doctor.ifBlank { "Non indicato" }, wide = true)
            val reports = item.optJSONObject("refertiReminder")
            if (reports != null && reports.str("data").isNotBlank())
                fields += DetailField(Icons.Rounded.Description, "Ritiro referti", listOf(
                    displayDate(reports.str("data")), if (reports.bool("ritirati")) "ritirati" else "", reports.str("nota")
                ).filter { it.isNotBlank() }.joinToString(" · "), wide = true)
            note = item.str("note")
        }
        "bookingReminders" -> {
            title = item.str("tipoVisita").ifBlank { "Visita da prenotare" }
            eyebrow = "Da prenotare"
            pills += if (item.bool("prenotata")) Triple("Prenotata", HealthStatus.success, HealthStatus.successContainer) else Triple("In attesa", Color.White, Color.White.copy(alpha = 0.22f))
            heroIcon = { Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(42.dp), tint = Color.White) }
            fields += DetailField(Icons.Rounded.DateRange, "Periodo desiderato", item.str("periodo").takeIf { it.isNotBlank() }?.let(::periodLabel) ?: "Non indicato")
            fields += DetailField(Icons.Rounded.Alarm, "Promemoria", displayDate(item.str("dataPromemoria")))
            note = item.str("nota")
        }
        else -> {
            title = item.str("nome").ifBlank { "Documento" }
            val category = item.str("categoria").ifBlank { "Altro" }
            eyebrow = category
            pills += Triple(documentFormatLabel(item), Color.White, Color.White.copy(alpha = 0.22f))
            heroIcon = { Icon(documentCategoryIcon(category), null, Modifier.size(42.dp), tint = Color.White) }
            val linkedVisit = store.list("appointments").firstOrNull { it.str("id") == item.str("visitaId") }
            fields += DetailField(Icons.Rounded.CalendarMonth, "Data", item.str("data").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "—")
            fields += DetailField(documentCategoryIcon(category), "Categoria", category)
            if (linkedVisit != null) fields += DetailField(Icons.Rounded.EventAvailable, "Visita collegata", "${linkedVisit.str("tipo")} · ${displayDate(linkedVisit.str("data"))}", wide = true)
            note = item.str("note")
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(s.background), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HeroSurface(
                Modifier.fillMaxWidth().healthSharedBounds("$key-${item.str("id")}"),
                brush = heroBrush(c.accent, if (area == Area.Therapy) s.secondary else s.primary),
                shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
                glow = c.accent
            ) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack, container = Color.White.copy(alpha = 0.2f), tint = Color.White)
                        Spacer(Modifier.weight(1f))
                        RoundIconButton(Icons.Rounded.Edit, "Modifica", onEdit, container = Color.White.copy(alpha = 0.2f), tint = Color.White)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Box(Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.2f)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { heroIcon() }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(eyebrow.uppercase(), fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                            Text(title, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 3)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pills.forEach { (text, fg, bg) -> StatusPill(text, fg, bg) } }
                        }
                    }
                }
            }
        }
        val rows = mutableListOf<List<DetailField>>()
        val pending = mutableListOf<DetailField>()
        fields.forEach { field ->
            if (field.wide) { if (pending.isNotEmpty()) { rows += pending.toList(); pending.clear() }; rows += listOf(field) }
            else { pending += field; if (pending.size == 2) { rows += pending.toList(); pending.clear() } }
        }
        if (pending.isNotEmpty()) rows += pending.toList()
        rows.forEachIndexed { index, row ->
            item {
                Row(Modifier.padding(horizontal = 20.dp).enterAnimation(index + 1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { field -> DetailTile(field, c, Modifier.weight(1f)) }
                    if (row.size == 1 && !row.first().wide) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (key == "documents") item {
            AttachmentCard(item, store, c, Modifier.padding(horizontal = 20.dp).enterAnimation(rows.size + 1), onOpenAttachment)
        }
        if (note.isNotBlank()) item {
            HealthCard(Modifier.padding(horizontal = 20.dp).enterAnimation(rows.size + 2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.StickyNote2, null, Modifier.size(18.dp), tint = c.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(note, fontSize = 15.sp, lineHeight = 21.sp, color = s.onSurface)
            }
        }
        item {
            Button(
                onClick = onEdit, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(56.dp).enterAnimation(rows.size + 3)
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("Modifica", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailTile(field: DetailField, c: AreaColors, modifier: Modifier) {
    val s = MaterialTheme.colorScheme
    HealthCard(modifier, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(field.icon, c.accent, c.container, 34.dp, 11.dp)
            Spacer(Modifier.width(10.dp))
            Text(field.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = s.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(field.value, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
    }
}

@Composable
private fun AttachmentCard(item: JSONObject, store: HealthStore, c: AreaColors, modifier: Modifier, onOpen: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val attachment = item.str("attachment")
    val hasAttachment = attachment.isNotBlank()
    val format = documentFormatLabel(item)
    val fileName = item.str("fileName").ifBlank { attachment.substringAfterLast('/') }
    val preview = remember(attachment) { if (hasAttachment && format == "Immagine") runCatching { BitmapFactory.decodeFile(store.attachmentPath(attachment).path) }.getOrNull() else null }
    val visual = documentVisual(item)
    HealthCard(modifier, onClick = if (hasAttachment) onOpen else null, padding = PaddingValues(0.dp)) {
        if (preview != null) Image(preview.asImageBitmap(), "Anteprima $fileName", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(visual.icon, visual.accent, visual.container, 48.dp, 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (hasAttachment) fileName.ifBlank { "Allegato" } else "Nessun allegato", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 2)
                Text(if (hasAttachment) "$format · tocca per aprire" else "Allega un file o una foto dal menu del documento.", fontSize = 13.sp, color = s.onSurfaceVariant)
            }
            if (hasAttachment) Icon(Icons.Rounded.OpenInNew, null, tint = c.accent)
        }
    }
}

internal fun documentFormatLabel(doc: JSONObject): String {
    if (doc.str("attachment").isBlank()) return "Nessun allegato"
    val mime = doc.str("fileType").lowercase()
    val extension = doc.str("fileName").ifBlank { doc.str("attachment") }.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/pdf" || extension == "pdf" -> "PDF"
        mime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp") -> "Immagine"
        mime.contains("msword") || mime.contains("wordprocessingml") || extension in setOf("doc", "docx") -> "Word"
        else -> "Altro file"
    }
}
