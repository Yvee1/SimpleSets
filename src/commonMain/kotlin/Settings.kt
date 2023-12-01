@file:Suppress("RUNTIME_ANNOTATION_NOT_SUPPORTED")

import kotlinx.serialization.Serializable
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.extra.color.spaces.toOKHSLa
import org.openrndr.extra.parameters.BooleanParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.OptionParameter

@Serializable
data class GeneralSettings(
    @BooleanParameter("Inflection", order=2000)
    var bendInflection: Boolean = true,

    @DoubleParameter("Max bend angle", 0.0, 360.0, order=3000)
    var maxBendAngle: Double = 180.0,

    @DoubleParameter("Max turning angle", 0.0, 180.0, order=4000)
    var maxTurningAngle: Double = 70.0,

    @DoubleParameter("Point size", 0.1, 10.0, order = 0)
    var pSize: Double = 10.0,
) {
    val expandRadius get() = pSize * 3
}

@Serializable
data class GrowSettings(
    @BooleanParameter("Banks")
    var banks: Boolean = true,

    @BooleanParameter("Islands")
    var islands: Boolean = true,

    @BooleanParameter("Postpone cover radius increase")
    var postponeCoverRadiusIncrease: Boolean = true,

    @BooleanParameter("Postpone intersections")
    var postponeIntersections: Boolean = true,

    @DoubleParameter("Forbid too close", 0.0, 1.0)
    var forbidTooClose: Double = 0.1
)

enum class IntersectionResolution {
    None,
    Voronoi,
    Overlap,
}

@Serializable
data class ComputeDrawingSettings(
    @OptionParameter("Overlap resolution", order = 4)
    var intersectionResolution: IntersectionResolution = IntersectionResolution.Overlap,

    @DoubleParameter("Point clearance", 0.0, 1.0, order = 5)
    var pointClearance: Double = 0.625,
)

@Serializable
data class ComputeBridgesSettings(
    @DoubleParameter("Clearance", 2.0, 20.0, order = 7000)
    var clearance: Double = 5.0,

    @BooleanParameter("Smooth bridges", order = 9015)
    var smoothBridges: Boolean = true,
)

val blue = rgb(0.651, 0.807, 0.89)
//val blue = rgb(179/255.0, 205/255.0, 228/255.0) // for NYC
val red = rgb(0.984, 0.603, 0.6)
//val red = rgb(248/255.0, 179/255.0, 174/255.0) // for NYC
val green = rgb(0.698, 0.874, 0.541)
//val green = rgb(204/255.0, 230/255.0, 196/255.0) // for NYC
val orange = rgb(0.992, 0.749, 0.435)
val purple = rgb(0.792, 0.698, 0.839)
val yellow = rgb(251 / 255.0, 240 / 255.0, 116 / 255.0)
val brown = rgb(234 / 255.0, 189 / 255.0, 162 / 255.0)

val defaultColors = listOf(blue, red, green, orange, purple, yellow, brown)

val diseasome = listOf(ColorRGBa.WHITE,
    rgb("#99CC00"),
    rgb("#6699CC"),
    rgb("#EE4444"),
//    rgb("#EE4444"),
    rgb("#999999"),
    rgb("#B3ECF7"),
    rgb("#FFCC00"),
    rgb("#CC6600"),
    rgb("#C0BB56"),
    rgb("#CC0099"),
    rgb("#FF0099"),
    rgb("#FF9999"),
    rgb("#CC9900"),
).map { it.whiten(0.35) }

@Serializable
data class DrawSettings(
    @DoubleParameter("Whiten", 0.0, 1.0, order = 1000)
    var whiten: Double = 0.7,

    @BooleanParameter("Show points", order = 8980)
    var showPoints: Boolean = true,

    @BooleanParameter("Show islands", order = 8990)
    var showIslands: Boolean = true,

    @BooleanParameter("Show visibility contours", order = 9000)
    var showVisibilityContours: Boolean = true,

    @BooleanParameter("Show bridges", order = 9010)
    var showBridges: Boolean = true,

    @BooleanParameter("Show cluster circles", order = 10000)
    var showClusterCircles: Boolean = false,

    @BooleanParameter("Show island Voronoi", order = 10003)
    var showVoronoiCells: Boolean = false,

    @BooleanParameter("Show bend distance", order = 10005)
    var showBendDistance: Boolean = false,

    @BooleanParameter("Show visibility graph", order=10010)
    var showVisibilityGraph: Boolean = false,

    @BooleanParameter("Show voronoi", order=10020)
    var showVoronoi: Boolean = false,

    @DoubleParameter("Show subset based on computation", 0.0, 1.0, order=10000000)
    var subset: Double = 1.0,

    @BooleanParameter("Shadows", order = 1)
    var shadows: Boolean = false,

    var colors: List<ColorRGB> = defaultColors.map { it.toColorRGB() }
) {
    fun pointStrokeWeight(gs: GeneralSettings) = gs.pSize / 2.5
    fun contourStrokeWeight(gs: GeneralSettings) = gs.pSize / 3.5
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