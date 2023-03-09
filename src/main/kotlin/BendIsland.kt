import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.shape.*

fun cwCircularArc(circle: Circle, cp1: Vector2, cp2: Vector2) = contour {
    val largeArcFlag = orientation(circle.center, cp1, cp2) != Orientation.RIGHT
    moveTo(cp1)
    arcTo(circle.radius, circle.radius, 90.0, largeArcFlag=largeArcFlag, sweepFlag=true, cp2)
}

class BendIsland(val points: List<Point>, val expandRadius: Double): Island {
    override val type = points.firstOrNull()?.type ?: -1
    override val circles: List<Circle> by lazy { points.map { Circle(it.pos, expandRadius) } }

    override val segments: List<LineSegment> by lazy {
        if (points.size == 1) return@lazy emptyList()

        val pairedSegs = circles.zipWithNext { c1, c2 ->
            val dir = c2.center - c1.center
            val n = dir.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius
            val start1 = c1.center - n
            val end1 = c2.center - n
            val segment1 = LineSegment(start1, end1)
            val start2 = c1.center + n
            val end2 = c2.center + n
            val segment2 = LineSegment(start2, end2)
            segment1 to segment2
        }

        val nil = LineSegment(Vector2.INFINITY, Vector2.INFINITY) to LineSegment(Vector2.INFINITY, Vector2.INFINITY)
        val paddedSegs = listOf(nil) + pairedSegs + nil

        paddedSegs.windowed(3) { (lsp, lsc, lsn) ->
            val fa = if (lsp == nil) 0.0 else lsc.first.contour.intersections(lsp.first.contour).firstOrNull()?.a?.contourT ?: 0.0
            val fb = if (lsn == nil) 1.0 else lsc.first.contour.intersections(lsn.first.contour).firstOrNull()?.a?.contourT ?: 1.0
            val sa = if (lsp == nil) 0.0 else lsc.second.contour.intersections(lsp.second.contour).firstOrNull()?.a?.contourT ?: 0.0
            val sb = if (lsn == nil) 1.0 else lsc.second.contour.intersections(lsn.second.contour).firstOrNull()?.a?.contourT ?: 1.0
            listOf(lsc.first.sub(fa, fb), lsc.second.sub(sa, sb))
        }.flatten()
    }

    override val circularArcs: List<ShapeContour> by lazy {
        if (points.size == 1) return@lazy emptyList()

        val cf = circles.first()
        val cfn = circles[1]
        val cl = circles.last()
        val clp = circles[circles.lastIndex-1]
        val nf = (cfn.center - cf.center).perpendicular().normalized * expandRadius
        val firstArc = cwCircularArc(cf, cf.center - nf, cf.center + nf)
        val nl = (cl.center - clp.center).perpendicular().normalized * expandRadius
        val lastArc = cwCircularArc(cl, cl.center + nl, cl.center - nl)

        val middleArcs = circles.windowed(3) { (prev, curr, next) ->
            val d1 = curr.center - prev.center
            val d2 = next.center - curr.center
            val or = when(orientation(prev.center, curr.center, next.center)) {
                Orientation.RIGHT -> YPolarity.CCW_POSITIVE_Y
                Orientation.LEFT -> YPolarity.CW_NEGATIVE_Y
                else -> TODO()
            }
            val n1 = d1.perpendicular(or).normalized * expandRadius
            val n2 = d2.perpendicular(or).normalized * expandRadius

            val cp1 = curr.center + n1
            val cp2 = curr.center + n2

            val sweep = orientation(curr.center, cp1, cp2) == Orientation.LEFT

            contour {
                moveTo(cp1)
                arcTo(curr.radius, curr.radius, 90.0, largeArcFlag=false, sweepFlag=sweep, cp2)
            }
        }

        listOf(firstArc) + middleArcs + lastArc
    }

    override val contour: ShapeContour by lazy {
        if (points.size == 1) {
            return@lazy circles.first().contour
        }

        var c = ShapeContour.EMPTY
        for (i in points.indices) {
            c += circularArcs[i]
            c += segments[i].contour
        }
        c.close()
    }
}

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    oliveProgram {
        val c = BendIsland(listOf(100 p 500, 200 p 300, 400 p 300, 500 p 100), 50.0)
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                contours(c.circularArcs)
                lineSegments(c.segments)
            }
        }
    }
}