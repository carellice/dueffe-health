package it.dueffe.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class HealthFilterOption(val label: String, val icon: ImageVector? = null, val count: Int? = null)

@Composable
internal fun HealthSearchBar(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .shadow(4.dp, shape, ambientColor = s.primary.copy(alpha = 0.05f), spotColor = s.primary.copy(alpha = 0.12f))
            .clip(shape).background(s.surfaceContainerLowest)
            .border(1.dp, s.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = s.primary)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = s.onSurface),
            cursorBrush = SolidColor(s.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = s.onSurfaceVariant.copy(alpha = 0.8f))
                    inner()
                }
            }
        )
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, "Cancella ricerca", Modifier.size(19.dp), tint = s.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun HealthFilterBar(options: List<HealthFilterOption>, selected: String, onSelected: (String) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option.label == selected
            val background by animateColorAsState(if (isSelected) accent else s.surfaceContainerLowest, spring(stiffness = 500f), label = "Sfondo filtro")
            val foreground by animateColorAsState(if (isSelected) Color.White else s.onSurfaceVariant, spring(stiffness = 500f), label = "Testo filtro")
            Row(
                Modifier.height(40.dp).clip(CircleShape).background(background)
                    .border(1.dp, if (isSelected) Color.Transparent else s.outlineVariant, CircleShape)
                    .clickable { onSelected(option.label) }.padding(start = 14.dp, end = if (option.count != null) 6.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                option.icon?.let { Icon(it, null, Modifier.size(17.dp), tint = foreground); Spacer(Modifier.width(6.dp)) }
                Text(option.label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = foreground, maxLines = 1)
                if (option.count != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.heightIn(min = 26.dp).widthIn(min = 26.dp).clip(CircleShape)
                            .background(if (isSelected) Color.White.copy(alpha = 0.22f) else s.surfaceContainerHigh).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(option.count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = foreground) }
                }
            }
        }
    }
}
