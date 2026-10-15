package it.dueffe.health

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailPage(
    key: String, item: JSONObject?, store: HealthStore, dark: Boolean,
    onBack: () -> Unit, onEdit: () -> Unit, onOpenAttachment: () -> Unit
) {
    BackHandler(onBack = onBack)
    val c = homeColors(dark)
    val title = when (key) {
        "medicines" -> "Dettaglio terapia"
        "appointments" -> "Dettaglio visita"
        "bookingReminders" -> "Dettaglio prenotazione"
        else -> "Dettaglio documento"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
            )
        }
    ) { padding ->
        if (item == null) {
            Column(Modifier.fillMaxSize().padding(padding).background(c.background).padding(20.dp)) {
                Text("Elemento non disponibile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Potrebbe essere stato eliminato.", color = c.muted)
            }
        } else if (key == "appointments") {
            AppointmentDetailContent(item, c, Modifier.padding(padding), onEdit)
        } else if (key == "medicines") {
            MedicineDetailContent(item, store, c, Modifier.padding(padding), onEdit)
        } else if (key == "documents") {
            DocumentDetailContent(item, store, c, Modifier.padding(padding), onEdit, onOpenAttachment)
        } else {
            val name = when (key) {
                "bookingReminders" -> item.str("tipoVisita")
                else -> item.str("nome")
            }.ifBlank { title }
            val fields = when (key) {
                "bookingReminders" -> listOf(
                    "Periodo desiderato" to item.str("periodo"),
                    "Data promemoria" to displayDate(item.str("dataPromemoria")),
                    "Stato" to if (item.bool("prenotata")) "Prenotata" else "Da prenotare",
                    "Note" to item.str("nota")
                )
                else -> {
                    val linkedVisit = store.list("appointments").firstOrNull { it.str("id") == item.str("visitaId") }
                    listOf(
                        "Categoria" to item.str("categoria"),
                        "Data" to displayDate(item.str("data")),
                        "Formato" to documentFormatLabel(item),
                        "Allegato" to item.str("fileName"),
                        "Visita collegata" to linkedVisit?.str("tipo").orEmpty(),
                        "Note" to item.str("note")
                    )
                }
            }.filter { it.second.isNotBlank() }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).background(c.background),
                contentPadding = PaddingValues(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(18.dp)).padding(18.dp)) {
                        Text(name, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        Spacer(Modifier.height(13.dp))
                        fields.forEachIndexed { index, (label, value) ->
                            if (index > 0) Spacer(Modifier.height(13.dp))
                            Text(label, fontSize = 12.sp, color = c.muted)
                            Text(value, fontSize = 16.sp, lineHeight = 21.sp, color = c.ink)
                        }
                    }
                }
                if (key == "documents" && item.str("attachment").isNotBlank()) item {
                    Button(onClick = onOpenAttachment, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = c.blue)) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apri allegato")
                    }
                }
                item {
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = c.blue)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Modifica")
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineDetailContent(item: JSONObject, store: HealthStore, c: HomeColors, modifier: Modifier, onEdit: () -> Unit) {
    val archived = item.bool("archiviata")
    val expired = !item.bool("senzaFine", true) && item.str("dataFine").isNotBlank() && item.str("dataFine") < today()
    val active = !archived && !expired && (item.str("dataInizio").isBlank() || item.str("dataInizio") <= today())
    val status = when {
        archived -> "Archiviata"
        expired -> "Scaduta"
        active -> "Attiva"
        else -> "Non iniziata"
    }
    val photo = item.str("photo").takeIf { it.isNotBlank() }?.let {
        runCatching { BitmapFactory.decodeFile(store.attachmentPath(it).path) }.getOrNull()
    }
    val dose = "${item.str("dosaggio")} ${item.str("unita")}".trim().ifBlank { "Non specificata" }
    val times = item.strings("orari").joinToString(", ").ifBlank {
        if (medicineFrequencyType(item) == "as_needed") "Al bisogno" else "Non specificati"
    }
    val stock = "${item.str("quantita")} ${item.str("quantitaUnita").ifBlank { item.str("unita") }}"
        .trim().ifBlank { "Non specificata" }

    LazyColumn(
        modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().healthSharedBounds("medicines-${item.str("id")}").clip(RoundedCornerShape(20.dp))
                    .background(c.iconBlue)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(c.lilac),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        Image(photo.asImageBitmap(), "Foto di ${item.str("nome")}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else CapsuleIcon(c.blue, Modifier.size(52.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.str("nome").ifBlank { "Terapia" }, fontSize = 27.sp, lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    MedicineStatusChip(status, active)
                }
            }
        }
        item {
            VisitDetailRow(Icons.Default.Medication, "Formulazione", item.str("formulazione").ifBlank { "Non specificata" },
                Color(0xFFF1E6FF), Color(0xFF8241D4), showChevron = false,
                customIcon = { CapsuleIcon(c.blue, Modifier.size(27.dp)) })
        }
        item {
            VisitDetailRow(Icons.Default.Block, "Dose", dose,
                Color(0xFFE5F1FF), Color(0xFF1B73C9), showChevron = false)
        }
        item {
            VisitDetailRow(Icons.Default.Sync, "Frequenza", medicineFrequencyDescription(item),
                Color(0xFFE2F8EF), Color(0xFF079765), showChevron = false)
        }
        item {
            VisitDetailRow(Icons.Default.Schedule, "Orari", times,
                Color(0xFFFFF0DF), Color(0xFFF17808))
        }
        item {
            VisitDetailRow(Icons.Default.CalendarMonth, "Data di inizio",
                item.str("dataInizio").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "Non specificata",
                Color(0xFFE5F1FF), Color(0xFF1B73C9), showChevron = false)
        }
        item {
            VisitDetailRow(Icons.Default.EventBusy, "Data di fine",
                if (item.bool("senzaFine", true)) "Senza scadenza" else item.str("dataFine").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "Non specificata",
                Color(0xFFFFE7EB), Color(0xFFD92848), showChevron = false)
        }
        item {
            VisitDetailRow(Icons.Default.Inventory2, "Scorta", stock,
                Color(0xFFFFF0DC), Color(0xFFB87608))
        }
        item {
            VisitDetailRow(Icons.Default.Shield, "Stato", status,
                Color(0xFFE1F8EC), Color(0xFF079765), showChevron = false,
                trailing = {
                    Switch(
                        checked = active,
                        onCheckedChange = { onEdit() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF258BEB),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFB8BEC9)
                        )
                    )
                })
        }
        if (item.str("note").isNotBlank()) item {
            VisitDetailRow(Icons.Default.Description, "Note", item.str("note"),
                Color(0xFFF1E7FF), Color(0xFF6D24CA), showChevron = false)
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(23.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Modifica", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun MedicineStatusChip(label: String, active: Boolean) {
    val tint = if (active) Color(0xFF07955F) else Color(0xFF687187)
    val background = if (active) Color(0xFFDDF8EC) else Color(0xFFE9ECF2)
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun DocumentDetailContent(
    item: JSONObject,
    store: HealthStore,
    c: HomeColors,
    modifier: Modifier,
    onEdit: () -> Unit,
    onOpenAttachment: () -> Unit
) {
    val name = item.str("nome").ifBlank { "Documento" }
    val category = item.str("categoria").ifBlank { "Altro" }
    val attachment = item.str("attachment")
    val hasAttachment = attachment.isNotBlank()
    val format = documentFormatLabel(item)
    val fileName = item.str("fileName").ifBlank { attachment.substringAfterLast('/') }
    val linkedVisit = store.list("appointments").firstOrNull { it.str("id") == item.str("visitaId") }
    val preview = if (hasAttachment && format == "Immagine") {
        runCatching { BitmapFactory.decodeFile(store.attachmentPath(attachment).path) }.getOrNull()
    } else null

    LazyColumn(
        modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().healthSharedBounds("documents-${item.str("id")}").clip(RoundedCornerShape(20.dp))
                    .background(c.iconBlue)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(c.lilac),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, Modifier.size(49.dp), tint = c.blue)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
                        color = c.ink, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(18.dp)).background(c.lilac)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = c.blue)
                        Spacer(Modifier.width(6.dp))
                        Text(category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                    }
                }
            }
        }
        item {
            VisitDetailRow(Icons.Default.CalendarMonth, "Data",
                item.str("data").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "Non specificata",
                Color(0xFFE5F1FF), Color(0xFF1B73C9))
        }
        item {
            VisitDetailRow(Icons.Default.Description, "Formato", format,
                Color(0xFFE2F8EC), Color(0xFF09A76D), showChevron = false)
        }
        item {
            VisitDetailRow(Icons.Default.MonitorHeart, "Visita collegata",
                linkedVisit?.str("tipo")?.ifBlank { "Visita medica" } ?: "Nessuna visita collegata",
                Color(0xFFFFE7EF), Color(0xFFE32660), showChevron = linkedVisit != null,
                customIcon = { StethoscopeIcon(c.blue, Modifier.size(29.dp)) })
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)
            ) {
                Text("Allegato", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(13.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 174.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawRoundRect(
                                color = Color(0xFFC9D9ED),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                            )
                        }
                        .then(if (hasAttachment) Modifier.clickable(onClick = onOpenAttachment) else Modifier)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (preview != null) {
                        Image(preview.asImageBitmap(), "Anteprima $fileName",
                            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(13.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(12.dp))
                    } else {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF0F2FA)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Description, null, Modifier.size(32.dp), tint = if (hasAttachment) c.blue else c.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(if (hasAttachment) fileName.ifBlank { "Allegato" } else "Nessun allegato",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(if (hasAttachment) "$format · Tocca per aprire" else "Questo documento non ha file allegati.",
                        fontSize = 13.sp, color = c.muted)
                }
            }
        }
        if (item.str("note").isNotBlank()) item {
            VisitDetailRow(Icons.Default.Description, "Note", item.str("note"),
                Color(0xFFF1E7FF), Color(0xFF6D24CA), showChevron = false)
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(23.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Modifica", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun AppointmentDetailContent(item: JSONObject, c: HomeColors, modifier: Modifier, onEdit: () -> Unit) {
    val completed = item.bool("completata")
    val status = if (completed) "Effettuata" else "Da effettuare"
    val date = runCatching { LocalDate.parse(item.str("data")) }.getOrNull()
    val doctor = item.str("medico").ifBlank {
        listOf(item.str("medicoNome"), item.str("medicoCognome")).filter { it.isNotBlank() }.joinToString(" ")
    }.ifBlank { "Non specificato" }
    LazyColumn(
        modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().healthSharedBounds("appointments-${item.str("id")}").clip(RoundedCornerShape(20.dp))
                    .background(c.iconBlue)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(c.lilac),
                    contentAlignment = Alignment.Center
                ) {
                    StethoscopeIcon(c.blue, Modifier.size(50.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.str("tipo").ifBlank { "Visita medica" }, fontSize = 24.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2)
                    Spacer(Modifier.height(9.dp))
                    VisitStatusChip(status, completed)
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.size(width = 79.dp, height = 78.dp).clip(RoundedCornerShape(18.dp)).background(c.lilac),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(date?.dayOfMonth?.toString() ?: "–", fontSize = 28.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold, color = c.blue)
                    Text(date?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN)) ?: "Data assente",
                        fontSize = 12.sp, color = c.blue)
                }
            }
        }
        item {
            VisitDetailRow(Icons.Default.Sell, "Categoria", item.str("categoria").ifBlank { "Non specificata" },
                Color(0xFFF1E6FF), Color(0xFF6D24CA))
        }
        item {
            VisitDetailRow(Icons.Default.CalendarMonth, "Data", item.str("data").takeIf { it.isNotBlank() }?.let(::displayDate) ?: "Non specificata",
                Color(0xFFE5F1FF), Color(0xFF1B73C9))
        }
        item {
            VisitDetailRow(Icons.Default.Schedule, "Ora", item.str("ora").ifBlank { "Non specificata" },
                Color(0xFFFFF0DF), Color(0xFFF17808))
        }
        item {
            VisitDetailRow(Icons.Default.LocationOn, "Luogo", item.str("luogo").ifBlank { "Non specificato" },
                Color(0xFFFFE7F0), Color(0xFFD91B61))
        }
        item {
            VisitDetailRow(Icons.Default.Person, "Medico", doctor,
                Color(0xFFE1F8F3), Color(0xFF0A9C86))
        }
        item {
            VisitDetailRow(Icons.Default.MonitorHeart, "Stato", status,
                Color(0xFFE5F2FF), Color(0xFF1978D2), trailing = { VisitStatusChip(status, completed) })
        }
        item {
            VisitDetailRow(Icons.Default.Description, "Note", item.str("note").ifBlank { "Nessuna nota inserita" },
                Color(0xFFF1E7FF), Color(0xFF6D24CA), mutedValue = item.str("note").isBlank())
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(23.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Modifica", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun VisitDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconBackground: Color,
    iconColor: Color,
    mutedValue: Boolean = false,
    showChevron: Boolean = true,
    customIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 65.dp).clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerLow).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (customIcon != null) customIcon() else Icon(icon, null, Modifier.size(27.dp), tint = scheme.primary)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = if (mutedValue) FontWeight.Normal else FontWeight.SemiBold,
                color = if (mutedValue) scheme.onSurfaceVariant else scheme.onSurface, maxLines = 2)
        }
        if (trailing != null) trailing()
        else if (showChevron) Icon(Icons.Default.ChevronRight, null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun VisitStatusChip(label: String, completed: Boolean) {
    val tint = if (completed) Color(0xFF07955F) else Color(0xFF1767C9)
    val background = if (completed) Color(0xFFDDF8EC) else Color(0xFFE6F0FF)
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.Schedule, null, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
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
