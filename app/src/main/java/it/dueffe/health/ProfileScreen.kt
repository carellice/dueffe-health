package it.dueffe.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import java.time.LocalDate
import java.time.Period

@Composable
fun ProfileScreen(
    store: HealthStore, revision: Int, listState: LazyListState, insets: PaddingValues,
    header: @Composable () -> Unit,
    onChange: () -> Unit, onContactEdit: (String, Int, JSONObject) -> Unit,
    onSettings: () -> Unit, onManageProfiles: () -> Unit
) {
    val s = MaterialTheme.colorScheme
    val context = LocalContext.current
    val original = remember(store.activeId, revision) { Triple(store.profileItem().str("nome"), store.profileItem().str("relazione"), store.profile().toString()) }
    var name by remember(store.activeId, revision) { mutableStateOf(original.first) }
    var relation by remember(store.activeId, revision) { mutableStateOf(original.second) }
    var data by remember(store.activeId, revision) { mutableStateOf(JSONObject(original.third)) }
    var importType by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    val dirty = name != original.first || relation != original.second || data.toString() != original.third
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
    val profiles = remember(revision) { JSONArray(store.profiles.toString()).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } }
    val age = runCatching { Period.between(LocalDate.parse(data.str("data_nascita")), LocalDate.now()).years }.getOrNull()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Box(Modifier.enterAnimation(0)) { header() } }
            item {
                HeroSurface(Modifier.fillMaxWidth().enterAnimation(1)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(initials(name.ifBlank { "Io" }), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOf(name, data.str("cognome")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Il tuo profilo" }, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(relation.ifBlank { null }, age?.let { "$it anni" }).joinToString(" · ").ifBlank { "Completa i tuoi dati qui sotto" }, fontSize = 14.sp, color = Color.White.copy(alpha = 0.88f))
                            }
                        }
                        val facts = listOfNotNull(
                            data.str("gruppo_sanguigno").takeIf { it.isNotBlank() }?.let { Icons.Rounded.Bloodtype to it },
                            data.str("altezza").takeIf { it.isNotBlank() }?.let { Icons.Rounded.Straighten to "$it cm" },
                            data.str("peso").takeIf { it.isNotBlank() }?.let { Icons.Rounded.MonitorWeight to "$it kg" },
                            data.str("allergie").takeIf { it.isNotBlank() }?.let { Icons.Rounded.WarningAmber to "Allergie" }
                        )
                        if (facts.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                facts.forEach { (icon, label) -> StatusPill(label, Color.White, Color.White.copy(alpha = 0.2f), icon) }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.enterAnimation(2).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    profiles.forEach { profile ->
                        val active = profile.str("id") == store.activeId
                        Row(
                            Modifier.clip(CircleShape).background(if (active) s.primary else s.surfaceContainerLowest)
                                .border(1.dp, if (active) Color.Transparent else s.outlineVariant, CircleShape)
                                .clickable { if (!active) { if (dirty) save(); store.switchProfile(profile.str("id")); onChange() } }
                                .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatar(profile.str("nome"), 30.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(profile.str("nome").ifBlank { "Profilo" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) s.onPrimary else s.onSurface)
                        }
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(s.primaryContainer).clickable(onClickLabel = "Gestisci profili", onClick = onManageProfiles), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, "Gestisci profili", tint = s.primary)
                    }
                }
            }
            item {
                ProfileSection(Icons.Rounded.Badge, "Dati personali", null, Modifier.enterAnimation(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileField("Nome", name, { name = it }, Modifier.weight(1f))
                        ProfileField("Cognome", data.str("cognome"), { change("cognome", it) }, Modifier.weight(1f))
                    }
                    ProfileChoice("Relazione", relation, profileRelations, Icons.Rounded.Group, Modifier.fillMaxWidth()) { relation = it }
                    ProfileDate(data.str("data_nascita")) { change("data_nascita", it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileChoice("Sesso", data.str("sesso"), listOf("Femmina", "Maschio", "Altro", "Preferisco non indicarlo"), Icons.Rounded.Wc, Modifier.weight(1f)) { change("sesso", it) }
                        ProfileChoice("Gruppo", data.str("gruppo_sanguigno"), listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "0-", "Non so"), Icons.Rounded.Bloodtype, Modifier.weight(1f)) { change("gruppo_sanguigno", it) }
                    }
                }
            }
            item {
                ProfileSection(Icons.Rounded.MonitorHeart, "Dati clinici", "Utili da mostrare in caso di emergenza.", Modifier.enterAnimation(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileField("Altezza (cm)", data.str("altezza"), { change("altezza", it) }, Modifier.weight(1f), Icons.Rounded.Straighten, KeyboardType.Decimal)
                        ProfileField("Peso (kg)", data.str("peso"), { change("peso", it) }, Modifier.weight(1f), Icons.Rounded.MonitorWeight, KeyboardType.Decimal)
                    }
                    ProfileField("Allergie", data.str("allergie"), { change("allergie", it) }, Modifier.fillMaxWidth(), Icons.Rounded.WarningAmber)
                    ProfileField("Patologie", data.str("patologie"), { change("patologie", it) }, Modifier.fillMaxWidth(), Icons.Rounded.MedicalInformation)
                    ProfileField("Note", data.str("note"), { change("note", it) }, Modifier.fillMaxWidth(), Icons.Rounded.StickyNote2)
                }
            }
            item {
                ProfileSection(Icons.Rounded.MedicalServices, "Medici e strutture", "Tocca un contatto per chiamarlo.", Modifier.enterAnimation(5)) {
                    ContactGroup(clinical, "Nessun medico salvato",
                        onEdit = { index, contact -> editContact("contatti_clinici", index, contact) },
                        onDelete = { index -> data = profileWithoutContact(data, "contatti_clinici", index); save() },
                        onAdd = { editContact("contatti_clinici", -1, JSONObject()) },
                        onImport = { importType = "contatti_clinici"; contactPicker.launch(phonePickerIntent()) })
                }
            }
            item {
                ProfileSection(Icons.Rounded.Emergency, "Contatti di emergenza", "Chi avvisare se succede qualcosa.", Modifier.enterAnimation(6)) {
                    ContactGroup(emergency, "Nessun contatto di emergenza",
                        onEdit = { index, contact -> editContact("contatti_emergenza", index, contact) },
                        onDelete = { index -> data = profileWithoutContact(data, "contatti_emergenza", index); save() },
                        onAdd = { editContact("contatti_emergenza", -1, JSONObject()) },
                        onImport = { importType = "contatti_emergenza"; contactPicker.launch(phonePickerIntent()) })
                    if (importError.isNotBlank()) ErrorBanner(importError)
                }
            }
            item {
                ProfileSection(Icons.Rounded.ContactPhone, "I miei recapiti", null, Modifier.enterAnimation(7)) {
                    ProfileField("Il mio telefono", data.str("telefono"), { change("telefono", it) }, Modifier.fillMaxWidth(), Icons.Rounded.Phone, KeyboardType.Phone)
                    ProfileField("La mia email", data.str("email"), { change("email", it) }, Modifier.fillMaxWidth(), Icons.Rounded.AlternateEmail, KeyboardType.Email)
                }
            }
            item {
                HealthCard(Modifier.enterAnimation(8), onClick = onSettings, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Settings, s.onSurfaceVariant, s.surfaceContainerHigh, 44.dp, 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Impostazioni", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Aspetto, promemoria, PIN e backup", fontSize = 13.sp, color = s.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = s.onSurfaceVariant)
                    }
                }
            }
        }
        AnimatedVisibility(
            dirty, Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 12.dp),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
        ) {
            ExtendedFloatingActionButton(
                onClick = { save() }, shape = CircleShape,
                icon = { Icon(Icons.Rounded.Check, null) }, text = { Text("Salva modifiche", fontWeight = FontWeight.Bold) },
                containerColor = s.primary, contentColor = s.onPrimary
            )
        }
    }
}

@Composable
private fun ProfileSection(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = MaterialTheme.colorScheme
    HealthCard(modifier, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, s.primary, s.primaryContainer, 40.dp, 13.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = s.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ProfileField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, icon: ImageVector? = null, keyboard: KeyboardType = KeyboardType.Text) {
    val s = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(s.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = s.primary); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (value.isNotBlank()) Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = s.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, color = s.onSurface, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(s.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(label, fontSize = 16.sp, lineHeight = 21.sp, color = s.onSurfaceVariant, maxLines = 1)
                    inner()
                } }
            )
        }
    }
}

@Composable
private fun ProfileChoice(label: String, value: String, options: List<String>, icon: ImageVector, modifier: Modifier, onChange: (String) -> Unit) {
    val s = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(s.surfaceContainerLow).clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = s.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = s.onSurfaceVariant, maxLines = 1)
                Text(value.ifBlank { "Seleziona" }, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = if (value.isBlank()) s.onSurfaceVariant else s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = s.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(18.dp), containerColor = s.surfaceContainerLowest) {
            DropdownMenuItem(text = { Text("Non indicato") }, onClick = { onChange(""); expanded = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }, trailingIcon = { if (option == value) Icon(Icons.Rounded.Check, null, tint = s.primary) }) }
        }
    }
}

@Composable
private fun ProfileDate(value: String, onChange: (String) -> Unit) {
    FormDate("Data di nascita", value, maxDate = LocalDate.now(), onChange = onChange)
}

@Composable
private fun ContactGroup(contacts: JSONArray, emptyLabel: String, onEdit: (Int, JSONObject) -> Unit, onDelete: (Int) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    if (contacts.length() == 0) Text(emptyLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    (0 until contacts.length()).forEach { index ->
        ContactRow(contacts.getJSONObject(index), onEdit = { onEdit(index, contacts.getJSONObject(index)) }, onDelete = { onDelete(index) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = onAdd, shape = CircleShape, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aggiungi", maxLines = 1)
        }
        OutlinedButton(onClick = onImport, shape = CircleShape, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Rounded.Contacts, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Dalla rubrica", maxLines = 1)
        }
    }
}

@Composable
private fun ContactRow(contact: JSONObject, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val phone = contact.str("telefono").trim()
    val name = listOf(contact.str("nome"), contact.str("cognome")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Contatto" }
    val kind = contact.str("tipo").ifBlank { contact.str("relazione") }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(s.surfaceContainerLow).padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(kind, phone.ifBlank { "Numero non indicato" }).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (phone.isNotBlank()) FilledIconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))) }, modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = HealthStatus.success)) {
            Icon(Icons.Rounded.Call, "Chiama $name", Modifier.size(20.dp))
        }
        OverflowMenu(listOf(
            MenuAction("Modifica", Icons.Rounded.Edit, onClick = onEdit),
            MenuAction("Elimina", Icons.Rounded.DeleteOutline, danger = true) { confirm = true }
        ), "Azioni per $name")
    }
    if (confirm) ConfirmDeleteDialog(name, onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false })
}

private fun profileWithoutContact(data: JSONObject, key: String, index: Int): JSONObject {
    val arr = data.optJSONArray(key) ?: JSONArray()
    return JSONObject(data.toString()).put(key, JSONArray((0 until arr.length()).filter { it != index }.map { arr.getJSONObject(it) }))
}
