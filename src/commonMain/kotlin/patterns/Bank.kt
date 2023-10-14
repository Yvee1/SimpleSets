package patterns

import ComputePartitionSettings
import PartitionInstance
import geometric.Orientation
import geometric.orientation
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.math.asRadians
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

data class Bank(override val points: List<Point>, override val weight: Int = points.size): Pattern() {
    override val boundaryPoints = points
    override val type = boundaryPoints.firstOrNull()?.type ?: -1
    override val contour by lazy {
        ShapeContour.fromPoints(boundaryPoints.map { it.pos }, false)
    }
    override val segments: List<LineSegment>
        get() = points.zipWithNext { a, b -> LineSegment(a.pos, b.pos) }
    companion object {
        val EMPTY = Bank(listOf(), 0)
    }
    override val vecs by lazy {
        boundaryPoints.map { it.pos }
    }
    override operator fun contains(v: Vector2) = v in vecs

    val maxDistance by lazy {
        boundaryPoints.zipWithNext { a, b -> a.pos.distanceTo(b.pos) }.maxOrNull() ?: 0.0
    }

    override val coverRadius = maxDistance / 2

    override fun isValid(cps: ComputePartitionSettings): Boolean {
        // TODO? Add angle constraints
        return maxDistance < cps.bendDistance
    }

    // A bank consists of bends, each with an orientation and an angle
    var bends: List<Bend> = buildList {
        var orientation: Orientation? = null
        var bendAngle = 0.0
        var startIndex = 0

        for (i in points.indices) {
            if (i + 2 !in points.indices) break
            val or = orientation(points[i].pos, points[i + 1].pos, points[i + 2].pos)
            val angle = angleBetween(points[i + 1].pos - points[i].pos, points[i + 2].pos - points[i + 1].pos)
            if (orientation == or.opposite()) {
                // Switched orientation
                add(Bend(orientation, bendAngle, startIndex, i + 1))
                orientation = or
                bendAngle = angle
                startIndex = i
            } else {
                orientation = or
                bendAngle += angle
            }
        }

        if (orientation == null) {
            println("Very strange stuff is happening $points")

        } else
            add(Bend(orientation!!, bendAngle, startIndex, points.lastIndex))
    }

    fun extensionStart(p: Point, cps: ComputePartitionSettings): Pair<Double, Bank>? {
        val angle = angleBetween(start.pos - points[1].pos, p.pos - start.pos)
        if (angle > cps.maxTurningAngle.asRadians) return null
        if (angle + bends[0].angle > cps.maxBendAngle.asRadians) return null
        val orient = orientation(p.pos, start.pos, points[1].pos)
        if (orient != bends.first().orientation && (bends.size >= 2 || !cps.bendInflection)) return null
        return start.pos.distanceTo(p.pos) / 2 to Bank(listOf(p) + points)
    }

    fun extensionEnd(p: Point, cps: ComputePartitionSettings): Pair<Double, Bank>? {
        val angle = angleBetween(end.pos - points[points.lastIndex - 1].pos, p.pos - end.pos)
        if (angle > cps.maxTurningAngle.asRadians) return null
        if (angle + bends.last().angle > cps.maxBendAngle.asRadians) return null
        val orient = orientation(points[points.lastIndex - 1].pos, points.last().pos, p.pos)
        if (orient != bends.last().orientation && (bends.size >= 2 || !cps.bendInflection)) return null
        return end.pos.distanceTo(p.pos) / 2 to Bank(points + listOf(p))
    }

    fun extension(p: Point, cps: ComputePartitionSettings): Pair<Double, Bank>? {
        return listOfNotNull(extensionStart(p, cps), extensionEnd(p, cps)).minByOrNull { it.first }
    }

    override fun original() = copy(points=boundaryPoints.map { it.originalPoint ?: it })
    override fun isEmpty() = boundaryPoints.isEmpty()

    val start get() = points.first()
    val end get() = points.last()
}

data class Bend(val orientation: Orientation, val angle: Double, val startIndex: Int, val endIndex: Int)

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
