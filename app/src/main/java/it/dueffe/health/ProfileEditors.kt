package it.dueffe.health

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

internal val profileRelations = listOf("Io", "Figlio/a", "Coniuge", "Partner", "Genitore", "Familiare", "Altro")

@Composable
internal fun ContactEditor(clinical: Boolean, initial: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var data by remember(initial) { mutableStateOf(JSONObject(initial.toString())) }
    var error by remember { mutableStateOf("") }
    fun field(key: String, value: String) { data = JSONObject(data.toString()).put(key, value) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contact = result.data?.data?.let { selectedPhoneContact(context, it) }
            if (contact == null) error = "Impossibile leggere il contatto selezionato"
            else {
                data = JSONObject(data.toString()).put("nome", contact.str("nome")).put("cognome", "").put("telefono", contact.str("telefono"))
                error = ""
            }
        }
    }
    BackHandler(onBack = onDismiss)
    FormScaffold(
        if (clinical) "Contatto clinico" else "Contatto di emergenza", onDismiss, "Salva contatto",
        onPrimary = { if (data.str("nome").isBlank() && data.str("cognome").isBlank()) error = "Inserisci nome o cognome" else onSave(data) }
    ) {
        HealthCard(onClick = { contactPicker.launch(phonePickerIntent()) }, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Contacts, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer, 44.dp, 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scegli dalla rubrica", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Compila nome e telefono in un tocco.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FormText(if (clinical) "Specialità (es. medico di base)" else "Relazione (es. sorella)", data.str(if (clinical) "tipo" else "relazione")) { field(if (clinical) "tipo" else "relazione", it) }
        FormText("Nome", data.str("nome")) { field("nome", it) }
        FormText("Cognome", data.str("cognome")) { field("cognome", it) }
        FormText("Telefono", data.str("telefono"), KeyboardType.Phone) { field("telefono", it) }
        if (clinical) FormText("Email", data.str("email"), KeyboardType.Email) { field("email", it) }
        FormText("Note", data.str("note")) { field("note", it) }
        AnimatedVisibility(error.isNotBlank()) { ErrorBanner(error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileManager(store: HealthStore, revision: Int, onDismiss: () -> Unit, onChange: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf("") }
    val profiles = remember(revision) { JSONArray(store.profiles.toString()).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } }
    val s = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)
    FormScaffold("Profili", onDismiss) {
        FormHint("Gestisci i dati sanitari di più persone, ad esempio i tuoi figli o un genitore. Ogni profilo ha terapie, visite e documenti separati.")
        profiles.forEachIndexed { index, item ->
            val active = item.str("id") == store.activeId
            HealthCard(Modifier.enterAnimation(index), onClick = { store.switchProfile(item.str("id")); onChange(); onDismiss() }, padding = PaddingValues(14.dp),
                color = if (active) s.primaryContainer else s.surfaceContainerLowest) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(item.str("nome"), 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.str("nome").ifBlank { "Senza nome" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.str("relazione").ifBlank { "Profilo" }, fontSize = 13.sp, color = s.onSurfaceVariant)
                    }
                    if (active) StatusPill("Attivo", s.primary, s.surfaceContainerLowest, Icons.Rounded.Check)
                    if (item.str("id") != "main") IconButton(onClick = { deleteId = item.str("id") }) { Icon(Icons.Rounded.DeleteOutline, "Elimina profilo", tint = s.error) }
                }
            }
        }
        AnimatedVisibility(adding) {
            HealthCard {
                Text("Nuovo profilo", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FormText("Nome", name) { name = it }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileRelations.forEach { option -> SelectChip(option, relation == option) { relation = if (relation == option) "" else option } }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { adding = false }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Annulla") }
                    Button(onClick = {
                        if (name.isNotBlank() && store.profiles.length() < 5) { store.addProfile(name.trim(), relation.trim()); name = ""; relation = ""; adding = false; onChange() }
                    }, shape = CircleShape, modifier = Modifier.weight(1f), enabled = name.isNotBlank()) { Text("Crea profilo") }
                }
            }
        }
        if (!adding) {
            if (store.profiles.length() < 5) FilledTonalButton(onClick = { adding = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi profilo", fontWeight = FontWeight.Bold)
            } else FormHint("Hai raggiunto il massimo di 5 profili.")
        }
    }
    if (deleteId.isNotBlank()) ConfirmDeleteDialog(
        profiles.firstOrNull { it.str("id") == deleteId }?.str("nome")?.ifBlank { null } ?: "questo profilo",
        "Verranno eliminati anche tutti i dati sanitari del profilo.",
        onConfirm = { store.deleteProfile(deleteId); deleteId = ""; onChange() }, onDismiss = { deleteId = "" }
    )
}

@Composable
internal fun PinEditor(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pinLength by remember { mutableIntStateOf(4) }
    BackHandler(onBack = onDismiss)
    FormScaffold("Imposta PIN", onDismiss, "Salva PIN", onPrimary = {
        if (first.length != pinLength || first != second) error = "I PIN devono coincidere e avere $pinLength cifre" else onSave(first)
    }) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            IconTile(Icons.Rounded.Lock, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 84.dp, 28.dp)
        }
        FormHint("Il PIN verrà chiesto ogni volta che apri Health. Non dimenticarlo: non è possibile recuperarlo.")
        FormLabel("Lunghezza del PIN")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 6).forEach { length -> SelectChip("$length cifre", pinLength == length) { pinLength = length; first = ""; second = ""; error = "" } }
        }
        OutlinedTextField(first, { if (it.length <= pinLength && it.all(Char::isDigit)) first = it }, label = { Text("Nuovo PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = formFieldColors())
        OutlinedTextField(second, { if (it.length <= pinLength && it.all(Char::isDigit)) second = it }, label = { Text("Conferma PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = formFieldColors())
        AnimatedVisibility(error.isNotBlank()) { ErrorBanner(error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSwitcherSheet(store: HealthStore, revision: Int, onDismiss: () -> Unit, onSwitch: (String) -> Unit, onManage: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val profiles = remember(revision) { JSONArray(store.profiles.toString()).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = s.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Di chi sono i dati?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Ogni profilo ha terapie, visite e documenti separati.", fontSize = 14.sp, color = s.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            profiles.forEach { item ->
                val active = item.str("id") == store.activeId
                HealthCard(onClick = { onSwitch(item.str("id")) }, padding = PaddingValues(12.dp), color = if (active) s.primaryContainer else s.surfaceContainerLowest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(item.str("nome"), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.str("nome").ifBlank { "Senza nome" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(item.str("relazione").ifBlank { "Profilo" }, fontSize = 13.sp, color = s.onSurfaceVariant)
                        }
                        if (active) Icon(Icons.Rounded.CheckCircle, "Profilo attivo", tint = s.primary)
                    }
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.ManageAccounts, null); Spacer(Modifier.width(8.dp)); Text("Gestisci profili")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val s = MaterialTheme.colorScheme
    data class Option(val key: String, val label: String, val hint: String, val icon: ImageVector, val area: Area)
    val options = listOf(
        Option("medicines", "Farmaco", "Terapia e orari", Icons.Rounded.Medication, Area.Therapy),
        Option("appointments", "Visita", "Appuntamento o esame", Icons.Rounded.EventAvailable, Area.Visit),
        Option("bookingReminders", "Da prenotare", "Promemoria", Icons.Rounded.NotificationsActive, Area.Visit),
        Option("documents", "Documento", "Referto o ricetta", Icons.Rounded.Description, Area.Document)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = s.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cosa vuoi aggiungere?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            options.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { column, option ->
                        val c = areaColors(option.area)
                        HealthCard(Modifier.weight(1f).enterAnimation(row * 2 + column), onClick = { onAdd(option.key) }, color = c.container, padding = PaddingValues(16.dp)) {
                            IconTile(option.icon, c.container, c.accent, 46.dp, 15.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(option.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onContainer)
                            Text(option.hint, fontSize = 12.sp, color = c.onContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }
    }
}
