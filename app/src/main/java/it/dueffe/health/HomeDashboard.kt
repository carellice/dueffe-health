package it.dueffe.health

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun greeting(now: LocalTime = LocalTime.now()) = when (now.hour) {
    in 5..12 -> "Buongiorno"
    in 13..17 -> "Buon pomeriggio"
    else -> "Buonasera"
}

internal fun relativeDayLabel(value: String): String {
    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return "Data da definire"
    return when (val days = ChronoUnit.DAYS.between(LocalDate.now(), date)) {
        0L -> "Oggi"
        1L -> "Domani"
        -1L -> "Ieri"
        in 2..13 -> "Tra $days giorni"
        in -13..-2 -> "${-days} giorni fa"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
    }
}

internal fun doseOverdue(time: String, taken: Boolean, now: LocalTime): Boolean {
    if (taken) return false
    val due = runCatching { LocalTime.parse(time) }.getOrNull() ?: return false
    return now.withSecond(0).withNano(0).isAfter(due)
}

internal fun doseTimeLabel(time: String) = if (time == "00:00") "24:00" else time

@Composable
fun HomeDashboard(
    store: HealthStore, revision: Int, listState: LazyListState, insets: PaddingValues,
    header: @Composable () -> Unit,
    onTab: (HealthTab, String?) -> Unit, onAdd: (String) -> Unit,
    onTake: (JSONObject, String) -> Unit,
    onOpenVisit: (JSONObject) -> Unit, onOpenMedicine: (JSONObject) -> Unit
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(30_000) } }
    val date = LocalDate.now()
    val meds = remember(revision, store.activeId, date) { store.list("medicines").filter(::isMedicineActive) }
    val visits = remember(revision, store.activeId, date) {
        store.list("appointments").filter { !it.bool("completata") && it.str("data") >= today() }.sortedWith(compareBy({ it.str("data") }, { it.str("ora") }))
    }
    val bookings = remember(revision, store.activeId) { store.list("bookingReminders").count { !it.bool("prenotata") } }
    val documents = remember(revision, store.activeId) { store.list("documents").size }
    val doses = remember(revision, store.activeId, date) { meds.flatMap { med -> medicineTimesOn(med, date).map { med to it } }.sortedBy { it.second } }
    fun isTaken(dose: Pair<JSONObject, String>) = store.dose(today(), dose.first.str("id"), dose.second).bool("taken")
    val takenCount = doses.count(::isTaken)
    val next = doses.firstOrNull { !isTaken(it) && !doseOverdue(it.second, false, now) } ?: doses.firstOrNull { !isTaken(it) }
    val lowStock = meds.filter { med -> med.str("quantita").toDoubleOrNull()?.let { amount -> med.str("soglia").toDoubleOrNull()?.let { amount <= it } } ?: false }
    val groups = doses.groupBy { it.second }.toList()

    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Box(Modifier.enterAnimation(0)) { header() } }
        item {
            HomeHero(takenCount, doses.size, next, meds.isEmpty(), Modifier.enterAnimation(1))
        }
        if (lowStock.isNotEmpty()) item {
            HealthCard(Modifier.enterAnimation(2), onClick = { onTab(HealthTab.Terapie, "Attivi") }, color = HealthStatus.warningContainer, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Inventory2, HealthStatus.warning, HealthStatus.warning.copy(alpha = 0.16f), 42.dp, 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Scorte in esaurimento", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HealthStatus.warning)
                        Text(lowStock.joinToString { it.str("nome") }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = HealthStatus.warning)
                }
            }
        }
        item {
            Column(Modifier.enterAnimation(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val s = MaterialTheme.colorScheme
                val overdue = doses.any { !isTaken(it) && doseOverdue(it.second, false, now) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AreaHubTile("Terapie", if (overdue) "Dosi in ritardo" else "Farmaci attivi", Icons.Rounded.Medication, s.primary, meds.size, Modifier.weight(1f), badge = overdue) { onTab(HealthTab.Terapie, null) }
                    AreaHubTile("Visite", if (bookings > 0) "$bookings da prenotare" else "In programma", Icons.Rounded.CalendarMonth, s.secondary, visits.size, Modifier.weight(1f), badge = bookings > 0) { onTab(HealthTab.Visite, null) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AreaHubTile("Documenti", "Referti e ricette", Icons.Rounded.FolderOpen, s.tertiary, documents, Modifier.weight(1f)) { onTab(HealthTab.Documenti, null) }
                    AreaHubTile("Profilo", "Scheda sanitaria", Icons.Rounded.Person, lerp(s.primary, s.secondary, 0.5f), null, Modifier.weight(1f)) { onTab(HealthTab.Profilo, null) }
                }
            }
        }
        item { SectionHeader("Programma di oggi", Modifier.enterAnimation(4), "Terapie") { onTab(HealthTab.Terapie, null) } }
        if (groups.isEmpty()) item {
            Box(Modifier.enterAnimation(5)) {
                if (meds.isEmpty()) EmptyState(Icons.Rounded.Medication, "Nessuna terapia", "Aggiungi un farmaco: qui vedrai le dosi da assumere ogni giorno.", actionLabel = "Aggiungi farmaco") { onAdd("medicines") }
                else EmptyState(Icons.Rounded.TaskAlt, "Nessuna dose programmata", "Oggi non ci sono assunzioni a orario. Le terapie al bisogno si segnano da Terapie.")
            }
        }
        groups.forEachIndexed { index, (time, entries) ->
            item(key = "dose-$time") {
                DoseTimelineGroup(time, entries.map { it.first }, store, now, index == groups.lastIndex, Modifier.enterAnimation(5 + index), onTake, onOpenMedicine)
            }
        }
        item { SectionHeader("Prossime visite", Modifier.enterAnimation(6), "Agenda") { onTab(HealthTab.Visite, "Prossime") } }
        item {
            if (visits.isEmpty()) Box(Modifier.enterAnimation(7)) {
                EmptyState(Icons.Rounded.CalendarMonth, "Agenda libera", "Non hai visite in programma.", accent = MaterialTheme.colorScheme.secondary, actionLabel = "Aggiungi visita") { onAdd("appointments") }
            } else LazyRow(
                Modifier.enterAnimation(7).fillMaxWidth().padding(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(visits.take(6), key = { it.str("id") }) { visit -> UpcomingVisitCard(visit) { onOpenVisit(visit) } }
            }
        }
    }
}

@Composable
private fun HomeHero(taken: Int, total: Int, next: Pair<JSONObject, String>?, noMeds: Boolean, modifier: Modifier) {
    val remaining = total - taken
    HeroSurface(modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                ProgressRing(if (total == 0) 0f else taken / total.toFloat(), Modifier.fillMaxSize(), track = Color.White.copy(alpha = 0.22f), color = Color.White, stroke = 11.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(taken, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "Dosi assunte") { value ->
                        Text("$value", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Text("di $total ${if (total == 1) "dose" else "dosi"}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        total == 0 -> "Giornata libera"
                        remaining == 0 -> "Tutto fatto!"
                        remaining == 1 -> "Manca 1 dose"
                        else -> "Mancano $remaining dosi"
                    },
                    fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        total == 0 -> if (noMeds) "Aggiungi una terapia per iniziare a seguirla." else "Nessuna assunzione a orario oggi."
                        remaining == 0 -> "Hai registrato tutte le dosi di oggi."
                        else -> "Segna ogni dose quando la assumi."
                    },
                    fontSize = 13.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.88f)
                )
                if (next != null && remaining > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(15.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text("${doseTimeLabel(next.second)} · ${next.first.str("nome")}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, label: String, colors: AreaColors, modifier: Modifier, onClick: () -> Unit) {
    HealthCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        IconTile(icon, colors.accent, colors.container, 40.dp, 13.dp)
        Spacer(Modifier.height(12.dp))
        AnimatedContent(value, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = label) { count ->
            Text("$count", fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DoseTimelineGroup(
    time: String, meds: List<JSONObject>, store: HealthStore, now: LocalTime, last: Boolean, modifier: Modifier,
    onTake: (JSONObject, String) -> Unit, onOpen: (JSONObject) -> Unit
) {
    val s = MaterialTheme.colorScheme
    val allTaken = meds.all { store.dose(today(), it.str("id"), time).bool("taken") }
    val overdue = !allTaken && doseOverdue(time, false, now)
    val dotColor = when { allTaken -> s.primary; overdue -> HealthStatus.warning; else -> s.surfaceContainerLowest }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(58.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(doseTimeLabel(time), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (overdue) HealthStatus.warning else s.onBackground)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor).border(2.dp, if (allTaken) s.primary else if (overdue) HealthStatus.warning else s.outline, CircleShape))
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).clip(CircleShape).background(s.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            meds.forEach { med ->
                val dose = store.dose(today(), med.str("id"), time)
                val taken = dose.bool("taken")
                val late = doseOverdue(time, taken, now)
                val photo = remember(med.str("photo")) { med.str("photo").takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(store.attachmentPath(it).path) }.getOrNull() } }
                HealthCard(onClick = { onOpen(med) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(null, s.primary, s.primaryContainer, 46.dp, 15.dp, custom = {
                            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else CapsuleIcon(s.primary, Modifier.size(28.dp))
                        })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(med.str("nome").ifBlank { "Farmaco" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf("${med.str("dosaggio")} ${med.str("unita")}".trim(), med.str("formulazione")).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (taken || late) {
                                Spacer(Modifier.height(6.dp))
                                if (taken) StatusPill("Assunta alle ${dose.str("actualTime")}", HealthStatus.success, HealthStatus.successContainer, Icons.Rounded.Check)
                                else StatusPill("In ritardo", HealthStatus.warning, HealthStatus.warningContainer, Icons.Rounded.WarningAmber)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        CheckBubble(taken, s.primary, 42.dp, med.str("nome")) { onTake(med, time) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DateBadge(value: String, accent: Color, modifier: Modifier = Modifier) {
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    Column(
        modifier.size(width = 58.dp, height = 64.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(accent, lerp(accent, Color.Black, 0.18f)))),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(date?.dayOfMonth?.toString() ?: "–", fontSize = 23.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(date?.format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN))?.uppercase()?.take(3) ?: "", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun UpcomingVisitCard(visit: JSONObject, onClick: () -> Unit) {
    val c = areaColors(Area.Visit)
    val s = MaterialTheme.colorScheme
    HealthCard(Modifier.width(272.dp).healthSharedBounds("appointments-${visit.str("id")}"), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(visit.str("data"), c.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(visit.str("tipo").ifBlank { "Visita" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(visit.str("ora"), visit.str("luogo")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Orario da definire" },
                    fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                StatusPill(relativeDayLabel(visit.str("data")), c.accent, c.container)
            }
        }
    }
}
