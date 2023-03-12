package islands

import geometric.Orientation
import geometric.PRECISION
import patterns.compareAround
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.intersections
import geometric.orientation
import kotlin.math.atan2
import kotlin.math.max

fun Island.tangents(v: Vector2): Pair<Vector2, Vector2> {
    val tangentPoints = circles.flatMap { it.tangents(v).toList() }
    val dirR = (v - circles.first().center)
    val angle = atan2(dirR.y, dirR.x).asDegrees
    val t1 = tangentPoints.minWith(compareAround(v, angle, Orientation.RIGHT))
    val t2 = tangentPoints.maxWith(compareAround(v, angle, Orientation.RIGHT))
    return t1 to t2
}

fun Circle.visibilityInterval(island: Island): Pair<Double, Double>? {
    val (tp1, tp2) = island.tangents(center)
    val ls1 = LineSegment(center, tp1)
    val ls2 = LineSegment(center, tp2)
    val ip1 = contour.intersections(ls1.contour).firstOrNull()
    val ip2 = contour.intersections(ls2.contour).firstOrNull()
    if (ip1 == null || ip2 == null) return null
    return ip1.a.contourT to ip2.a.contourT
}

fun Pair<Circle, ShapeContour>.visibilityInterval(island: Island): Pair<Double, Double>? {
    val (c, ca) = this
    val (ct1, ct2) = c.visibilityInterval(island) ?: return null
    val t1 = ca.nearest(c.contour.position(ct2)).contourT
    val t2 = ca.nearest(c.contour.position(ct1)).contourT
    return t1 to max(t1, t2)
}

fun LineSegment.visibilityInterval(island: Island): Pair<Double, Double>? {
    val dir = direction
    val v1 = island.circles.map { c ->
        val extremePoint = c.center - dir.normalized * c.radius
        if (orientation(start, end, extremePoint) == Orientation.RIGHT) {
            end
        } else {
            nearest(extremePoint)
        }
    }.minBy { (it - start).squaredLength }

    val v2 = island.circles.map { c ->
        val extremePoint = c.center + dir.normalized * c.radius
        if (orientation(start, end, extremePoint) == Orientation.RIGHT) {
            start
        } else {
            nearest(extremePoint)
        }
    }.maxBy { (it - start).squaredLength }

    val t1 = contour.tForLength((v1 - start).length)
    val t2 = contour.tForLength((v2 - start).length)

    return if (t1 >= t2 - PRECISION) return null else t1 to max(t1, t2)
}

fun LineSegment.visibilityContour(island: Island): ShapeContour? {
    val (a, b) = visibilityInterval(island) ?: return null
    return sub(a, b).contour
}

fun Pair<Circle, ShapeContour>.visibilityContour(island: Island): ShapeContour? {
    val (a, b) = visibilityInterval(island) ?: return null
    return second.sub(a, b)
}

fun Circle.visibilityContour(island: Island): ShapeContour? {
    val (a, b) = visibilityInterval(island) ?: return null
    return if (b > a) contour.sub(a, b) else contour.sub(a, 1.0) + contour.sub(0.0, b)
}

fun Island.visibilityContours(other: Island): List<ShapeContour> {
    if (this == other) return emptyList()
    return when(this) {
        is PointIsland -> circles.mapNotNull { it.visibilityContour(other) }
        is ConvexIsland -> circles.zip(circularArcs).mapNotNull { it.visibilityContour(other) } + segments.mapNotNull { it.visibilityContour(other) }
        is BendIsland -> circles.zip(circularArcs).mapNotNull { it.visibilityContour(other) } + segments.mapNotNull { it.visibilityContour(other) }
    }
}