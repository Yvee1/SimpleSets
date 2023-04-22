package islands

import geometric.*
import patterns.compareAround
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.*
import patterns.angleBetween
import kotlin.math.abs
import kotlin.math.atan2

data class BoundaryTangent(val start: ContourPoint, val end: ContourPoint?)

data class VisibilityInterval(val start: BoundaryTangent, val end: BoundaryTangent, val contour: ShapeContour)

fun Island.tangents(v: Vector2): Pair<ContourPoint, ContourPoint> {
    val tangentPoints = circles.flatMap { it.tangents(v).toList() }
    val dirR = (v - circles.first().center)
    val angle = atan2(dirR.y, dirR.x).asDegrees
    val t1 = tangentPoints.minWith(compareAround(v, angle, Orientation.RIGHT))
    val t2 = tangentPoints.maxWith(compareAround(v, angle, Orientation.RIGHT))
    return contour.nearest(t1) to contour.nearest(t2)
}

fun Circle.visibilityInterval(island: Island): VisibilityInterval? {
    val (tp1, tp2) = island.tangents(center)
    val ls1 = LineSegment(center, tp1.position)
    val ls2 = LineSegment(center, tp2.position)
    val ip1 = contour.intersections(ls1.contour).firstOrNull()
    val ip2 = contour.intersections(ls2.contour).firstOrNull()
    if (ip1 == null || ip2 == null) return null
    val t1 = ip1.a.contourT
    val t2 = ip2.a.contourT
    val piece = if (t2 > t1) contour.sub(t1, t2) else contour.sub(t1, 1.0) + contour.sub(0.0, t2)
    return VisibilityInterval(BoundaryTangent(ip1.a, tp1), BoundaryTangent(ip2.a, tp2), piece)
}

fun Pair<Circle, ShapeContour>.visibilityInterval(island: Island): VisibilityInterval? {
    val (c, ca) = this
    val (ct1, ct2) = c.visibilityInterval(island) ?: return null

    val t1 = ca.nearest(ct2.start.position)
    val t2 = ca.nearest(ct1.start.position)
    if (t2.contourT < t1.contourT) return null
    val bt1 = BoundaryTangent(t1, if (t1.position.squaredDistanceTo(ct2.start.position) < 1.0) ct2.end else null)
    val bt2 = BoundaryTangent(t2, if (t2.position.squaredDistanceTo(ct1.start.position) < 1.0) ct1.end else null)
    return VisibilityInterval(bt1, bt2, ca.sub(t1.contourT, t2.contourT))
}

fun LineSegment.visibilityInterval(island: Island): VisibilityInterval? {
    val dir = direction.normalized
    val v1 = island.circles.map { c ->
        val extremePoint = c.center - dir * c.radius
        if (orientation(start, end, extremePoint) == Orientation.RIGHT) {
            end to null
        } else {
            val np = nearest(extremePoint)
            if (abs(angleBetween(np - extremePoint, dir).asDegrees - 90.0) < PRECISION) {
                np to island.contour.nearest(extremePoint)
            } else {
                np to null
            }
        }
    }.minBy { (it.first - start).squaredLength }

    val v2 = island.circles.map { c ->
        val extremePoint = c.center + dir * c.radius
        if (orientation(start, end, extremePoint) == Orientation.RIGHT) {
            start to null
        } else {
            val np = nearest(extremePoint)
            if (abs(angleBetween(np - extremePoint, dir).asDegrees - 90.0) < PRECISION) {
                np to island.contour.nearest(extremePoint)
            } else {
                np to null
            }
        }
    }.maxBy { (it.first - start).squaredLength }

    val t1 = contour.nearest(v1.first)
    val t2 = contour.nearest(v2.first)

    return if (t1.contourT >= t2.contourT - PRECISION) return null else
        VisibilityInterval(BoundaryTangent(t1, v1.second), BoundaryTangent(t2, v2.second),
            contour.sub(t1.contourT, t2.contourT))
}

fun Island.visibilityIntervals(other: Island): List<VisibilityInterval> {
    if (this == other) return emptyList()
    return (when(this) {
        is PointIsland -> circles.mapNotNull { it.visibilityInterval(other) }
        is ConvexIsland -> circles.zip(circularArcs).mapNotNull { it.visibilityInterval(other) } + segments.mapNotNull { it.visibilityInterval(other) }
        is BendIsland -> circles.zip(circularArcs).mapNotNull { it.visibilityInterval(other) } + segments.mapNotNull { it.visibilityInterval(other) }
    }).mergeIntervals()
}

fun Island.visibilityContours(other: Island) = visibilityIntervals(other).map { it.contour }

fun List<VisibilityInterval>.mergeIntervals(): List<VisibilityInterval> {
    val sorted = sortedBy { (a, _) -> a.start.contourT }
    return buildList {
        var current: VisibilityInterval? = null
        for (int in sorted) {
            val (a, b) = int
            current = if (current == null) {
                int
            } else {
                val (left, right) = current
                if (abs(right.start.contourT - a.start.contourT) < PRECISION) {
                    VisibilityInterval(left, b, current.contour + int.contour)
                } else {
                    add(current)
                    int
                }
            }
        }
        if (current != null) {
            add(current)
        }
    }
}