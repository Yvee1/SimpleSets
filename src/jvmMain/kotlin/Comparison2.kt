import geometric.Orientation
import geometric.distanceTo
import geometric.overlaps
import highlights.ContourHighlight
import highlights.Highlight
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.shape.bounds
import org.openrndr.shape.contains
import org.openrndr.svg.loadSVG
import patterns.Point
import patterns.contains
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val gs = GeneralSettings(pSize = 1.5 * 1.33333)
        val ds = DrawSettings()

        val file = File("Vihrovs-mills.svg")
//        val file = File("ClusterSets-mills.svg")
//        val file = File("SimpleSets-mills.svg")

        val (pts, highlights) = parseSvg(file.readText(), gs.pSize, ds.colors.map { it.toColorRGBa() }, clusterSets=false)

        val comp = drawComposition {
        }

        fun inflectionPoints(h: Highlight): Int {
            val ors = h.contour.equidistantPositionsWithT((h.contour.length).toInt()).windowedCyclic(2) { (a, b) ->
                val v1 = h.contour.normal(a.second)
                val v2 = h.contour.normal(b.second)
                val dot = v1.x * v2.x + v1.y * v2.y
                val det = v1.x * v2.y - v1.y * v2.x
                val angle = atan2(det, dot)
                comp.draw {
                    strokeWeight = 0.25
                    lineSegment(a.first, a.first + v1 * 2.5)
                }
                h.contour.position(if (abs(a.second - b.second) < 0.1) (a.second + b.second)/2.0 else a.second) to if (abs(angle) < 1E-2) Orientation.STRAIGHT else if (angle < 0) Orientation.LEFT else Orientation.RIGHT
            }
            var start: Orientation? = null
            var current: Orientation? = null
            var inflections = 0
//            comp.draw {
//                fill = ColorRGBa.ORANGE
//                circle(ors[0].first, 1.5)
//            }
            for ((p, o) in ors) {
//                println(o)
                comp.draw {
                    fill = (if (o == Orientation.LEFT) ColorRGBa.RED else if (o == Orientation.RIGHT) ColorRGBa.BLUE else ColorRGBa.GRAY).opacify(0.25)
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
                    comp.draw {
                        fill = (if (o == Orientation.LEFT) ColorRGBa.RED else if (o == Orientation.RIGHT) ColorRGBa.BLUE else ColorRGBa.GRAY).opacify(0.5)
                        stroke = null
                        strokeWeight = 0.5
                        circle(p, 1.0)
                    }
                }
            }
            if (current != start) inflections++
            return inflections / 2
        }


//        for ((i, h) in highlights.withIndex()) {
//            println("$i: ${inflectionPoints(h)}")
//        }
//        for ((i, h) in highlights.withIndex()) {
//            println("$i: ${inflectionPoints(h)}")
//            println("$i: ${perimeterRatio(h)}")
//        }
        println("#inflection points: ${highlights.sumOf { inflectionPoints(it) }}")
        println("#shapes: ${highlights.size}")
        println("Area covered: ${areaCovered(highlights) / pts.map { it.pos }.bounds.area}")
        println("Density distortion: ${densityDistortion(highlights, pts)}")
        println("Max cover radius: ${maxCoverRadius(highlights)}")
        println("Sum perimeter radii - 1: ${highlights.sumOf { perimeterRatio(it) - 1 }}")

        extend(Camera2D())
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GRAY
                for (h in highlights)
                    highlight(h, gs, ds)
//                highlight(highlights[11], gs, ds)
                composition(comp)
            }
        }
    }
}

fun parseSvg(svg: String, ptSize: Double, colors: List<ColorRGBa>, clusterSets: Boolean): Pair<List<Point>, List<Highlight>> {
    val comp = loadSVG(svg)
    val (ptShapes, visShapes) = comp.findShapes().partition {
        it.effectiveShape.area < ptSize * ptSize * 4 && it.effectiveFill != null && it.effectiveStroke == ColorRGBa.BLACK
    }
    val pts = ptShapes.map { sn ->
        val type = colors.withIndex().minBy { (_, c) -> sn.effectiveFill!!.toVector4().squaredDistanceTo(c.toVector4()) }.index
        Point(sn.effectiveShape.bounds.center, type)
    }
    val svgColors = ptShapes.map { it.effectiveFill!! }.toSet()
    val highlights = visShapes.filter { if (clusterSets) it.effectiveStroke in svgColors else true }.mapNotNull { sn ->
        val c = sn.effectiveShape.contours[0]
        if (c.segments.isNotEmpty()) {
//            val contained1 = pts.filter { it.pos in c }
//            if (contained1.isNotEmpty()) {
                val buffered = if (clusterSets) c.buffer(sn.strokeWeight / 2) else c
                val contained = pts.filter { it.pos in buffered && (if (clusterSets) buffered.distanceTo(it.pos) > sn.strokeWeight / 4 - 1E-6 else true) }
                if (contained.isNotEmpty())
                    ContourHighlight(buffered, contained, 0.0)
                else null
//            } else null
        }
        else null
    }
    val minimal = highlights.filter { h ->
        if (clusterSets)
            highlights.none { other -> h != other && h.allPoints.all { p -> p in other.allPoints }  }
        else true
    }
    return pts to minimal
}
