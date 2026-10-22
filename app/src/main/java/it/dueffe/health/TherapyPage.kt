package it.dueffe.health

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TherapyPage(
    store: HealthStore, revision: Int, listState: LazyListState, insets: PaddingValues,
    header: @Composable () -> Unit,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onOpen: (JSONObject) -> Unit, onAdd: () -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(30_000) } }
    val all = remember(revision, store.activeId) { store.list("medicines") }
    fun matches(med: JSONObject, option: String) = when (option) {
        "Attivi" -> isMedicineActive(med)
        "Scaduti" -> isMedicineExpired(med)
        "Archiviati" -> med.bool("archiviata")
        else -> true
    }
    val medicines = all.filter { it.str("nome").contains(search, ignoreCase = true) && matches(it, filter) }
        .sortedWith(compareByDescending<JSONObject> { isMedicineActive(it) }.thenBy { it.str("nome").lowercase() })

    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(Modifier.enterAnimation(0)) { header() } }
        item { Box(Modifier.enterAnimation(1)) { HealthSearchBar(search, setSearch, "Cerca un farmaco…") } }
        item {
            Box(Modifier.enterAnimation(2)) {
                HealthFilterBar(listOf("Tutti", "Attivi", "Scaduti", "Archiviati").map { option ->
                    HealthFilterOption(option, when (option) { "Attivi" -> Icons.Rounded.CheckCircle; "Scaduti" -> Icons.Rounded.EventBusy; "Archiviati" -> Icons.Rounded.Inventory2; else -> null }, all.count { matches(it, option) })
                }, filter, setFilter)
            }
        }
        if (medicines.isEmpty()) item {
            Box(Modifier.enterAnimation(3)) {
                if (all.isEmpty()) EmptyState(Icons.Rounded.Medication, "Nessuna terapia", "Aggiungi il primo farmaco: Health ti ricorderà quando prenderlo.", actionLabel = "Aggiungi farmaco", onAction = onAdd)
                else EmptyState(Icons.Rounded.SearchOff, "Nessun risultato", "Prova un altro filtro o una ricerca diversa.")
            }
        }
        items(medicines, key = { it.str("id") }) { med ->
            TherapyCard(store, med, revision, now, Modifier.animateItem().enterAnimation(3 + medicines.indexOf(med)), onOpen, onEdit, onDelete, onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TherapyCard(
    store: HealthStore, med: JSONObject, revision: Int, now: LocalTime, modifier: Modifier,
    onOpen: (JSONObject) -> Unit, onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    val s = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val name = med.str("nome").ifBlank { "Senza nome" }
    val archived = med.bool("archiviata")
    val expired = isMedicineExpired(med)
    val active = isMedicineActive(med)
    val photo = remember(revision, med.str("photo")) {
        med.str("photo").takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(store.attachmentPath(it).path) }.getOrNull() }
    }
    val times = if (active) medicineTimesOn(med, LocalDate.now()) else emptyList()
    val prn = medicineFrequencyType(med) == "as_needed" && active
    val stock = med.str("quantita").toDoubleOrNull()
    val threshold = med.str("soglia").toDoubleOrNull()
    val lowStock = stock != null && threshold != null && stock <= threshold

    HealthCard(modifier.healthSharedBounds("medicines-${med.str("id")}"), onClick = { onOpen(med) }, padding = PaddingValues(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(null, s.primary, if (active) s.primaryContainer else s.surfaceContainerHigh, 54.dp, 18.dp, custom = {
                if (photo != null) Image(photo.asImageBitmap(), "Foto di $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CapsuleIcon(if (active) s.primary else s.outline, Modifier.size(32.dp))
            })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf("${med.str("dosaggio")} ${med.str("unita")}".trim(), medicineFrequencyDescription(med)).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 13.sp, lineHeight = 18.sp, color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            OverflowMenu(buildList {
                add(MenuAction("Modifica", Icons.Rounded.Edit) { onEdit(med) })
                add(MenuAction("Foto confezione", Icons.Rounded.PhotoCamera) { onAction(med, "photo") })
                add(MenuAction("Storico assunzioni", Icons.Rounded.History) { onAction(med, "history") })
                if (archived || expired) add(MenuAction("Rinnova terapia", Icons.Rounded.Autorenew) { onAction(med, "renew") })
                add(MenuAction(if (archived) "Ripristina" else "Archivia", if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive) { onAction(med, "archive") })
                add(MenuAction("Elimina", Icons.Rounded.DeleteOutline, danger = true) { confirmDelete = true })
            }, "Azioni per $name")
        }
        val pills = buildList<@Composable () -> Unit> {
            when {
                archived -> add { StatusPill("Archiviata", s.onSurfaceVariant, s.surfaceContainerHigh, Icons.Rounded.Inventory2) }
                expired -> add { StatusPill("Terminata", s.error, s.errorContainer, Icons.Rounded.EventBusy) }
                !active -> add { StatusPill("Inizia il ${displayDate(med.str("dataInizio"))}", s.primary, s.primaryContainer, Icons.Rounded.Schedule) }
            }
            if (stock != null) add {
                StatusPill("Scorta ${med.str("quantita")} ${med.str("quantitaUnita").ifBlank { med.str("unita") }}".trim(),
                    if (lowStock) HealthStatus.warning else s.onSurfaceVariant, if (lowStock) HealthStatus.warningContainer else s.surfaceContainer, Icons.Rounded.Inventory2)
            }
        }
        if (pills.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { pills.forEach { it() } }
        }
        if (med.str("note").isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(med.str("note"), Modifier.padding(end = 12.dp), fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (times.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Dosi di oggi", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = s.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { time ->
                    val taken = store.dose(today(), med.str("id"), time).bool("taken")
                    DoseChip(time, taken, doseOverdue(time, taken, now)) { store.toggleDose(med, time, !taken); onAction(med, "refresh") }
                }
            }
        }
        if (prn) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { store.recordAsNeededDose(med); onAction(med, "refresh") }, shape = CircleShape) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Segna dose assunta ora")
            }
            val prnDoses = store.asNeededDoses(today(), med.str("id"))
            if (prnDoses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prnDoses.forEach { (doseKey, dose) ->
                        Row(Modifier.clip(CircleShape).background(HealthStatus.successContainer).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = HealthStatus.success)
                            Spacer(Modifier.width(4.dp))
                            Text(dose.str("actualTime"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HealthStatus.success)
                            IconButton(onClick = { store.toggleDose(med, doseKey, false); onAction(med, "refresh") }, Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, "Annulla assunzione delle ${dose.str("actualTime")}", Modifier.size(16.dp), tint = HealthStatus.success)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDeleteDialog(name, onConfirm = { confirmDelete = false; onDelete(med) }, onDismiss = { confirmDelete = false })
}

@Composable
private fun DoseChip(time: String, taken: Boolean, overdue: Boolean, onToggle: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val background by animateColorAsState(when { taken -> s.primary; overdue -> HealthStatus.warningContainer; else -> s.surfaceContainer }, label = "Sfondo dose")
    val foreground by animateColorAsState(when { taken -> s.onPrimary; overdue -> HealthStatus.warning; else -> s.onSurface }, label = "Testo dose")
    Row(
        Modifier.clip(CircleShape).background(background).clickable(onClickLabel = if (taken) "Segna come non assunta" else "Segna come assunta", onClick = onToggle)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (taken) Color.White.copy(alpha = 0.25f) else s.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            Icon(if (taken) Icons.Rounded.Check else if (overdue) Icons.Rounded.WarningAmber else Icons.Rounded.Schedule, null, Modifier.size(16.dp), tint = foreground)
        }
        Spacer(Modifier.width(8.dp))
        Text(doseTimeLabel(time), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

/** Calendario mensile delle assunzioni: pieno, parziale o mancato per ogni giorno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoseHistorySheet(store: HealthStore, med: JSONObject, onDismiss: () -> Unit) {
    val s = MaterialTheme.colorScheme
    var month by remember { mutableStateOf(YearMonth.now()) }
    val prn = medicineFrequencyType(med) == "as_needed"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = s.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text("Storico assunzioni", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = s.primary)
            Text(med.str("nome"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = s.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Mese precedente") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.Rounded.ChevronRight, "Mese successivo") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, color = s.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.height(6.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = List(offset) { 0 } + (1..month.lengthOfMonth()).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day > 0) {
                                val date = month.atDay(day)
                                val key = date.toString()
                                val due = medicineTimesOn(med, date)
                                val count = if (prn) store.asNeededDoses(key, med.str("id")).size else due.count { store.dose(key, med.str("id"), it).bool("taken") }
                                val future = key > today()
                                val (bg, fg) = when {
                                    future || (!prn && due.isEmpty()) || (prn && count == 0) -> s.surfaceContainerLow to s.onSurfaceVariant
                                    prn || count == due.size -> s.primary to s.onPrimary
                                    count > 0 -> s.primaryContainer to s.onPrimaryContainer
                                    else -> s.errorContainer to s.onErrorContainer
                                }
                                Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                                    Text("$day", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(s.primary, if (prn) "Assunta" else "Completo")
                if (!prn) LegendDot(s.primaryContainer, "Parziale")
                if (!prn) LegendDot(s.errorContainer, "Saltata")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
