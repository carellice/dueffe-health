package it.dueffe.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DocumentPage(
    store: HealthStore, revision: Int, dark: Boolean,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onSettings: () -> Unit, onProfiles: () -> Unit,
    onOpen: (JSONObject) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    val c = homeColors(dark)
    val all = remember(revision, store.activeId) { store.list("documents") }
    val documents = all.filter { doc ->
        doc.str("nome").contains(search, ignoreCase = true) && (filter == "Tutti" || doc.str("categoria").ifBlank { "Altro" } == filter)
    }.sortedWith(compareByDescending<JSONObject> { it.str("data") }.thenBy { it.str("nome").lowercase() })

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 10.dp, bottom = 105.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HealthSearchBar(search, setSearch, "Cerca documenti…")
        }
        item {
            HealthFilterBar(
                listOf(
                    HealthFilterOption("Tutti", Icons.Default.GridView),
                    HealthFilterOption("Analisi", Icons.Default.Science),
                    HealthFilterOption("Referti", Icons.Default.Description),
                    HealthFilterOption("Ricette", Icons.Default.Restaurant),
                    HealthFilterOption("Altro", Icons.Default.MoreHoriz)
                ), filter, setFilter
            )
        }
        if (documents.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(0.5.dp, c.border, RoundedCornerShape(18.dp)).padding(20.dp)) {
                Text(if (search.isNotBlank()) "Nessun risultato" else if (all.isEmpty()) "Nessun documento" else "Nessun documento in questa categoria", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(if (all.isEmpty()) "Usa + per aggiungere il tuo primo documento." else "Prova un'altra categoria o una ricerca diversa.", fontSize = 14.sp, color = c.muted)
            }
        }
        items(documents, key = { it.str("id") }) { doc ->
            DocumentCard(doc, c, dark, onOpen, onEdit, onDelete, onAction)
        }
    }
}

@Composable
private fun DocumentCard(
    doc: JSONObject, c: HomeColors, dark: Boolean,
    onOpen: (JSONObject) -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val name = doc.str("nome").ifBlank { "Documento" }
    val category = doc.str("categoria").ifBlank { "Altro" }
    val hasAttachment = doc.str("attachment").isNotBlank()
    val visual = documentFileVisual(doc, c)
    val dateLabel = runCatching { LocalDate.parse(doc.str("data")).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) }.getOrDefault(displayDate(doc.str("data")))

    Column(
        Modifier.fillMaxWidth().healthSharedBounds("documents-${doc.str("id")}")
            .clip(RoundedCornerShape(19.dp)).background(c.surface)
            .border(1.25.dp, c.border, RoundedCornerShape(19.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(doc) }.padding(start = 14.dp, top = 15.dp, end = 14.dp, bottom = 13.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(visual.background), contentAlignment = Alignment.Center) {
                Icon(visual.icon, visual.description, Modifier.size(29.dp), tint = visual.tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.clip(CircleShape).background(if (dark) c.lilac else Color(0xFFE8F0FF)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, null, Modifier.size(14.dp), tint = c.blue)
                    Spacer(Modifier.width(4.dp))
                    Text(category, fontSize = 12.sp, color = c.blue)
                }
                if (doc.str("data").isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(17.dp), tint = c.muted)
                        Spacer(Modifier.width(7.dp))
                        Text(dateLabel, fontSize = 13.sp, color = c.muted)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, "Apri dettaglio documento", Modifier.align(Alignment.CenterVertically).size(22.dp), tint = c.muted)
        }
        if (doc.str("note").isNotBlank()) Text(doc.str("note"), Modifier.padding(start = 80.dp, end = 16.dp, bottom = 9.dp), fontSize = 12.sp, color = c.muted, maxLines = 2)
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DocumentAction("Apri", Icons.Default.OpenInNew, c.blue, Modifier.weight(1f), enabled = hasAttachment) { onAction(doc, "open") }
            DocumentSeparator(c)
            DocumentAction("Foto", Icons.Default.CameraAlt, c.purple, Modifier.weight(1f)) { onAction(doc, "camera") }
            DocumentSeparator(c)
            DocumentAction("Allega", Icons.Default.AttachFile, Color(0xFF168E70), Modifier.weight(1f)) { onAction(doc, "attach") }
            DocumentSeparator(c)
            DocumentAction("Modifica", Icons.Default.Edit, c.blue, Modifier.weight(1f)) { onEdit(doc) }
            DocumentSeparator(c)
            DocumentAction("Elimina", Icons.Default.Delete, Color(0xFFD93F35), Modifier.weight(1f)) { confirmDelete = true }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare $name?") },
        text = { Text("Questa operazione non può essere annullata.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(doc) }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
    )
}

private data class DocumentFileVisual(val icon: ImageVector, val background: Color, val tint: Color, val description: String)

private fun documentFileVisual(doc: JSONObject, c: HomeColors): DocumentFileVisual {
    return when (documentFormatLabel(doc)) {
        "PDF" -> DocumentFileVisual(Icons.Default.PictureAsPdf, Color(0xFFFFE6F0), Color(0xFFE84E75), "File PDF")
        "Immagine" -> DocumentFileVisual(Icons.Default.Image, Color(0xFFE6F6F2), Color(0xFF138A70), "Immagine")
        "Word" -> DocumentFileVisual(Icons.Default.Article, Color(0xFFE5EFFF), Color(0xFF2469B8), "Documento Word")
        "Nessun allegato" -> DocumentFileVisual(Icons.Default.Description, c.iconBlue, c.blue, "Documento senza allegato")
        else -> DocumentFileVisual(Icons.Default.InsertDriveFile, c.iconBlue, c.blue, "File allegato")
    }
}

@Composable
private fun DocumentAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shownColor = if (enabled) color else color.copy(alpha = 0.4f)
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(21.dp), tint = shownColor)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = shownColor, maxLines = 1)
    }
}

@Composable
private fun DocumentSeparator(c: HomeColors) {
    Box(Modifier.width(0.5.dp).height(35.dp).background(c.border))
}
