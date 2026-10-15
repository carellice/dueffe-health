package it.dueffe.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val choices = (if (optional) listOf("Non indicato") else emptyList()) + options + (if (custom) listOf("Personalizzato") else emptyList())
        choices.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { choice ->
                    val selected = when (choice) {
                        "Non indicato" -> value.isBlank() && !customSelected
                        "Personalizzato" -> customSelected || value.isNotBlank() && value !in options
                        else -> value == choice && !customSelected
                    }
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clickable {
                            customSelected = choice == "Personalizzato"
                            onChange(if (choice == "Personalizzato" || choice == "Non indicato") "" else choice)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = {
                            customSelected = choice == "Personalizzato"
                            onChange(if (choice == "Personalizzato" || choice == "Non indicato") "" else choice)
                        }, modifier = Modifier.size(38.dp))
                        Text(choice, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (customSelected || value.isNotBlank() && value !in options) {
            OutlinedTextField(value, onChange, label = { Text("Scrivi $label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
}

@Composable
internal fun NumberStepper(label: String, value: String, onChange: (String) -> Unit, min: Double = 0.0, step: Double = 1.0) {
    var editing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun changeBy(delta: Double) {
        val number = value.replace(',', '.').toDoubleOrNull()
        val next = if (number == null) (if (delta > 0) maxOf(step, min) else min) else (number + delta).coerceAtLeast(min)
        onChange(BigDecimal.valueOf(next).stripTrailingZeros().toPlainString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedIconButton(onClick = { changeBy(-step) }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.Remove, "Diminuisci $label") }
            OutlinedButton(onClick = { typed = value; error = false; editing = true }, modifier = Modifier.weight(1f).height(54.dp)) {
                Text(value.ifBlank { "—" }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedIconButton(onClick = { changeBy(step) }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.Add, "Aumenta $label") }
        }
        Text("Tocca il numero per inserirlo a mano.", style = MaterialTheme.typography.bodySmall)
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(label) },
        text = {
            OutlinedTextField(
                typed, { typed = it; error = false },
                label = { Text("Quantità") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error
            )
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
