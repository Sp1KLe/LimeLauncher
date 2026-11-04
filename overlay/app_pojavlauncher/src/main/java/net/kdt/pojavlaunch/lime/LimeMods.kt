package net.kdt.pojavlaunch.lime

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val LOADERS = listOf("fabric" to "Fabric", "forge" to "Forge", "neoforge" to "NeoForge", "quilt" to "Quilt")

@Composable
fun LimeModsScreen(model: LimeModsModel, onInstallLoader: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) { model.refreshInstalled() }
    // Любое изменение фильтров запускает новый поиск (с небольшой задержкой, чтобы не частить).
    LaunchedEffect(model.mcVersion, model.loader, model.sortIndex) {
        delay(350)
        model.search(true)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Левая панель: фильтры
            Card(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Моды", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        model.instanceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Каталог") })
                        FilterChip(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            label = { Text("Установлено (${model.installedFiles.size})") },
                        )
                    }
                    if (tab == 0) {
                        OutlinedTextField(
                            value = model.mcVersion,
                            onValueChange = { model.mcVersion = it.trim() },
                            label = { Text("Версия Minecraft") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Загрузчик", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LOADERS.forEach { (id, name) ->
                                FilterChip(
                                    selected = model.loader == id,
                                    onClick = { model.loader = id },
                                    label = { Text(name) },
                                )
                            }
                        }
                        Text("Сортировка", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = model.sortIndex == "downloads",
                                onClick = { model.sortIndex = "downloads" },
                                label = { Text("Популярные") },
                            )
                            FilterChip(
                                selected = model.sortIndex == "relevance",
                                onClick = { model.sortIndex = "relevance" },
                                label = { Text("По запросу") },
                            )
                        }
                    }
                    if (!model.hasLoader) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "У выбранной версии нет загрузчика модов. Установите Fabric или Forge, " +
                                        "иначе моды в игре не заработают.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Button(onClick = onInstallLoader, modifier = Modifier.fillMaxWidth()) {
                                    Text("Установить загрузчик")
                                }
                            }
                        }
                    } else {
                        OutlinedButton(onClick = onInstallLoader, modifier = Modifier.fillMaxWidth()) {
                            Text("Другая версия / загрузчик")
                        }
                    }
                }
            }

            // Правая часть: поиск и список
            Column(
                Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.notice?.let { text ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            IconButton(onClick = { model.notice = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Скрыть")
                            }
                        }
                    }
                }

                if (tab == 0) {
                    Button(
                        onClick = { model.installPerformancePack() },
                        enabled = !model.progress.containsKey("pack"),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(model.progress["pack"]?.let { "Пакет оптимизации: $it" }
                            ?: "Пакет оптимизации FPS (Lithium, FerriteCore, Starlight, EntityCulling, ModernFix)")
                    }
                    OutlinedTextField(
                        value = model.query,
                        onValueChange = { model.query = it },
                        placeholder = { Text("Поиск модов") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { model.search(true) }),
                        trailingIcon = {
                            IconButton(onClick = { model.search(true) }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Искать")
                            }
                        },
                    )
                    CatalogList(model)
                } else {
                    InstalledList(model, onDelete = { deleteTarget = it })
                }
            }
        }
    }

    deleteTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить мод?") },
            text = { Text("Файл «${f.name}» будет удалён из папки модов.") },
            confirmButton = {
                TextButton(onClick = {
                    model.remove(f)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CatalogList(model: LimeModsModel) {
    Box(Modifier.fillMaxSize()) {
        val err = model.error
        when {
            err != null -> Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(err, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { model.search(true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Повторить")
                }
            }
            model.hits.isEmpty() && model.loading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            model.hits.isEmpty() ->
                Text(
                    "Ничего не найдено для Minecraft ${model.mcVersion} (${model.loader}). " +
                        "Проверьте версию и загрузчик слева.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(model.hits, key = { it.id }) { hit -> ModCard(model, hit) }
                if (model.hits.size < model.total) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (model.loading) CircularProgressIndicator(Modifier.size(28.dp))
                            else OutlinedButton(onClick = { model.search(false) }) { Text("Показать ещё") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModCard(model: LimeModsModel, hit: LimeModrinth.Hit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteIcon(hit.iconUrl, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${hit.author} · ${LimeModsModel.formatCount(hit.downloads)} загрузок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    hit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val status = model.progress[hit.id]
            Box(Modifier.width(132.dp), contentAlignment = Alignment.Center) {
                when {
                    status != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    hit.id in model.installedIds -> OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Есть")
                    }
                    else -> Button(onClick = { model.install(hit) }) {
                        Icon(LimeIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Поставить")
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledList(model: LimeModsModel, onDelete: (File) -> Unit) {
    if (model.installedFiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Пока нет установленных модов. Выберите мод во вкладке «Каталог».",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(model.installedFiles, key = { it.absolutePath }) { f ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            LimeModsModel.formatSize(f.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(f) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteIcon(url: String?, size: Dp) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(LimeImageCache.get(url)) }
    LaunchedEffect(url) {
        if (url != null && bmp == null) {
            bmp = withContext(Dispatchers.IO) { LimeImageCache.load(url) }
        }
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                painter = BitmapPainter(b, IntOffset.Zero, IntSize(b.width, b.height), FilterQuality.Medium),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}
