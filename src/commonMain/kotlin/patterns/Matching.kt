package patterns

import ComputePartitionSettings
import org.openrndr.math.Vector2
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import kotlin.math.max

data class Matching(val point1: Point, val point2: Point) : Pattern() {
    override val weight = 2
    override val contour = ShapeContour.fromPoints(listOf(point1.pos, point2.pos), true)
    override val type = point1.type
    override val points = listOf(point1, point2)
    override val boundaryPoints = points
    override val segments: List<LineSegment>
        get() = listOf(LineSegment(point1.pos, point2.pos))
    override fun original() = Matching(point1.originalPoint ?: point1, point2.originalPoint ?: point2)
    override fun isEmpty() = false
    override operator fun contains(v: Vector2) = v in contour
    override fun isValid(cps: ComputePartitionSettings) = point1.pos.distanceTo(point2.pos) <= max(cps.bendDistance, cps.clusterRadius * 2)
}