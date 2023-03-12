package islands

import geometric.convexHull
import patterns.Cluster
import patterns.Point
import patterns.SinglePoint
import org.openrndr.math.YPolarity
import org.openrndr.shape.*

class ConvexIsland(val allPoints: List<Point>, expandRadius: Double): Island() {
    override val type = allPoints.firstOrNull()?.type ?: -1
    /* Points lying on the convex hull, in clockwise order. */
    val chPoints: List<Point> = convexHull(allPoints)
    override val circles: List<Circle> by lazy { chPoints.map { Circle(it.pos, expandRadius) } }

    val segments: List<LineSegment> by lazy {
        if (chPoints.size == 1) return@lazy emptyList()
        (circles + circles.first()).zipWithNext { c1, c2 ->
            val dir = c2.center - c1.center
            val n = dir.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius
            val start = c1.center + n
            val end = c2.center + n
            val segment = LineSegment(start, end)
            segment
        }
    }

    val circularArcs: List<ShapeContour> by lazy {
        if (chPoints.size == 1) return@lazy emptyList()
        (listOf(circles.last()) + circles + circles.first()).windowed(3) { (prev, curr, next) ->
            val d1 = curr.center - prev.center
            val d2 = next.center - curr.center
            val n1 = d1.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius
            val n2 = d2.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius

            contour {
                moveTo(curr.center + n1)
                arcTo(curr.radius, curr.radius, 90.0, largeArcFlag=false, sweepFlag=false, curr.center + n2)
            }
        }
    }

    override val contour: ShapeContour by lazy {
        if (chPoints.size == 1) {
            return@lazy circles.first().contour
        }

        var c = ShapeContour.EMPTY
        for (i in chPoints.indices) {
            c += circularArcs[i]
            c += segments[i].contour
        }
        c.close()
    }
}

fun Cluster.toIsland(expandRadius: Double) = ConvexIsland(original().points, expandRadius)

fun SinglePoint.toIsland(expandRadius: Double) =
    PointIsland(original().point, expandRadius)