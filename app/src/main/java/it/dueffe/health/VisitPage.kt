package it.dueffe.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VisitPage(
    store: HealthStore, revision: Int, dark: Boolean,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onSettings: () -> Unit, onProfiles: () -> Unit,
    onOpen: (JSONObject, Boolean) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit,
    onBooking: (JSONObject?) -> Unit, onBookNow: (JSONObject) -> Unit
) {
    val c = homeColors(dark)
    val appointments = remember(revision, store.activeId) { store.list("appointments") }
    val bookingMode = filter == "Da prenotare"
    val bookingReminders = remember(revision, store.activeId) { store.list("bookingReminders") }
    val entries = if (bookingMode) {
        bookingReminders.filter { !it.bool("prenotata") && it.str("tipoVisita").contains(search, ignoreCase = true) }
            .sortedBy { it.str("dataPromemoria") }
    } else {
        appointments.filter { visit ->
            visit.str("tipo").contains(search, ignoreCase = true) && when (filter) {
                "Prossime" -> visit.str("data") >= today() && !visit.bool("completata")
                "Passate" -> visit.str("data") < today() || visit.bool("completata")
                else -> true
            }
        }.sortedBy { it.str("data") }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 10.dp, bottom = 105.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HealthSearchBar(search, setSearch, "Cerca visite…")
        }
        item {
            HealthFilterBar(
                listOf(
                    HealthFilterOption("Tutti"),
                    HealthFilterOption("Prossime", Icons.Default.CalendarMonth),
                    HealthFilterOption("Passate", Icons.Default.EventAvailable),
                    HealthFilterOption("Da prenotare", Icons.Default.Notifications)
                ), filter, setFilter
            )
        }
        if (entries.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(18.dp)).padding(20.dp)) {
                Text(if (search.isNotBlank()) "Nessun risultato" else if (bookingMode) "Nessuna visita da prenotare" else "Nessuna visita", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(if (bookingMode) "Aggiungi un promemoria per ricordarti cosa prenotare." else "Usa + per aggiungere la tua prima visita.", fontSize = 14.sp, color = c.muted)
            }
        }
        items(entries, key = { it.str("id") }) { visit ->
            VisitCard(visit, bookingMode, c, dark, onOpen, onEdit, onDelete, onAction, onBooking, onBookNow)
        }
    }
}

@Composable
private fun VisitCard(
    visit: JSONObject, bookingMode: Boolean, c: HomeColors, dark: Boolean,
    onOpen: (JSONObject, Boolean) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit,
    onBooking: (JSONObject?) -> Unit, onBookNow: (JSONObject) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val name = (if (bookingMode) visit.str("tipoVisita") else visit.str("tipo")).ifBlank { "Visita" }
    val date = if (bookingMode) visit.str("dataPromemoria") else visit.str("data")
    val dateLabel = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) }.getOrDefault(displayDate(date))
    val time = if (bookingMode) "" else visit.str("ora")
    val completed = visit.bool("completata")
    val reportReminder = if (bookingMode) null else visit.optJSONObject("refertiReminder")?.takeIf { it.str("data").isNotBlank() }

    Column(
        Modifier.fillMaxWidth().healthSharedBounds("appointments-${visit.str("id")}")
            .clip(RoundedCornerShape(19.dp)).background(c.surface)
            .border(1.25.dp, c.border, RoundedCornerShape(19.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(visit, bookingMode) }.padding(start = 14.dp, top = 15.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(55.dp).clip(RoundedCornerShape(15.dp)).background(c.iconBlue), contentAlignment = Alignment.Center) {
                StethoscopeIcon(c.blue, Modifier.size(31.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = c.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(dateLabel, fontSize = 14.sp, lineHeight = 19.sp, color = c.muted)
                }
                if (time.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp), tint = c.muted)
                        Spacer(Modifier.width(6.dp))
                        Text(time, fontSize = 14.sp, lineHeight = 19.sp, color = c.muted)
                    }
                }
                if (bookingMode && visit.str("periodo").isNotBlank()) {
                    val period = runCatching { YearMonth.parse(visit.str("periodo")).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)) }.getOrDefault(visit.str("periodo"))
                    Text("Periodo desiderato: $period", fontSize = 12.sp, color = c.muted)
                }
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.align(Alignment.CenterVertically).size(22.dp), tint = c.muted)
        }
        val note = if (bookingMode) visit.str("nota") else visit.str("note")
        if (note.isNotBlank()) Text(note, Modifier.padding(start = 82.dp, end = 16.dp, bottom = 10.dp), fontSize = 12.sp, color = c.muted, maxLines = 2)
        if (reportReminder != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ritiro referti: ${displayDate(reportReminder.str("data"))}", Modifier.weight(1f), fontSize = 12.sp, color = c.muted)
                TextButton(onClick = { onAction(visit, "referti") }) { Text(if (reportReminder.bool("ritirati")) "Da ritirare" else "Ritirati", fontSize = 12.sp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            VisitAction(if (bookingMode) "Prenota" else if (completed) "Riapri" else "Effettuata", if (bookingMode) Icons.Default.OpenInNew else if (completed) Icons.Default.OpenInNew else Icons.Default.EventAvailable, c.blue, Modifier.weight(1f)) {
                if (bookingMode) onBookNow(visit) else onAction(visit, "complete")
            }
            VisitSeparator(c)
            VisitAction("Modifica", Icons.Default.Edit, c.blue, Modifier.weight(1f)) { if (bookingMode) onBooking(visit) else onEdit(visit) }
            VisitSeparator(c)
            VisitAction("Elimina", Icons.Default.Delete, Color(0xFFD93F35), Modifier.weight(1f)) { confirmDelete = true }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare $name?") },
        text = { Text("Questa operazione non può essere annullata.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; if (bookingMode) onAction(visit, "deleteBooking") else onDelete(visit) }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
    )
}

@Composable
private fun VisitAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun VisitSeparator(c: HomeColors) {
    Box(Modifier.width(0.5.dp).height(35.dp).background(c.border))
}

@Composable
internal fun StethoscopeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val left = size.width * 0.24f
        val right = size.width * 0.62f
        val yTop = size.height * 0.13f
        val yMid = size.height * 0.48f
        drawLine(color, Offset(left, yTop), Offset(left, yMid), stroke)
        drawLine(color, Offset(right, yTop), Offset(right, yMid), stroke)
        drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(left, yMid - size.height * 0.18f), size = Size(right - left, size.height * 0.36f), style = Stroke(stroke))
        drawLine(color, Offset((left + right) / 2f, yMid + size.height * 0.18f), Offset((left + right) / 2f, size.height * 0.77f), stroke)
        drawLine(color, Offset((left + right) / 2f, size.height * 0.77f), Offset(size.width * 0.77f, size.height * 0.77f), stroke)
        drawCircle(color, radius = stroke * 0.9f, center = Offset(left, yTop))
        drawCircle(color, radius = stroke * 0.9f, center = Offset(right, yTop))
        drawCircle(color, radius = size.width * 0.11f, center = Offset(size.width * 0.78f, size.height * 0.77f))
        drawCircle(Color.White, radius = size.width * 0.035f, center = Offset(size.width * 0.78f, size.height * 0.77f))
    }
}
