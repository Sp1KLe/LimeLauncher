package net.kdt.pojavlaunch.lime

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.fragments.MainMenuFragment

private fun findActivity(context: Context): FragmentActivity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Верхняя панель лаунчера (Material 3). Объявляется прямо в XML активности,
 * поэтому не требует изменений в Java-коде LauncherActivity.
 */
class LimeTopBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val hostActivity: FragmentActivity? = findActivity(context)

    init {
        // Старые версии AppCompat не выдают окну владельцев жизненного цикла,
        // без них Compose падает. Выдаём их сами.
        hostActivity?.let { a ->
            val decor = a.window.decorView
            if (decor.findViewTreeLifecycleOwner() == null) decor.setViewTreeLifecycleOwner(a)
            if (decor.findViewTreeViewModelStoreOwner() == null) decor.setViewTreeViewModelStoreOwner(a)
            if (decor.findViewTreeSavedStateRegistryOwner() == null) decor.setViewTreeSavedStateRegistryOwner(a)
        }
    }

    @Composable
    override fun Content() {
        LimeTheme { LimeTopBar(hostActivity) }
    }
}

@Composable
fun LimeTopBar(activity: FragmentActivity?) {
    var onHome by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    onHome = f is MainMenuFragment
                }
            }
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
            onDispose { activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(callbacks) }
        }
    }

    // Невидимая кнопка из старой разметки хранит логику переходов (настройки <-> главный экран).
    fun clickLegacySettingsButton() {
        activity?.findViewById<View>(R.id.setting_button)?.performClick()
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "LimeLauncher",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                if (onHome) showSettings = true else clickLegacySettingsButton()
            }) {
                Icon(
                    if (onHome) Icons.Rounded.Settings else Icons.Rounded.Home,
                    contentDescription = if (onHome) "Настройки" else "На главный экран",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onAdvanced = {
                showSettings = false
                clickLegacySettingsButton()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit, onAdvanced: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LimeThemeStore.mode(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("Тема оформления", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            val options = listOf(
                LimeThemeStore.SYSTEM to "Как в системе (по умолчанию)",
                LimeThemeStore.LIGHT to "Светлая",
                LimeThemeStore.DARK to "Тёмная",
            )
            options.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            mode = value
                            LimeThemeStore.set(context, value)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = mode == value,
                        onClick = {
                            mode = value
                            LimeThemeStore.set(context, value)
                        },
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                Text("Расширенные настройки (видео, управление, Java)")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "LimeLauncher построен на PojavLauncher (LGPL-3.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
