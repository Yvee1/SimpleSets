import geometric.Orientation
import geometric.convexHull
import highlights.Highlight
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

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val pts = getExampleInput(ExampleInput.Mills)
        val gs = GeneralSettings()
        val ds = DrawSettings()
        val cs = object {
            @DoubleParameter("Cover", 1.0, 8.0)
            var cover: Double = 3.0
        }
        val cds = ComputeDrawingSettings()
        val tgs = GrowSettings()

        val (gSettings, gCover) = goodSettings(ExampleInput.Mills)
        cs.cover = gCover
        val (gGs, gTgs, _, _) = gSettings
        gs.pSize = gGs.pSize
        gs.bendInflection = gGs.bendInflection
        gs.maxTurningAngle = gGs.maxTurningAngle
        gs.maxBendAngle = gGs.maxBendAngle
        tgs.forbidTooClose = gTgs.forbidTooClose
        tgs.postponeIntersections = gTgs.postponeIntersections

        val filtration = topoGrow(pts, gs, tgs, 8 * gs.expandRadius)
        val partition = filtration.takeWhile { it.first < cs.cover * gs.expandRadius }.lastOrNull()?.second!!
        val highlights = partition.patterns.map { it.toHighlight(gs.expandRadius) }
        val xGraph = XGraph(highlights, gs, cds)

        println(cs.cover * gs.expandRadius)

        val comp = drawComposition {
            xGraph.draw(this, ds)
        }

        fun inflectionPoints(h: Highlight): Int {
            val ors = h.contour.equidistantPositionsWithT(50).windowedCyclic(2) { (a, b) ->
                val v1 = h.contour.normal(a.second)
                val v2 = h.contour.normal(b.second)
                val dot = v1.x * v2.x + v1.y * v2.y
                val det = v1.x * v2.y - v1.y * v2.x
                val angle = atan2(det, dot)
                a.first to if (abs(angle) < 1E-6) Orientation.STRAIGHT else if (angle < 0) Orientation.LEFT else Orientation.RIGHT
            }
            var start: Orientation? = null
            var current: Orientation? = null
            var inflections = 0
            for ((p, o) in ors) {
                comp.draw {
                    fill = if (o == Orientation.LEFT) ColorRGBa.RED else if (o == Orientation.RIGHT) ColorRGBa.BLUE else ColorRGBa.GRAY
                    stroke = null
                    strokeWeight = 0.5
                    circle(p, 1.0)
                }
                if (current == null && o != Orientation.STRAIGHT) {
                    start = o
                    current = o
                }
                if (current != null && o == current.opposite()) {
                    current = o
                    inflections++
                }
            }
            if (current != start) inflections++
            return inflections / 2
        }

        println("#inflection points: ${highlights.sumOf { inflectionPoints(it) }}")
        println("#shapes: ${highlights.size}")
        println("Area covered: ${areaCovered(highlights) / pts.map { it.pos }.bounds.area}")
        println("Density distortion: ${densityDistortion(highlights, pts)}")
        println("Max cover radius: ${maxCoverRadius(highlights)}")

        extend(Camera2D())
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                scale(1.0, -1.0)
                composition(comp)
                coloredPoints(pts, gs, ds)
            }

        }
    }
}

fun areaCovered(highlights: List<Highlight>): Double {
    var total = Shape.EMPTY

    for (h in highlights) {
        total = h.contour.shape.union(total)
    }

    return total.area
}

fun densityDistortion(highlights: List<Highlight>, points: List<Point>): Double {
    val totalCovered = areaCovered(highlights)

    var total = 0.0

    for ((t, hs) in highlights.groupBy { it.type }) {
        val coveredArea = areaCovered(hs)
        val tNumPoints = hs.sumOf { it.allPoints.size }
        total += abs(coveredArea / totalCovered - tNumPoints / points.size)
    }

    return total
}

fun maxCoverRadius(highlights: List<Highlight>): Double {
    return highlights.withIndex().maxOf { (i, h) ->
        coverRadius(h.allPoints.map { it.pos }, shape = h.contour)
    }
}

fun <T, R> List<T>.windowedCyclic(windowSize: Int, transform: (List<T>) -> R): List<R> =
    windowed(windowSize, 1, false, transform) + (subList(size - windowSize + 1, size) + subList(0, windowSize - 1)).windowed(windowSize, 1, false, transform)

fun perimeterRatio(highlight: Highlight): Double {
    val ch = ShapeContour.fromPoints(convexHull(highlight.contour.equidistantPositions(1000)), closed = true)
    return highlight.contour.length / ch.length
}