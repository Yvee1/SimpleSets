package highlights

import geometric.Arc
import geometric.convexHull
import patterns.Island
import patterns.Point
import patterns.SinglePoint
import org.openrndr.math.YPolarity
import org.openrndr.shape.*
import patterns.Matching

class IslandHighlight(override val allPoints: List<Point>, expandRadius: Double): Highlight() {
    override val type = allPoints.firstOrNull()?.type ?: -1
    /* Points lying on the convex hull, in clockwise order. */
    override val points: List<Point> = convexHull(allPoints)
    override val circles: List<Circle> by lazy { points.map { Circle(it.pos, expandRadius) } }

    override val segments: List<LineSegment> by lazy {
        if (points.size == 1) return@lazy emptyList()
        (circles + circles.first()).zipWithNext { c1, c2 ->
            val dir = c2.center - c1.center
            val n = dir.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius
            val start = c1.center + n
            val end = c2.center + n
            val segment = LineSegment(start, end)
            segment
        }
    }

    override val arcs: List<Arc> by lazy {
        if (points.size == 1) return@lazy emptyList()
        (listOf(circles.last()) + circles + circles.first()).windowed(3) { (prev, curr, next) ->
            val d1 = curr.center - prev.center
            val d2 = next.center - curr.center
            val n1 = d1.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius
            val n2 = d2.perpendicular(YPolarity.CCW_POSITIVE_Y).normalized * expandRadius

            Arc(curr, curr.center + n1, curr.center + n2)
        }
    }

    override val contour: ShapeContour by lazy {
        if (points.size == 1) {
            return@lazy circles.first().contour
        }

        var c = ShapeContour.EMPTY
        for (i in points.indices) {
            c += arcs[i].contour
            c += segments[i].contour
        }
        c.close().reversed
    }

    override fun scale(s: Double) = IslandHighlight(allPoints, circles.first().radius * s)
}

fun Island.toHighlight(expandRadius: Double) = IslandHighlight(original().points, expandRadius)
fun Matching.toHighlight(expandRadius: Double) = IslandHighlight(original().points, expandRadius)

fun SinglePoint.toHighlight(expandRadius: Double) =
    PointHighlight(original().point, expandRadius)