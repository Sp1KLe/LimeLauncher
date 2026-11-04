package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lime.LimeCrashWatch;
import net.kdt.pojavlaunch.lime.LimeHomeActions;
import net.kdt.pojavlaunch.lime.LimeHomeUi;
import net.kdt.pojavlaunch.lime.LimeModsFragment;
import net.kdt.pojavlaunch.lime.LimeOptimizer;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.jre.GameRunner;

import java.io.File;

/**
 * Главный экран LimeLauncher. Интерфейс (Material 3) написан на Compose,
 * а вся логика запуска и переходов осталась прежней и вызывается через {@link LimeHomeActions}.
 */
public class MainMenuFragment extends Fragment implements LimeHomeActions {
    public static final String TAG = "MainMenuFragment";

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_lime_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Старые версии AppCompat/Fragment не выдают окну владельцев жизненного цикла,
        // а Compose без них падает. Выдаём их вручную (если ещё не выданы).
        FragmentActivity activity = requireActivity();
        View decor = activity.getWindow().getDecorView();
        if (ViewTreeLifecycleOwner.get(decor) == null)
            ViewTreeLifecycleOwner.set(decor, activity);
        if (ViewTreeViewModelStoreOwner.get(decor) == null)
            ViewTreeViewModelStoreOwner.set(decor, activity);
        if (ViewTreeSavedStateRegistryOwner.get(decor) == null)
            ViewTreeSavedStateRegistryOwner.set(decor, activity);

        ComposeView composeView = view.findViewById(R.id.lime_compose);
        LimeHomeUi.setup(composeView, this);
    }

    @Override
    public void play() {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gamedir = instance.getGameDirectory();
        LimeOptimizer.apply(gamedir, false);

        if (GameRunner.hasVkMod(gamedir)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vk_mod_title)
                    .setMessage(R.string.vk_mod_message)
                    .setPositiveButton(R.string.continue_button, (d, w) ->
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true))
                    .show();
        } else if (GameRunner.hasReplay(gamedir) && GameRunner.hasFfmpeg(requireContext())) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.no_ffmpeg_title)
                    .setMessage(R.string.no_ffmpeg_message)
                    .setPositiveButton(R.string.install_button, (d, w) ->
                            Tools.openURL(requireActivity(), "https://github.com/MojoLauncher/FFmpegPlugin/releases"))
                    .setNegativeButton(R.string.continue_button, (d, w) ->
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true))
                    .show();
        } else {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        }
    }

    @Override
    public void editInstance() {
        Tools.swapFragment(requireActivity(), InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
    }

    @Override
    public void createInstance() {
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null);
    }

    @Override
    public void openMods() {
        Tools.swapFragment(requireActivity(), LimeModsFragment.class, LimeModsFragment.TAG, null);
    }

    @Override
    public void optimize() {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        boolean ok = LimeOptimizer.apply(instance.getGameDirectory(), true);
        Toast.makeText(requireContext(), ok
                ? "Готово: дальность прорисовки 6, быстрая графика, лимит 60 FPS"
                : "Не удалось изменить options.txt", Toast.LENGTH_LONG).show();
    }

    @Override
    public void openControls() {
        startActivity(new Intent(requireContext(), CustomControlsActivity.class));
    }

    @Override
    public void openGameFolder() {
        openGameDirectory(requireContext());
    }

    @Override
    public void shareLogs() {
        shareLog(requireContext());
    }

    @Override
    public void installJar() {
        runInstallerWithConfirmation();
    }

    @Override
    public void openGamepadMapper() {
        Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
    }

    @Override
    public void openNews() {
        Tools.openURL(requireActivity(), Tools.URL_HOME);
    }

    @Override
    public void openCommunity() {
        Tools.openURL(requireActivity(), getString(R.string.social_media_invite));
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        // Если игра только что закрылась неожиданно — показываем причину из лога.
        LimeCrashWatch.checkAfterGame(requireActivity(), 0);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
