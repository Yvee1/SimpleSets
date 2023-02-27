import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains

data class ConvexIsland(val points: List<Point>, val weight: Int): Pattern() {
    override val contour by lazy {
        ShapeContour.fromPoints(points.map { it.pos }, true)
    }
    private val vecs by lazy {
        points.map { it.pos }
    }

    override operator fun contains(v: Vector2) = v in vecs || (weight > vecs.size && v in contour)

    operator fun contains(p: Point) = p in points || (weight > points.size && p.pos in contour)

    companion object {
        val EMPTY = ConvexIsland(listOf(), 0)
    }

    override fun original() = copy(points=points.map { it.originalPoint ?: it })
    override fun isEmpty() = points.isEmpty()
}