package patterns

import geometric.Orientation
import geometric.PRECISION
import PartitionInstance
import org.openrndr.math.asDegrees
import org.openrndr.shape.LineSegment
import org.openrndr.shape.intersections
import geometric.orientation
import kotlin.math.atan2

sealed interface PointProvider {
    val point: Point
}
data class ReefPoint(override val point: Point, val index: Int): PointProvider
infix fun Point.bp(index: Int) = ReefPoint(this, index)
data class TableEntry<T: PointProvider>(val weight: Int, val next: T?, val rest: List<Point>? = null)
infix fun <T: PointProvider> Int.te(next: T?) = TableEntry(this, next)
typealias Table<T> = Map<T, TableEntry<T>>

data class ConstrainedPoint(override val point: Point, val startIndex: Int, val endIndex: Int): PointProvider
fun cp(p: Point, iStart: Int, iEnd: Int) = ConstrainedPoint(p, iStart, iEnd)

fun PartitionInstance.largestMonotoneReef(dir: Orientation, uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bank =
    uncovered.asSequence().flatMap { a ->
        uncovered.asSequence().map { b ->
            largestMonotoneReefFrom(a, b, dir, uncovered, obstacles)
        }
    }.maxWithOrNull(compareBy { b: Bank -> b.weight }.thenBy { -it.contour.length }) ?: Bank.EMPTY

fun PartitionInstance.largestMonotoneReefFrom(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Bank {
    if (b.type != a.type || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(a.type)) return Bank.EMPTY
    val (T, maxT) =
        if (maxTurningAngle >= maxBendAngle)
            tableLargestMonotoneReefFrom(a, b, dir, uncovered, obstacles)
        else
            tableLargestConstrainedMonotoneReefFrom(a, b, dir, uncovered, obstacles)
    return Bank(listOf(a) + trace(T, b to maxT), maxT.weight + 1)
}

fun PartitionInstance.tableLargestMonotoneReefFrom(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points,
                                                   obstacles: List<Pattern> = emptyList()): Pair<Table<ReefPoint>, TableEntry<ReefPoint>> =
    tableLargestReefFrom(a, b, dir, uncovered, obstacles) { _, _ -> emptyList() }

fun PartitionInstance.tableLargestConstrainedMonotoneReefFrom(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points,
                                                              obstacles: List<Pattern> = emptyList()): Pair<Table<ConstrainedPoint>, TableEntry<ConstrainedPoint>> =
    tableLargestConstrainedReefFrom(a, b, dir, uncovered, obstacles) { _, _ -> emptyList() }

fun PartitionInstance.largestInflectionReef(uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()) =
    listOf(
        largestInflectionReef(Orientation.LEFT, uncovered, obstacles),
        largestInflectionReef(Orientation.RIGHT, uncovered, obstacles)
    ).maxBy { it.weight }

fun PartitionInstance.largestInflectionReef(dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                            obstacles: List<Pattern> = emptyList()): Bank {
    if (maxTurningAngle >= maxBendAngle) {
        val mapT = uncovered.asSequence().flatMap { a ->
            uncovered
                .asSequence()
                .filter { b ->
                    a != b && valid(a, b, obstacles)
                }
                .map { b ->
                    val (T, maxT) = tableLargestMonotoneReefFrom(a, b, dir.opposite(), uncovered, obstacles)
                    val key = a to b
                    val value = TableEntry<ReefPoint>(maxT.weight, null, trace(T, b to maxT))
                    key to value
                }
        }.toMap()

        return uncovered.asSequence().flatMap { a ->
            uncovered.asSequence().map { b ->
                largestInflectionReefFrom(a, b, mapT, dir, uncovered, obstacles)
            }
        }.maxWithOrNull(compareBy { b: Bank -> b.weight }.thenBy { -it.contour.length }) ?: Bank.EMPTY
    } else {
        val mapT = uncovered.asSequence().flatMap { a ->
            uncovered
                .asSequence()
                .filter { b ->
                    a != b && valid(a, b, obstacles)
                }
                .map { b ->
                    val (T, maxT) = tableLargestConstrainedMonotoneReefFrom(a, b, dir.opposite(), uncovered, obstacles)
                    val key = a to b
                    val value = TableEntry<ConstrainedPoint>(maxT.weight, null, trace(T, b to maxT))
                    key to value
                }
        }.toMap()
        return uncovered.asSequence().flatMap { a ->
            uncovered.asSequence().map { b ->
                largestConstrainedInflectionReefFrom(a, b, mapT, dir, uncovered, obstacles)
            }
        }.maxWithOrNull(compareBy { b: Bank -> b.weight }.thenBy { -it.contour.length }) ?: Bank.EMPTY
    }
}

fun PartitionInstance.largestInflectionReefFrom(a: Point, b: Point, mapT: Map<Pair<Point, Point>, TableEntry<ReefPoint>>,
                                                dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                                obstacles: List<Pattern> = emptyList()): Bank {
    if (b.type != a.type || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(a.type)) return Bank.EMPTY
    val (T, maxT) = tableLargestInflectionReefFrom(a, b, mapT, dir, uncovered, obstacles)
    return Bank(listOf(a) + trace(T, b to maxT), maxT.weight + 1)
}

fun PartitionInstance.largestConstrainedInflectionReefFrom(a: Point, b: Point, mapT: Map<Pair<Point, Point>, TableEntry<ConstrainedPoint>>,
                                                           dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                                           obstacles: List<Pattern> = emptyList()): Bank {
    if (b.type != a.type || a == b || !valid(a, b, obstacles) || !stripeData.segment.getE(a to b).hasType(a.type)) return Bank.EMPTY

    val (T, maxT) = tableLargestConstrainedInflectionReefFrom(a, b, mapT, dir, uncovered, obstacles)
    return Bank(listOf(a) + trace(T, b to maxT), maxT.weight + 1)
}

fun PartitionInstance.tableLargestInflectionReefFrom(a: Point, b: Point, mapT: Map<Pair<Point, Point>, TableEntry<ReefPoint>>,
                                                     dir: Orientation = Orientation.RIGHT, uncovered: List<Point> = points,
                                                     obstacles: List<Pattern> = emptyList())
        : Pair<Table<ReefPoint>, TableEntry<ReefPoint>>
    = tableLargestReefFrom(a, b, dir, uncovered, obstacles) { Pipi: Point, p: Point ->
        listOf(mapT[p to Pipi]!!.let { it.copy(weight = it.weight + 1) })
    }

fun PartitionInstance.tableLargestConstrainedInflectionReefFrom(a: Point, b: Point,
                                                                mapT: Map<Pair<Point, Point>, TableEntry<ConstrainedPoint>>,
                                                                dir: Orientation = Orientation.RIGHT,
                                                                uncovered: List<Point> = points,
                                                                obstacles: List<Pattern> = emptyList())
        : Pair<Table<ConstrainedPoint>, TableEntry<ConstrainedPoint>>
        = tableLargestConstrainedReefFrom(a, b, dir, uncovered, obstacles) { Pipi: Point, p: Point ->
            listOf(mapT[p to Pipi]!!.let { it.copy(weight = it.weight + 1) })
        }

fun PartitionInstance.tableLargestReefFrom(a: Point, b: Point, dir: Orientation,
                                           uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList(),
                                           f: (Point, Point) -> List<TableEntry<ReefPoint>>)
        : Pair<Table<ReefPoint>, TableEntry<ReefPoint>> {
    val (P, Pi) = tablePointSets(a, b, dir, uncovered, obstacles)

    fun fh(p: Point, q: Point) = firstHit(Pi[q]!!.asReversed(), p, q, q, dir)?.let { Pi[q]!!.size - 1 - it } ?: -1

    val T = mutableMapOf<ReefPoint, TableEntry<ReefPoint>>()
    for (p in P) {
        T[p bp -1] = 1 te null
        val Pip = Pi[p]!!
        for (i in Pip.indices) {
            val Pipi = Pip[i]
            val j = fh(p, Pipi)
            val tes = listOf(
                T[p bp i - 1]!!,
                T[Pipi bp j]!!.let { it.weight + 1 te (Pipi bp j) }
            ) + f(Pipi, p)
            T[p bp i] = tes.maxWith(compareBy { te: TableEntry<ReefPoint> -> te.weight }.thenBy {
                val nextPoint = it.next?.point ?: it.rest?.first()
                -(nextPoint?.pos?.squaredDistanceTo(p.pos) ?: 0.0)
            })
        }
    }

    val maxT = T[b bp Pi[b]!!.size-1]!!

    return T to maxT
}

fun PartitionInstance.tableLargestConstrainedReefFrom(a: Point, b: Point, dir: Orientation,
                                                      uncovered: List<Point> = points,
                                                      obstacles: List<Pattern> = emptyList(),
                                                      f: (Point, Point) -> List<TableEntry<ConstrainedPoint>>)
        : Pair<Table<ConstrainedPoint>, TableEntry<ConstrainedPoint>> {
    val (P, Pi) = tablePointSets(a, b, dir, uncovered, obstacles)

    fun fh(p: Point, q: Point) = firstHit(Pi[q]!!.asReversed(), p, q, q, dir)?.let { Pi[q]!!.size - 1 - it } ?: -1
    fun foh(p: Point, q: Point) = firstHit(Pi[q]!!, q,
        Point(q.pos + (q.pos-p.pos).rotate(if (dir == Orientation.RIGHT) -maxTurningAngle else maxTurningAngle),
            q.type), q, dir.opposite()) ?: Pi[q]!!.size

    val T = mutableMapOf<ConstrainedPoint, TableEntry<ConstrainedPoint>>()
    for (p in P) {
        T[cp(p, -1, -1)] = 1 te null
        val Pip = Pi[p]!!
        for (iEnd in -1 until Pip.size) {
            for (iStart in 0..Pip.size) {
                if (iEnd == -1 || iStart > iEnd){
                    T[cp(p, iStart, iEnd)] = 1 te null
                    continue
                }
                val Pipi = Pip[iEnd]
                val j = fh(p, Pipi)
                val k = foh(p, Pipi)
                val tes = listOfNotNull(
                    T[cp(p, iStart, iEnd - 1)],
                    T[cp(Pipi, k, j)]?.let { it.weight + 1 te (cp(Pipi, k, j)) }
                ) + f(Pipi, p)
                T[cp(p, iStart, iEnd)] = tes.maxWith(compareBy { te: TableEntry<ConstrainedPoint> -> te.weight }.thenBy {
                    val nextPoint = it.next?.point ?: it.rest?.first()
                    -(nextPoint?.pos?.squaredDistanceTo(p.pos) ?: 0.0)
                })
            }
        }
    }

    val maxT = T[cp(b, foh(a, b),Pi[b]!!.size-1)]!!

    return T to maxT
}

fun PartitionInstance.tablePointSets(a: Point, b: Point, dir: Orientation, uncovered: List<Point> = points,
                                     obstacles: List<Pattern> = emptyList())
    : Pair<List<Point>, Map<Point, List<Point>>> {
    val t = a.type
    val ab = b.pos - a.pos
    val angle = atan2(ab.y, ab.x)
    val lowerDir = ab.rotate(if (dir == Orientation.RIGHT) -maxBendAngle else maxBendAngle)

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
        .filter {
            it != a && it != b && it.type == t &&
                    orientation(a.pos, b.pos, it.pos) in listOf(dir, Orientation.STRAIGHT) &&
                    orientation(b.pos, b.pos + lowerDir, it.pos) in listOf(dir.opposite(), Orientation.STRAIGHT)
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

    return P to Pi
}

private fun <A: PointProvider>trace(T: Table<out A>, e: Pair<Point, TableEntry<out A>>): List<Point> {
    val (point, tValue) = e
    val (_, next, bend) = tValue
    return listOf(point) + (bend ?: next?.let { n -> trace(T, n.point to T[n]!!) } ?: emptyList())
}

//
/**
 * Index of the first point that ray `e1``e2` hits by rotating in `dir` around `v`.
 * If `points` contains collinear points, return the one with largest index.
 * @param points a list of points sorted in direction `dir`
 */
fun firstHit(points: List<Point>, e1: Point, e2: Point, v: Point, dir: Orientation): Int? {
    var index = points.binarySearch {
        val x = when (orientation(v.pos, v.pos + (e2.pos - e1.pos), it.pos)) {
            Orientation.LEFT -> -1
            Orientation.STRAIGHT -> 0
            Orientation.RIGHT -> 1
        }
        if (dir == Orientation.RIGHT) x else -x
    }
    if (index >= 0) {
        while (index - 1 >= 0 && orientation(v.pos, v.pos + (e2.pos - e1.pos), points[index - 1].pos)
            == Orientation.STRAIGHT
        ) {
            index--
        }
    }
    return if (index >= 0) index
        else if (-index - 1 in points.indices) -index - 1
        else null
}

fun PartitionInstance.valid(p: Point, q: Point, obstacles: List<Pattern>): Boolean {
    val seg = stripeData.segment.getE(p to q)
    val segContour = LineSegment(p.pos, q.pos).contour
    val intersects = obstacles.any { segContour.intersections(it.contour).isNotEmpty() }
    return (q.pos - p.pos).squaredLength < bendDistance * bendDistance && // Close together
            seg.hasType(p.type) && // p--q not blocked by a point
            !capsuleData.capsule.getF(p to q) && // no points near p--q
            !intersects // p--q not blocked by an obstacle
}
