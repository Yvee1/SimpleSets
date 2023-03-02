import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.LineSegment
import org.openrndr.shape.intersections
import kotlin.math.atan2

data class BendPoint(val point: Point, val index: Int)
infix fun Point.bp(index: Int) = BendPoint(this, index)
data class TableEntry(val weight: Int, val next: BendPoint?, val rest: List<Point>? = null)
infix fun Int.te(next: BendPoint?) = TableEntry(this, next)
typealias BendTable = Map<BendPoint, TableEntry>

fun ProblemInstance.largestMonotoneBend(dir: Orientation, uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bend =
    uncovered.asSequence().flatMap { a ->
        uncovered.asSequence().map { b ->
            largestMonotoneBendFrom(a, b, dir, uncovered, obstacles)
        }
    }.maxByOrNull { it.weight } ?: Bend.EMPTY

fun ProblemInstance.largestMonotoneBendFrom(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bend {
    if (b.type != a.type || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(a.type)) return Bend.EMPTY
    val (T, maxT) = tableLargestMonotoneBendFrom(a, b, dir, uncovered, obstacles)
    return Bend(listOf(a) + trace(T, b to maxT), maxT.weight + 1)
}

fun ProblemInstance.tableLargestMonotoneBendFrom(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points,
                                                 obstacles: List<Pattern> = emptyList()): Pair<BendTable, TableEntry> {
    val f = { T: BendTable, Pip: List<Point>, fh: (Point, Point) -> Int, p: Point, i: Int ->
        val Pipi = Pip[i]
        val v1 = T[p bp i - 1]!!.weight
        val v2 = T[Pipi bp fh(p, Pipi)]!!.weight + 1
        if (v1 > v2) {
            v1 te T[p bp i - 1]!!.next
        } else {
            v2 te (Pipi bp fh(p, Pipi))
        }
    }
    return tableLargestBendFrom(a, b, dir, f, uncovered, obstacles)
}

fun ProblemInstance.largestInflectionBend(dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                          obstacles: List<Pattern> = emptyList()): Bend {
    val mapT: Map<Pair<Point, Point>, TableEntry> = buildMap {
        for (a in uncovered) {
            for (b in uncovered) {
                val (T, maxT) = tableLargestMonotoneBendFrom(a, b, dir.opposite(), uncovered, obstacles)
                put(a to b, TableEntry(maxT.weight, null, trace(T, b to maxT)))
            }
        }
    }
    return uncovered.asSequence().flatMap { a ->
        uncovered.asSequence().map { b ->
            largestInflectionBendFrom(a, b, mapT, dir, uncovered, obstacles)
        }
    }.maxByOrNull { it.weight } ?: Bend.EMPTY
}

fun ProblemInstance.largestInflectionBendFrom(a: Point, b: Point, mapT: Map<Pair<Point, Point>, TableEntry>,
                                              dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                              obstacles: List<Pattern> = emptyList()): Bend {
    if (b.type != a.type || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(a.type)) return Bend.EMPTY
    val (T, maxT) = tableLargestInflectionBendFrom(a, b, mapT, dir, uncovered, obstacles)
    return Bend(listOf(a) + trace(T, b to maxT), maxT.weight + 1)
}

fun ProblemInstance.tableLargestInflectionBendFrom(a: Point, b: Point, mapT: Map<Pair<Point, Point>, TableEntry>,
                                                   dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                                   obstacles: List<Pattern> = emptyList())
        : Pair<BendTable, TableEntry> {
    val f = { T: BendTable, Pip: List<Point>, fh: (Point, Point) -> Int, p: Point, i: Int ->
        val Pipi = Pip[i]
        val j = fh(p, Pipi)
        val tes = listOf(
            T[p bp i - 1]!!,
            T[Pipi bp j]!!.let { it.weight + 1 te (Pipi bp j) },
            mapT[p to Pipi]!!.let { it.copy(weight = it.weight + 1) }
        )
        tes.maxBy { it.weight }
    }
    return tableLargestBendFrom(a, b, dir, f, uncovered, obstacles)
}

fun ProblemInstance.tableLargestBendFrom(a: Point, b: Point, dir: Orientation,
                                         f: (BendTable, List<Point>, (Point, Point) -> Int, Point, Int) -> TableEntry,
                                         uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList())
        : Pair<BendTable, TableEntry> {
    val t = a.type
    val ab = b.pos - a.pos
    val angle = atan2(ab.y, ab.x)
    val lowerDir = ab.rotate(if (dir == Orientation.RIGHT) -bendAngle else bendAngle)

    val comp = Comparator { p1: Point, p2: Point ->
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

    // Points in the right half-plane extending from the line going through ab.
    // Sorted in the reversed direction of the normal of ab.
    val P = uncovered
        .filter { it != a && it != b && it.type == t &&
                orientation(a.pos, b.pos, it.pos) in listOf(dir, Orientation.STRAIGHT) &&
                orientation(b.pos, b.pos + lowerDir, it.pos) in listOf(dir.opposite(), Orientation.STRAIGHT) &&
                valid(b, it, obstacles)
        }
        .sortedWith((if (dir == Orientation.RIGHT) comp else comp.reversed()) // Sort along the perpendicular to ab; the furthest away first.
            .thenBy { p: Point -> // Along ab; the furthest away first.
                if (orientation(a.pos, b.pos, p.pos) == Orientation.STRAIGHT)
                    -p.pos.rotate(-angle.asDegrees).x
                else p.pos.rotate(-angle.asDegrees).x
            }
        ) + listOf(b)

    val Pi = P.withIndex().associate { (i, p) ->
        p to P.subList(0, i)
            .filter {
                orientation(p.pos, p.pos + lowerDir, it.pos) in listOf(dir.opposite(), Orientation.STRAIGHT) &&
                        valid(p, it, obstacles)
            }
            .sortedWith(
                compareAround(p, angle.asDegrees, dir).then(awayFrom(p).reversed())
            )
    }

    fun fh(p: Point, q: Point) = firstHit(Pi[q]!!, p, q, q, dir) ?: -1

    val T = mutableMapOf<BendPoint, TableEntry>()
    for (p in P) {
        // Find points in wedge and take maximum T value.
        // If sorting has been done correctly, p should depend only on earlier calculated values.
        T[p bp -1] = 1 te null
        val Pip = Pi[p]!!
        for (i in Pip.indices) {
            T[p bp i] = f(T, Pip, ::fh, p, i)
        }
    }

    val maxT = T[b bp Pi[b]!!.size-1]!!

    return T to maxT
}

private fun trace(T: BendTable, e: Pair<Point, TableEntry>): List<Point> {
    val (point, tValue) = e
    val (_, next, bend) = tValue
    return listOf(point) + (bend ?: next?.let { n -> trace(T, n.point to T[n]!!) } ?: emptyList())
}

// Index of the first point that a ray e1e2 hits by rotating in `dir` around v.
// If `points` contains collinear points, return the one with largest index.
fun firstHit(points: List<Point>, e1: Point, e2: Point, v: Point, dir: Orientation): Int? {
    var index = points.binarySearch {
        val x = when (orientation(v.pos, v.pos + (e2.pos - e1.pos), it.pos)) {
            Orientation.LEFT -> 1
            Orientation.STRAIGHT -> 0
            Orientation.RIGHT -> -1
        }
        if (dir == Orientation.RIGHT) x else -x
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
