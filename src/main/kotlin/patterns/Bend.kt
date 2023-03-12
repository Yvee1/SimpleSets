package patterns

import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

data class Bend(val points: List<Point>, val weightB: Int): Pattern() {
    override val type = points.firstOrNull()?.type ?: -1
    override val weight = weightB
    override val contour by lazy {
        ShapeContour.fromPoints(points.map { it.pos }, false)
    }
    companion object {
        val EMPTY = Bend(listOf(), 0)
    }
    private val vecs by lazy {
        points.map { it.pos }
    }
    override operator fun contains(v: Vector2) = v in vecs
    override fun original() = copy(points=points.map { it.originalPoint ?: it })
    override fun isEmpty() = points.isEmpty()
}