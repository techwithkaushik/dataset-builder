@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.techwithkaushik.datasetbuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
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
    var fullScreen by remember { mutableStateOf(false) }
    var builderTab by remember { mutableStateOf("Annotate") }
    var builderMenu by remember { mutableStateOf(false) }
    var formGroupId by remember(index, libraryImages, groupIds) {
        mutableStateOf(libraryImages.getOrNull(index)?.let { groupIds[it.nameWithoutExtension] ?: it.nameWithoutExtension }.orEmpty())
    }
    var confirmRemove by remember { mutableStateOf(false) }
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
            status = if (loaded.first.isEmpty()) "Dataset खाली है। Add images दबाकर images जोड़ें।" else "Saved dataset loaded: ${loaded.first.size} images"
        } catch (e: Exception) {
            status = "Dataset load failed: " + (e.message ?: "unknown error")
        } finally { busy = false }
    }

    val addImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "dataset-projects/$projectId/train/images").apply { mkdirs() }
                    uris.forEachIndexed { i, uri ->
                        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
                        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                        var out = File(dir, "added_${System.currentTimeMillis()}_${i}.$ext")
                        while (out.exists()) out = File(dir, "added_${System.currentTimeMillis()}_${i}_${System.nanoTime()}.$ext")
                        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                            ?: error("Could not open selected image")
                        check(out.length() > 0L) { "Selected image is empty" }
                    }
                }
                val dir = File(context.filesDir, "dataset-projects/$projectId/train/images")
                val previousPaths = libraryImages.map { it.absolutePath }.toSet()
                val refreshed = dir.listFiles()?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in listOf("jpg", "jpeg", "png", "webp") }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
                libraryImages = refreshed
                index = refreshed.indexOfFirst { it.absolutePath !in previousPaths }.takeIf { it >= 0 } ?: 0
                status = "${uris.size} image(s) added. Label objects present, then Save."
            } catch (e: Exception) { status = "Add images failed: " + (e.message ?: "unknown error") }
            finally { busy = false }
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
                onMessage("dataset.zip export हो गया")
            } catch (e: Exception) { onMessage("Export failed: " + (e.message ?: "unknown error")) }
            finally { busy = false }
        }
    }

    fun saveAndNext() {
        val current = image ?: return
        val currentBoxes = boxes
        val normalizedGroup = formGroupId.trim().replace("\\t", " ").replace("\\n", " ").replace("\\r", " ")
        if (normalizedGroup.isBlank()) { status = "Enter a Group ID. Use the same ID for related images that must stay in one split."; return }
        // Any configured class can be annotated. Empty YOLO label files represent negative samples.
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
                        .filterNot { it.substringBefore("\t") == base }
                    groupsFile.writeText((existing + "$base\t${normalizedGroup.lowercase(Locale.ROOT)}").joinToString("\n", postfix = "\n"))
                }
                annotationsByImage = annotationsByImage + (current.absolutePath to currentBoxes)
                savedPaths = savedPaths + current.absolutePath
                groupIds = groupIds + (base to normalizedGroup.lowercase(Locale.ROOT))
                dirtyPaths = dirtyPaths - current.absolutePath
                if (index < libraryImages.lastIndex) {
                    index++
                    activeBox = null
                    status = "Next image: annotate only the objects that are present."
                } else status = "All images reviewed. Export the dataset when ready."
            } catch (e: Exception) { status = "Save failed: " + (e.message ?: "unknown error") }
            finally { busy = false }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this image?") },
            text = { Text("Image और उसकी saved annotations dataset से permanently हट जाएँगी। Original source file नहीं हटेगी।") },
            confirmButton = {
                TextButton(onClick = {
                    val target = image
                    confirmRemove = false
                    if (target != null) scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) {
                                target.delete()
                                File(context.filesDir, "dataset-projects/$projectId/train/labels/${target.nameWithoutExtension}.txt").delete()
                                val groupsFile = File(context.filesDir, "dataset-projects/$projectId/train/groups.txt")
                                if (groupsFile.isFile) groupsFile.writeText(groupsFile.readLines().filterNot { it.substringBefore("\t") == target.nameWithoutExtension }.joinToString("\n", postfix = "\n"))
                            }
                            annotationsByImage = annotationsByImage - target.absolutePath
                            savedPaths = savedPaths - target.absolutePath
                            groupIds = groupIds - target.nameWithoutExtension
                            dirtyPaths = dirtyPaths - target.absolutePath
                            libraryImages = libraryImages.filterNot { it.absolutePath == target.absolutePath }
                            index = index.coerceIn(0, (libraryImages.size - 1).coerceAtLeast(0))
                            activeBox = null
                            status = "Image और उसकी annotations हटाई गईं"
                        } catch (e: Exception) { status = "Remove failed: " + (e.message ?: "unknown error") }
                        finally { busy = false }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }

    BackHandler(enabled = fullScreen || confirmRemove) {
        when {
            confirmRemove -> confirmRemove = false
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
                            Text(projectName, fontWeight = FontWeight.ExtraBold, maxLines = 1, style = MaterialTheme.typography.titleMedium, color = Color(0xFF17243A))
                            Text("${libraryImages.size} images  ·  ${savedPaths.size} saved", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), maxLines = 1)
                        }
                    },
                    navigationIcon = { androidx.compose.material3.IconButton(onClick = onExit) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back to datasets") } },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { addImagesLauncher.launch(arrayOf("image/*")) }, enabled = !busy) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.AddPhotoAlternate, contentDescription = "Add images") }
                        androidx.compose.material3.IconButton(onClick = { builderMenu = true }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "Project actions") }
                        DropdownMenu(expanded = builderMenu, onDismissRequest = { builderMenu = false }) {
                            DropdownMenuItem(text = { Text("Export dataset ZIP") }, leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Archive, null) }, enabled = saved == libraryImages.size && libraryImages.isNotEmpty() && dirtyPaths.isEmpty() && !busy, onClick = { builderMenu = false; zipLauncher.launch("dataset.zip") })
                            DropdownMenuItem(text = { Text("Finish and return") }, leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, null) }, enabled = !busy, onClick = { builderMenu = false; onExit() })
                        }
                    }
                )
                Row(Modifier.fillMaxWidth().background(Color(0xFFF4F7FC)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Annotate", "Images", "Classes", "Info").forEach { tab ->
                        FilterChip(
                            selected = builderTab == tab,
                            onClick = { builderTab = tab },
                            label = { Text(tab, maxLines = 1, fontWeight = if (builderTab == tab) FontWeight.Bold else FontWeight.Medium) },
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
            if (!fullScreen) Surface(color = Color.White, shadowElevation = 8.dp) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { cls ->
                            val count = boxes.count { it.type == cls }
                            FilterChip(
                                selected = selected == cls,
                                onClick = { selected = cls },
                                label = { Text("${cls.title}  $count", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = cls.color,
                                    selectedLabelColor = Color.White,
                                    containerColor = cls.color.copy(alpha = .09f),
                                    labelColor = cls.color,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected == cls,
                                    borderColor = cls.color.copy(alpha = .28f),
                                    selectedBorderColor = cls.color,
                                ),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (boxes.isNotEmpty()) {
                                    image?.let {
                                        annotationsByImage = annotationsByImage + (it.absolutePath to boxes.dropLast(1))
                                        if (it.absolutePath in savedPaths) dirtyPaths = dirtyPaths + it.absolutePath
                                    }
                                    activeBox = null
                                    status = "आखिरी box हटाया गया"
                                }
                            },
                            enabled = boxes.isNotEmpty() && !busy,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("Undo") }
                        OutlinedButton(
                            onClick = {
                                image?.let {
                                    annotationsByImage = annotationsByImage + (it.absolutePath to emptyList())
                                    if (it.absolutePath in savedPaths) dirtyPaths = dirtyPaths + it.absolutePath
                                }
                                activeBox = null
                                status = "सभी boxes हटे। दोबारा mark करके Save करें; बदले हुए dataset को export से पहले save करना जरूरी है।"
                            },
                            enabled = boxes.isNotEmpty() && !busy,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("Clear") }
                    }
                    OutlinedTextField(value = formGroupId, onValueChange = { formGroupId = it }, label = { Text("Group ID") }, supportingText = { Text("Use the same ID for related images that should stay in the same split.") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    val oversized = boxes.count { (it.r - it.l) * (it.b - it.t) > 0.75f }
                    val tiny = boxes.count { (it.r - it.l) * (it.b - it.t) < 0.0005f || it.r - it.l < 0.005f || it.b - it.t < 0.005f }
                    if (oversized > 0 || tiny > 0) Text("Review annotations: $oversized unusually large and $tiny very small box(es).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text("Objects annotated: ${boxes.size}  •  Classes: ${classes.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Annotation progress", style = MaterialTheme.typography.labelMedium, color = Color(0xFF52647D), modifier = Modifier.weight(1f))
                        Text("$saved / ${libraryImages.size} saved", style = MaterialTheme.typography.labelMedium, color = Color(0xFF145DDE), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { if (libraryImages.isEmpty()) 0f else saved.toFloat() / libraryImages.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = Color(0xFF145DDE),
                        trackColor = Color(0xFFE7EDF7),
                    )
                    LazyRow(state = thumbnailListState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(libraryImages, key = { it.absolutePath }) { item ->
                            val itemIndex = libraryImages.indexOf(item)
                            val thumbState = produceState<Bitmap?>(initialValue = null, key1 = item.absolutePath) {
                                value = withContext(Dispatchers.IO) { decodeSampledBitmap(item, 150) }
                            }
                            val thumb = thumbState.value
                            DisposableEffect(thumb) { onDispose { thumb?.recycle() } }
                            Column(
                                Modifier.width(84.dp)
                                    .background(if (itemIndex == index) Color(0xFFEAF1FF) else Color(0xFFF8FAFD), RoundedCornerShape(12.dp))
                                    .border(if (itemIndex == index) 2.dp else 1.dp, if (itemIndex == index) Color(0xFF145DDE) else Color(0xFFE2E9F4), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !busy) { index = itemIndex; activeBox = null; status = item.name }
                                    .padding(5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (thumb != null) Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                ) else Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { Text("?", style = MaterialTheme.typography.titleMedium) }
                                Text(
                                    "${if (item.absolutePath in savedPaths) "✓" else "○"} ${itemIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (index > 0) { index--; activeBox = null; status = "पिछले form के saved boxes जाँचें" } }, enabled = index > 0 && !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Previous") }
                        Button(onClick = { saveAndNext() }, enabled = image != null && !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text(if (index < libraryImages.lastIndex) "Save & Next" else "Save Form") }
                        OutlinedButton(onClick = { confirmRemove = true }, enabled = image != null && !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Remove image") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { zipLauncher.launch("dataset.zip") }, enabled = saved == libraryImages.size && libraryImages.isNotEmpty() && dirtyPaths.isEmpty() && !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text(if (dirtyPaths.isNotEmpty()) "Save edits first" else if (saved != libraryImages.size) "Save all images first" else "Export dataset.zip") }
                        OutlinedButton(onClick = onExit, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Finish") }
                    }
                }
            }
        }
    ) { padding ->
        if (!fullScreen && builderTab != "Annotate") {
            when (builderTab) {
                "Images" -> {
                    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                        Text("Images · ${libraryImages.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                                        else Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("Loading…") }
                                        Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        Text(if (item.absolutePath in savedPaths) "Saved" else "Pending", style = MaterialTheme.typography.labelSmall, color = if (item.absolutePath in savedPaths) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                "Classes" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Annotation classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    classes.forEach { cls ->
                        val count = libraryImages.sumOf { file -> annotationsByImage[file.absolutePath].orEmpty().count { it.type == cls } }
                        ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(cls.color, RoundedCornerShape(50)))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(cls.title, fontWeight = FontWeight.SemiBold)
                                    Text("Class ID: ${cls.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Class IDs follow the configured class order and are included in data.yaml.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dataset info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(projectName, style = MaterialTheme.typography.titleMedium)
                    Text("Images: ${libraryImages.size}")
                    Text("Saved labels: ${savedPaths.size}")
                    Text("Pending images: ${(libraryImages.size - savedPaths.size).coerceAtLeast(0)}")
                    Text("Unsaved edits: ${dirtyPaths.size}")
                    Text("Workspace: app-private storage, isolated by project.")
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { builderTab = "Annotate" }) { Text("Return to annotation") }
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
                    Text("No dataset images yet", color = Color.White)
                    Button(onClick = { addImagesLauncher.launch(arrayOf("image/*")) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Add images") }
                }
            } else if (bitmap == null) Text("Image could not be opened", color = Color.White)
            else BoxWithConstraints(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(if (fullScreen) 0.dp else 16.dp))
                    .background(Color(0xFF101216)),
                contentAlignment = Alignment.Center,
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val scale = minOf(w / bitmap.width, h / bitmap.height)
                val dw = bitmap.width * scale
                val dh = bitmap.height * scale
                val ox = (w - dw) / 2f
                val oy = (h - dh) / 2f
                Image(bitmap.asImageBitmap(), "Imported form", Modifier.size(
                    with(androidx.compose.ui.platform.LocalDensity.current) { dw.toDp() },
                    with(androidx.compose.ui.platform.LocalDensity.current) { dh.toDp() }), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize().pointerInput(index) { detectTapGestures(onTap = { if (!fullScreen) fullScreen = true }) }.pointerInput(index, selected, dw, dh, ox, oy) {
                    var start: Offset? = null
                    detectDragGestures(
                        onDragStart = { start = it; activeBox = null },
                        onDragEnd = {
                            activeBox?.let { b ->
                                if (b.r - b.l > .005f && b.b - b.t > .005f) {
                                    image?.let { current ->
                                        annotationsByImage = annotationsByImage + (current.absolutePath to (boxes + b))
                                        if (current.absolutePath in savedPaths) dirtyPaths = dirtyPaths + current.absolutePath
                                    }
                                }
                            }
                            activeBox = null
                            start = null
                        },
                        onDragCancel = { activeBox = null; start = null }
                    ) { change, _ ->
                        val a = start ?: change.position.also { start = it }
                        val x1 = ((minOf(a.x, change.position.x) - ox) / dw).coerceIn(0f, 1f)
                        val y1 = ((minOf(a.y, change.position.y) - oy) / dh).coerceIn(0f, 1f)
                        val x2 = ((maxOf(a.x, change.position.x) - ox) / dw).coerceIn(0f, 1f)
                        val y2 = ((maxOf(a.y, change.position.y) - oy) / dh).coerceIn(0f, 1f)
                        if (x2 > x1 && y2 > y1) activeBox = LabelBox(selected, x1, y1, x2, y2)
                    }
                }) {
                    (boxes + listOfNotNull(activeBox)).forEach { b ->
                        drawRect(b.type.color, Offset(ox + b.l * dw, oy + b.t * dh),
                            Size((b.r - b.l) * dw, (b.b - b.t) * dh),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            if (fullScreen) {
                Surface(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp), color = Color(0xEE101216), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(image?.name ?: "Image", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text("${if (libraryImages.isEmpty()) 0 else index + 1}/${libraryImages.size}", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                            Button(onClick = { fullScreen = false }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp), shape = RoundedCornerShape(10.dp)) { Text("Done") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            classes.forEach { cls ->
                                val count = boxes.count { it.type == cls }
                                FilterChip(selected = selected == cls, onClick = { selected = cls }, label = { Text("${cls.title} (ID: ${cls.id}) · $count", maxLines = 1, softWrap = false) }, modifier = Modifier.weight(1f))
                            }
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
