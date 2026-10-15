package it.dueffe.health

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.app.Activity
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HealthApp() }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
internal fun displayDate(value: String) = runCatching { LocalDate.parse(value).format(dateFmt) }.getOrDefault(value)
private fun timeToMinutes(value: String) = runCatching { LocalTime.parse(value).toSecondOfDay() / 60 }.getOrDefault(0)
internal fun phonePickerIntent() = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
internal fun selectedPhoneContact(context: android.content.Context, uri: android.net.Uri): JSONObject? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
        val phone = if (phoneIndex >= 0) cursor.getString(phoneIndex).orEmpty() else ""
        if (name.isBlank() && phone.isBlank()) null else JSONObject().put("nome", name.ifBlank { phone }).put("telefono", phone)
    }
}.getOrNull()
private fun active(med: JSONObject) = !med.bool("archiviata") && (med.bool("senzaFine", true) || med.str("dataFine").isBlank() || med.str("dataFine") >= today()) && (med.str("dataInizio").isBlank() || med.str("dataInizio") <= today())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HealthApp() {
    val context = LocalContext.current
    val store = remember { HealthStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dynamicColorEnabled by remember { mutableStateOf(store.settings.bool("dynamicColor")) }
    var selectedPaletteId by remember { mutableStateOf(store.settings.str("colorPalette").ifBlank { "material" }) }
    fun refresh() { revision++; ReminderScheduler(context).scheduleAll() }
    LaunchedEffect(Unit) { ReminderScheduler(context).scheduleAll() }
    var section by remember { mutableStateOf("Oggi") }
    var visitFabExpanded by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(!store.settings.bool("onboarded")) }
    var welcomeName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactEditing by remember { mutableStateOf<Triple<String, Int, JSONObject>?>(null) }
    var showProfiles by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(store.settings.bool("pinEnabled")) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(store.settings.bool("biometricEnabled")) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Tutti") }
    var pendingAttachment by remember { mutableStateOf<JSONObject?>(null) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingMedicinePhoto by remember { mutableStateOf<JSONObject?>(null) }
    var toast by remember { mutableStateOf("") }
    var historyMedicine by remember { mutableStateOf<JSONObject?>(null) }
    var bookingToComplete by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { store.importProfile(it.readText()) }; refresh() }.onFailure { toast = "Importazione non riuscita" }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportProfile()) } }.onFailure { toast = "Esportazione non riuscita" }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val doc = pendingAttachment
        if (uri != null && doc != null) runCatching {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            val id = doc.str("id").ifBlank { java.util.UUID.randomUUID().toString().also { doc.put("id", it) } }
            val file = store.attachmentFile(id, extension)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            doc.put("fileName", uri.lastPathSegment ?: "Allegato").put("fileType", mime).put("attachment", file.name)
            store.put("documents", doc); refresh()
        }.onFailure { toast = "Allegato non salvato" }
        pendingAttachment = null
    }
    val medicinePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val med = pendingMedicinePhoto
        if (uri != null && med != null) runCatching {
            val id = med.str("id").ifBlank { java.util.UUID.randomUUID().toString().also { med.put("id", it) } }
            val file = store.attachmentFile("medicine_$id", "jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val source = android.graphics.BitmapFactory.decodeStream(input)
                val ratio = 320f / maxOf(source.width, source.height)
                val resized = if (ratio < 1f) android.graphics.Bitmap.createScaledBitmap(source, (source.width * ratio).toInt().coerceAtLeast(1), (source.height * ratio).toInt().coerceAtLeast(1), true) else source
                file.outputStream().use { output -> resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, output) }
            }
            med.put("photo", file.name); store.put("medicines", med); refresh()
        }.onFailure { toast = "Foto del farmaco non salvata" }
        pendingMedicinePhoto = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val doc = pendingAttachment
        val file = pendingCameraFile
        if (saved && doc != null && file != null) {
            doc.put("fileName", "Foto ${today()}.jpg").put("fileType", "image/jpeg").put("attachment", file.name)
            store.put("documents", doc); refresh()
        } else file?.delete()
        pendingAttachment = null; pendingCameraFile = null
    }
    fun captureDocument(doc: JSONObject) {
        val id = doc.str("id").ifBlank { java.util.UUID.randomUUID().toString().also { doc.put("id", it) } }
        val file = store.attachmentFile(id, "jpg")
        pendingAttachment = doc; pendingCameraFile = file
        cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.setSetting("notifications", granted); refresh(); if (!granted) toast = "Permesso notifiche negato"
    }
    fun openAttachment(doc: JSONObject) {
        if (doc.str("attachment").isBlank()) { toast = "Nessun allegato da aprire"; return }
        val file = store.attachmentPath(doc.str("attachment"))
        if (!file.exists()) { toast = "Allegato non disponibile"; return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, doc.str("fileType").ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { toast = "Nessuna app per aprire l'allegato" }
    }

    val dark = false
    val useDynamicColor = dynamicColorEnabled
    val paletteId = selectedPaletteId
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFA9C7FF),
            secondary = androidx.compose.ui.graphics.Color(0xFFA9C7FF),
            tertiary = androidx.compose.ui.graphics.Color(0xFFA9C7FF),
            background = androidx.compose.ui.graphics.Color(0xFF111318),
            surface = androidx.compose.ui.graphics.Color(0xFF111318),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF43474F),
            outline = androidx.compose.ui.graphics.Color(0xFF8D9199)
        )
    } else {
        healthColorScheme(paletteId)
    }
    MaterialTheme(colorScheme = colors) {
        val activity = context as? Activity
        fun promptBiometric() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || activity == null) {
                pinError = "Biometria non disponibile: accedi con il PIN."
                return
            }
            @Suppress("NewApi")
            runCatching {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("Sblocca Health")
                    .setSubtitle("Usa la biometria configurata sul dispositivo")
                    .setNegativeButton("Usa il PIN", executor) { _: DialogInterface, _: Int -> }
                    .build()
                prompt.authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        pin = ""; pinError = ""; locked = false
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                            errString?.toString() != "Usa il PIN") {
                            pinError = errString?.toString()?.ifBlank { "Biometria non riuscita: accedi con il PIN." }
                                ?: "Biometria non riuscita: accedi con il PIN."
                        }
                    }
                })
            }.onFailure { pinError = "Biometria non disponibile: accedi con il PIN." }
        }
        LaunchedEffect(locked, biometricEnabled) {
            if (locked && biometricEnabled) promptBiometric()
        }
        if (locked) {
            Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp)); Text("Health", style = MaterialTheme.typography.headlineMedium)
                Text("Inserisci il PIN per accedere ai tuoi dati")
                Spacer(Modifier.height(24.dp))
                val pinLength = store.settings.str("pin").length.let { if (it == 6) 6 else 4 }
                OutlinedTextField(pin, { if (it.length <= pinLength && it.all(Char::isDigit)) { pin = it; pinError = ""; if (it.length == pinLength) { if (it == store.settings.str("pin")) { locked = false; pin = "" } else pinError = "PIN errato" } } }, label = { Text("PIN ($pinLength cifre)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), isError = pinError.isNotBlank())
                if (pinError.isNotBlank()) Text(pinError, color = MaterialTheme.colorScheme.error)
                if (biometricEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { promptBiometric() }) { Text("Usa biometria") }
                }
            } }
        } else if (showWelcome) {
            WelcomeEditor(welcomeName, onNameChange = { welcomeName = it }, onDismiss = { showWelcome = false; store.setSetting("onboarded", true) }, onSave = {
                if (welcomeName.isNotBlank()) { store.updateProfileItem(welcomeName.trim(), "Io"); refresh() }
                store.setSetting("onboarded", true); showWelcome = false
            })
        } else if (editing != null) {
            val (key, item) = editing!!
            if (key == "medicines") MedicineEditor(item, onDismiss = { editing = null }, onSave = { saved -> store.put("medicines", saved); editing = null; refresh() })
            else GuidedEntityEditor(key, item, store, onDismiss = { editing = null; bookingToComplete = "" }, onSave = { saved ->
                val oldAttachment = if (key == "documents") item.str("attachment") else ""
                store.put(key, saved)
                if (oldAttachment.isNotBlank() && oldAttachment != saved.str("attachment")) store.attachmentPath(oldAttachment).delete()
                if (key == "appointments" && bookingToComplete.isNotBlank()) { store.list("bookingReminders").firstOrNull { it.str("id") == bookingToComplete }?.let { it.put("prenotata", true); store.put("bookingReminders", it) }; bookingToComplete = "" }
                editing = null; refresh()
            })
        } else if (contactEditing != null) {
            val (type, index, initial) = contactEditing!!
            ContactEditor(type == "contatti_clinici", initial, onDismiss = { contactEditing = null }, onSave = { contact ->
                val profile = store.profile()
                val arr = profile.optJSONArray(type) ?: JSONArray()
                val contacts = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
                if (index in contacts.indices) contacts[index] = contact else contacts.add(contact)
                store.saveProfile(JSONObject(profile.toString()).put(type, JSONArray(contacts)))
                contactEditing = null; refresh()
            })
        } else if (showProfiles) {
            ProfileManager(store, revision, onDismiss = { showProfiles = false }, onChange = { refresh() })
        } else if (showPin) {
            PinEditor(onDismiss = { showPin = false }, onSave = { store.setSetting("pin", it); store.setSetting("pinEnabled", true); showPin = false; refresh() })
        } else {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = detail,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(190)) + scaleOut(tween(240), targetScale = 1.02f))
                    },
                    label = "Apertura dettaglio"
                ) { activeDetail ->
                    CompositionLocalProvider(
                        LocalHealthSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalHealthAnimatedVisibilityScope provides this@AnimatedContent
                    ) {
                        if (activeDetail != null) {
                            val (key, id) = activeDetail
                            val item = store.list(key).firstOrNull { it.str("id") == id }
                            EntityDetailPage(
                                key = key, item = item, store = store, dark = dark,
                                onBack = { detail = null },
                                onEdit = { if (item != null) editing = key to JSONObject(item.toString()) },
                                onOpenAttachment = { if (item != null) openAttachment(item) }
                            )
                        } else Scaffold(
                topBar = {
                    Surface(color = homeColors(false).background) {
                        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 17.dp, end = 17.dp, top = 13.dp, bottom = 10.dp)) {
                            HomeHeader(store.profileItem().str("nome"), homeColors(false), onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true })
                        }
                    }
                },
                bottomBar = { HomeNavigationBar(selected = section, onSelect = { section = it; search = ""; filter = "Tutti"; visitFabExpanded = false }) },
                floatingActionButton = {
                    if (section == "Visite") {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimatedVisibility(visitFabExpanded) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ExtendedFloatingActionButton(
                                        onClick = { editing = "bookingReminders" to JSONObject(); visitFabExpanded = false },
                                        icon = { Icon(Icons.Default.Notifications, null) }, text = { Text("Nuovo promemoria") },
                                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                                    )
                                    ExtendedFloatingActionButton(
                                        onClick = { editing = "appointments" to JSONObject(); visitFabExpanded = false },
                                        icon = { Icon(Icons.Default.CalendarMonth, null) }, text = { Text("Aggiungi visita") },
                                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                                    )
                                }
                            }
                            FloatingActionButton(onClick = { visitFabExpanded = !visitFabExpanded }, modifier = Modifier.size(60.dp), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                                Icon(if (visitFabExpanded) Icons.Default.Close else Icons.Default.Add, if (visitFabExpanded) "Chiudi" else "Aggiungi", modifier = Modifier.size(30.dp))
                            }
                        }
                    } else if (section in listOf("Terapie", "Documenti")) {
                        FloatingActionButton(onClick = { editing = (if (section == "Terapie") "medicines" else "documents") to JSONObject() }, modifier = Modifier.size(60.dp), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                            Icon(Icons.Default.Add, "Aggiungi $section", modifier = Modifier.size(30.dp))
                        }
                    }
                }
                        ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    when(section) {
                        "Oggi" -> HomeDashboard(store, revision, dark, onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true }, onSection = { section = it }, onAdd = { key -> editing = key to JSONObject() }, onTake = { med, time -> store.toggleDose(med, time, !store.dose(today(), med.str("id"), time).bool("taken")); refresh() }, onOpenVisit = { detail = "appointments" to it.str("id") })
                        "Terapie" -> TherapyPage(store, revision, dark, search, { search = it }, filter, { filter = it }, onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true }, onOpen = { detail = "medicines" to it.str("id") }, onEdit = { editing = Pair("medicines", JSONObject(it.toString())) }, onDelete = { store.delete("medicines", it.str("id")); refresh() }, onAction = { item, action ->
                            when(action) {
                                "dose" -> { val first = item.strings("orari").firstOrNull() ?: "00:00"; store.setDose(today(), item.str("id"), first, !store.dose(today(), item.str("id"), first).bool("taken")); refresh() }
                                "archive" -> { item.put("archiviata", !item.bool("archiviata")); store.put("medicines", item); refresh() }
                                "history" -> historyMedicine = item
                                "photo" -> { pendingMedicinePhoto = item; medicinePhotoLauncher.launch(arrayOf("image/*")) }
                                "renew" -> { item.put("archiviata", true); store.put("medicines", item); val renewed = JSONObject(item.toString()); renewed.remove("id"); renewed.put("archiviata", false).put("dataInizio", today()).put("dataFine", "").put("senzaFine", true); editing = Pair("medicines", renewed); refresh() }
                                "refresh" -> refresh()
                            }
                        })
                        "Visite" -> VisitPage(store, revision, dark, search, { search = it }, filter, { filter = it }, onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true }, onOpen = { item, booking -> detail = (if (booking) "bookingReminders" else "appointments") to item.str("id") }, onEdit = { editing = Pair("appointments", JSONObject(it.toString())) }, onDelete = { store.delete("appointments", it.str("id")); refresh() }, onAction = { item, action -> if (action == "complete") { item.put("completata", !item.bool("completata")); store.put("appointments", item); refresh() } else if (action == "referti") { val reminder = item.optJSONObject("refertiReminder") ?: JSONObject(); reminder.put("ritirati", !reminder.bool("ritirati")); item.put("refertiReminder", reminder); store.put("appointments", item); refresh() } else if (action == "deleteBooking") { store.delete("bookingReminders", item.str("id")); refresh() } }, onBooking = { editing = Pair("bookingReminders", it ?: JSONObject()) }, onBookNow = { booking -> bookingToComplete = booking.str("id"); editing = Pair("appointments", JSONObject().put("tipo", booking.str("tipoVisita"))) })
                        "Documenti" -> DocumentPage(store, revision, dark, search, { search = it }, filter, { filter = it }, onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true }, onOpen = { detail = "documents" to it.str("id") }, onEdit = { editing = Pair("documents", JSONObject(it.toString())) }, onDelete = { val file = it.str("attachment"); if (file.isNotBlank()) store.attachmentPath(file).delete(); store.delete("documents", it.str("id")); refresh() }, onAction = { item, action -> if (action == "open") openAttachment(item) else if (action == "camera") captureDocument(item) else { pendingAttachment = item; attachmentLauncher.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) } })
                        "Profilo" -> ProfileScreen(store, revision, dark, onSettings = { section = "Impostazioni" }, onProfiles = { showProfiles = true }, onChange = { refresh() }, onContactEdit = { type, index, contact -> contactEditing = Triple(type, index, contact) })
                        "Impostazioni" -> SettingsPage(store, revision, dark, onProfiles = { showProfiles = true }, onTheme = { refresh() }, onDynamicColor = {
                            dynamicColorEnabled = it
                            store.setSetting("dynamicColor", it); refresh()
                        }, onPalette = {
                            selectedPaletteId = it
                            store.setSetting("colorPalette", it); refresh()
                        }, onPin = { showPin = true }, onBiometric = { enabled ->
                            biometricEnabled = enabled
                            store.setSetting("biometricEnabled", enabled)
                            refresh()
                        }, onNotifications = {
                            if (!store.settings.bool("notifications") && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { store.setSetting("notifications", !store.settings.bool("notifications")); refresh() }
                        }, onExport = { exportLauncher.launch("dueffe_health_backup_${today()}.json") }, onImport = { importLauncher.launch(arrayOf("application/json")) }, onClear = { store.clearProfile(); refresh() })
                    }
                }
                        }
                    }
                }
            }
        }
        if (historyMedicine != null) DoseHistoryDialog(store, historyMedicine!!, onDismiss = { historyMedicine = null })
        if (toast.isNotBlank()) AlertDialog(onDismissRequest = { toast = "" }, title = { Text("Avviso") }, text = { Text(toast) }, confirmButton = { TextButton(onClick = { toast = "" }) { Text("OK") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeEditor(name: String, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Scaffold(topBar = { TopAppBar(title = { Text("Benvenuto in Health") }) }, bottomBar = { Surface(shadowElevation = 0.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Più tardi") }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Inizia") }
    } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Terapie, visite e documenti, sempre con te e conservati sul dispositivo.")
            FormText("Come ti chiami?", name, onChange = onNameChange)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium) } }

@Composable
private fun EntityList(store: HealthStore, revision: Int, key: String, search: String, setSearch: (String) -> Unit, filter: String, setFilter: (String) -> Unit, onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit, onAction: (JSONObject, String) -> Unit, onBooking: ((JSONObject?) -> Unit)? = null, onBookNow: ((JSONObject) -> Unit)? = null) {
    val title = when(key) { "medicines" -> "Terapie"; "appointments" -> "Visite"; else -> "Documenti" }
    val filters = when(key) { "medicines" -> listOf("Tutti", "Attivi", "Scaduti", "Archiviati"); "appointments" -> listOf("Tutti", "Prossime", "Passate", "Da prenotare"); else -> listOf("Tutti", "Analisi", "Referti", "Ricette", "Altro") }
    val source = remember(revision, store.activeId, key, filter) { if (filter == "Da prenotare") store.list("bookingReminders") else store.list(key) }
    val list = source.filter { item ->
        val name = when(key) { "medicines" -> item.str("nome"); "appointments" -> item.str("tipo").ifBlank { item.str("tipoVisita") }; else -> item.str("nome") }
        val match = name.contains(search, ignoreCase = true) && (filter != "Da prenotare" || !item.bool("prenotata"))
        val kind = when(key) {
            "medicines" -> when(filter) { "Attivi" -> active(item); "Scaduti" -> !item.bool("archiviata") && !item.bool("senzaFine", true) && item.str("dataFine") < today(); "Archiviati" -> item.bool("archiviata"); else -> true }
            "appointments" -> when(filter) { "Prossime" -> item.str("data") >= today() && !item.bool("completata"); "Passate" -> item.str("data") < today() || item.bool("completata"); else -> true }
            else -> filter == "Tutti" || item.str("categoria") == filter
        }
        match && kind
    }.sortedWith(compareBy<JSONObject> { if (key == "medicines") it.str("nome") else it.str("data") })
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        OutlinedTextField(search, setSearch, label = { Text("Cerca") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { filters.forEach { item -> FilterChip(selected = filter == item, onClick = { setFilter(item) }, label = { Text(item) }) } }
        if (key == "appointments") TextButton(onClick = { onBooking?.invoke(null) }, modifier = Modifier.padding(start = 12.dp)) { Icon(Icons.Default.AddAlert, null); Spacer(Modifier.width(6.dp)); Text("Promemoria prenotazione") }
        if (list.isEmpty()) EmptyState("Nessun elemento", "Usa + per aggiungerne uno")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.str("id") }) { item ->
                var confirmDelete by remember { mutableStateOf(false) }
                val name = when(key) { "medicines" -> item.str("nome"); "appointments" -> item.str("tipo").ifBlank { item.str("tipoVisita") }; else -> item.str("nome") }
                ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp,
                    hoveredElevation = 0.dp, draggedElevation = 0.dp, disabledElevation = 0.dp
                )) { Column(Modifier.padding(16.dp)) {
                    val name = when(key) { "medicines" -> item.str("nome"); "appointments" -> item.str("tipo").ifBlank { item.str("tipoVisita") }; else -> item.str("nome") }
                    if (key == "medicines" && item.str("photo").isNotBlank()) {
                        val photo = remember(item.str("photo"), store.attachmentPath(item.str("photo")).lastModified()) { runCatching { android.graphics.BitmapFactory.decodeFile(store.attachmentPath(item.str("photo")).path) }.getOrNull() }
                        if (photo != null) Image(photo.asImageBitmap(), contentDescription = "Foto di $name", modifier = Modifier.size(64.dp), contentScale = ContentScale.Crop)
                    }
                    Text(name.ifBlank { "Senza nome" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val subtitle = when(key) { "medicines" -> "${item.str("dosaggio")} ${item.str("unita")} · ${medicineFrequencyDescription(item)}${if (medicineFrequencyType(item) == "as_needed") "" else " · ${item.strings("orari").joinToString(", ")}"}"; "appointments" -> if (filter == "Da prenotare") "Da prenotare · ${item.str("periodo")}" else "${displayDate(item.str("data"))} ${item.str("ora")} · ${item.str("medico")}"; else -> "${item.str("categoria")} · ${displayDate(item.str("data"))}" }
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (key == "medicines") {
                        val qty = item.str("quantita"); if (qty.isNotBlank()) Text("Scorta: $qty ${item.str("quantitaUnita")}")
                        if (item.str("note").isNotBlank()) Text(item.str("note"), maxLines = 2)
                        medicineTimesOn(item, LocalDate.now()).forEach { time -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(store.dose(today(), item.str("id"), time).bool("taken"), onCheckedChange = { store.toggleDose(item, time, it); onAction(item, "refresh") }); Text("Dose ${if (time == "00:00") "24:00" else time}") } }
                        if (medicineFrequencyType(item) == "as_needed") {
                            if (active(item)) OutlinedButton(onClick = { store.recordAsNeededDose(item); onAction(item, "refresh") }) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Segna dose assunta ora") }
                            store.asNeededDoses(today(), item.str("id")).forEach { (doseKey, dose) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Assunta alle ${dose.str("actualTime")}", modifier = Modifier.weight(1f))
                                    IconButton(onClick = { store.toggleDose(item, doseKey, false); onAction(item, "refresh") }) { Icon(Icons.Default.Undo, "Annulla assunzione") }
                                }
                            }
                        }
                    }
                    if (key == "appointments" && filter != "Da prenotare" && item.str("note").isNotBlank()) Text(item.str("note"), maxLines = 2)
                    if (key == "appointments" && item.optJSONObject("refertiReminder")?.str("data")?.isNotBlank() == true) Text("Ritiro referti: ${displayDate(item.optJSONObject("refertiReminder")!!.str("data"))}${if (item.optJSONObject("refertiReminder")!!.bool("ritirati")) " · ritirati" else ""}")
                    if (key == "documents" && item.str("note").isNotBlank()) Text(item.str("note"), maxLines = 2)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Start) {
                        if (key == "medicines") { TextButton(onClick = { onAction(item, "photo") }) { Text("Foto") }; TextButton(onClick = { onAction(item, "history") }) { Text("Storico") }; if (item.bool("archiviata") || item.str("dataFine").isNotBlank() && item.str("dataFine") < today()) TextButton(onClick = { onAction(item, "renew") }) { Text("Rinnova") }; TextButton(onClick = { onAction(item, "archive") }) { Text(if (item.bool("archiviata")) "Ripristina" else "Archivia") } }
                        if (filter == "Da prenotare") TextButton(onClick = { onBookNow?.invoke(item) }) { Text("Prenota") }
                        if (key == "appointments" && item.optJSONObject("refertiReminder")?.str("data")?.isNotBlank() == true) TextButton(onClick = { onAction(item, "referti") }) { Text(if (item.optJSONObject("refertiReminder")!!.bool("ritirati")) "Da ritirare" else "Ritirati") }
                        if (key == "appointments" && filter != "Da prenotare") TextButton(onClick = { onAction(item, "complete") }) { Text(if (item.bool("completata")) "Riapri" else "Effettuata") }
                        if (key == "documents") { if (item.str("attachment").isNotBlank()) TextButton(onClick = { onAction(item, "open") }) { Text("Apri") }; TextButton(onClick = { onAction(item, "camera") }) { Text("Foto") }; TextButton(onClick = { onAction(item, "attach") }) { Text("Allega") } }
                        TextButton(onClick = { if (filter == "Da prenotare") onBooking?.invoke(item) else onEdit(item) }) { Text("Modifica") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, "Elimina") }
                    }
                } }
                if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminare $name?") }, text = { Text("Questa operazione non può essere annullata.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; if (filter == "Da prenotare") onAction(item, "deleteBooking") else onDelete(item) }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineEditor(initial: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var data by remember(initial) { mutableStateOf(JSONObject(initial.toString())) }
    var step by remember(initial) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var times by remember(initial) { mutableStateOf(initial.strings("orari").distinct().sorted()) }
    var frequency by remember(initial) { mutableStateOf(if (initial.str("id").isBlank() && initial.str("frequenza").isBlank()) "" else medicineFrequencyType(initial)) }
    var intervalDays by remember(initial) { mutableIntStateOf(initial.optInt("intervalloGiorni", 2).coerceIn(2, 30)) }
    var endByDays by remember(initial) { mutableStateOf(initial.optInt("durataGiorni", 0) > 0) }
    var durationDays by remember(initial) { mutableIntStateOf(initial.optInt("durataGiorni", 7).coerceAtLeast(1)) }
    var weekdays by remember(initial) { mutableStateOf(initial.optJSONArray("giorniSettimana")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()) }
    var advances by remember(initial) { mutableStateOf(initial.optJSONObject("notifiche")?.optJSONArray("anticipo")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 }.toSet() } ?: emptySet()) }
    var timeIndex by remember { mutableIntStateOf(-2) }
    fun field(name: String, value: String) { data = JSONObject(data.toString()).put(name, value) }
    fun flag(name: String, value: Boolean) { data = JSONObject(data.toString()).put(name, value) }
    fun endAfter(days: Int) {
        durationDays = days.coerceAtLeast(1)
        val start = runCatching { LocalDate.parse(data.str("dataInizio").ifBlank { today() }) }.getOrDefault(LocalDate.now())
        data = JSONObject(data.toString()).put("durataGiorni", durationDays).put("dataFine", start.plusDays(durationDays.toLong()).toString())
    }
    val steps = listOf("Farmaco", "Dose e frequenza", "Durata e promemoria", "Altri dettagli")
    val frequencyOptions = linkedMapOf("daily" to "Ogni giorno", "interval" to "Ogni N giorni", "weekdays" to "Giorni della settimana", "as_needed" to "Al bisogno")
    fun validateCurrentStep(): String = when (step) {
        0 -> if (data.str("nome").isBlank()) "Inserisci il nome del farmaco" else ""
        1 -> when {
            data.str("dosaggio").isBlank() -> "Indica la quantità da assumere a ogni dose"
            data.str("unita").isBlank() -> "Seleziona l'unità della dose"
            frequency.isBlank() -> "Scegli ogni quanto assumere il farmaco"
            frequency == "weekdays" && weekdays.isEmpty() -> "Scegli almeno un giorno della settimana"
            frequency != "as_needed" && times.isEmpty() -> "Aggiungi almeno un orario"
            else -> ""
        }
        2 -> when {
            !data.bool("senzaFine", true) && data.str("dataFine").isBlank() -> "Seleziona la data di fine"
            !data.bool("senzaFine", true) && data.str("dataFine") < data.str("dataInizio").ifBlank { today() } -> "La data di fine precede quella di inizio"
            else -> ""
        }
        else -> ""
    }
    BackHandler { if (step > 0) { step--; error = "" } else onDismiss() }
    Scaffold(
        topBar = { Column {
            TopAppBar(title = { Text(if (initial.str("id").isBlank()) "Nuovo farmaco" else "Modifica farmaco") }, navigationIcon = { IconButton(onClick = { if (step > 0) { step--; error = "" } else onDismiss() }) { Icon(Icons.Default.ArrowBack, "Indietro") } })
            LinearProgressIndicator(progress = { (step + 1) / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        } },
        bottomBar = { Surface(shadowElevation = 0.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (step > 0) { step--; error = "" } else onDismiss() }, modifier = Modifier.weight(1f)) { Text(if (step == 0) "Annulla" else "Indietro") }
            Button(onClick = {
                error = validateCurrentStep()
                if (error.isBlank()) {
                    if (step < steps.lastIndex) { step++; timeIndex = -2 }
                    else {
                        data.put("frequenzaTipo", frequency)
                        data.put("frequenza", frequencyOptions[frequency] ?: "")
                        data.put("intervalloGiorni", intervalDays)
                        data.put("giorniSettimana", JSONArray(weekdays.sorted()))
                        data.put("orari", JSONArray(if (frequency == "as_needed") emptyList<String>() else times.sortedBy(::timeToMinutes)))
                        if (data.str("dataInizio").isBlank()) data.put("dataInizio", today())
                        if (!data.has("senzaFine")) data.put("senzaFine", true)
                        val notifications = data.optJSONObject("notifiche") ?: JSONObject()
                        notifications.put("attive", frequency != "as_needed" && notifications.bool("attive", true))
                        notifications.put("anticipo", JSONArray(advances.sorted()))
                        data.put("notifiche", notifications)
                        onSave(data)
                    }
                }
            }, modifier = Modifier.weight(1f)) { Text(if (step == steps.lastIndex) "Salva" else "Continua") }
        } } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${step + 1} di ${steps.size} · ${steps[step]}", style = MaterialTheme.typography.headlineSmall)
            when (step) {
                0 -> {
                    Text("Inserisci i dati del farmaco come riportati sulla confezione.")
                    FormText("Nome farmaco *", data.str("nome")) { field("nome", it) }
                    FormRadioChoice("Formulazione", data.str("formulazione"), listOf("Compressa", "Capsula", "Gocce", "Sciroppo", "Soluzione", "Iniezione", "Crema", "Cerotto", "Spray", "Supposta"), { field("formulazione", it) }, optional = true)
                }
                1 -> {
                    Text("Indica quanto assumi ogni volta, seguendo la prescrizione.")
                    NumberStepper("Quantità per dose *", data.str("dosaggio"), { field("dosaggio", it) }, min = 0.1)
                    FormRadioChoice("Unità *", data.str("unita"), listOf("mg", "g", "mcg", "ml", "UI", "gocce", "compresse", "capsule"), { field("unita", it) })
                    FormRadioChoice("Ogni quanto? *", frequencyOptions[frequency] ?: "", frequencyOptions.values.toList(), { choice -> frequency = frequencyOptions.entries.firstOrNull { it.value == choice }?.key ?: "" }, custom = false)
                    if (frequency == "interval") {
                        Text("Ripeti ogni quanti giorni?", style = MaterialTheme.typography.titleMedium)
                        NumberStepper("Intervallo in giorni", intervalDays.toString(), { intervalDays = (it.toIntOrNull() ?: 2).coerceIn(2, 30) }, min = 2.0)
                    }
                    if (frequency == "weekdays") {
                        Text("Quali giorni?", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom").forEachIndexed { index, day ->
                                val number = index + 1
                                FilterChip(selected = number in weekdays, onClick = { weekdays = if (number in weekdays) weekdays - number else weekdays + number }, label = { Text(day) })
                            }
                        }
                    }
                    if (frequency == "as_needed") Text("Al bisogno: non saranno creati orari o promemoria automatici.")
                    if (frequency.isNotBlank() && frequency != "as_needed") {
                        Text("A che ora?", style = MaterialTheme.typography.titleMedium)
                        Text("Aggiungi un orario per ogni dose prevista nel giorno.")
                        times.forEachIndexed { index, time ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { timeIndex = index }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text(time) }
                                IconButton(onClick = { times = times.filterIndexed { i, _ -> i != index }; timeIndex = -2 }) { Icon(Icons.Default.DeleteOutline, "Rimuovi $time") }
                            }
                        }
                        OutlinedButton(onClick = { timeIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi orario") }
                        if (timeIndex >= -1) {
                            val current = times.getOrNull(timeIndex)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(8, 0)
                            key(timeIndex) {
                                val picker = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    TimePicker(state = picker)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { timeIndex = -2 }) { Text("Annulla") }
                                        Button(onClick = {
                                            val value = String.format(Locale.ITALY, "%02d:%02d", picker.hour, picker.minute)
                                            times = (if (timeIndex >= 0) times.filterIndexed { i, _ -> i != timeIndex } + value else times + value).distinct().sortedBy(::timeToMinutes)
                                            timeIndex = -2
                                        }) { Text("Conferma orario") }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Text("Scegli quando inizia la terapia e se ha una data di fine.")
                    FormDate("Data inizio", data.str("dataInizio").ifBlank { today() }) { start ->
                        field("dataInizio", start)
                        if (endByDays) endAfter(durationDays)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(data.bool("senzaFine", true), { flag("senzaFine", it); if (!it && endByDays) endAfter(durationDays) }); Spacer(Modifier.width(8.dp)); Text("Senza data di fine") }
                    if (!data.bool("senzaFine", true)) {
                        FormRadioChoice("Come termina la terapia?", if (endByDays) "Dopo N giorni" else "Data precisa", listOf("Data precisa", "Dopo N giorni"), { choice ->
                            endByDays = choice == "Dopo N giorni"
                            if (endByDays) endAfter(durationDays) else field("durataGiorni", "")
                        }, custom = false)
                        if (endByDays) {
                            NumberStepper("Giorni dopo l'inizio", durationDays.toString(), { endAfter(it.toIntOrNull() ?: 1) }, min = 1.0)
                            Text("Data di fine: ${displayDate(data.str("dataFine"))}")
                        } else FormDate("Data fine", data.str("dataFine")) { field("dataFine", it) }
                    }
                    if (frequency != "as_needed") {
                        val enabled = data.optJSONObject("notifiche")?.bool("attive", true) ?: true
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { data = JSONObject(data.toString()).put("notifiche", (data.optJSONObject("notifiche") ?: JSONObject()).put("attive", it)) }); Spacer(Modifier.width(8.dp)); Text("Promemoria all'orario della dose") }
                        if (enabled) {
                            Text("Vuoi anche un avviso prima?", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (setOf(5, 10, 15, 30, 60) + advances).sorted().forEach { minutes ->
                                    FilterChip(selected = minutes in advances, onClick = { advances = if (minutes in advances) advances - minutes else advances + minutes }, label = { Text(if (minutes == 60) "1 ora" else "$minutes min") })
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Text("Questi dettagli sono facoltativi. Controlla la dose e gli orari prima di salvare.")
                    val frequencySummary = when (frequency) { "interval" -> "Ogni $intervalDays giorni"; "weekdays" -> weekdays.sorted().joinToString(", ") { listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")[it - 1] }; else -> frequencyOptions[frequency].orEmpty() }
                    Text("${data.str("dosaggio")} ${data.str("unita")} · $frequencySummary${if (frequency != "as_needed") " · ${times.sortedBy(::timeToMinutes).joinToString(", ")}" else ""}", style = MaterialTheme.typography.titleMedium)
                    NumberStepper("Quantità in scorta", data.str("quantita"), { field("quantita", it) })
                    FormRadioChoice("Unità scorta", data.str("quantitaUnita"), listOf("compresse", "capsule", "fiale", "flaconi", "gocce", "ml", "g", "cerotti"), { field("quantitaUnita", it) }, optional = true)
                    NumberStepper("Soglia scorta bassa", data.str("soglia"), { field("soglia", it) })
                    FormText("Note", data.str("note")) { field("note", it) }
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}


@Composable
internal fun FormText(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(), singleLine = false) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormSuggested(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it) }, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, shadowElevation = 0.dp) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormChoice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, shadowElevation = 0.dp) {
            DropdownMenuItem(text = { Text("Non indicato") }, onClick = { onChange(""); expanded = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDate(label: String, value: String, maxDate: LocalDate? = null, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${if (value.isBlank()) "Seleziona data" else displayDate(value)}")
    }
    if (open) {
        val selected = runCatching { LocalDate.parse(value) }.getOrNull()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected?.toEpochDay()?.times(86_400_000),
            yearRange = 1900..(maxDate?.year ?: LocalDate.now().year + 10),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = maxDate == null || utcTimeMillis <= maxDate.toEpochDay() * 86_400_000
            }
        )
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis -> onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text("Conferma") }
        }, dismissButton = { Row {
            if (value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text("Rimuovi") }
            TextButton(onClick = { open = false }) { Text("Annulla") }
        } }) { DatePicker(state = picker) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactEditor(clinical: Boolean, initial: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
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
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (clinical) "Contatto clinico" else "Contatto di emergenza") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Indietro") } }) },
        bottomBar = { Surface(shadowElevation = 0.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annulla") }
            Button(onClick = { if (data.str("nome").isBlank() && data.str("cognome").isBlank()) error = "Inserisci nome o cognome" else onSave(data) }, modifier = Modifier.weight(1f)) { Text("Salva") }
        } } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { contactPicker.launch(phonePickerIntent()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(8.dp)); Text("Scegli dalla rubrica") }
            FormText(if (clinical) "Specialità" else "Relazione", data.str(if (clinical) "tipo" else "relazione")) { field(if (clinical) "tipo" else "relazione", it) }
            FormText("Nome", data.str("nome")) { field("nome", it) }
            FormText("Cognome", data.str("cognome")) { field("cognome", it) }
            FormText("Telefono", data.str("telefono"), KeyboardType.Phone) { field("telefono", it) }
            if (clinical) FormText("Email", data.str("email"), KeyboardType.Email) { field("email", it) }
            FormText("Note", data.str("note")) { field("note", it) }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileManager(store: HealthStore, revision: Int, onDismiss: () -> Unit, onChange: () -> Unit) {
    var adding by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var relation by remember { mutableStateOf("") }; var deleteId by remember { mutableStateOf("") }
    val profiles = remember(revision) { JSONArray(store.profiles.toString()) }
    BackHandler(onBack = onDismiss)
    Scaffold(topBar = { TopAppBar(title = { Text("Profili") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Indietro") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until profiles.length()) {
            val item = profiles.getJSONObject(i)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(item.str("id") == store.activeId, onClick = { store.switchProfile(item.str("id")); onChange(); onDismiss() })
                Text(item.str("nome"), Modifier.weight(1f))
                if (item.str("id") != "main") IconButton(onClick = { deleteId = item.str("id") }) { Icon(Icons.Default.DeleteOutline, "Elimina profilo") }
            }
        }
        if (adding) { FormText("Nome", name) { name = it }; FormChoice("Relazione", relation, listOf("Io", "Figlio/a", "Coniuge", "Partner", "Genitore", "Familiare", "Altro")) { relation = it }; Button(onClick = { if (name.isNotBlank() && store.profiles.length() < 5) { store.addProfile(name.trim(), relation.trim()); name = ""; relation = ""; adding = false; onChange() } }) { Text("Salva") } }
        else if (store.profiles.length() < 5) TextButton(onClick = { adding = true }) { Text("Aggiungi profilo") }
        }
    }
    if (deleteId.isNotBlank()) AlertDialog(onDismissRequest = { deleteId = "" }, title = { Text("Eliminare profilo?") }, text = { Text("Verranno eliminati anche i dati sanitari di questo profilo.") }, confirmButton = { TextButton(onClick = { store.deleteProfile(deleteId); deleteId = ""; onChange() }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { deleteId = "" }) { Text("Annulla") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinEditor(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var pinLength by remember { mutableIntStateOf(4) }
    BackHandler(onBack = onDismiss)
    Scaffold(topBar = { TopAppBar(title = { Text("Imposta PIN") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Indietro") } }) }, bottomBar = { Surface(shadowElevation = 0.dp) { Button(onClick = { if (first.length != pinLength || first != second) error = "I PIN devono coincidere e avere $pinLength cifre" else onSave(first) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text("Salva") } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lunghezza del PIN", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = pinLength == 4, onClick = { pinLength = 4; first = ""; second = ""; error = "" }, label = { Text("4 cifre") })
                FilterChip(selected = pinLength == 6, onClick = { pinLength = 6; first = ""; second = ""; error = "" }, label = { Text("6 cifre") })
            }
            OutlinedTextField(first, { if (it.length <= pinLength && it.all(Char::isDigit)) first = it }, label = { Text("Nuovo PIN ($pinLength cifre)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(second, { if (it.length <= pinLength && it.all(Char::isDigit)) second = it }, label = { Text("Conferma PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DoseHistoryDialog(store: HealthStore, med: JSONObject, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Storico · ${med.str("nome")}") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Mese precedente") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ITALIAN)), Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < java.time.YearMonth.now()) { Icon(Icons.Default.ChevronRight, "Mese successivo") }
            }
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day).toString()
                    if (date > today()) break
                    val due = medicineTimesOn(med, month.atDay(day))
                    val taken = due.count { store.dose(date, med.str("id"), it).bool("taken") }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text("${day.toString().padStart(2, '0')}/${month.monthValue.toString().padStart(2, '0')}", Modifier.weight(1f)); Text(if (medicineFrequencyType(med) == "as_needed") "${store.asNeededDoses(date, med.str("id")).size} dosi" else "$taken / ${due.size} dosi") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } })
}
