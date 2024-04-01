import geometric.Orientation
import geometric.convexHull
import highlights.Highlight
import highlights.ShapeHighlight
import highlights.toHighlight
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.bounds
import org.openrndr.shape.union
import patterns.Point
import patterns.coverRadius
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

fun areaCovered(highlights: List<Highlight>): Double {
    var total = Shape.EMPTY
    var iters = 0

    for (h in highlights) {
        iters++
        total = if (h is ShapeHighlight) {
            h.shape.union(total)
        } else {
            h.contour.clockwise.shape.union(total)
        }
    }

    return total.area
}

fun areaCoveredCustom(highlights: List<Highlight>): Double {
    var total = Shape.EMPTY
    var iters = 0

    for (h in highlights) {
        if (iters == 61) {
            iters++
            continue
        }
        total = if (h is ShapeHighlight) {
            h.shape.union(total)
        } else {
            h.contour.clockwise.shape.union(total)
        }
        iters++
    }
    total = total.union(highlights[61].contour.clockwise.shape)
    return total.area
}

fun densityDistortion(highlights: List<Highlight>, points: List<Point>): Pair<Double, Double> {
    val totalCovered = areaCovered(highlights)

    var total = 0.0
    var maxim = 0.0

    val grouped = highlights.groupBy { it.type }
    for ((t, hs) in grouped) {
        val coveredArea = areaCovered(hs)
        val tNumPoints = hs.sumOf { it.allPoints.size }
        val delta = abs(coveredArea / totalCovered - tNumPoints.toDouble() / points.size)
        total += delta
        maxim = max(delta, maxim)
    }

    return total / grouped.size * 100 to maxim * 100
}

fun densityDistortionCustom(highlights: List<Highlight>, points: List<Point>): Pair<Double, Double> {
    val totalCovered = areaCoveredCustom(highlights)

    var total = 0.0
    var maxim = 0.0

    val grouped = highlights.groupBy { it.type }
    for ((t, hs) in grouped) {
        val coveredArea = areaCovered(hs)
        val tNumPoints = hs.sumOf { it.allPoints.size }
        val delta = abs(coveredArea / totalCovered - tNumPoints.toDouble() / points.size)
        total += delta
        maxim = max(delta, maxim)
    }

    return total / grouped.size * 100 to maxim * 100
}

fun maxCoverRadius(highlights: List<Highlight>): Double {
    return highlights.maxOf { h ->
        coverRadius(h.allPoints.map { it.pos }, shape = if (h is ShapeHighlight) h.shape else h.contour.shape)
    }
}

fun avgCoverRadius(highlights: List<Highlight>): Double {
    return highlights.sumOf { h ->
        coverRadius(h.allPoints.map { it.pos }, shape = if (h is ShapeHighlight) h.shape else h.contour.shape)
    } / highlights.size
}

fun <T, R> List<T>.windowedCyclic(windowSize: Int, transform: (List<T>) -> R): List<R> =
    windowed(windowSize, 1, false, transform) + (subList(size - windowSize + 1, size) + subList(0, windowSize - 1)).windowed(windowSize, 1, false, transform)

fun perimeterRatio(highlight: Highlight): Double {
    val ch = ShapeContour.fromPoints(convexHull(highlight.contour.equidistantPositions(1000)), closed = true)
    val s = if (highlight is ShapeHighlight) highlight.shape else highlight.contour.shape
    return s.contours.sumOf { it.length } / ch.length
}

fun areaRatio(highlight: Highlight): Double {
    val ch = ShapeContour.fromPoints(convexHull(highlight.contour.equidistantPositions(1000)), closed = true)
    return ch.shape.area / (if (highlight is ShapeHighlight) highlight.shape.area else highlight.contour.shape.area)
}

inline fun <T> List<T>.avgOf(selector: (T) -> Double): Double =
    sumOf(selector) / size