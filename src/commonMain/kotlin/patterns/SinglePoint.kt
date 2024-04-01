package patterns

import GeneralSettings
import org.openrndr.math.Vector2
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

data class SinglePoint(val point: Point) : Pattern() {
    override val weight = 1
    override val contour = ShapeContour.fromPoints(listOf(point.pos), true)
    override val type = point.type
    override val points = listOf(point)
    override val vecs = listOf(point.pos)
    override val boundaryPoints = listOf(point)
    override val segments = emptyList<LineSegment>()
    override val coverRadius = 0.0
    override fun original() = point.originalPoint?.let { SinglePoint(it) } ?: this
    override fun isEmpty() = false
    override operator fun contains(v: Vector2) = v == point.pos
    override fun isValid(gs: GeneralSettings) = true
}