package it.dueffe.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ProfileScreen(
    store: HealthStore, revision: Int, dark: Boolean,
    onSettings: () -> Unit, onProfiles: () -> Unit,
    onChange: () -> Unit, onContactEdit: (String, Int, JSONObject) -> Unit
) {
    val c = homeColors(dark)
    val context = LocalContext.current
    var name by remember(store.activeId, revision) { mutableStateOf(store.profileItem().str("nome")) }
    var relation by remember(store.activeId, revision) { mutableStateOf(store.profileItem().str("relazione")) }
    var data by remember(store.activeId, revision) { mutableStateOf(JSONObject(store.profile().toString())) }
    var importType by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    fun save() {
        store.updateProfileItem(name.trim().ifBlank { "Io" }, relation)
        store.saveProfile(data)
        onChange()
    }
    fun change(key: String, value: String) { data = JSONObject(data.toString()).put(key, value) }
    fun editContact(type: String, index: Int, contact: JSONObject) {
        save()
        onContactEdit(type, index, JSONObject(contact.toString()))
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && importType.isNotBlank()) {
            val contact = result.data?.data?.let { selectedPhoneContact(context, it) }
            if (contact == null) importError = "Impossibile leggere il contatto selezionato"
            else {
                val contacts = data.optJSONArray(importType) ?: JSONArray()
                contacts.put(contact)
                data = JSONObject(data.toString()).put(importType, contacts)
                save()
                importError = ""
            }
        }
        importType = ""
    }
    val clinical = data.optJSONArray("contatti_clinici") ?: JSONArray()
    val emergency = data.optJSONArray("contatti_emergenza") ?: JSONArray()

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Profilo sanitario", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("I tuoi dati personali e sanitari, sempre a portata di mano.", fontSize = 14.sp, lineHeight = 19.sp, color = c.muted, maxLines = 2)
            }
        }
        item {
            ProfileCard(c, dark, c.iconBlue, Icons.Default.AccountCircle, c.blue, "Dati personali") {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ProfileField("Nome", name, { name = it }, c, Modifier.weight(1f))
                    ProfileField("Cognome", data.str("cognome"), { change("cognome", it) }, c, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                ProfileChoice("Relazione", relation, listOf("Io", "Figlio/a", "Coniuge", "Partner", "Genitore", "Familiare", "Altro"), null, c, Modifier.fillMaxWidth()) { relation = it }
                Spacer(Modifier.height(8.dp))
                ProfileDate(data.str("data_nascita"), c) { change("data_nascita", it) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ProfileChoice("Sesso", data.str("sesso"), listOf("Femmina", "Maschio", "Altro", "Preferisco non indicarlo"), Icons.Default.Male, c, Modifier.weight(1f)) { change("sesso", it) }
                    ProfileChoice("Gruppo sanguigno", data.str("gruppo_sanguigno"), listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "0-", "Non so"), Icons.Default.WaterDrop, c, Modifier.weight(1f)) { change("gruppo_sanguigno", it) }
                }
            }
        }
        item {
            ProfileCard(c, dark, c.iconBlue, Icons.Default.Favorite, c.blue, "Dati clinici", "Informazioni utili per la tua salute.") {
                listOf(
                    Triple("altezza", "Altezza (cm)", Icons.Default.Straighten),
                    Triple("peso", "Peso (kg)", Icons.Default.MonitorWeight),
                    Triple("allergie", "Allergie", Icons.Default.WarningAmber),
                    Triple("patologie", "Patologie", Icons.Default.Description),
                    Triple("note", "Note", Icons.Default.Note)
                ).forEachIndexed { index, (key, label, icon) ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    ProfileField(label, data.str(key), { change(key, it) }, c, Modifier.fillMaxWidth(), icon, if (key == "altezza" || key == "peso") KeyboardType.Decimal else KeyboardType.Text)
                }
            }
        }
        item {
            ProfileCard(c, dark, c.iconBlue, Icons.Default.Phone, c.blue, "Contatti", "Numeri utili da avere sempre con te.") {
                ProfileContactGroup("Contatti clinici", "Aggiungi medico", Icons.Default.MedicalServices, c.blue, "contatti_clinici", clinical, c,
                    onEdit = { index, contact -> editContact("contatti_clinici", index, contact) },
                    onDelete = { index -> data = profileWithoutContact(data, "contatti_clinici", index); save() },
                    onAdd = { editContact("contatti_clinici", -1, JSONObject()) },
                    onImport = { importType = "contatti_clinici"; contactPicker.launch(phonePickerIntent()) })
                Spacer(Modifier.height(12.dp))
                ProfileContactGroup("Contatti di emergenza", "Aggiungi contatto", Icons.Default.LocalHospital, c.blue, "contatti_emergenza", emergency, c,
                    onEdit = { index, contact -> editContact("contatti_emergenza", index, contact) },
                    onDelete = { index -> data = profileWithoutContact(data, "contatti_emergenza", index); save() },
                    onAdd = { editContact("contatti_emergenza", -1, JSONObject()) },
                    onImport = { importType = "contatti_emergenza"; contactPicker.launch(phonePickerIntent()) })
                if (importError.isNotBlank()) Text(importError, color = Color(0xFFC72E31), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Text("I miei recapiti personali", fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Il tuo numero di telefono e la tua email.", fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
                Spacer(Modifier.height(9.dp))
                ProfileField("Il mio telefono", data.str("telefono"), { change("telefono", it) }, c, Modifier.fillMaxWidth(), Icons.Default.Phone, KeyboardType.Phone)
                Spacer(Modifier.height(8.dp))
                ProfileField("La mia email", data.str("email"), { change("email", it) }, c, Modifier.fillMaxWidth(), Icons.Default.AlternateEmail, KeyboardType.Email)
            }
        }
        item {
            Button(
                onClick = { save() }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Salva profilo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProfileRoundAction(icon: ImageVector, description: String, c: HomeColors, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)) {
        Icon(icon, description, Modifier.size(22.dp), tint = c.muted)
    }
}

@Composable
private fun ProfileCard(c: HomeColors, dark: Boolean, headerColor: Color, icon: ImageVector, iconTint: Color, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(c.surface).border(0.5.dp, c.border.copy(alpha = 0.45f), RoundedCornerShape(15.dp))) {
        Row(Modifier.fillMaxWidth().background(scheme.primaryContainer).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = scheme.primary) }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.ink)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun ProfileField(label: String, value: String, onChange: (String) -> Unit, c: HomeColors, modifier: Modifier, icon: ImageVector? = null, keyboard: KeyboardType = KeyboardType.Text) {
    Row(modifier.height(54.dp).clip(RoundedCornerShape(10.dp)).border(0.7.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(21.dp), tint = c.muted); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (value.isNotBlank()) Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = c.muted, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, color = c.ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) Text(label, fontSize = 16.sp, lineHeight = 21.sp, color = c.muted, maxLines = 1)
                    inner()
                } }
            )
        }
    }
}

@Composable
private fun ProfileChoice(label: String, value: String, options: List<String>, icon: ImageVector?, c: HomeColors, modifier: Modifier, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).border(0.7.dp, c.border, RoundedCornerShape(10.dp)).clickable { expanded = true }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(21.dp), tint = c.blue); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = c.muted, maxLines = 1)
                Text(value.ifBlank { "Seleziona" }, fontSize = 16.sp, lineHeight = 21.sp, color = if (value.isBlank()) c.muted else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("▾", fontSize = 19.sp, color = c.muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shadowElevation = 0.dp) {
            DropdownMenuItem(text = { Text("Non indicato") }, onClick = { onChange(""); expanded = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun ProfileDate(value: String, c: HomeColors, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).border(0.7.dp, c.border, RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(21.dp), tint = c.muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Data di nascita", fontSize = 12.sp, lineHeight = 16.sp, color = c.muted)
            Text(if (value.isBlank()) "Seleziona data" else displayDate(value), fontSize = 16.sp, lineHeight = 21.sp, color = if (value.isBlank()) c.muted else c.ink)
        }
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = c.muted)
    }
    if (open) {
        val selected = runCatching { LocalDate.parse(value) }.getOrNull()
        val maxDate = LocalDate.now()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected?.toEpochDay()?.times(86_400_000),
            yearRange = 1900..maxDate.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxDate.toEpochDay() * 86_400_000
            }
        )
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text("Conferma") }
        }, dismissButton = {
            Row {
                if (value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text("Rimuovi") }
                TextButton(onClick = { open = false }) { Text("Annulla") }
            }
        }) { DatePicker(picker) }
    }
}

@Composable
private fun ProfileContactGroup(title: String, addLabel: String, icon: ImageVector, tint: Color, type: String, contacts: JSONArray, c: HomeColors, onEdit: (Int, JSONObject) -> Unit, onDelete: (Int) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = accent) }
        Spacer(Modifier.width(11.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
    }
    if (contacts.length() == 0) {
        Text("Nessun contatto salvato", Modifier.padding(start = 47.dp, top = 5.dp, bottom = 6.dp), fontSize = 13.sp, color = c.muted)
    } else {
        (0 until contacts.length()).forEach { index ->
            ProfileContactRow(contacts.getJSONObject(index), c, Modifier.padding(top = 8.dp), onEdit = { onEdit(index, contacts.getJSONObject(index)) }, onDelete = { onDelete(index) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f).height(42.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text(addLabel, fontSize = 13.sp, maxLines = 1)
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(0.85f).height(42.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Icon(Icons.Default.Contacts, null, Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("Rubrica", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProfileContactRow(contact: JSONObject, c: HomeColors, modifier: Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val phone = contact.str("telefono").trim()
    val name = listOf(contact.str("nome"), contact.str("cognome")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Contatto" }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.iconBlue.copy(alpha = 0.45f)).padding(start = 14.dp, end = 5.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = phone.isNotBlank()) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
        }.padding(vertical = 5.dp)) {
            Text(name, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1)
            val kind = contact.str("tipo").ifBlank { contact.str("relazione") }
            if (kind.isNotBlank()) Text(kind, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted, maxLines = 1)
            Text(if (phone.isBlank()) "Numero non indicato" else "$phone  ·  Tocca per chiamare", fontSize = 14.sp, lineHeight = 20.sp, color = if (phone.isBlank()) c.muted else c.blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.MoreVert, "Azioni per $name", Modifier.size(24.dp), tint = c.muted)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, shadowElevation = 0.dp) {
                DropdownMenuItem(text = { Text("Modifica", fontSize = 15.sp) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text("Elimina", fontSize = 15.sp) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; confirm = true })
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Eliminare contatto?") }, confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } })
}

private fun profileWithoutContact(data: JSONObject, key: String, index: Int): JSONObject {
    val arr = data.optJSONArray(key) ?: JSONArray()
    return JSONObject(data.toString()).put(key, JSONArray((0 until arr.length()).filter { it != index }.map { arr.getJSONObject(it) }))
}
