package it.dueffe.health

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

internal data class HomeColors(
    val background: Color, val surface: Color, val ink: Color, val muted: Color,
    val blue: Color, val purple: Color, val lilac: Color, val iconBlue: Color,
    val iconMint: Color, val border: Color
)

@Composable
internal fun homeColors(dark: Boolean): HomeColors {
    val scheme = MaterialTheme.colorScheme
    return HomeColors(
        background = scheme.background,
        surface = scheme.surface,
        ink = scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        blue = scheme.primary,
        purple = scheme.primary,
        lilac = scheme.primaryContainer,
        iconBlue = scheme.primaryContainer.copy(alpha = if (dark) 0.58f else 0.72f),
        iconMint = scheme.primaryContainer.copy(alpha = if (dark) 0.48f else 0.62f),
        border = scheme.outlineVariant
    )
}

@Composable
fun HomeNavigationBar(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val labels = remember { listOf("Oggi", "Terapie", "Visite", "Documenti", "Profilo") }
    val selectedIndex = labels.indexOf(selected)
    val pillAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
        label = "Visibilità selezione navigazione"
    )
    Box(
        Modifier.fillMaxWidth().background(scheme.background)
            .navigationBarsPadding().padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(29.dp),
            color = scheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 4.dp, vertical = 5.dp)) {
                val itemWidth = maxWidth / labels.size
                val pillOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex.coerceAtLeast(0),
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f),
                    label = "Posizione pillola navigazione"
                )
                Box(
                    Modifier.offset(x = pillOffset).width(itemWidth).fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .scale(scaleX = 1f, scaleY = pillAlpha)
                        .clip(RoundedCornerShape(25.dp))
                        .background(scheme.primaryContainer.copy(alpha = pillAlpha))
                )
                Row(Modifier.fillMaxSize()) {
                    labels.forEach { label ->
                        val isSelected = selected == label
                        val itemColor by animateColorAsState(
                            targetValue = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
                            label = "Colore $label"
                        )
                        val itemScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
                            label = "Scala $label"
                        )
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .selectable(
                                    selected = isSelected,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(label) }
                                )
                                .semantics { contentDescription = label }
                                .padding(top = 7.dp, bottom = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(Modifier.height(29.dp), contentAlignment = Alignment.Center) {
                                if (label == "Terapie") CapsuleIcon(itemColor, Modifier.size(26.dp).scale(itemScale))
                                else Icon(
                                    when (label) {
                                        "Oggi" -> Icons.Default.Home
                                        "Visite" -> Icons.Default.CalendarMonth
                                        "Documenti" -> Icons.Default.Description
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(27.dp).scale(itemScale),
                                    tint = itemColor
                                )
                            }
                            Text(
                                label,
                                color = itemColor,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeDashboard(
    store: HealthStore, revision: Int, dark: Boolean,
    onSettings: () -> Unit, onProfiles: () -> Unit,
    onSection: (String) -> Unit, onAdd: (String) -> Unit,
    onTake: (JSONObject, String) -> Unit,
    onOpenVisit: (JSONObject) -> Unit
) {
    val c = homeColors(dark)
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(30_000)
        }
    }
    val date = LocalDate.now()
    val meds = remember(revision, store.activeId, date) { store.list("medicines").filter { !it.bool("archiviata") && (it.bool("senzaFine", true) || it.str("dataFine").isBlank() || it.str("dataFine") >= today()) && (it.str("dataInizio").isBlank() || it.str("dataInizio") <= today()) } }
    val visits = remember(revision, store.activeId, date) { store.list("appointments").filter { !it.bool("completata") && it.str("data") >= today() }.sortedBy { it.str("data") } }
    val doses = meds.flatMap { med -> medicineTimesOn(med, date).map { med to it } }.sortedBy { it.second }
    val takenCount = doses.count { (med, time) -> store.dose(today(), med.str("id"), time).bool("taken") }
    val lowStock = meds.filter { med -> med.str("quantita").toDoubleOrNull()?.let { amount -> med.str("soglia").toDoubleOrNull()?.let { amount <= it } } ?: false }
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(dateLabel, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
                Text("La tua giornata", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Ecco il riepilogo di oggi, tutto sotto controllo.", fontSize = 14.sp, lineHeight = 18.sp, color = c.muted)
            }
        }
        item {
            HomeSummaryCard(takenCount, doses.size, meds.isEmpty())
        }
        item {
            HomeDoseSection(doses, store, currentTime, c,
                onAll = { onSection("Terapie") },
                onAdd = { if (meds.isEmpty()) onAdd("medicines") else onSection("Terapie") },
                onTake = onTake)
        }
        if (lowStock.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFFFFEEE6))
                .clickable { onSection("Terapie") }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, null, Modifier.size(19.dp), tint = Color(0xFFB84B1C))
                Spacer(Modifier.width(7.dp))
                Text("Scorte da controllare: ${lowStock.joinToString { it.str("nome") }}", Modifier.weight(1f), fontSize = 12.sp, color = c.ink)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = c.blue)
            }
        }
        item {
            HomeVisitSection(visits.take(2), c,
                onAll = { onSection("Visite") }, onAdd = { onAdd("appointments") }, onOpenVisit = onOpenVisit)
        }
    }
}

@Composable
private fun HomeSummaryCard(taken: Int, total: Int, noMeds: Boolean) {
    val c = homeColors(false)
    val blue = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    Row(
        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp))
            .background(c.iconBlue)
            .padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                drawCircle(primaryContainer, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                if (total > 0 && taken > 0) drawArc(blue, -90f, 360f * taken / total, false, style = Stroke(stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CapsuleIcon(blue, Modifier.size(25.dp))
                Spacer(Modifier.height(2.dp))
                Text("$taken/$total", fontSize = 19.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF11182A))
                Text("dosi", fontSize = 12.sp, lineHeight = 14.sp, color = Color(0xFF415678))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Terapie di oggi", fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF11182A))
            Text(when {
                total == 0 -> "Nessuna dose oggi"
                taken == total -> "Tutte le dosi segnate"
                else -> "${total - taken} ${if (total - taken == 1) "dose da segnare" else "dosi da segnare"}"
            }, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF11182A), maxLines = 2)
            Text(when {
                total == 0 -> if (noMeds) "Aggiungi una terapia per iniziare." else "Nessuna assunzione in programma."
                taken == total -> "Hai registrato tutte le dosi di oggi."
                else -> "Hai ancora ${total - taken} ${if (total - taken == 1) "dose da registrare" else "dosi da registrare"} per oggi."
            }, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFF566276), maxLines = 2)
        }
        Image(painterResource(R.drawable.home_daily_illustration), null,
            Modifier.size(88.dp).offset(x = 7.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun HomeCardHeading(title: String, color: Color, iconBackground: Color, icon: @Composable () -> Unit, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(12.dp)).background(iconBackground), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.clickable(onClick = onAll).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vedi tutte", fontSize = 13.sp, color = color)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = color)
        }
    }
}

@Composable
private fun HomeDoseSection(doses: List<Pair<JSONObject, String>>, store: HealthStore, now: LocalTime, c: HomeColors,
                            onAll: () -> Unit, onAdd: () -> Unit, onTake: (JSONObject, String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeCardHeading("Dosi di oggi", c.blue, c.iconBlue,
            { CapsuleIcon(c.blue, Modifier.size(20.dp)) }, onAll)
        if (doses.isEmpty()) {
            HomeEmptyDose(c, store.list("medicines").isEmpty(), onAdd)
        } else doses.forEach { (med, time) ->
            val taken = store.dose(today(), med.str("id"), time).bool("taken")
            val overdue = doseOverdue(time, taken, now)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(c.surface)
                    .border(1.25.dp, c.border, RoundedCornerShape(19.dp))
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(c.lilac), contentAlignment = Alignment.Center) {
                        CapsuleIcon(c.blue, Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(med.str("nome").ifBlank { "Farmaco" }, fontSize = 19.sp, lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${med.str("dosaggio")} ${med.str("unita")}".trim(), fontSize = 13.sp, color = c.muted, maxLines = 1)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (overdue) MaterialTheme.colorScheme.errorContainer else c.lilac)
                        .clickable { onTake(med, time) }
                        .padding(start = 7.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = taken, onCheckedChange = { onTake(med, time) }, modifier = Modifier.size(39.dp),
                        colors = CheckboxDefaults.colors(checkedColor = c.blue, uncheckedColor = c.muted))
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dose di oggi", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(time, fontSize = 13.sp, color = c.muted)
                    }
                    Text(if (taken) "Assunta" else if (overdue) "In ritardo" else "Da segnare",
                        Modifier.clip(CircleShape).background(c.surface).padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp, color = if (overdue) MaterialTheme.colorScheme.error else c.blue)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HomeVisitSection(visits: List<JSONObject>, c: HomeColors,
                             onAll: () -> Unit, onAdd: () -> Unit, onOpenVisit: (JSONObject) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeCardHeading("Prossime visite", c.blue, c.iconBlue,
            { Icon(Icons.Default.EventAvailable, null, Modifier.size(21.dp), tint = c.blue) }, onAll)
        if (visits.isEmpty()) HomeEmptyVisit(c, onAdd)
        else visits.forEach { visit ->
            val visitDate = runCatching { LocalDate.parse(visit.str("data")) }.getOrNull()
            Row(Modifier.fillMaxWidth().healthSharedBounds("appointments-${visit.str("id")}")
                .clip(RoundedCornerShape(19.dp)).background(c.surface)
                .border(1.25.dp, c.border, RoundedCornerShape(19.dp))
                .clickable { onOpenVisit(visit) }.padding(start = 14.dp, top = 15.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(55.dp).clip(RoundedCornerShape(15.dp)).background(c.iconBlue), contentAlignment = Alignment.Center) {
                    StethoscopeIcon(c.blue, Modifier.size(31.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(visit.str("tipo").ifBlank { "Visita" }, fontSize = 19.sp, lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = c.muted)
                        Spacer(Modifier.width(6.dp))
                        Text(visitDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) ?: "Data da definire", fontSize = 14.sp, color = c.muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp), tint = c.muted)
                        Spacer(Modifier.width(6.dp))
                        Text(visit.str("ora").ifBlank { "Orario da definire" }, fontSize = 14.sp, color = c.muted)
                    }
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.size(22.dp), tint = c.muted)
            }
        }
    }
}

@Composable
private fun HomeQuickAccess(c: HomeColors, onSection: (String) -> Unit) {
    Column {
        Text("Accesso rapido", Modifier.padding(start = 3.dp, top = 4.dp, bottom = 8.dp),
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Terapie", "Visite", "Documenti", "Profilo").forEach { section ->
                val tint = when(section) {
                    "Terapie" -> Color(0xFF2C79CB)
                    "Visite" -> Color(0xFF0A8068)
                    "Documenti" -> Color(0xFF6A38B8)
                    else -> Color(0xFFE02E67)
                }
                val background = when(section) {
                    "Terapie" -> Color(0xFFEAF3FF)
                    "Visite" -> Color(0xFFE9FAF5)
                    "Documenti" -> Color(0xFFF3EAFE)
                    else -> Color(0xFFFFEDEF)
                }
                Column(Modifier.weight(1f).height(91.dp).clip(RoundedCornerShape(13.dp))
                    .background(background).clickable { onSection(section) }.padding(horizontal = 3.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween) {
                    when(section) {
                        "Terapie" -> CapsuleIcon(tint, Modifier.size(25.dp))
                        "Visite" -> Icon(Icons.Default.EventAvailable, null, Modifier.size(25.dp), tint = tint)
                        "Documenti" -> Icon(Icons.Default.Description, null, Modifier.size(25.dp), tint = tint)
                        else -> Icon(Icons.Default.Favorite, null, Modifier.size(25.dp), tint = tint)
                    }
                    Text(if (section == "Profilo") "Profilo sanitario" else section,
                        modifier = Modifier.fillMaxWidth(), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold,
                        color = c.ink, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(when(section) {
                        "Terapie" -> "Gestisci i\nfarmaci"
                        "Visite" -> "I tuoi\nappuntamenti"
                        "Documenti" -> "Referti e\nricette"
                        else -> "Dati clinici\ne contatti"
                    }, modifier = Modifier.fillMaxWidth(), fontSize = 8.sp, lineHeight = 9.sp, color = c.muted, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun HomeHeader(name: String, c: HomeColors, onSettings: () -> Unit, onProfiles: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Health", fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(name, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.muted, maxLines = 1)
        }
        HomeRoundAction(Icons.Default.Settings, "Impostazioni", c, onSettings)
        Spacer(Modifier.width(10.dp))
        HomeRoundAction(Icons.Default.AccountCircle, "Profili", c, onProfiles)
    }
}

@Composable
private fun HomeRoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, c: HomeColors, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(43.dp).background(if (c.background == Color(0xFFFFFCFF)) Color(0xFFF5F2F9) else c.surface, CircleShape)) {
        Icon(icon, description, Modifier.size(23.dp), tint = c.muted)
    }
}

@Composable
private fun HomeSectionHeading(title: String, subtitle: String, action: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconBackground: Color, tint: Color, c: HomeColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(45.dp).clip(RoundedCornerShape(15.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            if (title == "Dosi di oggi") CapsuleIcon(Color(0xFF194B8A), Modifier.size(27.dp))
            else Icon(icon, null, Modifier.size(25.dp), tint = Color(0xFF159379))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(subtitle, fontSize = 12.sp, lineHeight = 15.sp, color = c.muted, maxLines = 2)
        }
        Spacer(Modifier.width(5.dp))
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(if (c.background == Color(0xFFFFFCFF)) Color(0xFFF4F1FF) else c.surface).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, fontSize = 13.sp, color = tint)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(17.dp), tint = tint)
        }
    }
}

@Composable
internal fun CapsuleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        rotate(-45f) {
            val left = size.width * 0.13f
            val top = size.height * 0.34f
            val width = size.width * 0.74f
            val height = size.height * 0.32f
            val radius = CornerRadius(height / 2f)
            drawRoundRect(color, Offset(left, top), Size(width, height), radius)
            clipRect(left, top, left + width / 2f, top + height) {
                drawRoundRect(Color.White, Offset(left + size.width * 0.035f, top + size.height * 0.035f), Size(width - size.width * 0.07f, height - size.height * 0.07f), CornerRadius((height - size.height * 0.07f) / 2f))
            }
        }
    }
}

@Composable
private fun HomeEmptyDose(c: HomeColors, noMeds: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).clip(RoundedCornerShape(19.dp)).background(c.surface)
        .border(1.25.dp, c.border, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Nessuna dose programmata", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(if (noMeds) "Aggiungi un farmaco per iniziare." else "Apri Terapie per le assunzioni al bisogno.", fontSize = 12.sp, color = c.muted)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = c.blue)
    }
}

internal fun doseOverdue(time: String, taken: Boolean, now: LocalTime): Boolean {
    if (taken) return false
    val due = runCatching { LocalTime.parse(time) }.getOrNull() ?: return false
    return now.withSecond(0).withNano(0).isAfter(due)
}

@Composable
private fun HomeEmptyVisit(c: HomeColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).clip(RoundedCornerShape(19.dp)).background(c.surface)
        .border(1.25.dp, c.border, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Nessuna visita in programma", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text("Aggiungi una visita per vederla qui.", fontSize = 12.sp, color = c.muted)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = c.blue)
    }
}
