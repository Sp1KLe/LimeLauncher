package net.kdt.pojavlaunch.lime

import android.content.Context
import net.kdt.pojavlaunch.Tools
import java.io.File

/** Ставит раскладку в стиле Bedrock один раз; потом не перезаписывает правки игрока. */
object LimeControls {
    private const val MARK = ".lime_bedrock_v1"

    @JvmStatic
    fun install(ctx: Context) {
        try {
            val dir = File(Tools.CTRLMAP_PATH)
            dir.mkdirs()
            val mark = File(dir, MARK)
            val target = File(Tools.CTRLDEF_FILE)
            val first = !mark.exists()
            Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, first || !target.exists())
            if (first) mark.writeText("1")
        } catch (e: Exception) {
            android.util.Log.e("LimeControls", "install failed", e)
        }
    }
}
