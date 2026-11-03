package net.kdt.pojavlaunch.lime

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Точка входа для Java: подключает домашний экран Material 3 к ComposeView. */
object LimeHomeUi {
    @JvmStatic
    fun setup(view: ComposeView, actions: LimeHomeActions) {
        val model = LimeHomeModel(view.resources)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            LimeTheme {
                LimeHome(model, actions)
            }
        }
        model.reload()
    }
}
