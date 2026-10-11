@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.techwithkaushik.datasetbuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private data class LabelClass(val id: Int, val title: String, val color: Color)
private data class LabelBox(val type: LabelClass, val l: Float, val t: Float, val r: Float, val b: Float)

@Composable
internal fun DatasetAnnotationScreen(images: List<File>, onExit: () -> Unit, onMessage: (String) -> Unit, projectId: String = "default", projectName: String = "Dataset", classNames: List<String> = listOf("Object")) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredClassNames = classNames.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Object") }
    val classes = remember(configuredClassNames) {
        val palette = listOf(Color(0xFF1768E8), Color(0xFF168B58), Color(0xFFE67E22), Color(0xFF8E44AD), Color(0xFF008B8B), Color(0xFFC0392B), Color(0xFF596275), Color(0xFFB33939))
        configuredClassNames.mapIndexed { i, name -> LabelClass(i, name, palette[i % palette.size]) }
    }
    var libraryImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember(classes) { mutableStateOf(classes.first()) }
    var annotationsByImage by remember { mutableStateOf<Map<String, List<LabelBox>>>(emptyMap()) }
    var groupIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var activeBox by remember { mutableStateOf<LabelBox?>(null) }
    var busy by remember { mutableStateOf(false) }
    var importingImages by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var importProgressLabel by remember { mutableStateOf("") }
    var fullScreen by remember { mutableStateOf(false) }
    var builderTab by remember { mutableStateOf("Annotate") }
    var builderMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var imageSearch by remember { mutableStateOf("") }
    var imageSort by remember { mutableStateOf("Name") }
    var searchVisible by remember { mutableStateOf(false) }
    var zoomScale by remember(index) { mutableFloatStateOf(1f) }
    var zoomOffset by remember(index) { mutableStateOf(Offset.Zero) }
    var panMode by remember(index) { mutableStateOf(false) }
    var overlayVisible by remember(index) { mutableStateOf(true) }
    var redoBoxes by remember(index) { mutableStateOf<List<List<LabelBox>>>(emptyList()) }
    var formGroupId by remember(index, libraryImages, groupIds) {
        mutableStateOf(libraryImages.getOrNull(index)?.let { groupIds[it.nameWithoutExtension] ?: it.nameWithoutExtension }.orEmpty())
    }
    var imageToRemove by remember { mutableStateOf<File?>(null) }
    var savedPaths by remember(images) { mutableStateOf<Set<String>>(emptySet()) }
    var dirtyPaths by remember(images) { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("Select a class, then drag around each object to annotate it.") }
    val image = libraryImages.getOrNull(index)
    val boxes = image?.let { annotationsByImage[it.absolutePath].orEmpty() }.orEmpty()
    val saved = savedPaths.size
    val bitmap = remember(image?.absolutePath) { image?.let { decodeSampledBitmap(it, 1800) } }
    val thumbnailListState = rememberLazyListState()
    LaunchedEffect(index, libraryImages.size) {
        if (libraryImages.isNotEmpty()) thumbnailListState.animateScrollToItem(index.coerceIn(0, libraryImages.lastIndex))
    }

    // Dataset images and YOLO labels live in app-private storage, not transient picker cache.
    // This makes the library available after leaving the screen or restarting the app.
    LaunchedEffect(images) {
        busy = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                val root = File(context.filesDir, "dataset-projects/$projectId")
                val imageDir = File(root, "train/images").apply { mkdirs() }
                val labelDir = File(root, "train/labels").apply { mkdirs() }
                images.forEachIndexed { i, source ->
                    if (source.isFile && source.length() > 0L) {
                        val alreadyManaged = runCatching { source.canonicalFile.parentFile == imageDir.canonicalFile }.getOrDefault(false)
                        if (!alreadyManaged) {
                            val ext = source.extension.lowercase(Locale.ROOT).let { if (it in listOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
                            var destination = File(imageDir, "import_${System.currentTimeMillis()}_${i}_${source.nameWithoutExtension.take(40)}.$ext")
                            var suffix = 1
                            while (destination.exists()) {
                                destination = File(imageDir, "import_${System.currentTimeMillis()}_${i}_${suffix++}.$ext")
                            }
                            source.copyTo(destination, false)
                        }
                    }
                }
                val all = imageDir.listFiles()?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in listOf("jpg", "jpeg", "png", "webp") }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
                val loadedAnnotations = mutableMapOf<String, List<LabelBox>>()
                val loadedSaved = mutableSetOf<String>()
                val groupFile = File(root, "train/groups.txt")
                val groups = groupFile.takeIf { it.isFile }?.readLines().orEmpty().mapNotNull { line ->
                    val parts = line.split("\t", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap()
                all.forEach { file ->
                    val label = File(labelDir, file.nameWithoutExtension + ".txt")
                    if (label.isFile) {
                        loadedSaved += file.absolutePath
                        val parsed = label.readLines().mapNotNull { line ->
                            val v = line.trim().split(Regex("\\s+"))
                            if (v.size != 5) null else runCatching {
                                val cls = classes.first { it.id == v[0].toInt() }
                                val cx = v[1].toFloat(); val cy = v[2].toFloat()
                                val bw = v[3].toFloat(); val bh = v[4].toFloat()
                                LabelBox(cls, (cx - bw / 2f).coerceIn(0f, 1f), (cy - bh / 2f).coerceIn(0f, 1f),
                                    (cx + bw / 2f).coerceIn(0f, 1f), (cy + bh / 2f).coerceIn(0f, 1f))
                            }.getOrNull()
                        }
                        loadedAnnotations[file.absolutePath] = parsed
                    }
                }
                Triple(all, loadedAnnotations, Pair(loadedSaved, groups))
            }
            libraryImages = loaded.first
            annotationsByImage = loaded.second
            savedPaths = loaded.third.first
            groupIds = loaded.third.second
            dirtyPaths = emptySet()
            index = index.coerceIn(0, (loaded.first.size - 1).coerceAtLeast(0))
            status = if (loaded.first.isEmpty()) "Dataset is empty. Select Add images to get started." else "Saved dataset loaded: ${loaded.first.size} images"
        } catch (e: Exception) {
            status = "Dataset load failed: " + (e.message ?: "unknown error")
        } finally { busy = false }
    }

    val addImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            importingImages = true
            importProgress = 0f
            importProgressLabel = "Preparing import…"
            try {
                val dir = File(context.filesDir, "dataset-projects/$projectId/train/images")
                dir.mkdirs()
                val previousPaths = libraryImages.map { it.absolutePath }.toSet()
                uris.forEachIndexed { i, uri ->
                    importProgressLabel = "Importing image ${i + 1} of ${uris.size}"
                    withContext(Dispatchers.IO) {
                        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
                        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                        val namedExtension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
                        val ext = when {
                            namedExtension in listOf("jpg", "jpeg", "png", "webp") -> namedExtension
                            mime == "image/png" -> "png"
                            mime == "image/webp" -> "webp"
                            mime == "image/jpeg" -> "jpg"
                            else -> error("Unsupported image format: ${displayName ?: mime.ifBlank { "unknown" }}. Please choose JPG, PNG, or WebP.")
                        }
                        var out = File(dir, "added_${System.currentTimeMillis()}_${i}.$ext")
                        while (out.exists()) out = File(dir, "added_${System.currentTimeMillis()}_${i}_${System.nanoTime()}.$ext")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                out.outputStream().buffered().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                            } ?: error("Could not open selected image")
                            check(out.length() > 0L) { "Selected image is empty" }
                        } catch (e: Exception) {
                            out.delete()
                            throw e
                        }
                    }
                    importProgress = (i + 1).toFloat() / uris.size
                }
                val refreshed = dir.listFiles()?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in listOf("jpg", "jpeg", "png", "webp") }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
                libraryImages = refreshed
                index = refreshed.indexOfFirst { it.absolutePath !in previousPaths }.takeIf { it >= 0 } ?: 0
                status = "${uris.size} image(s) added without resizing or re-encoding. Original image quality preserved."
            } catch (e: Exception) { status = "Add images failed: " + (e.message ?: "unknown error") }
            finally {
                importingImages = false
                importProgress = 0f
                importProgressLabel = ""
                busy = false
            }
        }
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { writeDatasetZip(context, uri, projectId, configuredClassNames) }
                onMessage("Dataset ZIP exported successfully")
            } catch (e: Exception) { onMessage("Export failed: " + (e.message ?: "unknown error")) }
            finally { busy = false }
        }
    }

    fun saveCurrent(onSaved: () -> Unit = {}) {
        val current = image ?: return
        val currentBoxes = boxes
        val normalizedGroup = formGroupId.trim().replace("\\t", " ").replace("\\n", " ").replace("\\r", " ")
        if (normalizedGroup.isBlank()) {
            status = "Enter a Group ID before switching images."
            return
        }
        scope.launch {
            busy = true
            try {
                val base = current.nameWithoutExtension
                withContext(Dispatchers.IO) {
                    val root = File(context.filesDir, "dataset-projects/$projectId")
                    val labelDir = File(root, "train/labels").apply { mkdirs() }
                    val text = currentBoxes.joinToString("\n") { b ->
                        String.format(Locale.US, "%d %.6f %.6f %.6f %.6f", b.type.id,
                            (b.l + b.r) / 2f, (b.t + b.b) / 2f, b.r - b.l, b.b - b.t)
                    } + "\n"
                    File(labelDir, base + ".txt").writeText(text)
                    val groupsFile = File(root, "train/groups.txt")
                    val existing = groupsFile.takeIf { it.isFile }?.readLines().orEmpty()
                        .filterNot { it.substringBefore("\\t") == base }
                    groupsFile.writeText((existing + "$base\\t${normalizedGroup.lowercase(Locale.ROOT)}").joinToString("\n", postfix = "\n"))
                }
                annotationsByImage = annotationsByImage + (current.absolutePath to currentBoxes)
                savedPaths = savedPaths + current.absolutePath
                groupIds = groupIds + (base to normalizedGroup.lowercase(Locale.ROOT))
                dirtyPaths = dirtyPaths - current.absolutePath
                status = "Saved annotations: ${current.name}"
                onSaved()
            } catch (e: Exception) {
                status = "Save failed: " + (e.message ?: "unknown error")
            } finally {
                busy = false
            }
        }
    }

    fun saveAndNext() {
        if (index >= libraryImages.lastIndex) {
            saveCurrent { status = "All images reviewed. Export the dataset when ready." }
        } else {
            saveCurrent {
                index++
                activeBox = null
                status = "Next image: annotate only the objects that are present."
            }
        }
    }


    if (imageToRemove != null) {
        AlertDialog(
            onDismissRequest = { imageToRemove = null },
            title = { LText("Delete this image?") },
            text = { LText("This image and its saved annotations will be permanently removed from the dataset. The original source file will remain untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = imageToRemove
                    imageToRemove = null
                    if (target != null) scope.launch {
                        busy = true
                        try {
                            val removedIndex = libraryImages.indexOfFirst { it.absolutePath == target.absolutePath }
                            withContext(Dispatchers.IO) {
                                if (!target.delete()) throw IllegalStateException("Image file could not be deleted")
                                File(context.filesDir, "dataset-projects/$projectId/train/labels/${target.nameWithoutExtension}.txt").delete()
                                val groupsFile = File(context.filesDir, "dataset-projects/$projectId/train/groups.txt")
                                if (groupsFile.isFile) groupsFile.writeText(groupsFile.readLines().filterNot { it.substringBefore("\\t") == target.nameWithoutExtension }.joinToString("\\n", postfix = "\\n"))
                            }
                            annotationsByImage = annotationsByImage - target.absolutePath
                            savedPaths = savedPaths - target.absolutePath
                            groupIds = groupIds - target.nameWithoutExtension
                            dirtyPaths = dirtyPaths - target.absolutePath
                            libraryImages = libraryImages.filterNot { it.absolutePath == target.absolutePath }
                            index = when {
                                libraryImages.isEmpty() -> 0
                                removedIndex in 0..index -> (index - 1).coerceAtLeast(0).coerceAtMost(libraryImages.lastIndex)
                                else -> index.coerceAtMost(libraryImages.lastIndex)
                            }
                            activeBox = null
                            status = "Image deleted from dataset"
                        } catch (e: Exception) { status = "Delete failed: " + (e.message ?: "unknown error") }
                        finally { busy = false }
                    }
                }) { LText("Delete") }
            },
            dismissButton = { TextButton(onClick = { imageToRemove = null }) { LText("Cancel") } },
        )
    }

    BackHandler(enabled = fullScreen || imageToRemove != null) {
        when {
            imageToRemove != null -> imageToRemove = null
            fullScreen -> fullScreen = false
        }
    }
    Scaffold(
        topBar = {
            if (!fullScreen) Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            LText(projectName, fontWeight = FontWeight.ExtraBold, maxLines = 1, style = MaterialTheme.typography.titleMedium, color = Color(0xFF17243A))
                            LText("${libraryImages.size} images  ·  ${savedPaths.size} saved", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), maxLines = 1)
                        }
                    },
                    navigationIcon = { androidx.compose.material3.IconButton(onClick = onExit) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back to datasets") } },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { addImagesLauncher.launch(arrayOf("image/*")) }, enabled = !busy) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.AddPhotoAlternate, contentDescription = "Add images") }
                        androidx.compose.material3.IconButton(onClick = { builderMenu = true }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "Project actions") }
                        DropdownMenu(expanded = builderMenu, onDismissRequest = { builderMenu = false }) {
                            DropdownMenuItem(text = { LText("Export dataset ZIP") }, leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Archive, null) }, enabled = saved == libraryImages.size && libraryImages.isNotEmpty() && dirtyPaths.isEmpty() && !busy, onClick = { builderMenu = false; zipLauncher.launch("${projectName.trim().ifBlank { "Dataset" }.replace(Regex("[/\\\\:*?\"<>|]"), "_")}-dataset.zip") })
                            DropdownMenuItem(text = { LText("Finish and return") }, leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, null) }, enabled = !busy, onClick = { builderMenu = false; onExit() })
                        }
                    }
                )
                if (importingImages) {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            LText(importProgressLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color(0xFF334155))
                            LText("${(importProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF145DDE))
                        }
                        LinearProgressIndicator(
                            progress = { importProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = Color(0xFF145DDE),
                            trackColor = Color(0xFFE5EAF2),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFFF4F7FC)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Annotate", "Images", "Classes", "Info").forEach { tab ->
                        FilterChip(
                            selected = builderTab == tab,
                            onClick = { builderTab = tab },
                            label = { LText(tab, maxLines = 1, fontWeight = if (builderTab == tab) FontWeight.Bold else FontWeight.Medium) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF145DDE),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF34445C),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = builderTab == tab,
                                borderColor = Color(0xFFE2E9F4),
                                selectedBorderColor = Color(0xFF145DDE),
                            ),
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!fullScreen && builderTab == "Annotate") Surface(color = Color.White, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 1.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        LText("Annotations (${boxes.size})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF182230))
                        Box(Modifier.clickable { builderMenu = true }.padding(horizontal = 8.dp, vertical = 3.dp)) { LText("Manage", color = Color(0xFF145DDE), style = MaterialTheme.typography.labelLarge) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        classes.forEach { cls ->
                            val count = boxes.count { it.type == cls }
                            Row(Modifier.weight(1f).heightIn(min = 29.dp).clip(RoundedCornerShape(10.dp)).background(cls.color.copy(alpha = .09f)).border(1.dp, cls.color.copy(alpha = .16f), RoundedCornerShape(10.dp)).clickable { selected = cls }.padding(horizontal = 5.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(cls.color, RoundedCornerShape(50)))
                                LText(cls.title, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color(0xFF27364A))
                                LText("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cls.color)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        LText("Images (${libraryImages.size})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF182230))
                        Box {
                            Box(Modifier.clickable { sortMenu = true }.padding(horizontal = 5.dp, vertical = 3.dp)) { LText("Sort: $imageSort ⌄", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155)) }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf("Name", "Newest", "Oldest").forEach { option ->
                                    DropdownMenuItem(text = { LText(option) }, onClick = { imageSort = option; sortMenu = false })
                                }
                            }
                        }
                        Box(Modifier.size(28.dp).clickable { searchVisible = !searchVisible; if (!searchVisible) imageSearch = "" }, contentAlignment = Alignment.Center) { LText(if (searchVisible) "×" else "⌕", style = MaterialTheme.typography.titleLarge, color = Color(0xFF334155)) }
                    }
                    if (searchVisible) {
                        OutlinedTextField(value = imageSearch, onValueChange = { imageSearch = it }, singleLine = true, placeholder = { LText("Search images…") }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp), shape = RoundedCornerShape(10.dp), textStyle = MaterialTheme.typography.bodySmall)
                    }
                    val visibleImages = remember(libraryImages, imageSearch, imageSort) {
                        val filtered = libraryImages.filter { it.name.contains(imageSearch.trim(), ignoreCase = true) }
                        when (imageSort) {
                            "Newest" -> filtered.sortedByDescending { it.lastModified() }
                            "Oldest" -> filtered.sortedBy { it.lastModified() }
                            else -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
                        }
                    }
                    LazyRow(state = thumbnailListState, horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp)) {
                        items(visibleImages, key = { it.absolutePath }) { item ->
                            val itemIndex = libraryImages.indexOf(item)
                            val thumbState = produceState<Bitmap?>(initialValue = null, key1 = item.absolutePath) { value = withContext(Dispatchers.IO) { decodeSampledBitmap(item, 150) } }
                            val thumb = thumbState.value
                            DisposableEffect(thumb) { onDispose { thumb?.recycle() } }
                            Column(Modifier.width(58.dp).clickable(enabled = !busy) { if (itemIndex != index) saveCurrent { index = itemIndex; activeBox = null; status = item.name } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF4F6F9)).border(if (itemIndex == index) 2.dp else 1.dp, if (itemIndex == index) Color(0xFF145DDE) else Color(0xFFD9E0E9), RoundedCornerShape(8.dp))) {
                                    if (thumb != null) Image(thumb.asImageBitmap(), item.name, Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                    Box(Modifier.align(Alignment.TopStart).padding(2.dp).size(19.dp).background(Color(0xFFDC2626), RoundedCornerShape(50)).clickable(enabled = !busy) { imageToRemove = item }, contentAlignment = Alignment.Center) { LText("×", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                                    if (item.absolutePath in savedPaths) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(17.dp).background(Color(0xFF16A34A), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { LText("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    if (itemIndex == index) Box(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(17.dp).background(Color(0xFF145DDE), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { LText("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                }
                                LText(item.name, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color(0xFF182230))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { if (index > 0) saveCurrent { index--; activeBox = null; status = "Previous image" } }, enabled = index > 0 && !busy, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(vertical = 2.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF0F4FA), contentColor = Color(0xFF17243A)), border = null) { LText("←  Previous", fontWeight = FontWeight.SemiBold) }
                        LText("${if (libraryImages.isEmpty()) 0 else index + 1} / ${libraryImages.size}", modifier = Modifier.widthIn(min = 54.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Color(0xFF26364C))
                        Button(onClick = { saveAndNext() }, enabled = image != null && !busy, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075BFA))) { LText(if (index < libraryImages.lastIndex) "Save & Next  →" else "Save ✓", fontWeight = FontWeight.SemiBold) }
                    }
                }
            } else if (!fullScreen) Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { builderTab = "Annotate" }, modifier = Modifier.weight(1f)) { LText("Back to annotation") }
                    Button(onClick = { zipLauncher.launch("${projectName.trim().ifBlank { "Dataset" }.replace(Regex("[/\\\\:*?\"<>|]"), "_")}-dataset.zip") }, enabled = saved == libraryImages.size && libraryImages.isNotEmpty() && dirtyPaths.isEmpty() && !busy, modifier = Modifier.weight(1f)) { LText("Export ZIP") }
                }
            }
        }
    ) { padding ->
        if (!fullScreen && builderTab != "Annotate") {
            when (builderTab) {
                "Images" -> {
                    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                        LText("Images · ${libraryImages.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(libraryImages.size) { i ->
                                val item = libraryImages[i]
                                val thumbState = produceState<Bitmap?>(initialValue = null, key1 = item.absolutePath) {
                                    value = withContext(Dispatchers.IO) { decodeSampledBitmap(item, 240) }
                                }
                                val thumb = thumbState.value
                                DisposableEffect(thumb) { onDispose { thumb?.recycle() } }
                                ElevatedCard(onClick = { index = i; builderTab = "Annotate" }, shape = RoundedCornerShape(10.dp)) {
                                    Column(Modifier.padding(5.dp)) {
                                        if (thumb != null) Image(thumb.asImageBitmap(), item.name, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                                        else Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { LText("Loading…") }
                                        LText(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        LText(if (item.absolutePath in savedPaths) "Saved" else "Pending", style = MaterialTheme.typography.labelSmall, color = if (item.absolutePath in savedPaths) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                "Classes" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LText("Annotation classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    classes.forEach { cls ->
                        val count = libraryImages.sumOf { file -> annotationsByImage[file.absolutePath].orEmpty().count { it.type == cls } }
                        ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(cls.color, RoundedCornerShape(50)))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    LText(cls.title, fontWeight = FontWeight.SemiBold)
                                    LText("Class ID: ${cls.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LText(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    LText("Class IDs follow the configured class order and are included in data.yaml.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LText("Dataset info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LText(projectName, style = MaterialTheme.typography.titleMedium)
                    LText("Images: ${libraryImages.size}")
                    LText("Saved labels: ${savedPaths.size}")
                    LText("Pending images: ${(libraryImages.size - savedPaths.size).coerceAtLeast(0)}")
                    LText("Unsaved edits: ${dirtyPaths.size}")
                    LText("Workspace: app-private storage, isolated by project.")
                    LText(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { builderTab = "Annotate" }) { LText("Return to annotation") }
                }
            }
        } else Box(
            Modifier.fillMaxSize()
                .then(if (fullScreen) Modifier else Modifier.padding(padding))
                .background(Color(0xFF101216))
                .padding(if (fullScreen) 0.dp else 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (libraryImages.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LText("No dataset images yet", color = Color.White)
                    Button(onClick = { addImagesLauncher.launch(arrayOf("image/*")) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { LText("Add images") }
                }
            } else if (bitmap == null) LText("Image could not be opened", color = Color.White)
            else BoxWithConstraints(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(if (fullScreen) 0.dp else 16.dp))
                    .background(Color(0xFF101216))
                    .pointerInput(fullScreen, index, selected, bitmap, panMode) {
                        awaitEachGesture {
                            val down = awaitPointerEvent()
                            val firstDown = down.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                            var start = firstDown.position
                            var previousCentroid = Offset.Zero
                            var previousDistance = 0f
                            var moved = false
                            var multi = false
                            var lastPoint = start
                            var currentBox: LabelBox? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multi = true
                                    val centroid = pressed.map { it.position }.reduce { p, q -> p + q } / pressed.size.toFloat()
                                    val distance = pressed.sumOf { (it.position - centroid).getDistance().toDouble() }.toFloat() / pressed.size
                                    if (fullScreen && previousDistance > 0f && distance > 0f) {
                                        val oldScale = zoomScale.coerceAtLeast(1f)
                                        val nextScale = (oldScale * distance / previousDistance).coerceIn(1f, 10f)
                                        // Keep the image point under the fingers stable while scaling.
                                        // The current centroid already includes two-finger panning.
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val previousFocus = previousCentroid - center
                                        val pan = centroid - previousCentroid
                                        val factor = nextScale / oldScale
                                        // Transform the image point under the previous centroid to
                                        // the current centroid, avoiding doubled pan movement.
                                        val proposed = Offset(
                                            previousFocus.x - (previousFocus.x - zoomOffset.x) * factor + pan.x,
                                            previousFocus.y - (previousFocus.y - zoomOffset.y) * factor + pan.y,
                                        )
                                        zoomScale = nextScale
                                        if (nextScale <= 1f) zoomOffset = Offset.Zero else {
                                            val fit = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                                            val maxX = ((bitmap.width * fit * nextScale - size.width) / 2f).coerceAtLeast(0f)
                                            val maxY = ((bitmap.height * fit * nextScale - size.height) / 2f).coerceAtLeast(0f)
                                            zoomOffset = Offset(proposed.x.coerceIn(-maxX, maxX), proposed.y.coerceIn(-maxY, maxY))
                                        }
                                    }
                                    previousDistance = distance
                                    previousCentroid = centroid
                                    pressed.forEach { it.consume() }
                                } else {
                                    previousDistance = 0f
                                    val point = pressed.first().position
                                    if ((point - start).getDistance() > 3f) moved = true
                                    if (moved && !multi) {
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val fit = minOf(w / bitmap.width, h / bitmap.height)
                                        val dw = bitmap.width * fit * if (fullScreen) zoomScale else 1f
                                        val dh = bitmap.height * fit * if (fullScreen) zoomScale else 1f
                                        if (fullScreen && panMode) {
                                            // In Move mode, a single-finger drag pans the zoomed image
                                            // so content hidden behind the bottom toolbar can be brought into view.
                                            val delta = point - lastPoint
                                            val maxX = ((dw - w) / 2f).coerceAtLeast(0f)
                                            val maxY = ((dh - h) / 2f).coerceAtLeast(0f)
                                            zoomOffset = Offset(
                                                (zoomOffset.x + delta.x).coerceIn(-maxX, maxX),
                                                (zoomOffset.y + delta.y).coerceIn(-maxY, maxY),
                                            )
                                        } else if (!panMode) {
                                            val ox = (w - dw) / 2f + if (fullScreen) zoomOffset.x else 0f
                                            val oy = (h - dh) / 2f + if (fullScreen) zoomOffset.y else 0f
                                            val x1 = ((minOf(start.x, point.x) - ox) / dw).coerceIn(0f, 1f)
                                            val y1 = ((minOf(start.y, point.y) - oy) / dh).coerceIn(0f, 1f)
                                            val x2 = ((maxOf(start.x, point.x) - ox) / dw).coerceIn(0f, 1f)
                                            val y2 = ((maxOf(start.y, point.y) - oy) / dh).coerceIn(0f, 1f)
                                            currentBox = if (x2 > x1 && y2 > y1) LabelBox(selected, x1, y1, x2, y2) else null
                                            activeBox = currentBox
                                        }
                                    }
                                    lastPoint = point
                                    pressed.forEach { it.consume() }
                                }
                            }
                            if (multi) {
                                activeBox = null
                            } else if (moved) {
                                if (!panMode) currentBox?.let { box ->
                                    if (box.r - box.l > .005f && box.b - box.t > .005f) {
                                        image?.let { current ->
                                            annotationsByImage = annotationsByImage + (current.absolutePath to (boxes + box))
                                            if (current.absolutePath in savedPaths) dirtyPaths = dirtyPaths + current.absolutePath
                                        }
                                    }
                                }
                                activeBox = null
                            } else if (!fullScreen) {
                                fullScreen = true
                                overlayVisible = true
                            } else {
                                // A tap anywhere on the canvas toggles the controls overlay.
                                overlayVisible = !overlayVisible
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val scale = minOf(w / bitmap.width, h / bitmap.height)
                val dw = bitmap.width * scale * (if (fullScreen) zoomScale else 1f)
                val dh = bitmap.height * scale * (if (fullScreen) zoomScale else 1f)
                val panX = if (fullScreen) zoomOffset.x else 0f
                val panY = if (fullScreen) zoomOffset.y else 0f
                val ox = (w - dw) / 2f + panX
                val oy = (h - dh) / 2f + panY
                Image(bitmap.asImageBitmap(), "Imported form", Modifier.offset(
                    x = with(androidx.compose.ui.platform.LocalDensity.current) { panX.toDp() },
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { panY.toDp() }
                ).requiredSize(
                    with(androidx.compose.ui.platform.LocalDensity.current) { dw.toDp() },
                    with(androidx.compose.ui.platform.LocalDensity.current) { dh.toDp() }), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    (boxes + listOfNotNull(activeBox)).forEach { b ->
                        drawRect(b.type.color, Offset(ox + b.l * dw, oy + b.t * dh),
                            Size((b.r - b.l) * dw, (b.b - b.t) * dh),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            if (!fullScreen && builderTab == "Annotate" && libraryImages.isNotEmpty()) {
                Row(Modifier.align(Alignment.TopStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = Color(0xCC20252D), shape = RoundedCornerShape(10.dp)) {
                        LText("${index + 1} / ${libraryImages.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = Color(0xCC20252D), shape = RoundedCornerShape(10.dp)) {
                        LText("⌕", modifier = Modifier.clickable { fullScreen = true }.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Surface(color = Color(0xCC20252D), shape = RoundedCornerShape(10.dp)) {
                        LText("⛶", modifier = Modifier.clickable { fullScreen = true }.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Row(Modifier.align(Alignment.CenterStart).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xB820252D), shape = RoundedCornerShape(50.dp), modifier = Modifier.padding(start = 2.dp).clickable(enabled = index > 0 && !busy) { index--; activeBox = null }) {
                        LText("‹", modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    Surface(color = Color(0xB820252D), shape = RoundedCornerShape(50.dp), modifier = Modifier.padding(end = 2.dp).clickable(enabled = index < libraryImages.lastIndex && !busy) { index++; activeBox = null }) {
                        LText("›", modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            if (fullScreen) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(color = Color(0xEE101216), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            IconButton(onClick = { fullScreen = false }, modifier = Modifier.size(34.dp)) { Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                            LText(image?.name ?: "Image", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { fullScreen = false }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp), shape = RoundedCornerShape(8.dp)) { LText("Done", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    Surface(color = Color(0xCC101216), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            classes.forEach { cls ->
                                val count = boxes.count { it.type == cls }
                                val isSelected = selected.id == cls.id
                                Row(
                                    Modifier
                                        .widthIn(min = 92.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cls.color.copy(alpha = if (isSelected) .42f else .14f))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) cls.color else cls.color.copy(alpha = .4f),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { selected = cls }
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(if (isSelected) 10.dp else 8.dp).background(cls.color, RoundedCornerShape(50)))
                                    Column {
                                        LText(cls.title, color = Color.White, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        LText("ID: ${cls.id} · $count", color = if (isSelected) Color.White else Color.LightGray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    if (isSelected) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Selected class", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (overlayVisible) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), color = Color(0xDD101216), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (fullScreen) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                LText(
                                    if (panMode) "↔ Move image" else "▱ Draw boxes",
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (panMode) Color(0xFF075BFA) else Color(0xFF343B46))
                                        .clickable { panMode = !panMode; activeBox = null }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        LazyRow(state = thumbnailListState, horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                            items(libraryImages, key = { it.absolutePath }) { item ->
                                val itemIndex = libraryImages.indexOf(item)
                                val thumbState = produceState<Bitmap?>(initialValue = null, key1 = item.absolutePath) { value = withContext(Dispatchers.IO) { decodeSampledBitmap(item, 150) } }
                                val thumb = thumbState.value
                                DisposableEffect(thumb) { onDispose { thumb?.recycle() } }
                                Box(Modifier.width(58.dp).height(62.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF252A32)).border(if (itemIndex == index) 2.dp else 1.dp, if (itemIndex == index) Color(0xFF075BFA) else Color(0xFF525866), RoundedCornerShape(8.dp)).clickable(enabled = !busy) { if (itemIndex != index) saveCurrent { index = itemIndex; activeBox = null; status = item.name } }) {
                                    if (thumb != null) Image(thumb.asImageBitmap(), item.name, Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                                    Box(Modifier.align(Alignment.TopStart).padding(2.dp).size(18.dp).background(Color(0xFFDC2626), RoundedCornerShape(50)).clickable(enabled = !busy) { imageToRemove = item }, contentAlignment = Alignment.Center) { LText("×", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    if (item.absolutePath in savedPaths) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp).background(Color(0xFF16A34A), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Saved", tint = Color.White, modifier = Modifier.size(11.dp)) }
                                    if (itemIndex == index) Box(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(16.dp).background(Color(0xFF075BFA), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp)) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LText("${if (libraryImages.isEmpty()) 0 else index + 1} / ${libraryImages.size}", color = Color.LightGray, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            LText("−", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { zoomScale = (zoomScale - .25f).coerceAtLeast(1f); if (zoomScale <= 1f) zoomOffset = Offset.Zero }.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.titleLarge)
                            LText("${(zoomScale * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            LText("+", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { zoomScale = (zoomScale + .25f).coerceAtMost(10f) }.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.titleLarge)
                            LText("↶", color = Color.White, modifier = Modifier.clickable { if (boxes.isNotEmpty()) { redoBoxes = redoBoxes + listOf(boxes); image?.let { annotationsByImage = annotationsByImage + (it.absolutePath to boxes.dropLast(1)); if (it.absolutePath in savedPaths) dirtyPaths = dirtyPaths + it.absolutePath }; activeBox = null } }.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.titleMedium)
                            LText("↷", color = Color.White, modifier = Modifier.clickable { val next = redoBoxes.lastOrNull(); if (next != null) { image?.let { annotationsByImage = annotationsByImage + (it.absolutePath to next); if (it.absolutePath in savedPaths) dirtyPaths = dirtyPaths + it.absolutePath }; redoBoxes = redoBoxes.dropLast(1); activeBox = null } }.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.titleMedium)
                            LText("⌫", color = Color(0xFFFF9B9B), modifier = Modifier.clickable { if (boxes.isNotEmpty()) { redoBoxes = redoBoxes + listOf(boxes); image?.let { annotationsByImage = annotationsByImage + (it.absolutePath to emptyList()); if (it.absolutePath in savedPaths) dirtyPaths = dirtyPaths + it.absolutePath }; activeBox = null } }.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Decode large form images at a bounded resolution. Uniform downsampling preserves
 * normalized YOLO coordinates while avoiding full-resolution bitmap allocations on
 * memory-constrained Android devices.
 */
private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun writeDatasetZip(context: Context, uri: Uri, projectId: String, classNames: List<String>) {
    val root = File(context.filesDir, "dataset-projects/$projectId")
    val images = File(root, "train/images").listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
    require(classNames.isNotEmpty()) { "Add at least one annotation class before exporting." }
    require(images.isNotEmpty()) { "Add at least one image before exporting." }
    val labelsByImage = images.associateWith { image ->
        File(root, "train/labels/" + image.name.substringBeforeLast('.') + ".txt")
    }
    labelsByImage.forEach { (image, label) ->
        require(label.isFile) { "Missing label for ${image.name}" }
        val lines = label.readLines().filter { it.isNotBlank() }
        // Empty label files are valid YOLO negative samples: this image contains neither target.
        lines.forEachIndexed { lineIndex, line ->
            val values = line.trim().split(Regex("\\s+"))
            require(values.size == 5) {
                "Invalid YOLO label at ${label.name}:${lineIndex + 1}; expected class x_center y_center width height"
            }
            val classId = values[0].toIntOrNull()
                ?: error("Invalid class ID at ${label.name}:${lineIndex + 1}")
            require(classId in classNames.indices) { "Unknown class ID $classId in ${label.name}; configured classes: ${classNames.size}" }
            val box = values.drop(1).mapNotNull { it.toFloatOrNull() }
            require(box.size == 4 && box.all { it.isFinite() }) {
                "Invalid numeric coordinates at ${label.name}:${lineIndex + 1}"
            }
            val (cx, cy, width, height) = box
            require(cx in 0f..1f && cy in 0f..1f && width > 0f && width <= 1f && height > 0f && height <= 1f) {
                "Coordinates out of range at ${label.name}:${lineIndex + 1}"
            }
            require(cx - width / 2f >= -0.0001f && cy - height / 2f >= -0.0001f &&
                cx + width / 2f <= 1.0001f && cy + height / 2f <= 1.0001f) {
                "Box extends outside image at ${label.name}:${lineIndex + 1}"
            }
        }
    }
    val groupFile = File(root, "train/groups.txt")
    val groupByBase = groupFile.takeIf { it.isFile }?.readLines().orEmpty()
        .mapNotNull { line ->
                    val parts = line.split("\t", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    val groupByImage = images.associateWith { image ->
        groupByBase[image.name.substringBeforeLast(".")] ?: "legacy-${image.name.substringBeforeLast(".")}"
    }
    val groups = groupByImage.values.distinct().sorted()
    require(groups.size >= 3) {
        "At least 3 distinct Group IDs are required for train/validation/test. Current groups: ${groups.size}. Use the same ID for related images that should stay together."
    }
    val splitByGroup = groups.mapIndexed { index, group ->
        group to when {
            groups.size < 10 && index == groups.lastIndex -> "test"
            groups.size < 10 && index == groups.lastIndex - 1 -> "valid"
            groups.size < 10 -> "train"
            index % 10 == 8 -> "valid"
            index % 10 == 9 -> "test"
            else -> "train"
        }
    }.toMap()
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        ZipOutputStream(stream).use { zip ->
            images.forEach { image ->
                val split = splitByGroup.getValue(groupByImage.getValue(image))
                val label = labelsByImage.getValue(image)
                // Re-encode only the exported copy. Keep the user's source image intact.
                // Normalized YOLO coordinates remain valid after proportional resizing.
                val decoded = decodeSampledBitmap(image, 1600)
                    ?: error("Could not decode dataset image: ${image.name}")
                val maxDimension = 1600
                val resizeScale = minOf(
                    1f,
                    maxDimension.toFloat() / maxOf(decoded.width, decoded.height),
                )
                val exportBitmap = if (resizeScale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * resizeScale).toInt().coerceAtLeast(1),
                        (decoded.height * resizeScale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else decoded
                val compressed = ByteArrayOutputStream()
                try {
                    check(exportBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, compressed)) {
                        "Could not compress dataset image: ${image.name}"
                    }
                    val jpgName = image.name.substringBeforeLast('.') + ".jpg"
                    zip.putNextEntry(ZipEntry(split + "/images/" + jpgName))
                    compressed.writeTo(zip)
                    zip.closeEntry()
                } finally {
                    if (exportBitmap !== decoded) exportBitmap.recycle()
                    decoded.recycle()
                }
                zip.putNextEntry(ZipEntry(split + "/labels/" + label.name))
                label.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val yamlNames = classNames.mapIndexed { index, name -> "  $index: '${name.replace("'", "''")}'" }.joinToString("\n")
            val yaml = """path: .
train: train/images
val: valid/images
test: test/images
names:
$yamlNames
"""
            zip.putNextEntry(ZipEntry("data.yaml")); zip.write(yaml.toByteArray()); zip.closeEntry()
            val note = """Annotation classes:
${classNames.mapIndexed { index, name -> "$index = $name" }.joinToString("\n")}
Dataset preflight: every YOLO label is checked for a valid configured class ID, finite normalized coordinates, positive box size, and image-boundary containment. Empty label files are valid negative samples.
Annotation quality checklist:
- Draw a tight box around each object that belongs to a configured class.
- Annotate every visible instance consistently. Do not draw boxes for absent objects.
- Include varied scenes, lighting, blur, rotation, scale, and background conditions when relevant to your task.
- Review every box before export. Incorrect or inconsistent boxes teach the model incorrect boundaries.
Split: all images with the same Group ID are kept together in one split to reduce data leakage. Groups are assigned deterministically; with fewer than 10 groups, the final two groups are validation and test. Images without a saved Group ID are treated as individual legacy groups. Group IDs do not detect near-duplicates automatically; use the same ID for related images.
"""
            zip.putNextEntry(ZipEntry("README.txt")); zip.write(note.toByteArray()); zip.closeEntry()
        }
    } ?: error("Could not open ZIP output")
}
