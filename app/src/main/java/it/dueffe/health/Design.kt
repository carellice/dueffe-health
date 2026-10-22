package it.dueffe.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Ogni area dell'app ha un accento fisso, così il colore aiuta a orientarsi. */
internal enum class Area { Therapy, Visit, Document }

internal data class AreaColors(val accent: Color, val container: Color, val onContainer: Color)

@Composable
internal fun areaColors(area: Area): AreaColors {
    val s = MaterialTheme.colorScheme
    return when (area) {
        Area.Therapy -> AreaColors(s.primary, s.primaryContainer, s.onPrimaryContainer)
        Area.Visit -> AreaColors(s.secondary, s.secondaryContainer, s.onSecondaryContainer)
        Area.Document -> AreaColors(s.tertiary, s.tertiaryContainer, s.onTertiaryContainer)
    }
}

internal object HealthStatus {
    val success = Color(0xFF128A5C)
    val successContainer = Color(0xFFDDF5E9)
    val warning = Color(0xFFB4530A)
    val warningContainer = Color(0xFFFFEDD6)
}

@Composable
internal fun heroBrush(start: Color = MaterialTheme.colorScheme.primary, end: Color = MaterialTheme.colorScheme.secondary): Brush =
    Brush.linearGradient(listOf(start, lerp(start, end, 0.7f)))

/** Superficie con gradiente e bolle luminose che si muovono lentamente. */
@Composable
internal fun HeroSurface(
    modifier: Modifier = Modifier,
    brush: Brush = heroBrush(),
    shape: RoundedCornerShape = RoundedCornerShape(30.dp),
    glow: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "Sfondo animato")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Movimento bolle")
    Box(
        modifier.shadow(18.dp, shape, ambientColor = glow.copy(alpha = 0.3f), spotColor = glow.copy(alpha = 0.4f))
            .clip(shape).background(brush)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.11f), radius = size.minDimension * 0.78f,
                    center = Offset(size.width * (0.95f - 0.1f * drift), size.height * (0.02f + 0.12f * drift)))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.42f,
                    center = Offset(size.width * (0.08f + 0.08f * drift), size.height * (1.02f - 0.1f * drift)))
            },
        content = content
    )
}

/** Card bianca con ombra morbida che si comprime leggermente al tocco. */
@Composable
internal fun HealthCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val s = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f), label = "Pressione card")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = s.primary.copy(alpha = 0.06f), spotColor = s.primary.copy(alpha = 0.14f))
            .clip(shape).background(color)
            .border(1.dp, s.outlineVariant.copy(alpha = 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** Gli elementi entrano in sequenza scivolando dal basso. */
internal fun Modifier.enterAnimation(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 8) * 45L)
        progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 220f))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 40.dp.toPx()
    }
}

internal fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
internal fun ProfileAvatar(name: String, size: Dp = 44.dp, onClick: (() -> Unit)? = null) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).shadow(6.dp, CircleShape, spotColor = s.primary.copy(alpha = 0.4f)).clip(CircleShape)
            .background(Brush.linearGradient(listOf(s.primary, s.secondary)))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Cambia profilo", onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
internal fun PageHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = s.primary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, color = s.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
internal fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 44.dp, container: Color = MaterialTheme.colorScheme.surfaceContainerLowest, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).shadow(4.dp, CircleShape, spotColor = s.primary.copy(alpha = 0.18f)).clip(CircleShape)
            .background(container).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, Modifier.size(size * 0.5f), tint = tint) }
}

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = s.onBackground)
        if (action != null) Row(
            Modifier.clip(CircleShape).clickable(onClick = onAction).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.primary)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = s.primary)
        }
    }
}

@Composable
internal fun IconTile(icon: ImageVector?, accent: Color, container: Color, size: Dp = 48.dp, corner: Dp = 16.dp, iconSize: Dp = size * 0.52f, custom: (@Composable () -> Unit)? = null) {
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(container), contentAlignment = Alignment.Center) {
        if (custom != null) custom() else if (icon != null) Icon(icon, null, Modifier.size(iconSize), tint = accent)
    }
}

@Composable
internal fun StatusPill(text: String, color: Color, container: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = color); Spacer(Modifier.width(4.dp)) }
        Text(text, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, message: String, accent: Color = MaterialTheme.colorScheme.primary, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val s = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "Stato vuoto")
    val pulse by transition.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Pulsazione")
    HealthCard(padding = PaddingValues(horizontal = 24.dp, vertical = 30.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(accent.copy(alpha = 0.09f)))
                Box(Modifier.size(68.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(34.dp), tint = accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 14.sp, lineHeight = 19.sp, color = s.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (actionLabel != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onAction, shape = CircleShape) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(actionLabel)
                }
            }
        }
    }
}

internal data class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun OverflowMenu(actions: List<MenuAction>, description: String) {
    var open by remember { mutableStateOf(false) }
    val s = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, description, tint = s.onSurfaceVariant) }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp), containerColor = s.surfaceContainerLowest, shadowElevation = 10.dp) {
            actions.forEach { action ->
                val tint = if (action.danger) s.error else s.onSurface
                DropdownMenuItem(
                    text = { Text(action.label, color = tint, fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(action.icon, null, tint = if (action.danger) s.error else s.onSurfaceVariant) },
                    onClick = { open = false; action.onClick() }
                )
            }
        }
    }
}

@Composable
internal fun ConfirmDeleteDialog(name: String, message: String = "Questa operazione non può essere annullata.", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = s.error) },
        title = { Text("Eliminare $name?", textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = s.error)) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        containerColor = s.surfaceContainerLowest
    )
}

/** Cerchio di spunta con riempimento e rimbalzo quando la dose viene segnata. */
@Composable
internal fun CheckBubble(checked: Boolean, accent: Color, size: Dp = 40.dp, description: String = "Dose", onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val background by animateColorAsState(if (checked) accent else Color.Transparent, spring(stiffness = 600f), label = "Riempimento spunta")
    val border by animateColorAsState(if (checked) accent else s.outline.copy(alpha = 0.7f), label = "Bordo spunta")
    val scale by animateFloatAsState(if (checked) 1f else 0.3f, spring(dampingRatio = 0.42f, stiffness = 520f), label = "Scala spunta")
    Box(
        Modifier.size(size).clip(CircleShape).background(background).border(2.dp, border, CircleShape)
            .clickable(onClickLabel = if (checked) "Segna come non assunta" else "Segna come assunta", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Check, if (checked) "$description assunta" else "$description da assumere",
            Modifier.size(size * 0.58f).graphicsLayer { scaleX = scale; scaleY = scale; alpha = ((scale - 0.3f) / 0.7f).coerceIn(0f, 1f) },
            tint = Color.White)
    }
}

internal data class NavItem(val label: String, val icon: ImageVector, val badge: Boolean = false)

/** Barra di navigazione fluttuante: la voce attiva si allarga e mostra l'etichetta. */
@Composable
internal fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(s.background.copy(alpha = 0f), s.background.copy(alpha = 0.92f), s.background)))
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = s.primary.copy(alpha = 0.16f), spotColor = s.primary.copy(alpha = 0.28f))
                .clip(shape).background(s.surfaceContainerLowest).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val background by animateColorAsState(if (isSelected) s.primary else Color.Transparent, spring(stiffness = 420f), label = "Sfondo ${item.label}")
                val foreground by animateColorAsState(if (isSelected) s.onPrimary else s.onSurfaceVariant, spring(stiffness = 420f), label = "Colore ${item.label}")
                val iconScale by animateFloatAsState(if (isSelected) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "Scala ${item.label}")
                Row(
                    Modifier.clip(CircleShape).background(background)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Icon(item.icon, if (isSelected) null else item.label, Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = foreground)
                        if (item.badge && !isSelected) Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-1).dp).size(8.dp).clip(CircleShape).background(s.error))
                    }
                    AnimatedVisibility(isSelected, enter = fadeIn(tween(220, 60)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)), exit = fadeOut(tween(90)) + shrinkHorizontally(spring(stiffness = 500f))) {
                        Text(item.label, Modifier.padding(start = 7.dp), color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Anello di avanzamento che si riempie con un'animazione. */
@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier, track: Color, color: Color, stroke: Dp = 10.dp) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) { animated.animateTo(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
        if (animated.value > 0f) drawArc(color, -90f, 360f * animated.value, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
internal fun CapsuleIcon(color: Color, modifier: Modifier = Modifier, highlight: Color = Color.White) {
    Canvas(modifier) {
        rotate(-45f) {
            val left = size.width * 0.13f
            val top = size.height * 0.34f
            val width = size.width * 0.74f
            val height = size.height * 0.32f
            drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2f))
            clipRect(left, top, left + width / 2f, top + height) {
                val inset = size.width * 0.035f
                drawRoundRect(highlight, Offset(left + inset, top + inset), Size(width - inset * 2, height - inset * 2), CornerRadius((height - inset * 2) / 2f))
            }
        }
    }
}

@Composable
internal fun StethoscopeIcon(color: Color, modifier: Modifier = Modifier, hole: Color = Color.White) {
    Canvas(modifier) {
        val stroke = size.width * 0.085f
        val left = size.width * 0.24f
        val right = size.width * 0.62f
        val yTop = size.height * 0.13f
        val yMid = size.height * 0.48f
        drawLine(color, Offset(left, yTop), Offset(left, yMid), stroke)
        drawLine(color, Offset(right, yTop), Offset(right, yMid), stroke)
        drawArc(color, 0f, 180f, false, Offset(left, yMid - size.height * 0.18f), Size(right - left, size.height * 0.36f), style = Stroke(stroke))
        drawLine(color, Offset((left + right) / 2f, yMid + size.height * 0.18f), Offset((left + right) / 2f, size.height * 0.77f), stroke)
        drawLine(color, Offset((left + right) / 2f, size.height * 0.77f), Offset(size.width * 0.77f, size.height * 0.77f), stroke)
        drawCircle(color, stroke * 0.9f, Offset(left, yTop))
        drawCircle(color, stroke * 0.9f, Offset(right, yTop))
        drawCircle(color, size.width * 0.11f, Offset(size.width * 0.78f, size.height * 0.77f))
        drawCircle(hole, size.width * 0.035f, Offset(size.width * 0.78f, size.height * 0.77f))
    }
}
