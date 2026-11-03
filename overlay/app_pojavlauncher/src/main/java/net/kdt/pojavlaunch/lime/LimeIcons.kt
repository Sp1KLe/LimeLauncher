package net.kdt.pojavlaunch.lime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Несколько значков Material, которых нет в базовом наборе. */
object LimeIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

    val Folder: ImageVector by lazy {
        icon("Folder", "M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    val Gamepad: ImageVector by lazy {
        icon("Gamepad", "M15,7.5V2H9v5.5l3,3L15,7.5zM7.5,9H2v6h5.5l3,-3L7.5,9zM9,16.5V22h6v-5.5l-3,-3L9,16.5zM16.5,9l-3,3l3,3H22V9H16.5z")
    }
    val Code: ImageVector by lazy {
        icon("Code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
    }
    val Download: ImageVector by lazy {
        icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    }
}
