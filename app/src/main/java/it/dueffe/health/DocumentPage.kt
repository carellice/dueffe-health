package it.dueffe.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal val documentCategories = listOf("Analisi", "Referti", "Ricette", "Altro")

internal fun documentCategoryIcon(category: String): ImageVector = when (category) {
    "Analisi" -> Icons.Rounded.Science
    "Referti" -> Icons.Rounded.Assignment
    "Ricette" -> Icons.Rounded.Receipt
    else -> Icons.Rounded.Folder
}

internal data class DocumentVisual(val icon: ImageVector, val accent: Color, val container: Color)

@Composable
internal fun documentVisual(doc: JSONObject): DocumentVisual {
    val c = areaColors(Area.Document)
    return when (documentFormatLabel(doc)) {
        "PDF" -> DocumentVisual(Icons.Rounded.PictureAsPdf, Color(0xFFE8466C), Color(0xFFE8466C).copy(alpha = 0.16f))
        "Immagine" -> DocumentVisual(Icons.Rounded.Image, Color(0xFF19A884), Color(0xFF19A884).copy(alpha = 0.16f))
        "Word" -> DocumentVisual(Icons.Rounded.Article, Color(0xFF3B7BE8), Color(0xFF3B7BE8).copy(alpha = 0.16f))
        "Nessun allegato" -> DocumentVisual(documentCategoryIcon(doc.str("categoria")), c.accent, c.container)
        else -> DocumentVisual(Icons.Rounded.InsertDriveFile, c.accent, c.container)
    }
}

@Composable
fun DocumentPage(
    store: HealthStore, revision: Int, listState: LazyListState, insets: PaddingValues,
    header: @Composable () -> Unit,
    search: String, setSearch: (String) -> Unit,
    filter: String, setFilter: (String) -> Unit,
    onOpen: (JSONObject) -> Unit, onAdd: () -> Unit,
    onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    val c = areaColors(Area.Document)
    val all = remember(revision, store.activeId) { store.list("documents") }
    fun category(doc: JSONObject) = doc.str("categoria").ifBlank { "Altro" }
    val documents = all.filter { doc -> doc.str("nome").contains(search, ignoreCase = true) && (filter == "Tutti" || category(doc) == filter) }
        .sortedWith(compareByDescending<JSONObject> { it.str("data") }.thenBy { it.str("nome").lowercase() })

    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(Modifier.enterAnimation(0)) { header() } }
        item { Box(Modifier.enterAnimation(1)) { HealthSearchBar(search, setSearch, "Cerca un documento…") } }
        item {
            Box(Modifier.enterAnimation(2)) {
                HealthFilterBar(
                    listOf(HealthFilterOption("Tutti", Icons.Rounded.GridView, all.size)) +
                        documentCategories.map { HealthFilterOption(it, documentCategoryIcon(it), all.count { doc -> category(doc) == it }) },
                    filter, setFilter, c.accent
                )
            }
        }
        if (documents.isEmpty()) item {
            Box(Modifier.enterAnimation(3)) {
                if (all.isEmpty()) EmptyState(Icons.Rounded.FolderOpen, "Archivio vuoto", "Conserva qui referti, analisi e ricette: puoi allegare un PDF o scattare una foto.", c.accent, "Aggiungi documento", onAdd)
                else EmptyState(Icons.Rounded.SearchOff, "Nessun risultato", "Prova un'altra categoria o una ricerca diversa.", c.accent)
            }
        }
        var lastMonth = ""
        documents.forEachIndexed { index, doc ->
            val month = monthLabel(doc.str("data"))
            if (month != lastMonth) {
                lastMonth = month
                item(key = "mese-$month") { Text(month, Modifier.animateItem().padding(top = 6.dp, start = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent) }
            }
            item(key = doc.str("id")) { DocumentCard(doc, Modifier.animateItem().enterAnimation(3 + index), onOpen, onEdit, onDelete, onAction) }
        }
    }
}

@Composable
private fun DocumentCard(
    doc: JSONObject, modifier: Modifier,
    onOpen: (JSONObject) -> Unit, onEdit: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit,
    onAction: (JSONObject, String) -> Unit
) {
    val s = MaterialTheme.colorScheme
    val c = areaColors(Area.Document)
    var confirmDelete by remember { mutableStateOf(false) }
    val name = doc.str("nome").ifBlank { "Documento" }
    val category = doc.str("categoria").ifBlank { "Altro" }
    val hasAttachment = doc.str("attachment").isNotBlank()
    val visual = documentVisual(doc)

    HealthCard(modifier.healthSharedBounds("documents-${doc.str("id")}"), onClick = { onOpen(doc) }, padding = PaddingValues(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(visual.icon, visual.accent, visual.container, 54.dp, 18.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = s.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(category, c.accent, c.container, documentCategoryIcon(category))
                    if (doc.str("data").isNotBlank()) Text(displayDate(doc.str("data")), fontSize = 12.sp, color = s.onSurfaceVariant)
                }
            }
            if (hasAttachment) IconButton(onClick = { onAction(doc, "open") }) { Icon(Icons.Rounded.OpenInNew, "Apri allegato", tint = c.accent) }
            OverflowMenu(buildList {
                if (hasAttachment) add(MenuAction("Apri allegato", Icons.Rounded.OpenInNew) { onAction(doc, "open") })
                add(MenuAction("Scatta una foto", Icons.Rounded.PhotoCamera) { onAction(doc, "camera") })
                add(MenuAction(if (hasAttachment) "Sostituisci file" else "Allega un file", Icons.Rounded.AttachFile) { onAction(doc, "attach") })
                add(MenuAction("Modifica", Icons.Rounded.Edit) { onEdit(doc) })
                add(MenuAction("Elimina", Icons.Rounded.DeleteOutline, danger = true) { confirmDelete = true })
            }, "Azioni per $name")
        }
        if (doc.str("note").isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(doc.str("note"), Modifier.padding(end = 12.dp), fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (confirmDelete) ConfirmDeleteDialog(name, "Anche l'allegato verrà eliminato dal dispositivo.", onConfirm = { confirmDelete = false; onDelete(doc) }, onDismiss = { confirmDelete = false })
}
