import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.LineSegment
import org.openrndr.shape.intersections
import kotlin.math.atan2

fun ProblemInstance.computeLargestCwBend(uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bend =
    uncovered.asSequence().flatMap { a ->
        uncovered.asSequence().map { b ->
            computeLargestCwBendFrom(a, b, uncovered, obstacles)
        }
    }.maxByOrNull { it.weight } ?: Bend.EMPTY

fun ProblemInstance.computeLargestCwBendFrom(a: Point, b: Point, uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bend {
    val t = a.type
    if (b.type != t || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(t)) return Bend.EMPTY
    val dir = b.pos - a.pos
    val angle = atan2(dir.y, dir.x)

    // Points in the right half-plane extending from the line going through ab.
    // Sorted in the reversed direction of the normal of ab.
    val P = uncovered
        .filter { it != a && it != b && it.type == t &&
                orientation(a.pos, b.pos, it.pos) in listOf(Orientation.RIGHT, Orientation.STRAIGHT) &&
                valid(b, it, obstacles)
        }
        .sortedWith( // Sort along the perpendicular to ab; the furthest away first.
            Comparator { p1: Point, p2: Point ->
                val y1 = p1.pos.rotate(-angle.asDegrees).y
                val y2 = p2.pos.rotate(-angle.asDegrees).y
                if (y1 < y2 - PRECISION) {
                    -1
                } else if (y1 > y2 + PRECISION) {
                    1
                } else {
                    0
                }
            }
                .thenBy { p: Point -> // Along ab; the furthest away first.
                    if (orientation(a.pos, b.pos, p.pos) == Orientation.STRAIGHT)
                        -p.pos.rotate(-angle.asDegrees).x
                    else p.pos.rotate(-angle.asDegrees).x
                }
        )

    val Pi = P.withIndex().associate { (i, p) ->
        p to P.subList(0, i)
            .filter { valid(p, it, obstacles) }
            .sortedWith(clockwiseAround(p, angle.asDegrees).then(awayFrom(p).reversed()))
    }

    if (P.isEmpty()) return Bend.EMPTY

    fun fch(p: Point, q: Point) = firstClockwiseHit(Pi[q]!!, p, q, q) ?: -1

    val T = mutableMapOf<Pair<Point, Int>, Pair<Int, Pair<Point, Int>?>>()
    for (p in P) {
        // Find points in wedge and take maximum T value.
        // If sorting has been done correctly, p should depend only on earlier calculated values.
        T[p to -1] = 1 to null
        val Pip = Pi[p]!!
        for (i in Pip.indices) {
            val Pipi = Pip[i]
            val v1 = T[p to i-1]!!.first
            val v2 = T[Pipi to fch(p, Pipi)]!!.first + 1
            T[p to i] = if (v1 > v2) {
                v1 to T[p to i-1]!!.second
            } else {
                v2 to (Pipi to fch(p, Pipi))
            }
        }
    }

    val maxT = P.asSequence().map { it to T[it to fch(b, it)]!! }.maxBy { it.second.first }

    fun fortrack(e: Pair<Point, Pair<Int, Pair<Point, Int>?>>): List<Point> {
        val (point, tValue) = e
        val (_, next) = tValue
        return listOf(point) + (next?.let { n -> fortrack(n.first to T[n]!!) } ?: emptyList())
    }

    return Bend(listOf(a, b) + fortrack(maxT), maxT.second.first + 2)
}

// Index of the first point that a ray e1e2 hits by clockwise rotating around v.
// If `points` contains collinear points, return the one with largest index.
fun firstClockwiseHit(points: List<Point>, e1: Point, e2: Point, v: Point): Int? {
    var index = points.binarySearch {
        when (orientation(v.pos, v.pos + (e2.pos - e1.pos), it.pos)) {
            Orientation.LEFT -> 1
            Orientation.STRAIGHT -> 0
            Orientation.RIGHT -> -1
        }
    }
    if (index >= 0){
        while (index + 1 < points.size && orientation(v.pos, v.pos + (e2.pos - e1.pos), points[index + 1].pos) == Orientation.STRAIGHT){
            index++
        }
    }
    return if (index >= 0) index else if (-index-2 in points.indices) -index-2 else null
}

fun ProblemInstance.valid(p: Point, q: Point, obstacles: List<Pattern>): Boolean {
    val seg = stripeData.segment.getE(p to q)
    val segContour = LineSegment(p.pos, q.pos).contour
    val intersects = obstacles.any { segContour.intersections(it.contour).isNotEmpty() }
    return (q.pos - p.pos).squaredLength < bendDistance * bendDistance && // Close together
            seg.hasType(p.type) && // p--q not blocked by a points
            !intersects // p--q not blocked by an obstacle
}

infix fun Double.p(y: Double) = Point(Vector2(this, y), 0)
infix fun Int.p(y: Int) = Point(Vector2(this.toDouble(), y.toDouble()), 0)
