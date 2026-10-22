package it.dueffe.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun monthLabel(value: String): String =
    runCatching { YearMonth.from(LocalDate.parse(value)).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) } }
        .getOrDefault("Senza data")

internal fun periodLabel(value: String): String =
    runCatching { YearMonth.parse(value).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)) }.getOrDefault(value)

@Composable
fun VisitPage(
    store: HealthStore, revision: Int, listState: LazyListState, insets: PaddingValues,
    header: @Composable () -> Unit,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onOpen: (JSONObject, Boolean) -> Unit,
    onAddVisit: () -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit,
    onBooking: (JSONObject?) -> Unit, onBookNow: (JSONObject) -> Unit
) {
    val c = areaColors(Area.Visit)
    val appointments = remember(revision, store.activeId) { store.list("appointments") }
    val bookingReminders = remember(revision, store.activeId) { store.list("bookingReminders").filter { !it.bool("prenotata") } }
    val bookingMode = filter == "Da prenotare"
    fun upcoming(visit: JSONObject) = visit.str("data") >= today() && !visit.bool("completata")
    val entries = if (bookingMode) {
        bookingReminders.filter { it.str("tipoVisita").contains(search, ignoreCase = true) }.sortedBy { it.str("dataPromemoria") }
    } else {
        val filtered = appointments.filter { visit ->
            visit.str("tipo").contains(search, ignoreCase = true) && when (filter) {
                "Prossime" -> upcoming(visit)
                "Passate" -> !upcoming(visit)
                else -> true
            }
        }
        if (filter == "Passate") filtered.sortedWith(compareByDescending<JSONObject> { it.str("data") }.thenByDescending { it.str("ora") })
        else filtered.sortedWith(compareBy<JSONObject> { it.str("data") }.thenBy { it.str("ora") })
    }

    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(Modifier.enterAnimation(0)) { header() } }
        item { Box(Modifier.enterAnimation(1)) { HealthSearchBar(search, setSearch, if (bookingMode) "Cerca cosa prenotare…" else "Cerca una visita…") } }
        item {
            Box(Modifier.enterAnimation(2)) {
                HealthFilterBar(listOf(
                    HealthFilterOption("Prossime", Icons.Rounded.Upcoming, appointments.count(::upcoming)),
                    HealthFilterOption("Da prenotare", Icons.Rounded.NotificationsActive, bookingReminders.size),
                    HealthFilterOption("Passate", Icons.Rounded.History, appointments.count { !upcoming(it) }),
                    HealthFilterOption("Tutti", null, appointments.size)
                ), filter, setFilter, c.accent)
            }
        }
        if (entries.isEmpty()) item {
            Box(Modifier.enterAnimation(3)) {
                when {
                    search.isNotBlank() -> EmptyState(Icons.Rounded.SearchOff, "Nessun risultato", "Prova con un'altra parola.", c.accent)
                    bookingMode -> EmptyState(Icons.Rounded.NotificationsActive, "Niente da prenotare", "Crea un promemoria per ricordarti di fissare una visita.", c.accent, "Nuovo promemoria") { onBooking(null) }
                    else -> EmptyState(Icons.Rounded.CalendarMonth, if (filter == "Passate") "Nessuna visita passata" else "Agenda libera", "Aggiungi una visita per ricevere un promemoria prima dell'appuntamento.", c.accent, "Aggiungi visita", onAddVisit)
                }
            }
        }
        var lastMonth = ""
        entries.forEachIndexed { index, visit ->
            val month = monthLabel(if (bookingMode) visit.str("dataPromemoria") else visit.str("data"))
            if (!bookingMode && month != lastMonth) {
                lastMonth = month
                item(key = "mese-$month") {
                    Text(month, Modifier.animateItem().padding(top = 6.dp, start = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent)
                }
            }
            item(key = visit.str("id")) {
                val itemModifier = Modifier.animateItem().enterAnimation(3 + index)
                if (bookingMode) BookingCard(visit, itemModifier, { onOpen(visit, true) }, { onBookNow(visit) }, { onBooking(visit) }, { onAction(visit, "deleteBooking") })
                else VisitCard(visit, itemModifier, { onOpen(visit, false) }, onEdit, onDelete, onAction)
            }
        }
    }
}

@Composable
private fun VisitCard(
    visit: JSONObject, modifier: Modifier, onOpen: () -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit, onAction: (JSONObject, String) -> Unit
) {
    val s = MaterialTheme.colorScheme
    val c = areaColors(Area.Visit)
    var confirmDelete by remember { mutableStateOf(false) }
    val name = visit.str("tipo").ifBlank { "Visita" }
    val completed = visit.bool("completata")
    val past = visit.str("data") < today()
    val reportReminder = visit.optJSONObject("refertiReminder")?.takeIf { it.str("data").isNotBlank() }
    val doctor = visit.str("medico").ifBlank { listOf(visit.str("medicoNome"), visit.str("medicoCognome")).filter { it.isNotBlank() }.joinToString(" ") }

    HealthCard(modifier.healthSharedBounds("appointments-${visit.str("id")}"), onClick = onOpen, padding = PaddingValues(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(visit.str("data"), if (completed || past) s.outline else c.accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                IconLine(Icons.Rounded.Schedule, visit.str("ora").ifBlank { "Orario da definire" })
                if (visit.str("luogo").isNotBlank()) IconLine(Icons.Rounded.LocationOn, visit.str("luogo"))
                if (doctor.isNotBlank()) IconLine(Icons.Rounded.Person, doctor)
            }
            OverflowMenu(buildList {
                add(MenuAction(if (completed) "Segna da effettuare" else "Segna come effettuata", if (completed) Icons.Rounded.Replay else Icons.Rounded.TaskAlt) { onAction(visit, "complete") })
                if (reportReminder != null) add(MenuAction(if (reportReminder.bool("ritirati")) "Referti da ritirare" else "Referti ritirati", Icons.Rounded.Description) { onAction(visit, "referti") })
                add(MenuAction("Modifica", Icons.Rounded.Edit) { onEdit(visit) })
                add(MenuAction("Elimina", Icons.Rounded.DeleteOutline, danger = true) { confirmDelete = true })
            }, "Azioni per $name")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                completed -> StatusPill("Effettuata", HealthStatus.success, HealthStatus.successContainer, Icons.Rounded.TaskAlt)
                past -> StatusPill("Da confermare", HealthStatus.warning, HealthStatus.warningContainer, Icons.Rounded.HelpOutline)
                else -> StatusPill(relativeDayLabel(visit.str("data")), c.accent, c.container, Icons.Rounded.Event)
            }
            if (reportReminder != null) {
                val collected = reportReminder.bool("ritirati")
                StatusPill(if (collected) "Referti ritirati" else "Referti dal ${displayDate(reportReminder.str("data"))}",
                    if (collected) HealthStatus.success else s.tertiary, if (collected) HealthStatus.successContainer else s.tertiaryContainer, Icons.Rounded.Description)
            }
        }
        if (past && !completed) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { onAction(visit, "complete") }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.TaskAlt, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Segna come effettuata", fontSize = 13.sp)
            }
        }
    }
    if (confirmDelete) ConfirmDeleteDialog(name, onConfirm = { confirmDelete = false; onDelete(visit) }, onDismiss = { confirmDelete = false })
}

@Composable
private fun BookingCard(item: JSONObject, modifier: Modifier, onOpen: () -> Unit, onBook: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val c = areaColors(Area.Visit)
    var confirmDelete by remember { mutableStateOf(false) }
    val name = item.str("tipoVisita").ifBlank { "Visita" }
    HealthCard(modifier.healthSharedBounds("bookingReminders-${item.str("id")}"), onClick = onOpen, padding = PaddingValues(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.NotificationsActive, c.accent, c.container, 54.dp, 18.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.str("periodo").isNotBlank()) IconLine(Icons.Rounded.DateRange, "Da fare a ${periodLabel(item.str("periodo"))}")
                IconLine(Icons.Rounded.Alarm, "Promemoria ${relativeDayLabel(item.str("dataPromemoria")).replaceFirstChar { it.lowercase() }}")
            }
            OverflowMenu(listOf(
                MenuAction("Modifica", Icons.Rounded.Edit, onClick = onEdit),
                MenuAction("Elimina", Icons.Rounded.DeleteOutline, danger = true) { confirmDelete = true }
            ), "Azioni per $name")
        }
        if (item.str("nota").isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(item.str("nota"), Modifier.padding(end = 12.dp), fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBook, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = c.accent), modifier = Modifier.padding(end = 10.dp).fillMaxWidth()) {
            Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Ho prenotato: aggiungi la visita")
        }
    }
    if (confirmDelete) ConfirmDeleteDialog(name, onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
}

@Composable
internal fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = s.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, lineHeight = 17.sp, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
