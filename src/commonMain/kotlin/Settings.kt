@file:Suppress("RUNTIME_ANNOTATION_NOT_SUPPORTED")

import kotlinx.serialization.Serializable
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.extra.color.spaces.toOKHSLa
import org.openrndr.extra.parameters.BooleanParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.OptionParameter
import kotlin.math.max

// TODO: Settings
// 1. High level: What to compute? Bridges? Voronoi? Overlap resolution? etc.
// 2. Settings for computing the partition.
// 3. Settings for computing the partition drawing.
// 4. Settings for computing the bridges.
// 5. Draw settings: colors, stroke weights, etc.

@Serializable
data class ComputePartitionSettings(
    @DoubleParameter("Bend distance", 1.0, 500.0, order=1000)
    var bendDistance: Double = 20.0,

    @BooleanParameter("Inflection", order=2000)
    var bendInflection: Boolean = true,

    @DoubleParameter("Max bend angle", 0.0, 180.0, order=3000)
    var maxBendAngle: Double = 180.0,

    @DoubleParameter("Max turning angle", 0.0, 180.0, order=4000)
    var maxTurningAngle: Double = 70.0,

    @DoubleParameter("Cluster radius", 0.0, 100.0, order=5000)
    var coverRadius: Double = 50.0,

    @DoubleParameter("Partition clearance", 0.0, 10.0, order=6000)
    var partitionClearance: Double = 1.0,

    @DoubleParameter("Single-double threshold", 0.0, 200.0)
    var singleDouble: Double = coverRadius * 2
) {
    fun alignPartitionClearance(avoidOverlap: Double, expandRadius: Double) {
        partitionClearance = avoidOverlap * expandRadius
    }
    fun alignSingleDouble() {
        singleDouble = max(coverRadius * 2, bendDistance)
    }
}

enum class IntersectionResolution {
    None,
    Voronoi,
    Overlap,
}

@Serializable
data class ComputeDrawingSettings(
    @DoubleParameter("Expand radius", 0.1, 100.0, order = 3)
    var expandRadius: Double = 30.0,

    @OptionParameter("Overlap resolution", order = 4)
    var intersectionResolution: IntersectionResolution = IntersectionResolution.Overlap,

    @DoubleParameter("Point clearance", 0.0, 1.0, order = 5)
    var pointClearance: Double = 0.625,

    @DoubleParameter("Smoothing", 0.001, 0.3)
    var smoothing: Double = 0.3
) {
    val smoothingRadius get() = smoothing * expandRadius

    fun alignExpandRadius(pointSize: Double) {
        expandRadius = if (intersectionResolution != IntersectionResolution.None) pointSize * 3 else 0.0001
    }
}

@Serializable
data class ComputeBridgesSettings(
    @DoubleParameter("Clearance", 2.0, 20.0, order = 7000)
    var clearance: Double = 5.0,

    @BooleanParameter("Smooth bridges", order = 9015)
    var smoothBridges: Boolean = true,
)

//    @DoubleParameter("Avoid overlap", 0.0, 1.0, order = 8000)
//    var avoidOverlap: Double = 0.25,

//@BooleanParameter("Disjoint", order = 5)
//var disjoint: Boolean = true,

val blue = rgb(0.651, 0.807, 0.89) to rgb(0.121, 0.47, 0.705)
val red = rgb(0.984, 0.603, 0.6) to rgb(0.89, 0.102, 0.109)
val green = rgb(0.698, 0.874, 0.541) to rgb(0.2, 0.627, 0.172)
val orange = rgb(0.992, 0.749, 0.435) to rgb(1.0, 0.498, 0.0)
val purple = rgb(0.792, 0.698, 0.839) to rgb(0.415, 0.239, 0.603)
//val yellow = ColorRGBa(255 / 255.0, 255 / 255.0, 153 / 255.0) to rgb(251 / 255.0, 251 / 255.0, 0 / 255.0)
//val brown = ColorRGBa(200 / 255.0, 130 / 255.0, 34 / 255.0) to rgb(176 / 255.0, 88 / 255.0, 40 / 255.0)
//val yellow = rgb(251 / 255.0, 251 / 255.0, 0 / 255.0) to ColorRGBa(255 / 255.0, 255 / 255.0, 153 / 255.0)
//val yellow = rgb(251 / 255.0, 236 / 255.0, 47 / 255.0) to ColorRGBa(255 / 255.0, 255 / 255.0, 153 / 255.0)
val yellow = rgb(251 / 255.0, 240 / 255.0, 116 / 255.0) to ColorRGBa(255 / 255.0, 255 / 255.0, 153 / 255.0)
//val brown = ColorRGBa(200 / 255.0, 130 / 255.0, 34 / 255.0) to rgb(176 / 255.0, 88 / 255.0, 40 / 255.0)
//val brown = ColorRGBa(223 / 255.0, 153 / 255.0, 115 / 255.0) to ColorRGBa(200 / 255.0, 130 / 255.0, 34 / 255.0)
val brown = ColorRGBa(234 / 255.0, 189 / 255.0, 162 / 255.0) to ColorRGBa(200 / 255.0, 130 / 255.0, 34 / 255.0)

val colorPairs = listOf(blue, red, green, orange, purple, yellow, brown)
val lightColors = colorPairs.map { it.first }
val darkColors = colorPairs.map { it.second }

@Serializable
data class DrawSettings(
    @DoubleParameter("Point size", 0.1, 10.0, order = 0)
    var pSize: Double = 10.0,

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
//    var useGrid: Boolean = true,
//    var gridSize: Double = 40.0,
) {
    val pointStrokeWeight: Double get() = pSize / 3
    val contourStrokeWeight: Double get() = pSize / 3.5
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