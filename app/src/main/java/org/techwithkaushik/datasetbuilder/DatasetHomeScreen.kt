@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.techwithkaushik.datasetbuilder

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private val brand = Color(0xFF145DDE)
private val canvas = Color(0xFFF4F7FC)
private val cardBorder = Color(0xFFE2E9F4)
private data class Project(val id: String, val name: String, val description: String, val labels: List<String>, val updated: Long)

@Composable
internal fun DatasetHomeEntry(context: Context, onOpen: (String, String, List<String>) -> Unit) {
    ProjectHome(context) { project -> onOpen(project.id, project.name, project.labels) }
}

@Composable
private fun ProjectHome(context: Context, onOpen: (Project) -> Unit) {
    val prefs = remember { context.getSharedPreferences("dataset_projects", Context.MODE_PRIVATE) }
    var projects by remember { mutableStateOf(readProjects(prefs)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var edit by remember { mutableStateOf<Project?>(null) }
    var create by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Project?>(null) }
    var settings by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    fun persist(list: List<Project>) {
        projects = list
        prefs.edit().putString("items", JSONArray().apply {
            list.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("description", p.description).put("labels", JSONArray(p.labels)).put("updated", p.updated)) }
        }.toString()).apply()
    }
    fun save(name: String, description: String, labels: List<String>, old: Project?) {
        if (name.isBlank() || labels.isEmpty()) return
        val p = Project(old?.id ?: UUID.randomUUID().toString(), name.trim(), description.trim(), labels, System.currentTimeMillis())
        persist(if (old == null) listOf(p) + projects else projects.map { if (it.id == old.id) p else it })
    }
    BackHandler(enabled = settings || tab == 2 || create || edit != null || delete != null) {
        when {
            settings || tab == 2 -> {
                settings = false
                tab = 0
            }
            create -> create = false
            edit != null -> edit = null
            delete != null -> delete = null
        }
    }
    Scaffold(
        containerColor = canvas,
        topBar = {
            if (!settings && tab != 2) Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = brand.copy(alpha = .10f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Hub, null, tint = brand, modifier = Modifier.size(25.dp)) }
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        LText("Dataset Builder", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        LText("Create, manage and annotate datasets", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }
                    IconButton(onClick = { settings = true; tab = 2 }) { Icon(Icons.Default.Settings, "Settings", tint = Color(0xFF263449)) }
                    Surface(color = brand, shape = RoundedCornerShape(50), modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) { LText("D", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        bottomBar = {
            if (!settings && tab != 2) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(tab == 0, { tab = 0; settings = false }, icon = { Icon(Icons.Default.FolderOpen, null) }, label = { LText("Datasets") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = brand, selectedTextColor = brand, indicatorColor = brand.copy(alpha = .10f)))
                NavigationBarItem(tab == 1, { tab = 1; settings = false }, icon = { Icon(Icons.Default.Insights, null) }, label = { LText("Stats") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = brand, selectedTextColor = brand, indicatorColor = brand.copy(alpha = .10f)))
                NavigationBarItem(tab == 2, { tab = 2; settings = true }, icon = { Icon(Icons.Default.Settings, null) }, label = { LText("Settings") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = brand, selectedTextColor = brand, indicatorColor = brand.copy(alpha = .10f)))
            }
        },
        floatingActionButton = { if (tab == 0 && !settings) ExtendedFloatingActionButton(onClick = { create = true }, icon = { Icon(Icons.Default.Add, null) }, text = { LText("Create Dataset") }, containerColor = brand, contentColor = Color.White) }
    ) { pad ->
        when {
            settings || tab == 2 -> SettingsPage(Modifier.padding(pad)) { settings = false; tab = 0 }
            tab == 1 -> Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LText("Dataset overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LText("Projects: " + projects.size)
                LText("Annotation classes: " + projects.sumOf { it.labels.size })
            }
            else -> Column(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { LText("Search datasets…", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF52647D)) }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = cardBorder, focusedBorderColor = brand))
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { filter = if (filter == "All") "Recent" else "All" }, shape = RoundedCornerShape(15.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF263449)), modifier = Modifier.size(52.dp)) { Icon(Icons.Default.FilterList, "Filter") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Recent", "A–Z", "Modified").forEach { item ->
                        FilterChip(selected = filter == item, onClick = { filter = item }, label = { LText(item, fontWeight = if (filter == item) FontWeight.Bold else FontWeight.Medium) }, shape = RoundedCornerShape(22.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = brand, selectedLabelColor = Color.White, containerColor = Color.White), border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == item, borderColor = cardBorder, selectedBorderColor = brand))
                    }
                }
                val visible = projects.filter { it.name.contains(query, true) || it.description.contains(query, true) }
                    .let { list -> if (filter == "A–Z") list.sortedBy { it.name.lowercase() } else if (filter == "Recent" || filter == "Modified") list.sortedByDescending { it.updated } else list }
                if (visible.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FolderOpen, null, tint = brand, modifier = Modifier.size(52.dp))
                        LText("No datasets found", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { create = true }) { Icon(Icons.Default.Add, null); LText("Create Dataset") }
                    }
                } else LazyColumn(contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    items(visible, key = { it.id }) { p ->
                        val projectRoot = java.io.File(context.filesDir, "dataset-projects/${p.id}")
                        val imageCount = remember(p.id) { java.io.File(projectRoot, "train/images").listFiles()?.count { it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") } ?: 0 }
                        val savedCount = remember(p.id, imageCount) { java.io.File(projectRoot, "train/labels").listFiles()?.count { it.isFile && it.extension.equals("txt", true) } ?: 0 }
                        Card(onClick = { onOpen(p) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                                Surface(color = brand.copy(alpha = .09f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = brand, modifier = Modifier.size(32.dp)) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    LText(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    LText(p.description.ifBlank { "No description added" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        DatasetMetaPill(Icons.Default.Image, "${imageCount} images", Color(0xFF1768E8))
                                        DatasetMetaPill(Icons.Default.Label, "${p.labels.size} classes", Color(0xFF8E44AD))
                                        DatasetMetaPill(Icons.Default.CheckCircle, "${savedCount} saved", Color(0xFF168B58))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Schedule, null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                                        LText("Updated " + formatUpdated(p.updated), style = MaterialTheme.typography.labelSmall, color = Color(0xFF52647D), fontWeight = FontWeight.Medium)
                                    }
                                }
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Project actions") }
                                    DropdownMenu(expanded, { expanded = false }) {
                                        DropdownMenuItem(text = { LText("Edit Project") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { expanded = false; edit = p })
                                        DropdownMenuItem(text = { LText("Duplicate Dataset") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { expanded = false; save(p.name + " Copy", p.description, p.labels, null) })
                                        DropdownMenuItem(text = { LText("Delete Dataset", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; delete = p })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (create) ProjectDialog(null, { create = false }) { n, d, l -> save(n, d, l, null); create = false }
    edit?.let { old -> ProjectDialog(old, { edit = null }) { n, d, l -> save(n, d, l, old); edit = null } }
    delete?.let { p -> AlertDialog(onDismissRequest = { delete = null }, title = { LText("Delete dataset?") }, text = { LText("Project details for “" + p.name + "” will be removed.") }, confirmButton = { TextButton(onClick = { persist(projects.filterNot { it.id == p.id }); delete = null }) { LText("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { delete = null }) { LText("Cancel") } }) }
}

private fun formatUpdated(timestamp: Long): String {
    if (timestamp <= 0L) return "date unavailable"
    val delta = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val relative = when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L} min ago"
        delta < 86_400_000L -> "${delta / 3_600_000L} hr ago"
        delta < 2 * 86_400_000L -> "yesterday"
        delta < 7 * 86_400_000L -> "${delta / 86_400_000L} days ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)) + " · " + relative
}

@Composable
private fun DatasetMetaPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Surface(color = tint.copy(alpha = .09f), shape = RoundedCornerShape(9.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
            LText(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProjectDialog(project: Project?, dismiss: () -> Unit, save: (String, String, List<String>) -> Unit) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var description by remember(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var classesText by remember(project?.id) { mutableStateOf(project?.labels?.joinToString(", ").orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { LText(if (project == null) "Create New Dataset" else "Edit Project") },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { LText("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { LText("Description (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                LText("Annotation classes", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = classesText,
                    onValueChange = { classesText = it },
                    label = { LText("Class names") },
                    placeholder = { LText("Person, Vehicle, Animal") },
                    supportingText = { LText("Separate class names with commas. Their order determines YOLO class IDs starting at 0.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && classesText.split(",").any { it.trim().isNotBlank() }, onClick = { save(name, description, classesText.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }) }) { LText(if (project == null) "Create Dataset" else "Save Changes") } },
        dismissButton = { TextButton(onClick = dismiss) { LText("Cancel") } },
    )
}

@Composable
private fun SettingsPage(modifier: Modifier = Modifier, back: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("dataset_projects", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf("System default") }
    var expanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(preferences.getString("app_language", "English") ?: "English") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null) }; LText("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        LText("Language", color = brand, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = brand)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        LText("App language", fontWeight = FontWeight.SemiBold)
                        LText("Choose the language used by the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { languageExpanded = true }) { LText(language); Icon(Icons.Default.ArrowDropDown, null) }
                        DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                            listOf("English", "हिन्दी", "Español", "Français", "العربية").forEach { option ->
                                DropdownMenuItem(
                                    text = { LText(option) },
                                    leadingIcon = { if (language == option) Icon(Icons.Default.Check, null, tint = brand) },
                                    onClick = {
                                        language = option
                                        AppLanguageState.language = option
                                        preferences.edit().putString("app_language", option).apply()
                                        languageExpanded = false
                                        Toast.makeText(context, "Language preference saved", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        LText("Appearance", color = brand, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null); LText("Theme", Modifier.weight(1f).padding(start = 10.dp))
                    Box { TextButton(onClick = { expanded = true }) { LText(theme); Icon(Icons.Default.ArrowDropDown, null) }; DropdownMenu(expanded, { expanded = false }) { listOf("System default", "Light", "Dark").forEach { DropdownMenuItem(text = { LText(it) }, onClick = { theme = it; expanded = false }) } } }
                }
                HorizontalDivider()
                ListItem(headlineContent = { LText("Dynamic color") }, leadingContent = { Icon(Icons.Default.Palette, null) }, trailingContent = { var enabled by remember { mutableStateOf(true) }; Switch(enabled, { enabled = it }) })
            }
        }
        LText("Data & Storage", color = brand, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp)) { Column { ListItem(headlineContent = { LText("Dataset storage") }, supportingContent = { LText("App-private YOLO workspace") }, leadingContent = { Icon(Icons.Default.Folder, null) }); HorizontalDivider(); ListItem(headlineContent = { LText("Export format") }, supportingContent = { LText("YOLO dataset ZIP") }, leadingContent = { Icon(Icons.Default.Archive, null) }) } }
        LText("About", color = brand, fontWeight = FontWeight.Bold)
        ListItem(headlineContent = { LText("Dataset Builder") }, supportingContent = { LText("Version 1.0.0 • org.techwithkaushik") }, leadingContent = { Icon(Icons.Default.Info, null) })
    }
}

private fun readProjects(prefs: android.content.SharedPreferences): List<Project> {
    val json = prefs.getString("items", null) ?: return emptyList()
    return runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val labels = o.optJSONArray("labels") ?: JSONArray()
            Project(o.getString("id"), o.getString("name"), o.optString("description"), (0 until labels.length()).map { labels.getString(it) }, o.optLong("updated"))
        }
    }.getOrDefault(emptyList())
}
