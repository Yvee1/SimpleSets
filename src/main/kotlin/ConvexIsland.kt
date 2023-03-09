import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.math.asDegrees
import org.openrndr.shape.*
import kotlin.math.atan2
import kotlin.math.max

class ConvexIsland(val allPoints: List<Point>, val expandRadius: Double): Island {
    override val type = allPoints.firstOrNull()?.type ?: -1
    /* Points lying on the convex hull, in clockwise order. */
    val chPoints: List<Point> = convexHull(allPoints)
    override val circles: List<Circle> by lazy { chPoints.map { Circle(it.pos, expandRadius) } }

    override val segments: List<LineSegment> by lazy {
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

    override val circularArcs: List<ShapeContour> by lazy {
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

fun ConvexIsland.tangents(v: Vector2): Pair<Vector2, Vector2> {
    val tangentPoints = circles.flatMap { it.tangents(v).toList() }
    val dirR = (v - chPoints.first().pos)
    val angle = atan2(dirR.y, dirR.x).asDegrees
    val t1 = tangentPoints.minWith(compareAround(v, angle, Orientation.RIGHT))
    val t2 = tangentPoints.maxWith(compareAround(v, angle, Orientation.RIGHT))
    return t1 to t2
}

fun ConvexIsland.circleVisibilityIntervals(other: ConvexIsland): List<Pair<Double, Double>> =
    circles.map { c ->
        val (tp1, tp2) = other.tangents(c.center)
        val ls1 = LineSegment(c.center, tp1)
        val ls2 = LineSegment(c.center, tp2)
        val ip1 = c.contour.intersections(ls1.contour).firstOrNull()
        val ip2 = c.contour.intersections(ls2.contour).firstOrNull()
        if (ip1 == null || ip2 == null) return@map 0.0 to 0.0
        ip1.a.contourT to ip2.a.contourT
    }

fun ConvexIsland.arcVisibilityIntervals(other: ConvexIsland): List<Pair<Double, Double>> =
    circles.zip(circularArcs) { c, ca ->
        val (tp1, tp2) = other.tangents(c.center)
        val ls1 = LineSegment(c.center, tp1)
        val ls2 = LineSegment(c.center, tp2)
        val ip1 = c.contour.intersections(ls1.contour).firstOrNull()
        val ip2 = c.contour.intersections(ls2.contour).firstOrNull()
        if (ip1 == null || ip2 == null) return@zip 0.0 to 0.0
        val t2 = ca.nearest(ip1.position).contourT
        val t1 = ca.nearest(ip2.position).contourT
        t1 to max(t1, t2)
    }

fun ConvexIsland.segmentVisibilityIntervals(other: ConvexIsland): List<Pair<Double, Double>> =
    segments.map { ls ->
        val dir = ls.direction
        val v1 = other.circles.map { c ->
            val extremePoint = c.center - dir.normalized * c.radius
            if (orientation(ls.start, ls.end, extremePoint) == Orientation.RIGHT) {
                ls.end
            } else {
                ls.nearest(extremePoint)
            }
        }.minBy { (it - ls.start).squaredLength }

        val v2 = other.circles.map { c ->
            val extremePoint = c.center + dir.normalized * c.radius
            if (orientation(ls.start, ls.end, extremePoint) == Orientation.RIGHT) {
                ls.start
            } else {
                ls.nearest(extremePoint)
            }
        }.maxBy { (it - ls.start).squaredLength }

        val t1 = ls.contour.tForLength((v1 - ls.start).length)
        val t2 = ls.contour.tForLength((v2 - ls.start).length)

        t1 to max(t1, t2)
    }

fun ConvexIsland.segmentVisibilityContours(other: ConvexIsland): List<ShapeContour> =
    if (this == other) emptyList()
    else segments.zip(segmentVisibilityIntervals(other))
            .filter { (_, t) -> t.first <= t.second - PRECISION }
            .map { (c, t) -> c.contour.sub(t.first, t.second) }

fun ConvexIsland.arcVisibilityContours(other: ConvexIsland): List<ShapeContour> =
    if (this == other) emptyList()
    else circularArcs
        .zip(arcVisibilityIntervals(other))
        .filter { (_, t) -> t.first <= t.second - PRECISION }
        .map { (c, t) -> c.sub(t.first, t.second) }

fun ConvexIsland.circleVisibilityContours(other: ConvexIsland): List<ShapeContour> =
    if (this == other) emptyList()
    else circles
        .zip(circleVisibilityIntervals(other))
        .filter { (_, t) -> t.first <= t.second - PRECISION }
        .map { (c, t) -> c.contour.sub(t.first, t.second) }

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    oliveProgram {
        val c1 = ConvexIsland(listOf(100 p 400, 200 p 300, 400 p 300, 300 p 375, 300 p 450), 50.0)
        val c2 = ConvexIsland(listOf(600 p 600, 700 p 300, 500 p 400), 50.0)
        val c3 = ConvexIsland(listOf(150 p 60, 380 p 170, 630 p 90), 50.0)
        val islands = listOf(c1, c2, c3)

        extend {
            with(drawer) {
                clear(ColorRGBa.WHITE)

                for (island in islands) {
                    contour(island.contour)

                    isolated {
                        stroke = ColorRGBa.RED.opacify(0.3)
                        strokeWeight *= 6
                        contours(islands.flatMap { island.segmentVisibilityContours(it) })
                        contours(islands.flatMap { island.arcVisibilityContours(it) })
                    }
                }
            }
        }
    }
}