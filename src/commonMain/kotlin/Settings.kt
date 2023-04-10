import kotlinx.serialization.Serializable
import org.openrndr.color.rgb

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

@Serializable
data class DrawSettings(
    val pSize: Double = 10.0,
    val pointStrokeWeight: Double = pSize / 3,
    val contourStrokeWeight: Double = pSize / 3.5,
    val showVisibilityContours: Boolean = true,
    val showBridges: Boolean = true,
    val showClusterCircles: Boolean = false,
    val showBendDistance: Boolean = false,
    val showVisibilityGraph: Boolean = false,
    val showVoronoi: Boolean = false,
    val subset: Double = 1.0,
    val colorSettings: ColorSettings
//    var useGrid: Boolean = true,
//    var gridSize: Double = 40.0,
)

@Serializable
data class ColorSettings(
    val colors: List<Pair<ColorRGB, ColorRGB>>
) {
    val lightColors: List<ColorRGB> by lazy {
        colors.map { it.first }
    }

    val darkColors: List<ColorRGB> by lazy {
        colors.map { it.second }
    }
}

@Serializable
data class ColorRGB(val r: Double, val g: Double, val b: Double) {
    fun toColorRGBa() = rgb(r, g, b)
    fun toSvgString() = "rgb(${(r*255).toInt()}, ${(g*255).toInt()}, ${(b*255).toInt()})"
}