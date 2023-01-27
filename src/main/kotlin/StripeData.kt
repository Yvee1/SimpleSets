import kotlin.math.abs
import kotlin.math.atan2

fun <K> Map<K, Int>.get0(key: K) = getOrDefault(key, 0)
fun <K> Map<K, Map<Int, Int>>.getE(key: K) = getOrDefault(key, emptyMap())

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
            for (p in xSorted){
                val l = xSorted
                    .filter { it.pos.x <= p.pos.x && it != p }
                    .sortedWith(clockwiseAround(p))
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
                if (orient == Orientation.RIGHT)
                    mapOf(y.type to 1).sum(segment.getE(x to y)).sum(segment.getE(y to z))
                else segment.getE(x to z))
        val boundary = segment.getE(x to y)
            .sum(segment.getE(y to z))
            .sum(segment.getE(x to z))
            .sum(mapOf(p1.type to 1).sum(mapOf(p2.type to 1)).sum(mapOf(p3.type to 1)))
        return interior.sum(boundary)
    }
}

fun clockwiseAround(p: Point) = Comparator<Point> { p1, p2 ->
        val a1 = atan2(p.pos.y - p1.pos.y, p.pos.x - p1.pos.x)
        val a2 = atan2(p.pos.y - p2.pos.y, p.pos.x - p2.pos.x)
        if (a1 < a2 - PRECISION){
            -1
        } else if (a1 > a2 + PRECISION) {
            1
        } else {
            0
        }
    }.thenBy { (p.pos - it.pos).squaredLength }

fun <K, V1, V2, R> Map<K, V1>.mergeReduce(other: Map<K, V2>, reduce: (key: K, value1: V1?, value2: V2?) -> R): Map<K, R> =
    (this.keys + other.keys).associateWith { reduce(it, this[it], other[it]) }

fun <K> Map<K, Int>.sum(other: Map<K, Int>) = mergeReduce(other) { _, v1, v2 -> (v1 ?: 0) + (v2 ?: 0) }
fun <K> Map<K, Int>.diff(other: Map<K, Int>) = mergeReduce(other) { _, v1, v2 -> (v1 ?: 0) - (v2 ?: 0) }