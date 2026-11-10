package it.dueffe.health

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
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
internal fun timeToMinutes(value: String) = runCatching { LocalTime.parse(value).toSecondOfDay() / 60 }.getOrDefault(0)
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

/** Le cinque aree principali, sempre raggiungibili dalla barra in basso. */
enum class HealthTab(val label: String, val icon: ImageVector) {
    Oggi("Oggi", Icons.Rounded.Home),
    Terapie("Terapie", Icons.Rounded.Medication),
    Visite("Visite", Icons.Rounded.CalendarMonth),
    Documenti("Documenti", Icons.Rounded.FolderOpen),
    Profilo("Profilo", Icons.Rounded.Person)
}

private fun defaultFilter(tab: HealthTab) = if (tab == HealthTab.Visite) "Prossime" else "Tutti"

/** Le schermate sopra le schede; la profondità decide la direzione dell'animazione. */
private sealed interface Screen {
    val depth: Int
    data object Locked : Screen { override val depth = -1 }
    data object Welcome : Screen { override val depth = -1 }
    data object Tabs : Screen { override val depth = 0 }
    data class Detail(val key: String, val id: String) : Screen { override val depth = 1 }
    data object Settings : Screen { override val depth = 1 }
    data object Profiles : Screen { override val depth = 2 }
    data class Contact(val type: String, val index: Int, val item: JSONObject) : Screen { override val depth = 3 }
    data object Pin : Screen { override val depth = 3 }
    data class Edit(val key: String, val item: JSONObject) : Screen { override val depth = 3 }
}

private val Screen.isSheet get() = this is Screen.Edit || this is Screen.Contact || this is Screen.Pin

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HealthApp() {
    val context = LocalContext.current
    val store = remember { HealthStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var dynamicColorEnabled by remember { mutableStateOf(store.settings.bool("dynamicColor")) }
    var selectedPaletteId by remember { mutableStateOf(store.settings.str("colorPalette").ifBlank { DEFAULT_PALETTE }) }
    fun refresh() { revision++; ReminderScheduler(context).scheduleAll() }
    LaunchedEffect(Unit) { ReminderScheduler(context).scheduleAll() }
    var tab by remember { mutableStateOf(HealthTab.Oggi) }
    var showWelcome by remember { mutableStateOf(!store.settings.bool("onboarded")) }
    var welcomeName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactEditing by remember { mutableStateOf<Triple<String, Int, JSONObject>?>(null) }
    var showProfiles by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(store.settings.bool("pinEnabled")) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pinErrorCount by remember { mutableIntStateOf(0) }
    var biometricEnabled by remember { mutableStateOf(store.settings.bool("biometricEnabled")) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(defaultFilter(HealthTab.Oggi)) }
    var pendingAttachment by remember { mutableStateOf<JSONObject?>(null) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingMedicinePhoto by remember { mutableStateOf<JSONObject?>(null) }
    var toast by remember { mutableStateOf("") }
    var historyMedicine by remember { mutableStateOf<JSONObject?>(null) }
    var bookingToComplete by remember { mutableStateOf("") }
    val listStates = HealthTab.entries.associateWith { rememberLazyListState() }
    fun selectTab(target: HealthTab, targetFilter: String? = null) {
        if (target != tab) { search = "" }
        tab = target
        filter = targetFilter ?: defaultFilter(target)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { store.importProfile(it.readText()) }; refresh(); toast = "Backup importato" }.onFailure { toast = "Importazione non riuscita" }
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
    fun pickAttachment(doc: JSONObject) {
        pendingAttachment = doc
        attachmentLauncher.launch(arrayOf("image/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    val screen: Screen = when {
        locked -> Screen.Locked
        showWelcome -> Screen.Welcome
        editing != null -> Screen.Edit(editing!!.first, editing!!.second)
        contactEditing != null -> contactEditing!!.let { Screen.Contact(it.first, it.second, it.third) }
        showPin -> Screen.Pin
        showProfiles -> Screen.Profiles
        showSettings -> Screen.Settings
        detail != null -> Screen.Detail(detail!!.first, detail!!.second)
        else -> Screen.Tabs
    }
    // Le schermate con intestazione colorata vogliono icone di sistema chiare.
    val lightBars = screen !is Screen.Locked && screen !is Screen.Welcome && screen !is Screen.Detail
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = true
            }
        }
    }

    HealthTheme(selectedPaletteId, dynamicColorEnabled) {
        val activity = context as? Activity
        fun promptBiometric() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || activity == null) {
                pinError = "Biometria non disponibile: usa il PIN."
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
                            pinError = errString?.toString()?.ifBlank { "Biometria non riuscita: usa il PIN." } ?: "Biometria non riuscita: usa il PIN."
                        }
                    }
                })
            }.onFailure { pinError = "Biometria non disponibile: usa il PIN." }
        }
        LaunchedEffect(locked, biometricEnabled) { if (locked && biometricEnabled) promptBiometric() }

        val scheme = MaterialTheme.colorScheme
        val profileName = store.profileItem().str("nome")
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = screen,
                    contentKey = { if (it is Screen.Detail) "detail" else it },
                    transitionSpec = {
                        val from = initialState
                        val to = targetState
                        when {
                            from.depth < 0 || to.depth < 0 ->
                                (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.04f)) togetherWith fadeOut(tween(300))
                            (from is Screen.Tabs && to is Screen.Detail) || (from is Screen.Detail && to is Screen.Tabs) ->
                                fadeIn(tween(320, delayMillis = 60)) togetherWith fadeOut(tween(220))
                            to.isSheet && !from.isSheet ->
                                (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 } + fadeIn(tween(240))) togetherWith
                                    (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.94f))
                            from.isSheet && !to.isSheet ->
                                (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.94f)) togetherWith
                                    (slideOutVertically(tween(260)) { it / 4 } + fadeOut(tween(200)))
                            to.depth >= from.depth ->
                                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 400f)) { it / 3 } + fadeIn(tween(240))) togetherWith
                                    (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(200)))
                            else ->
                                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 400f)) { -it / 6 } + fadeIn(tween(240))) togetherWith
                                    (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                        } using SizeTransform(clip = false)
                    },
                    label = "Navigazione"
                ) { current ->
                    CompositionLocalProvider(
                        LocalHealthSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalHealthAnimatedVisibilityScope provides this@AnimatedContent
                    ) {
                        when (current) {
                            Screen.Locked -> {
                                val pinLength = store.settings.str("pin").length.let { if (it == 6) 6 else 4 }
                                LockScreen(
                                    profileName, pinLength, pin.length, pinError, pinErrorCount,
                                    biometricEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                                    onDigit = { digit ->
                                        if (pin.length < pinLength) {
                                            pin += digit; pinError = ""
                                            if (pin.length == pinLength) {
                                                if (pin == store.settings.str("pin")) { locked = false; pin = "" }
                                                else { pinError = "PIN errato, riprova"; pinErrorCount++; pin = "" }
                                            }
                                        }
                                    },
                                    onDelete = { pin = pin.dropLast(1) },
                                    onBiometric = { promptBiometric() }
                                )
                            }
                            Screen.Welcome -> WelcomeScreen(welcomeName, onNameChange = { welcomeName = it }, onDismiss = { showWelcome = false; store.setSetting("onboarded", true) }, onSave = {
                                if (welcomeName.isNotBlank()) { store.updateProfileItem(welcomeName.trim(), "Io"); refresh() }
                                store.setSetting("onboarded", true); showWelcome = false
                            })
                            is Screen.Edit -> {
                                val key = current.key
                                val item = current.item
                                if (key == "medicines") MedicineEditor(item, onDismiss = { editing = null }, onSave = { saved -> store.put("medicines", saved); editing = null; refresh() })
                                else GuidedEntityEditor(key, item, store, onDismiss = { editing = null; bookingToComplete = "" }, onSave = { saved ->
                                    val oldAttachment = if (key == "documents") item.str("attachment") else ""
                                    store.put(key, saved)
                                    if (oldAttachment.isNotBlank() && oldAttachment != saved.str("attachment")) store.attachmentPath(oldAttachment).delete()
                                    if (key == "appointments" && bookingToComplete.isNotBlank()) {
                                        store.list("bookingReminders").firstOrNull { it.str("id") == bookingToComplete }?.let { it.put("prenotata", true); store.put("bookingReminders", it) }
                                        bookingToComplete = ""
                                    }
                                    editing = null; refresh()
                                })
                            }
                            is Screen.Contact -> ContactEditor(current.type == "contatti_clinici", current.item, onDismiss = { contactEditing = null }, onSave = { contact ->
                                val profile = store.profile()
                                val arr = profile.optJSONArray(current.type) ?: JSONArray()
                                val contacts = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
                                if (current.index in contacts.indices) contacts[current.index] = contact else contacts.add(contact)
                                store.saveProfile(JSONObject(profile.toString()).put(current.type, JSONArray(contacts)))
                                contactEditing = null; refresh()
                            })
                            Screen.Pin -> PinEditor(onDismiss = { showPin = false }, onSave = { store.setSetting("pin", it); store.setSetting("pinEnabled", true); showPin = false; refresh() })
                            Screen.Profiles -> ProfileManager(store, revision, onDismiss = { showProfiles = false }, onChange = { refresh() })
                            Screen.Settings -> SettingsPage(store, revision, onBack = { showSettings = false }, onProfiles = { showProfiles = true }, onTheme = { refresh() }, onDynamicColor = {
                                dynamicColorEnabled = it; store.setSetting("dynamicColor", it); refresh()
                            }, onPalette = {
                                selectedPaletteId = it; store.setSetting("colorPalette", it); refresh()
                            }, onPin = { showPin = true }, onBiometric = { enabled ->
                                biometricEnabled = enabled; store.setSetting("biometricEnabled", enabled); refresh()
                            }, onNotifications = {
                                if (!store.settings.bool("notifications") && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else { store.setSetting("notifications", !store.settings.bool("notifications")); refresh() }
                            }, onExport = { exportLauncher.launch("dueffe_health_backup_${today()}.json") }, onImport = { importLauncher.launch(arrayOf("application/json")) }, onClear = { store.clearProfile(); refresh() })
                            is Screen.Detail -> {
                                val item = store.list(current.key).firstOrNull { it.str("id") == current.id }
                                EntityDetailPage(
                                    key = current.key, item = item, store = store,
                                    onBack = { detail = null },
                                    onEdit = { if (item != null) editing = current.key to JSONObject(item.toString()) },
                                    onOpenAttachment = { if (item != null) openAttachment(item) }
                                )
                            }
                            Screen.Tabs -> {
                                BackHandler(enabled = tab != HealthTab.Oggi) { selectTab(HealthTab.Oggi) }
                                val header: @Composable (HealthTab) -> Unit = { page ->
                                    val (eyebrow, title) = when (page) {
                                        HealthTab.Oggi -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)) to
                                            (if (profileName.isBlank() || profileName == "Io") greeting() else "${greeting()}, $profileName")
                                        HealthTab.Terapie -> "I tuoi farmaci" to "Terapie"
                                        HealthTab.Visite -> "Agenda" to "Visite"
                                        HealthTab.Documenti -> "Archivio" to "Documenti"
                                        HealthTab.Profilo -> "Scheda sanitaria" to "Profilo"
                                    }
                                    PageHeader(eyebrow, title) {
                                        if (page == HealthTab.Oggi || page == HealthTab.Profilo) RoundIconButton(Icons.Rounded.Settings, "Impostazioni", { showSettings = true }, tint = scheme.onSurfaceVariant)
                                        ProfileAvatar(profileName.ifBlank { "Io" }, 44.dp) { showProfileSheet = true }
                                    }
                                }
                                val overdue = remember(revision, tab) {
                                    val now = LocalTime.now()
                                    store.list("medicines").filter(::isMedicineActive).any { med ->
                                        medicineTimesOn(med, LocalDate.now()).any { doseOverdue(it, store.dose(today(), med.str("id"), it).bool("taken"), now) }
                                    }
                                }
                                val fab: Triple<String, ImageVector, () -> Unit>? = when (tab) {
                                    HealthTab.Oggi -> Triple("Aggiungi", Icons.Rounded.Add) { showQuickAdd = true }
                                    HealthTab.Terapie -> Triple("Nuovo farmaco", Icons.Rounded.Add) { editing = "medicines" to JSONObject() }
                                    HealthTab.Visite -> if (filter == "Da prenotare") Triple("Nuovo promemoria", Icons.Rounded.AddAlert) { editing = "bookingReminders" to JSONObject() }
                                        else Triple("Nuova visita", Icons.Rounded.Add) { editing = "appointments" to JSONObject() }
                                    HealthTab.Documenti -> Triple("Nuovo documento", Icons.Rounded.Add) { editing = "documents" to JSONObject() }
                                    HealthTab.Profilo -> null
                                }
                                var lastFab by remember { mutableStateOf(fab) }
                                if (fab != null) lastFab = fab
                                val fabExpanded = listStates.getValue(tab).firstVisibleItemIndex < 2
                                Scaffold(
                                    // Lo sfondo con le stelle, come Dueffe Home e Chiarella.
                                    modifier = Modifier.starry(),
                                    containerColor = Color.Transparent,
                                    bottomBar = {
                                        FloatingNavBar(HealthTab.entries.map { NavItem(it.label, it.icon, badge = it == HealthTab.Oggi && overdue) }, tab.ordinal) { selectTab(HealthTab.entries[it]) }
                                    },
                                    floatingActionButton = {
                                        AnimatedVisibility(fab != null, enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                                            lastFab?.let { (label, icon, action) ->
                                                ExtendedFloatingActionButton(
                                                    onClick = action, expanded = fabExpanded,
                                                    icon = { Icon(icon, null) },
                                                    text = { AnimatedContent(label, label = "Testo pulsante") { Text(it, fontWeight = FontWeight.Bold) } },
                                                    shape = RoundedCornerShape(22.dp),
                                                    containerColor = scheme.primary, contentColor = scheme.onPrimary,
                                                    elevation = FloatingActionButtonDefaults.elevation(6.dp, 10.dp, 6.dp, 8.dp)
                                                )
                                            }
                                        }
                                    }
                                ) { padding ->
                                    AnimatedContent(
                                        targetState = tab,
                                        transitionSpec = {
                                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { direction * it / 5 } + fadeIn(tween(260))) togetherWith
                                                (slideOutHorizontally(tween(220)) { -direction * it / 5 } + fadeOut(tween(160)))
                                        },
                                        label = "Cambio scheda"
                                    ) { page ->
                                        val listState = listStates.getValue(page)
                                        when (page) {
                                            HealthTab.Oggi -> HomeDashboard(store, revision, listState, padding, { header(page) },
                                                onTab = { target, targetFilter -> selectTab(target, targetFilter) },
                                                onAdd = { key -> editing = key to JSONObject() },
                                                onTake = { med, time -> store.toggleDose(med, time, !store.dose(today(), med.str("id"), time).bool("taken")); refresh() },
                                                onOpenVisit = { detail = "appointments" to it.str("id") },
                                                onOpenMedicine = { detail = "medicines" to it.str("id") })
                                            HealthTab.Terapie -> TherapyPage(store, revision, listState, padding, { header(page) }, search, { search = it }, filter, { filter = it },
                                                onOpen = { detail = "medicines" to it.str("id") },
                                                onAdd = { editing = "medicines" to JSONObject() },
                                                onEdit = { editing = "medicines" to JSONObject(it.toString()) },
                                                onDelete = { store.delete("medicines", it.str("id")); refresh() },
                                                onAction = { item, action ->
                                                    when (action) {
                                                        "archive" -> { item.put("archiviata", !item.bool("archiviata")); store.put("medicines", item); refresh() }
                                                        "history" -> historyMedicine = item
                                                        "photo" -> { pendingMedicinePhoto = item; medicinePhotoLauncher.launch(arrayOf("image/*")) }
                                                        "renew" -> {
                                                            item.put("archiviata", true); store.put("medicines", item)
                                                            val renewed = JSONObject(item.toString()); renewed.remove("id")
                                                            renewed.put("archiviata", false).put("dataInizio", today()).put("dataFine", "").put("senzaFine", true)
                                                            editing = "medicines" to renewed; refresh()
                                                        }
                                                        "refresh" -> refresh()
                                                    }
                                                })
                                            HealthTab.Visite -> VisitPage(store, revision, listState, padding, { header(page) }, search, { search = it }, filter, { filter = it },
                                                onOpen = { item, booking -> detail = (if (booking) "bookingReminders" else "appointments") to item.str("id") },
                                                onAddVisit = { editing = "appointments" to JSONObject() },
                                                onEdit = { editing = "appointments" to JSONObject(it.toString()) },
                                                onDelete = { store.delete("appointments", it.str("id")); refresh() },
                                                onAction = { item, action ->
                                                    when (action) {
                                                        "complete" -> { item.put("completata", !item.bool("completata")); store.put("appointments", item); refresh() }
                                                        "referti" -> {
                                                            val reminder = item.optJSONObject("refertiReminder") ?: JSONObject()
                                                            reminder.put("ritirati", !reminder.bool("ritirati")); item.put("refertiReminder", reminder)
                                                            store.put("appointments", item); refresh()
                                                        }
                                                        "deleteBooking" -> { store.delete("bookingReminders", item.str("id")); refresh() }
                                                    }
                                                },
                                                onBooking = { editing = "bookingReminders" to (it?.let { b -> JSONObject(b.toString()) } ?: JSONObject()) },
                                                onBookNow = { booking -> bookingToComplete = booking.str("id"); editing = "appointments" to JSONObject().put("tipo", booking.str("tipoVisita")) })
                                            HealthTab.Documenti -> DocumentPage(store, revision, listState, padding, { header(page) }, search, { search = it }, filter, { filter = it },
                                                onOpen = { detail = "documents" to it.str("id") },
                                                onAdd = { editing = "documents" to JSONObject() },
                                                onEdit = { editing = "documents" to JSONObject(it.toString()) },
                                                onDelete = { val file = it.str("attachment"); if (file.isNotBlank()) store.attachmentPath(file).delete(); store.delete("documents", it.str("id")); refresh() },
                                                onAction = { item, action ->
                                                    when (action) {
                                                        "open" -> openAttachment(item)
                                                        "camera" -> captureDocument(item)
                                                        else -> pickAttachment(item)
                                                    }
                                                })
                                            HealthTab.Profilo -> ProfileScreen(store, revision, listState, padding, { header(page) },
                                                onChange = { refresh() },
                                                onContactEdit = { type, index, contact -> contactEditing = Triple(type, index, contact) },
                                                onSettings = { showSettings = true },
                                                onManageProfiles = { showProfiles = true })
                                        }
                                    }
                                    // Velo sotto la barra di stato: il contenuto che scorre non si sovrappone all'orologio.
                                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scheme.background.copy(alpha = 0.94f)))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showProfileSheet) ProfileSwitcherSheet(store, revision, onDismiss = { showProfileSheet = false },
            onSwitch = { id -> store.switchProfile(id); showProfileSheet = false; refresh() },
            onManage = { showProfileSheet = false; showProfiles = true })
        if (showQuickAdd) QuickAddSheet(onDismiss = { showQuickAdd = false }, onAdd = { key -> showQuickAdd = false; editing = key to JSONObject() })
        historyMedicine?.let { DoseHistorySheet(store, it, onDismiss = { historyMedicine = null }) }
        if (toast.isNotBlank()) AlertDialog(
            onDismissRequest = { toast = "" },
            icon = { Icon(Icons.Rounded.Info, null) },
            title = { Text("Avviso") }, text = { Text(toast) },
            confirmButton = { TextButton(onClick = { toast = "" }) { Text("OK") } },
            containerColor = scheme.surfaceContainerLowest
        )
    }
}
