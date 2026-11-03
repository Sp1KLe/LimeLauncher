package net.kdt.pojavlaunch.lime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Действия, которые выполняет Java-часть лаунчера (запуск игры, экраны и т. д.). */
interface LimeHomeActions {
    fun play()
    fun editInstance()
    fun createInstance()
    fun openControls()
    fun openGameFolder()
    fun shareLogs()
    fun installJar()
    fun openGamepadMapper()
    fun openNews()
    fun openCommunity()
}

@Composable
fun LimeHome(model: LimeHomeModel, actions: LimeHomeActions) {
    var showAccounts by remember { mutableStateOf(false) }
    var showInstances by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AccountItem?>(null) }

    // Вход мог завершиться в фоне — аккуратно подтягиваем изменения аккаунтов.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            model.refreshAccounts()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccountCard(model, onSwitch = { showAccounts = true })
                        InstanceCard(
                            model,
                            onSwitch = { showInstances = true },
                            onEdit = actions::editInstance,
                        )
                        QuickActions(actions)
                    }
                    PlayPanel(
                        model = model,
                        onPlay = actions::play,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccountCard(model, onSwitch = { showAccounts = true })
                        InstanceCard(
                            model,
                            onSwitch = { showInstances = true },
                            onEdit = actions::editInstance,
                        )
                        QuickActions(actions)
                    }
                    Spacer(Modifier.height(12.dp))
                    PlayButton(onClick = actions::play, modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }

    if (showAccounts) {
        AccountSheet(
            model = model,
            onDismiss = { showAccounts = false },
            onDelete = { deleteTarget = it },
        )
    }
    if (showInstances) {
        InstanceSheet(
            model = model,
            onDismiss = { showInstances = false },
            onCreate = {
                showInstances = false
                actions.createInstance()
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Аккаунт «${target.name}» будет удалён из лаунчера.") },
            confirmButton = {
                TextButton(onClick = {
                    model.deleteAccount(target)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun Avatar(bitmap: androidx.compose.ui.graphics.ImageBitmap?, fallbackIcon: Boolean, size: Int = 56) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = if (fallbackIcon) RoundedCornerShape(14.dp) else CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), FilterQuality.None),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun AccountCard(model: LimeHomeModel, onSwitch: () -> Unit) {
    SectionCard("АККАУНТ") {
        val acc = model.currentAccount
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(acc?.face, fallbackIcon = false)
            Column(Modifier.weight(1f)) {
                Text(
                    acc?.name ?: "Нет аккаунта",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (acc != null) acc.typeLabel else "Добавьте аккаунт, чтобы играть",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onSwitch) { Text(if (acc != null) "Сменить" else "Войти") }
        }
    }
}

@Composable
private fun InstanceCard(model: LimeHomeModel, onSwitch: () -> Unit, onEdit: () -> Unit) {
    SectionCard("ВЕРСИЯ") {
        val inst = model.currentInstance
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(inst?.icon, fallbackIcon = true)
            Column(Modifier.weight(1f)) {
                Text(
                    inst?.name ?: if (model.loaded) "Версия не выбрана" else "Загрузка…",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    inst?.version.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Настроить версию")
            }
            FilledTonalButton(onClick = onSwitch) { Text("Выбрать") }
        }
    }
}

@Composable
private fun QuickActions(actions: LimeHomeActions) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(onClick = actions::openControls, label = { Text("Управление") })
        AssistChip(onClick = actions::openGameFolder, label = { Text("Папка игры") })
        AssistChip(onClick = actions::installJar, label = { Text("Установить .jar") })
        AssistChip(onClick = actions::shareLogs, label = { Text("Логи") })
        AssistChip(onClick = actions::openGamepadMapper, label = { Text("Геймпад") })
        AssistChip(onClick = actions::openNews, label = { Text("Новости") })
        AssistChip(onClick = actions::openCommunity, label = { Text("Сообщество") })
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.size(8.dp))
        Text("ИГРАТЬ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayPanel(model: LimeHomeModel, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "LimeLauncher",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Готов к игре",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val inst = model.currentInstance
                if (inst != null) {
                    SuggestionChip(onClick = {}, label = { Text("${inst.name}  ${inst.version}") })
                }
                if (model.loaded && model.currentAccount == null) {
                    Text(
                        "Сначала добавьте аккаунт",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            PlayButton(onClick = onPlay, modifier = Modifier.navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(model: LimeHomeModel, onDismiss: () -> Unit, onDelete: (AccountItem) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Аккаунты", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(model.accounts) { index, item ->
                    val selected = index == model.accountIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                model.selectAccount(index)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(item.face, fallbackIcon = false, size = 44)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Выбран", tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Удалить")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onDismiss()
                    model.addAccount()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Добавить аккаунт")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstanceSheet(model: LimeHomeModel, onDismiss: () -> Unit, onCreate: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Версии", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(model.instances) { index, item ->
                    val selected = index == model.instanceIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                model.selectInstance(index)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(item.icon, fallbackIcon = true, size = 44)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Выбрана", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Создать новую версию")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
