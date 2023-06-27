import kotlinx.serialization.Serializable
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.extra.color.spaces.toOKHSLa

@Serializable
data class ComputeSettings(
    val expandRadius: Double = 30.0, // pSize * 3
    val disjoint: Boolean = true,
    val bendDistance: Double = 20.0,
    val bendInflection: Boolean = true,
    val maxBendAngle: Double = 180.0,
    val maxTurningAngle: Double = 180.0,
    val clusterRadius: Double = 50.0,
    val clearance: Double = 5.0,
)

val blue = rgb(0.651, 0.807, 0.89) to rgb(0.121, 0.47, 0.705)
val red = rgb(0.984, 0.603, 0.6) to rgb(0.89, 0.102, 0.109)
val green = rgb(0.698, 0.874, 0.541) to rgb(0.2, 0.627, 0.172)
val orange = rgb(0.992, 0.749, 0.435) to rgb(1.0, 0.498, 0.0)
val purple = rgb(0.792, 0.698, 0.839) to rgb(0.415, 0.239, 0.603)

val colorPairs = listOf(blue, red, green, orange, purple)
val lightColors = colorPairs.map { it.first }
val darkColors = colorPairs.map { it.second }

@Serializable
data class DrawSettings(
    val pointSize: Double = 10.0,
    val pointStrokeWeight: Double = pointSize / 3,
    val contourStrokeWeight: Double = pointSize / 3.5,
    val showVisibilityContours: Boolean = true,
    val showBridges: Boolean = true,
    val showClusterCircles: Boolean = false,
    val showBendDistance: Boolean = false,
    val showVisibilityGraph: Boolean = false,
    val showVoronoi: Boolean = false,
    val subset: Double = 1.0,
    val colorSettings: ColorSettings = ColorSettings(lightColors.map { it.toColorRGB() }, darkColors.map { it.toColorRGB() })
//    var useGrid: Boolean = true,
//    var gridSize: Double = 40.0,
)

@Serializable
data class ColorSettings(val lightColors: List<ColorRGB>, val darkColors: List<ColorRGB>) {
    constructor(colors: List<ColorRGB>): this(colors, colors.map {
        it.toColorRGBa().toOKHSLa().saturate(1.25).toRGBa().shade(0.9).toColorRGB()
    })
}

@Serializable
data class ColorRGB(val r: Double, val g: Double, val b: Double) {
    fun toColorRGBa() = rgb(r, g, b)
    fun toSvgString() = "rgb(${(r*255).toInt()}, ${(g*255).toInt()}, ${(b*255).toInt()})"
    fun toHex(): String = "#" +
            (1 shl 24 or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt())
                .toString(16).drop(1)

    companion object {
        fun fromHex(hex: String): ColorRGB {
            val rgba = rgb(hex)
            return ColorRGB(rgba.r, rgba.g, rgba.b)
        }
    }
}

fun ColorRGBa.toColorRGB() = ColorRGB(r, g, b)