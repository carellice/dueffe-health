package it.dueffe.health

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LockScreen(
    name: String, pinLength: Int, entered: Int, error: String, errorCount: Int, biometric: Boolean,
    onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: () -> Unit
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorCount) {
        if (errorCount > 0) for (target in listOf(-18f, 16f, -12f, 9f, -5f, 0f)) shake.animateTo(target, tween(55))
    }
    HeroSurface(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(38.dp), tint = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text(if (name.isBlank() || name == "Io") "Bentornato" else "Ciao, $name", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Inserisci il PIN per aprire Health", fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(28.dp))
            Row(Modifier.graphicsLayer { translationX = shake.value * density }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(pinLength) { index ->
                    val filled = index < entered
                    val scale by animateFloatAsState(if (filled) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "Punto PIN")
                    val color by animateColorAsState(if (error.isNotBlank()) Color(0xFFFFC2CB) else if (filled) Color.White else Color.Transparent, label = "Colore punto PIN")
                    Box(Modifier.size(16.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color).border(2.dp, Color.White, CircleShape))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(error, Modifier.heightIn(min = 20.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD6DC), textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> if (biometric) KeypadButton(onClick = onBiometric) { Icon(Icons.Rounded.Fingerprint, "Usa biometria", Modifier.size(30.dp), tint = Color.White) } else Spacer(Modifier.size(76.dp))
                            "del" -> KeypadButton(onClick = onDelete, filled = false) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Cancella", Modifier.size(26.dp), tint = Color.White) }
                            else -> KeypadButton(onClick = { onDigit(key.first()) }) { Text(key, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KeypadButton(onClick: () -> Unit, filled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "Tasto")
    Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = if (pressed) 0.32f else 0.16f) else Color.Transparent)
            .clickable(interaction, ripple(color = Color.White), onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun WelcomeScreen(name: String, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val s = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "Logo")
    val float by transition.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Fluttuazione logo")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    Column(Modifier.fillMaxSize().starry().verticalScroll(rememberScrollState())) {
        HeroSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(112.dp).graphicsLayer { translationY = float * density; scaleX = appear.value; scaleY = appear.value }
                        .shadow(20.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Image(painterResource(R.drawable.ic_health_logo_foreground), null, Modifier.size(150.dp)) }
                Spacer(Modifier.height(24.dp))
                Text("Benvenuto in Health", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("La salute di tutta la famiglia, in ordine e sempre con te.", fontSize = 16.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WelcomeFeature(Icons.Rounded.Medication, "Terapie e promemoria", "Orari, scorte e dosi segnate in un tocco.", Area.Therapy, 0)
            WelcomeFeature(Icons.Rounded.EventAvailable, "Visite ed esami", "Appuntamenti, referti e cose da prenotare.", Area.Visit, 1)
            WelcomeFeature(Icons.Rounded.FolderOpen, "Documenti sanitari", "Ricette e analisi sempre a portata di mano.", Area.Document, 2)
            WelcomeFeature(Icons.Rounded.Shield, "Solo sul tuo telefono", "Nessun account: i dati restano sul dispositivo.", Area.Therapy, 3)
            Spacer(Modifier.height(8.dp))
            FormText("Come ti chiami?", name, onChange = onNameChange)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                Text("Inizia", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, null)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Più tardi") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, text: String, area: Area, index: Int) {
    val c = areaColors(area)
    Row(Modifier.fillMaxWidth().enterAnimation(index + 1), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.accent, c.container, 48.dp, 16.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
