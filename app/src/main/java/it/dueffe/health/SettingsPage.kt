package it.dueffe.health

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun SettingsPage(
    store: HealthStore, revision: Int, dark: Boolean,
    onProfiles: () -> Unit, onTheme: () -> Unit, onDynamicColor: (Boolean) -> Unit, onPalette: (String) -> Unit, onPin: () -> Unit, onBiometric: (Boolean) -> Unit,
    onNotifications: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onClear: () -> Unit
) {
    val c = homeColors(dark)
    val settings = remember(revision) { JSONObject(store.settings.toString()) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Column(Modifier.padding(top = 1.dp, bottom = 2.dp)) {
                Text("Impostazioni", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Personalizza l’app in base alle tue esigenze.", fontSize = 15.sp, lineHeight = 21.sp, color = c.muted)
            }
        }
        item {
            SettingsToggleCard(
                icon = Icons.Default.Palette, title = "Colori di sistema",
                subtitle = "Usa la palette Material 3 del tuo smartphone.",
                iconBackground = c.iconBlue, iconTint = c.blue,
                checked = settings.bool("dynamicColor"), c = c,
                onChange = onDynamicColor
            )
        }
        if (!settings.bool("dynamicColor")) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(0.5.dp, c.border.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Text("Palette dell’app", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text("Scegli una palette Material 3.", fontSize = 12.sp, color = c.muted)
                Spacer(Modifier.height(8.dp))
                val selectedPalette = settings.str("colorPalette").ifBlank { "material" }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(healthPalettes, key = { it.id }) { palette ->
                        FilterChip(
                            selected = selectedPalette == palette.id,
                            onClick = { onPalette(palette.id) },
                            label = { Text(palette.label) },
                            leadingIcon = {
                                Box(Modifier.size(15.dp).clip(CircleShape).background(palette.primary))
                            }
                        )
                    }
                }
            }
        }
        item {
            SettingsToggleCard(
                icon = Icons.Default.Notifications, title = "Promemoria",
                subtitle = "Ricevi promemoria per farmaci e visite.",
                iconBackground = c.iconBlue, iconTint = c.blue,
                checked = settings.bool("notifications"), c = c,
                onChange = { onNotifications() }
            )
        }
        item {
            Column {
                SettingsToggleCard(
                    icon = Icons.Default.Lock, title = "Blocco con PIN",
                    subtitle = "Proteggi l’accesso all’app con un PIN.",
                    iconBackground = c.iconBlue, iconTint = c.blue,
                    checked = settings.bool("pinEnabled"), c = c,
                    onChange = { enabled -> if (enabled) onPin() else { onBiometric(false); store.setSetting("pinEnabled", false); onTheme() } }
                )
                if (settings.bool("pinEnabled")) {
                    TextButton(onClick = onPin, modifier = Modifier.align(Alignment.End)) { Text("Cambia PIN") }
                }
            }
        }
        if (settings.bool("pinEnabled") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) item {
            SettingsToggleCard(
                icon = Icons.Default.LockOpen, title = "Accesso biometrico",
                subtitle = "Sblocca con impronta o riconoscimento del volto.",
                iconBackground = c.iconBlue, iconTint = c.blue,
                checked = settings.bool("biometricEnabled"), c = c,
                onChange = onBiometric
            )
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(11.dp)
            ) {
                SettingsHeading(Icons.Default.CloudDownload, "Dati e backup", "Esporta o importa i tuoi dati.", c.iconBlue, c.blue, c)
                Spacer(Modifier.height(12.dp))
                SettingsBackupAction(Icons.Default.ArrowDownward, "Esporta backup JSON", "Salva una copia dei tuoi dati sul dispositivo.", c.blue, c.iconBlue, c, onExport)
                Spacer(Modifier.height(10.dp))
                SettingsBackupAction(Icons.Default.ArrowUpward, "Importa backup JSON", "Ripristina i tuoi dati da un file di backup.", c.purple, c.lilac, c, onImport)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(19.dp), tint = c.muted)
                    Spacer(Modifier.width(10.dp))
                    Text("Il backup contiene dati e cronologia delle dosi. Gli allegati rimangono sul dispositivo.", fontSize = 11.sp, lineHeight = 15.sp, color = c.muted)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(if (dark) Color(0xFF40242B) else Color(0xFFFFF2F5))
                    .border(0.5.dp, Color(0xFFF2C6D0), RoundedCornerShape(17.dp))
                    .clickable { confirmClear = true }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIcon(Icons.Default.Delete, if (dark) Color(0xFF65323C) else Color(0xFFFFE0E6), if (dark) Color(0xFFFF8F94) else Color(0xFFC72E31))
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text("Elimina dati del profilo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (dark) Color(0xFFFF9CA2) else Color(0xFFAA2026))
                    Text("Rimuovi tutti i tuoi dati dall’app.", fontSize = 12.sp, color = c.muted)
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.size(23.dp), tint = c.muted)
            }
        }
        item { it.dueffe.updates.AppUpdateCard("health", it.dueffe.health.BuildConfig.UPDATE_REPOSITORY) }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Eliminare i dati del profilo?") },
        text = { Text("Farmaci, visite, documenti e cronologia verranno cancellati definitivamente.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
    )
}

@Composable
private fun SettingsHeading(icon: ImageVector, title: String, subtitle: String, iconBackground: Color, iconTint: Color, c: HomeColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsIcon(icon, iconBackground, iconTint)
        Spacer(Modifier.width(15.dp))
        Column {
            Text(title, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = c.muted)
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(23.dp), tint = tint)
    }
}

@Composable
private fun SettingsToggleCard(icon: ImageVector, title: String, subtitle: String, iconBackground: Color, iconTint: Color, checked: Boolean, c: HomeColors, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(c.surface)
            .border(0.5.dp, c.border.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, iconBackground, iconTint)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = c.muted)
        }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsBackupAction(icon: ImageVector, title: String, subtitle: String, tint: Color, iconBackground: Color, c: HomeColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, iconBackground, tint)
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp, color = c.muted)
        }
    }
}
