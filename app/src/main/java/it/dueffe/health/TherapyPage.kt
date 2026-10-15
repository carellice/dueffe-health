package it.dueffe.health

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

private fun therapyActive(med: JSONObject): Boolean =
    !med.bool("archiviata") &&
        (med.bool("senzaFine", true) || med.str("dataFine").isBlank() || med.str("dataFine") >= today()) &&
        (med.str("dataInizio").isBlank() || med.str("dataInizio") <= today())

@Composable
fun TherapyPage(
    store: HealthStore, revision: Int, dark: Boolean,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onSettings: () -> Unit, onProfiles: () -> Unit,
    onOpen: (JSONObject) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    val c = homeColors(dark)
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(30_000)
        }
    }
    val all = remember(revision, store.activeId) { store.list("medicines") }
    val medicines = all.filter { med ->
        med.str("nome").contains(search, ignoreCase = true) && when (filter) {
            "Attivi" -> therapyActive(med)
            "Scaduti" -> !med.bool("archiviata") && !med.bool("senzaFine", true) && med.str("dataFine").isNotBlank() && med.str("dataFine") < today()
            "Archiviati" -> med.bool("archiviata")
            else -> true
        }
    }.sortedBy { it.str("nome").lowercase() }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 10.dp, bottom = 105.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HealthSearchBar(search, setSearch, "Cerca farmaci o terapie…")
        }
        item {
            HealthFilterBar(
                listOf(
                    HealthFilterOption("Tutti"),
                    HealthFilterOption("Attivi", Icons.Default.CheckCircle),
                    HealthFilterOption("Scaduti", Icons.Default.WarningAmber),
                    HealthFilterOption("Archiviati", Icons.Default.Archive)
                ), filter, setFilter
            )
        }
        if (medicines.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(18.dp)).padding(20.dp)) {
                Text(if (search.isNotBlank()) "Nessun risultato" else if (all.isEmpty()) "Nessuna terapia" else "Nessuna terapia in questo filtro", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(if (all.isEmpty()) "Usa + per aggiungere il tuo primo farmaco." else "Prova un altro filtro o una ricerca diversa.", fontSize = 14.sp, color = c.muted)
            }
        }
        items(medicines, key = { it.str("id") }) { med ->
            TherapyCard(store, med, revision, c, dark, currentTime, onOpen, onEdit, onDelete, onAction)
        }
    }
}

@Composable
private fun TherapyCard(
    store: HealthStore, med: JSONObject, revision: Int, c: HomeColors, dark: Boolean, currentTime: LocalTime,
    onOpen: (JSONObject) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val name = med.str("nome").ifBlank { "Senza nome" }
    val archived = med.bool("archiviata")
    val expired = !med.bool("senzaFine", true) && med.str("dataFine").isNotBlank() && med.str("dataFine") < today()
    val photo = remember(revision, med.str("photo")) {
        med.str("photo").takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(store.attachmentPath(it).path) }.getOrNull() }
    }
    val times = if (therapyActive(med)) medicineTimesOn(med, LocalDate.now()) else emptyList()
    val prn = medicineFrequencyType(med) == "as_needed" && therapyActive(med)
    val prnDoses = if (prn) store.asNeededDoses(today(), med.str("id")) else emptyList()

    Column(
        Modifier.fillMaxWidth().healthSharedBounds("medicines-${med.str("id")}")
            .clip(RoundedCornerShape(19.dp)).background(c.surface)
            .border(1.25.dp, c.border, RoundedCornerShape(19.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(med) }.padding(start = 14.dp, top = 15.dp, end = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(c.lilac), contentAlignment = Alignment.Center) {
                if (photo != null) Image(photo.asImageBitmap(), "Foto di $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CapsuleIcon(c.purple, Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dose = "${med.str("dosaggio")} ${med.str("unita")}".trim()
                val frequency = medicineFrequencyDescription(med)
                val hours = if (medicineFrequencyType(med) == "as_needed") "" else med.strings("orari").joinToString(", ").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Text("$dose · $frequency$hours", fontSize = 13.sp, lineHeight = 18.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val stock = med.str("quantita")
                if (stock.isNotBlank()) {
                    val stockUnit = med.str("quantitaUnita").ifBlank { med.str("unita") }
                    Text("Scorta: $stock $stockUnit", fontSize = 13.sp, lineHeight = 19.sp, color = c.muted)
                }
            }
            Icon(Icons.Default.ChevronRight, "Apri dettaglio terapia", Modifier.align(Alignment.CenterVertically).size(22.dp), tint = c.muted)
        }
        if (med.str("note").isNotBlank()) Text(med.str("note"), Modifier.padding(start = 80.dp, end = 16.dp, bottom = 8.dp), fontSize = 12.sp, color = c.muted, maxLines = 2)
        if (archived || expired) TextButton(onClick = { onAction(med, "renew") }, modifier = Modifier.padding(start = 73.dp)) { Text("Rinnova terapia", color = c.blue) }
        times.forEach { time ->
            val taken = store.dose(today(), med.str("id"), time).bool("taken")
            val overdue = doseOverdue(time, taken, currentTime)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (overdue) Color(0xFFFFF4EB) else Color(0xFFF7F4FF))
                    .border(if (overdue) 1.dp else 0.dp, if (overdue) Color(0xFFFFBA82) else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { store.toggleDose(med, time, !taken); onAction(med, "refresh") }
                    .padding(start = 7.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = taken, onCheckedChange = { store.toggleDose(med, time, it); onAction(med, "refresh") }, modifier = Modifier.size(39.dp), colors = CheckboxDefaults.colors(checkedColor = c.blue, uncheckedColor = c.muted))
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dose di oggi", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(if (time == "00:00") "24:00" else time, fontSize = 13.sp, color = c.muted)
                }
                if (overdue) Row(Modifier.clip(CircleShape).background(Color(0xFFFFE2C8)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(16.dp), tint = Color(0xFFAE5415))
                    Spacer(Modifier.width(4.dp))
                    Text("In ritardo", fontSize = 12.sp, color = Color(0xFFAE5415))
                } else Text(if (taken) "Assunta" else "Da segnare", Modifier.clip(CircleShape).background(c.lilac).padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, color = c.purple)
            }
        }
        if (prn) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { store.recordAsNeededDose(med); onAction(med, "refresh") }, colors = ButtonDefaults.buttonColors(containerColor = c.blue), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Segna dose assunta ora", fontSize = 12.sp)
                }
            }
            prnDoses.forEach { (doseKey, dose) ->
                Row(Modifier.fillMaxWidth().padding(start = 19.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Assunta alle ${dose.str("actualTime")}", Modifier.weight(1f), fontSize = 13.sp, color = c.muted)
                    IconButton(onClick = { store.toggleDose(med, doseKey, false); onAction(med, "refresh") }) { Icon(Icons.Default.Undo, "Annulla assunzione", tint = c.blue) }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TherapyAction("Foto", Icons.Default.CameraAlt, c.blue, Modifier.weight(1f)) { onAction(med, "photo") }
            TherapySeparator(c)
            TherapyAction("Storico", Icons.Default.History, Color(0xFF139D78), Modifier.weight(1f)) { onAction(med, "history") }
            TherapySeparator(c)
            TherapyAction(if (archived) "Ripristina" else "Archivia", Icons.Default.Description, c.muted, Modifier.weight(1f)) { onAction(med, "archive") }
            TherapySeparator(c)
            TherapyAction("Modifica", Icons.Default.Edit, c.blue, Modifier.weight(1f)) { onEdit(med) }
            TherapySeparator(c)
            TherapyAction("Elimina", Icons.Default.Delete, Color(0xFFD93F35), Modifier.weight(1f)) { confirmDelete = true }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare $name?") },
        text = { Text("Questa operazione non può essere annullata.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(med) }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
    )
}

@Composable
private fun TherapyAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, color = color)
    }
}

@Composable
private fun TherapySeparator(c: HomeColors) {
    Box(Modifier.width(0.5.dp).height(35.dp).background(c.border))
}
