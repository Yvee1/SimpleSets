package patterns

import PartitionInstance
import geometric.orientation
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.ShapeContour

data class Reef(override val boundaryPoints: List<Point>, val weightB: Int): Pattern() {
    override val type = boundaryPoints.firstOrNull()?.type ?: -1
    override val weight = weightB
    override val contour by lazy {
        ShapeContour.fromPoints(boundaryPoints.map { it.pos }, false)
    }
    companion object {
        val EMPTY = Reef(listOf(), 0)
    }
    private val vecs by lazy {
        boundaryPoints.map { it.pos }
    }
    override operator fun contains(v: Vector2) = v in vecs
    override fun original() = copy(boundaryPoints=boundaryPoints.map { it.originalPoint ?: it })
    override fun isEmpty() = boundaryPoints.isEmpty()
}

fun PartitionInstance.clusterIsMonotoneBend(island: Island): Boolean {
    if (island.weight != island.points.size) return false
    return (0 until island.points.size).any {
        val pts = island.points.subList(it, island.points.size) + island.points.subList(0, it)
        isMonotoneBend(pts)
    }
}

/**
    Returns whether [points] forms a monotone bend satisfying the restrictions in the [PartitionInstance].
    It is not checked whether any points lie close to the bend.
 */
fun PartitionInstance.isMonotoneBend(points: List<Point>): Boolean {
    if (points.size <= 1) return true
    if (!points.all { it.type == points.first().type }) return false
    if (!points.zipWithNext().all { (p, q) -> p.pos.squaredDistanceTo(q.pos) <= bendDistance * bendDistance }) return false
    if (points.size == 2) return true
    val dir = orientation(points[0].pos, points[1].pos, points[2].pos)
    val monotone = points.windowed(3) { (p, q, r) ->
        orientation(p.pos, q.pos, r.pos) == dir
    }.all { it }
    if (!monotone) return false
    val angles = points.windowed(3) { (p, q, r) ->
        angleBetween(q.pos - p.pos, r.pos - q.pos).asDegrees
    }
    if (angles.max() > maxTurningAngle) return false
    if (angles.sum() > maxBendAngle) return false

    return true
}
