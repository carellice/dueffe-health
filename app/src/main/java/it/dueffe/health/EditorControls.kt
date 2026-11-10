package it.dueffe.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/** Schermata a passi: la barra in alto avanza e i contenuti scorrono lateralmente tra un passo e l'altro. */
@Composable
internal fun StepScaffold(
    title: String, steps: List<String>, step: Int, error: String,
    onBack: () -> Unit, onNext: () -> Unit,
    content: @Composable ColumnScope.(Int) -> Unit
) {
    val s = MaterialTheme.colorScheme
    val last = step >= steps.lastIndex
    Scaffold(
        modifier = Modifier.starry(),
        containerColor = Color.Transparent,
        topBar = {
            Column(Modifier.fillMaxWidth().background(s.background.copy(alpha = 0.92f)).statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(if (step == 0) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (step == 0) "Chiudi" else "Indietro") }
                    Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = s.onBackground)
                    Text("${step + 1} di ${steps.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = s.primary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { index ->
                        val fill by animateFloatAsState(if (index <= step) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "Avanzamento passo")
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(s.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(CircleShape).background(s.primary))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(s.background.copy(alpha = 0.92f)).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(step > 0) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.height(56.dp), shape = CircleShape) { Text("Indietro") }
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f).height(56.dp), shape = CircleShape) {
                    AnimatedContent(last, label = "Etichetta pulsante") { isLast ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isLast) "Salva" else "Continua", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut()) using SizeTransform(clip = false)
            },
            label = "Passo editor"
        ) { current ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(steps.getOrElse(current) { "" }, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, color = s.onBackground)
                content(current)
                AnimatedVisibility(error.isNotBlank(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { ErrorBanner(error) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Schermata modulo semplice con titolo, pulsante indietro e azione principale opzionale. */
@Composable
internal fun FormScaffold(
    title: String, onBack: () -> Unit,
    primaryLabel: String? = null, onPrimary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val s = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.starry(),
        containerColor = Color.Transparent,
        topBar = {
            Row(Modifier.fillMaxWidth().background(s.background.copy(alpha = 0.92f)).statusBarsPadding().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onBack)
                Spacer(Modifier.width(14.dp))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = s.onBackground)
            }
        },
        bottomBar = {
            if (primaryLabel != null) Box(Modifier.fillMaxWidth().background(s.background.copy(alpha = 0.92f)).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(primaryLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content
        )
    }
}

@Composable
internal fun ErrorBanner(message: String) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(s.errorContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = s.error)
        Spacer(Modifier.width(10.dp))
        Text(message, color = s.onErrorContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun FormHint(text: String) {
    Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun FormLabel(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Composable
internal fun FormText(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp), colors = formFieldColors()
    )
}

/** Riga cliccabile in stile campo, usata per date, orari e scelte. */
@Composable
internal fun FieldTile(icon: ImageVector, label: String, value: String, placeholder: Boolean = false, onClick: () -> Unit, trailing: @Composable () -> Unit = { Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(s.surfaceContainerLowest)
            .border(1.dp, s.outlineVariant, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, s.primary, s.primaryContainer, 40.dp, 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = s.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = if (placeholder) FontWeight.Normal else FontWeight.SemiBold, color = if (placeholder) s.onSurfaceVariant else s.onSurface)
        }
        trailing()
    }
}

@Composable
internal fun FormSwitch(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(s.surfaceContainerLowest)
            .border(1.dp, s.outlineVariant, RoundedCornerShape(18.dp)).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface)
            if (description != null) Text(description, fontSize = 13.sp, color = s.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, onChange)
    }
}

@Composable
internal fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) s.primary else s.surfaceContainerLowest, spring(stiffness = 500f), label = "Sfondo scelta")
    val foreground by animateColorAsState(if (selected) s.onPrimary else s.onSurface, spring(stiffness = 500f), label = "Testo scelta")
    Row(
        Modifier.heightIn(min = 42.dp).clip(CircleShape).background(background)
            .border(1.dp, if (selected) Color.Transparent else s.outlineVariant, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(selected) { Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(17.dp), tint = foreground) }
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = foreground)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormRadioChoice(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    custom: Boolean = true,
    optional: Boolean = false
) {
    var customSelected by remember(label) { mutableStateOf(value.isNotBlank() && value !in options) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormLabel(label)
        val choices = (if (optional) listOf("Non indicato") else emptyList()) + options + (if (custom) listOf("Personalizzato") else emptyList())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val selected = when (choice) {
                    "Non indicato" -> value.isBlank() && !customSelected
                    "Personalizzato" -> customSelected || value.isNotBlank() && value !in options
                    else -> value == choice && !customSelected
                }
                SelectChip(choice, selected) {
                    customSelected = choice == "Personalizzato"
                    onChange(if (choice == "Personalizzato" || choice == "Non indicato") "" else choice)
                }
            }
        }
        AnimatedVisibility(customSelected || value.isNotBlank() && value !in options) {
            OutlinedTextField(value, onChange, label = { Text("Scrivi ${label.removeSuffix(" *").lowercase()}") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp), colors = formFieldColors())
        }
    }
}

@Composable
internal fun NumberStepper(label: String, value: String, onChange: (String) -> Unit, min: Double = 0.0, step: Double = 1.0) {
    val s = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun changeBy(delta: Double) {
        val number = value.replace(',', '.').toDoubleOrNull()
        val next = if (number == null) (if (delta > 0) maxOf(step, min) else min) else (number + delta).coerceAtLeast(min)
        onChange(BigDecimal.valueOf(next).stripTrailingZeros().toPlainString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormLabel(label)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(s.surfaceContainerLowest).border(1.dp, s.outlineVariant, RoundedCornerShape(22.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = { changeBy(-step) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Remove, "Diminuisci $label") }
            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).clickable { typed = value; error = false; editing = true }, contentAlignment = Alignment.Center) {
                AnimatedContent(value, label = "Valore") { shown -> Text(shown.ifBlank { "—" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = s.onSurface) }
            }
            FilledTonalIconButton(onClick = { changeBy(step) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Add, "Aumenta $label") }
        }
        Text("Tocca il numero per scriverlo a mano.", fontSize = 12.sp, color = s.onSurfaceVariant)
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(label.removeSuffix(" *")) },
        text = {
            OutlinedTextField(typed, { typed = it; error = false }, label = { Text("Valore") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error, shape = RoundedCornerShape(16.dp))
        },
        confirmButton = { TextButton(onClick = {
            val number = typed.replace(',', '.').toDoubleOrNull()
            if (typed.isBlank()) { onChange(""); editing = false }
            else if (number == null || number < min || !number.isFinite()) error = true
            else { onChange(BigDecimal.valueOf(number).stripTrailingZeros().toPlainString()); editing = false }
        }) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormChoice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(18.dp), colors = formFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            DropdownMenuItem(text = { Text("Non indicato") }, onClick = { onChange(""); expanded = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDate(label: String, value: String, maxDate: LocalDate? = null, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldTile(Icons.Rounded.CalendarMonth, label.removeSuffix(" *"), if (value.isBlank()) "Seleziona una data" else displayDate(value), placeholder = value.isBlank(), onClick = { open = true })
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
internal fun TimeSelectDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val time = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(8, 0))
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = picker) } },
        confirmButton = { TextButton(onClick = { onConfirm(String.format(Locale.ITALY, "%02d:%02d", picker.hour, picker.minute)) }) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
internal fun FormTime(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldTile(Icons.Rounded.Schedule, label, value.ifBlank { "Seleziona un orario" }, placeholder = value.isBlank(), onClick = { open = true })
    if (open) TimeSelectDialog(value.ifBlank { "09:00" }, onDismiss = { open = false }) { onChange(it); open = false }
}
