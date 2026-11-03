package net.kdt.pojavlaunch.lime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment
import net.kdt.pojavlaunch.instances.Instances

/** Экран «Моды»: поиск на Modrinth и установка в папку mods выбранной версии. */
class LimeModsFragment : Fragment() {
    private var model: LimeModsModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val activity = requireActivity()
        val decor = activity.window.decorView
        if (decor.findViewTreeLifecycleOwner() == null) decor.setViewTreeLifecycleOwner(activity)
        if (decor.findViewTreeViewModelStoreOwner() == null) decor.setViewTreeViewModelStoreOwner(activity)
        if (decor.findViewTreeSavedStateRegistryOwner() == null) decor.setViewTreeSavedStateRegistryOwner(activity)

        val m = model ?: run {
            val inst = try {
                Instances.loadSelectedInstance()
            } catch (e: Exception) {
                null
            }
            val versionId = inst?.versionId
            val loader = LimeModsModel.detectLoader(versionId)
            LimeModsModel(
                gameDir = inst?.gameDirectory,
                initialVersion = LimeModsModel.detectMcVersion(versionId),
                initialLoader = loader,
                instanceLabel = if (inst != null) "Версия: ${inst.name} (${versionId ?: "?"})" else "Версия не выбрана",
                hasLoader = loader != null,
            ).also { model = it }
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LimeTheme {
                    LimeModsScreen(m) {
                        Tools.swapFragment(
                            requireActivity(),
                            ProfileTypeSelectFragment::class.java,
                            ProfileTypeSelectFragment.TAG,
                            null,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "LimeModsFragment"
    }
}
