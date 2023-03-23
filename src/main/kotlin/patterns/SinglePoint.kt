package patterns

import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

data class SinglePoint(val point: Point) : Pattern() {
    override val weight = 1
    override val contour = ShapeContour.fromPoints(listOf(point.pos), true)
    override val type = point.type
    override val boundaryPoints = listOf(point)
    override fun original() = point.originalPoint?.let { SinglePoint(it) } ?: this
    override fun isEmpty() = false
    override operator fun contains(v: Vector2) = v == point.pos
}