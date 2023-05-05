package patterns

import geometric.Orientation
import geometric.PRECISION
import geometric.compare
import org.openrndr.math.Vector2
import geometric.orientation
import kotlin.math.abs
import kotlin.math.atan2

fun <K> Map<K, Int>.get0(key: K) = getOrElse(key) { 0 }
fun <K> Map<K, Map<Int, Int>>.getE(key: K) = getOrElse(key) { emptyMap() }

/**
 * Returns a data structure that takes O(n^2 log n) preprocessing time to provide O(1) access to
 * the number (and color) of points inside any triangle with vertices in `points`. The data structure uses
 * O(n^2 * k) storage, where k is the number of colors.
 *
 * Reference
 * ---------
 * Finding Minimum Area k-gons, by
 * David Eppstein, Mark Overmars, Günter Rote, and Gerhard Woeginger (1992)
 *
 * @param points a list of `points` with distinct x-coordinates
 * @throws error if `points` do not have distinct x-coordinates
 */
class StripeData(points: List<Point>) {
    /** `stripe[pi to pj]` contains the number of points that lie *below* segment pi--pj */
    val stripe: Map<Pair<Point, Point>, Map<Int, Int>>
    /** `segment[pi to pj]` contains the number of points that lie *on* segment pi--pj */
    val segment: Map<Pair<Point, Point>, Map<Int, Int>>

    init {
        val xSorted = points.sortedBy { it.pos.x }
        val cwOrder = buildMap(xSorted.size) {
            for (p in xSorted) {
                val l = xSorted
                    .filter { it.pos.x <= p.pos.x && it != p }
                    .sortedWith(compareAround(p, 90.0, Orientation.RIGHT).then(awayFrom(p)))
                put(p, l)
            }
        }

        segment = buildMap {
            val segmentMut = this@buildMap
            stripe = buildMap {
                for (p in xSorted) {
                    val pi = cwOrder[p]!!
                    for (j in pi.indices) {
                        if (j == 0) continue

                        val orient = compare(pi[j].pos.x, pi[j - 1].pos.x)
                        val collinear = orientation(p.pos, pi[j - 1].pos, pi[j].pos) == Orientation.STRAIGHT
                        val v = when (orient) {
                            Orientation.LEFT -> getE(pi[j - 1] to p).sum(
                                getE(pi[j] to pi[j - 1])
                            ).sum(
                                (if (collinear) emptyMap() else
                                    mapOf(pi[j - 1].type to 1).sum(segmentMut.getE(pi[j - 1] to p)))
                            )

                            Orientation.RIGHT -> getE(pi[j - 1] to p).sum(
                                segmentMut.getE(pi[j - 1] to p)
                            ).diff(
                                getE(pi[j] to pi[j - 1])
                            )

                            Orientation.STRAIGHT -> error("Points should have distinct x coordinates!")
                        }
                        put(pi[j] to p, v)
                        put(p to pi[j], v)
                        if (collinear) {
                            val w = mapOf(pi[j - 1].type to 1).sum(segmentMut.getE(pi[j - 1] to p))
                            segmentMut[pi[j] to p] = w
                            segmentMut[p to pi[j]] = w
                        }
                    }
                }
            }
        }
    }

    /** Returns the number of points that lie in the interior and on the boundary
     *  of the triangle with vertices p1, p2 and p3. */
    fun triangle(p1: Point, p2: Point, p3: Point): Map<Int, Int> {
        val ps = listOf(p1, p2, p3).sortedBy { it.pos.x }
        val x = ps[0]
        val y = ps[1]
        val z = ps[2]
        val orient = orientation(x.pos, z.pos, y.pos)
        val interior = stripe.getE(x to y)
            .sum(stripe.getE(y to z))
            .diff(stripe.getE(x to z))
            .mapValues { abs(it.value) }
            .diff( // Subtract points not in the interior that have been counted
                if (orient == Orientation.LEFT)
                    mapOf(y.type to 1).sum(segment.getE(x to y)).sum(segment.getE(y to z))
                else segment.getE(x to z))
        val boundary = segment.getE(x to y)
            .sum(segment.getE(y to z))
            .sum(segment.getE(x to z))
            .sum(mapOf(p1.type to 1).sum(mapOf(p2.type to 1)).sum(mapOf(p3.type to 1)))
        return interior.sum(boundary)
    }
}

/**
 * Clockwise ordering around a point `p`.
 * @param p the reference point
 * @param start the start angle in degrees, counter-clockwise starting at 3 o'clock.
 */
fun compareAround(p: Point, start: Double, dir: Orientation) = Comparator<Point> { p1, p2 ->
        compareAround(p.pos, start, dir).compare(p1.pos, p2.pos)
    }

/**
 * Clockwise ordering around a point `p`.
 * @param p the reference point
 * @param start the start angle in degrees, counter-clockwise starting at 3 o'clock.
 */
fun compareAround(p: Vector2, start: Double, dir: Orientation) = Comparator<Vector2> { p1, p2 ->
    val v1 = (p1 - p).rotate(-(start - 180))
    val v2 = (p2 - p).rotate(-(start - 180))
    val a1 = -atan2(v1.y, v1.x)
    val a2 = -atan2(v2.y, v2.x)
    val x = if (a1 < a2 - PRECISION){
        1
    } else if (a1 > a2 + PRECISION) {
        -1
    } else {
        0
    }
    if (dir == Orientation.RIGHT) x else -x
}

fun awayFrom(p: Point): Comparator<Point> = compareBy { (p.pos - it.pos).squaredLength }

fun <K, V1, V2, R> Map<K, V1>.mergeReduce(other: Map<K, V2>, reduce: (key: K, value1: V1?, value2: V2?) -> R): Map<K, R> =
    (this.keys + other.keys).associateWith { reduce(it, this[it], other[it]) }

fun <K> Map<K, Int>.sum(other: Map<K, Int>) = mergeReduce(other) { _, v1, v2 -> (v1 ?: 0) + (v2 ?: 0) }
fun <K> Map<K, Int>.diff(other: Map<K, Int>) = mergeReduce(other) { _, v1, v2 -> (v1 ?: 0) - (v2 ?: 0) }