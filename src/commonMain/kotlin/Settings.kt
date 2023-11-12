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

    @DoubleParameter("Max bend angle", 0.0, 180.0, order=3000)
    var maxBendAngle: Double = 180.0,

    @DoubleParameter("Max turning angle", 0.0, 180.0, order=4000)
    var maxTurningAngle: Double = 70.0,

    @DoubleParameter("Point size", 0.1, 10.0, order = 0)
    var pSize: Double = 10.0,
) {
    val expandRadius get() = pSize * 3
}

@Serializable
data class TopoGrowSettings(
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

    @DoubleParameter("Smoothing", 0.001, 0.3)
    var smoothing: Double = 0.3
)

@Serializable
data class ComputeBridgesSettings(
    @DoubleParameter("Clearance", 2.0, 20.0, order = 7000)
    var clearance: Double = 5.0,

    @BooleanParameter("Smooth bridges", order = 9015)
    var smoothBridges: Boolean = true,
)

val blue = rgb(0.651, 0.807, 0.89) to rgb(0.121, 0.47, 0.705)
val red = rgb(0.984, 0.603, 0.6) to rgb(0.89, 0.102, 0.109)
val green = rgb(0.698, 0.874, 0.541) to rgb(0.2, 0.627, 0.172)
val orange = rgb(0.992, 0.749, 0.435) to rgb(1.0, 0.498, 0.0)
val purple = rgb(0.792, 0.698, 0.839) to rgb(0.415, 0.239, 0.603)
val yellow = rgb(251 / 255.0, 240 / 255.0, 116 / 255.0) to rgb(255 / 255.0, 255 / 255.0, 153 / 255.0)
val brown = rgb(234 / 255.0, 189 / 255.0, 162 / 255.0) to rgb(200 / 255.0, 130 / 255.0, 34 / 255.0)

val colorPairs = listOf(blue, red, green, orange, purple, yellow, brown)
val lightColors = colorPairs.map { it.first }
val darkColors = colorPairs.map { it.second }

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

    var colorSettings: ColorSettings = ColorSettings(lightColors.map { it.toColorRGB() }, darkColors.map { it.toColorRGB() })
) {
    fun pointStrokeWeight(gs: GeneralSettings) = gs.pSize / 3
    fun contourStrokeWeight(gs: GeneralSettings) = gs.pSize / 3.5
}

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