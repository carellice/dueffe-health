package it.dueffe.health

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun SettingsPage(
    store: HealthStore, revision: Int, onBack: () -> Unit,
    onProfiles: () -> Unit, onTheme: () -> Unit, onDynamicColor: (Boolean) -> Unit, onPalette: (String) -> Unit, onPin: () -> Unit, onBiometric: (Boolean) -> Unit,
    onNotifications: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onClear: () -> Unit
) {
    BackHandler(onBack = onBack)
    val s = MaterialTheme.colorScheme
    val settings = remember(revision) { JSONObject(store.settings.toString()) }
    var confirmClear by remember { mutableStateOf(false) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LazyColumn(
        Modifier.fillMaxSize().starry(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("PREFERENZE", fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = s.primary)
                    Text("Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = s.onBackground)
                }
            }
        }
        item {
            SettingsGroup("Aspetto", Modifier.enterAnimation(0)) {
                if (dynamicAvailable) SettingsSwitchRow(Icons.Rounded.AutoAwesome, "Colori del telefono", "Usa la palette Material You dello sfondo.", settings.bool("dynamicColor"), onDynamicColor)
                AnimatedVisibility(!settings.bool("dynamicColor") || !dynamicAvailable) {
                    Column {
                        Text("Palette", Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = s.onSurfaceVariant)
                        val selected = settings.str("colorPalette").ifBlank { DEFAULT_PALETTE }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            healthPalettes.forEach { palette -> PaletteSwatch(palette, palette.id == selected) { onPalette(palette.id) } }
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("Notifiche", Modifier.enterAnimation(1)) {
                SettingsSwitchRow(Icons.Rounded.NotificationsActive, "Promemoria", "Avvisi per dosi, visite e prenotazioni.", settings.bool("notifications")) { onNotifications() }
            }
        }
        item {
            SettingsGroup("Sicurezza", Modifier.enterAnimation(2)) {
                SettingsSwitchRow(Icons.Rounded.Lock, "Blocco con PIN", "Chiedi il PIN all'apertura dell'app.", settings.bool("pinEnabled")) { enabled ->
                    if (enabled) onPin() else { onBiometric(false); store.setSetting("pinEnabled", false); onTheme() }
                }
                AnimatedVisibility(settings.bool("pinEnabled")) {
                    Column {
                        SettingsActionRow(Icons.Rounded.Password, "Cambia PIN", null, onClick = onPin)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            SettingsSwitchRow(Icons.Rounded.Fingerprint, "Sblocco biometrico", "Impronta o riconoscimento del volto.", settings.bool("biometricEnabled"), onBiometric)
                    }
                }
            }
        }
        item {
            SettingsGroup("Profili e dati", Modifier.enterAnimation(3)) {
                SettingsActionRow(Icons.Rounded.Group, "Gestisci profili", "Aggiungi familiari o cambia profilo.", onClick = onProfiles)
                SettingsActionRow(Icons.Rounded.CloudUpload, "Esporta backup", "Salva un file JSON con dati e storico dosi.", onClick = onExport)
                SettingsActionRow(Icons.Rounded.CloudDownload, "Importa backup", "Ripristina da un file JSON nel profilo attivo.", onClick = onImport)
                Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = s.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Gli allegati restano sul dispositivo e non sono inclusi nel backup.", fontSize = 12.sp, lineHeight = 16.sp, color = s.onSurfaceVariant)
                }
            }
        }
        item { Box(Modifier.enterAnimation(4)) { it.dueffe.updates.AppUpdateCard("health", BuildConfig.UPDATE_REPOSITORY) } }
        item {
            SettingsGroup("Zona pericolosa", Modifier.enterAnimation(5)) {
                SettingsActionRow(Icons.Rounded.DeleteForever, "Elimina i dati del profilo", "Cancella farmaci, visite, documenti e storico.", danger = true) { confirmClear = true }
            }
        }
        item {
            Text("Health ${BuildConfig.VERSION_NAME} · i tuoi dati restano su questo telefono", Modifier.fillMaxWidth().padding(top = 8.dp), fontSize = 12.sp, color = s.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
    if (confirmClear) ConfirmDeleteDialog(
        "tutti i dati del profilo", "Farmaci, visite, documenti e cronologia verranno cancellati definitivamente.",
        onConfirm = { confirmClear = false; onClear() }, onDismiss = { confirmClear = false }
    )
}

@Composable
private fun SettingsGroup(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(title, Modifier.padding(start = 6.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        HealthCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun SettingsSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, s.primary, s.primaryContainer, 40.dp, 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
            Text(subtitle, fontSize = 13.sp, color = s.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange, thumbContent = if (checked) { { Icon(Icons.Rounded.Check, null, Modifier.size(14.dp)) } } else null)
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val accent = if (danger) s.error else s.primary
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, accent, if (danger) s.errorContainer else s.primaryContainer, 40.dp, 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (danger) s.error else s.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = s.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = s.onSurfaceVariant)
    }
}

@Composable
private fun PaletteSwatch(palette: HealthPalette, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "Selezione palette")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).border(3.dp, if (selected) palette.primary else Color.Transparent, CircleShape).padding(5.dp)
                .clip(CircleShape).background(Brush.sweepGradient(listOf(palette.primary, palette.secondary, palette.tertiary, palette.primary))),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = palette.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(palette.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) s.onSurface else s.onSurfaceVariant)
    }
}
