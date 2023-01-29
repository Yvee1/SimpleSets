import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import org.openrndr.shape.intersections

/**
 * Returns the largest monochromatic convex polygon in O(n^3 log n) time.
 *
 * Reference
 * ---------
 * Computing optimal islands, by
 * C. Bautista-Santiago, J.M. Díaz-Báñez, D. Lara, P. Pérez-Lantero, J. Urrutia ,and I. Ventura (2011)
 *
 * @param points a list of `points` with distinct x-coordinates
 * @throws error if `points` do not have distinct x-coordinates
 */
fun computeLargestConvexIsland(points: List<Point>, obstacles: List<ConvexIsland>): ConvexIsland {
    val stripeData = StripeData(points)
    val contourObstacles = obstacles.map { ShapeContour.fromPoints(it.points.map { it.pos }, true) }

    fun compatible(q: Point, r: Point, s: Point) = orientation(q.pos, r.pos, s.pos) != Orientation.LEFT

    data class Edge(
        val u: Point, val v: Point, var weight: Int? = null, var prev: Edge? = null,
        var weightCol: Int? = null, var prevCol: Edge? = null
    )

    fun computeLargestConvexIslandAt(p: Point): ConvexIsland {
        val t = p.type

        val P = points
            .filter { it.pos.x <= p.pos.x && it != p && it.type == t }
            .sortedWith(clockwiseAround(p))

        if (P.isEmpty()) return ConvexIsland(listOf(p), 1)

        val groupedP = buildList(P.size) {
            var currentGroup: MutableList<Point> = mutableListOf(P[0])

            for (i in 1 until P.size) {
                if (orientation(p.pos, P[i].pos, P[i - 1].pos) == Orientation.STRAIGHT) {
                    currentGroup.add(P[i])
                } else {
                    add(currentGroup.toList())
                    currentGroup = mutableListOf(P[i])
                }
            }
            add(currentGroup)
        }

        val Ai = P.associateWith { mutableListOf<Point>() }
        val Bi = P.associateWith { mutableListOf<Point>() }
        val edges = mutableMapOf<Pair<Point, Point>, Edge>()

        for (i in P.indices) {
            for (j in i + 1 until P.size) {
                val tri = stripeData.triangle(p, P[i], P[j])

                val contour = ShapeContour.fromPoints(listOf(p.pos, P[i].pos, P[j].pos), true)
                val intersects = contourObstacles.any { contour.overlaps(it) }

                // Triangle p P[i] P[j] should contain no points of a type other than t.
                if (!tri.hasType(t) || intersects) continue

                // We store edge P[i] -> P[j], add P[i] to the predecessors of P[j], and add P[j] to successors of P[i].
                edges[P[i] to P[j]] = Edge(P[i], P[j], if (i == 0) tri.get0(t) else null) // i == 0: base case of DP
                Ai[P[j]]!!.add(P[i])
                Bi[P[i]]!!.add(P[j])

                // If p, P[i], and P[j] are collinear, then add an edge in the other direction as well.
                if (orientation(p.pos, P[i].pos, P[j].pos) == Orientation.STRAIGHT) {
                    edges[P[j] to P[i]] = Edge(P[j], P[i], if (i == 0) tri.get0(t) else null)
                    Ai[P[i]]!!.add(P[j])
                    Bi[P[j]]!!.add(P[i])
                }
            }
        }

        // If there are no edges, then check any segments
        if (edges.isEmpty()) {
            for (q in P){
                val seg = stripeData.segment.getE(q to p)
                val contour = LineSegment(p.pos, q.pos).contour
                val intersects = contourObstacles.any { contour.intersections(it).isNotEmpty() }
                if (seg.hasType(t) && !intersects){
                    return ConvexIsland(listOf(q, p), 2 + seg.get0(t))
                }
            }
            return ConvexIsland(listOf(p), 1)
        }

        for ((pi, l) in Ai) {
            l.sortWith(clockwiseAround(pi))
        }
        for ((pi, l) in Bi) {
            l.sortWith(clockwiseAround(pi))
        }
        fun updateB(pi: Point, A: List<Point>, B: List<Point>) {
            // Goal: compute weights of edges from pi to points in B

            // z[l] is the smallest integer such that edges[A[z[l]] to pi]!!.weight!! is maximum among edges
            // edges[A[1] to pi]!!.weight!!   ...  edges[A[l] to pi]!!.weight!!
            val z = buildList {
                for (l in A.indices) {
                    if (l == 0) {
                        add(0)
                    }
                    else if (edges[A[l] to pi]!!.weight!! > edges[A[last()] to pi]!!.weight!!) {
                        add(l)
                    } else {
                        add(last())
                    }
                }
            }

            // For each m, s[m] is the largest integer such that A[s[m]] and B[m] are p-compatible.
            // So it is the index of the last incoming edge of pi that B[m] can be 'joined' to.
            val s = buildList {
                var k = 0
                for (m in B.indices) {
                    if (A.isEmpty()) {
                        add(-1)
                        continue
                    }
                    var found = false
                    while (k < A.size) {
                        if (compatible(A[k], pi, B[m])) {
                            found = true
                            k++
                        } else {
                            break
                        }
                    }
                    if (found) {
                        k--
                        add(k)
                    } else {
                        add(-1)
                    }
                    if (k == A.size) {
                        k--
                    }
                }
            }

            for (m in B.indices) {
                if (s[m] == -1) {
                    val w = stripeData.triangle(p, pi, B[m]).get0(t)
                    edges[pi to B[m]]!!.weight = w
                } else {
                    val w = edges[A[z[s[m]]] to pi]!!.weight!! +
                            stripeData.triangle(p, pi, B[m]).get0(t) - 2 - stripeData.segment.getE(pi to p).get0(t)
                    val prev = edges[A[z[s[m]]] to pi]!!
                    edges[pi to B[m]]!!.weight = w
                    edges[pi to B[m]]!!.prev = prev
                }
            }
        }

        for (group in groupedP) {
            if (group.size == 1) {
                val pi = group[0]
                val A = Ai[pi]!!
                val B = Bi[pi]!!
                updateB(pi, A, B)
            } else {
                for (pi in group) {
                    val A = Ai[pi]!!
                    val B = Bi[pi]!!
                    updateB(pi, A.filter { it !in group }, B)
                }
            }
        }

        val maxEdge = edges.maxBy { it.value.weight!! }.value

        fun backtrack(e: Edge): List<Point> {
            return if (e.prev == null) {
                listOf(p, e.u, e.v)
            } else {
                backtrack(e.prev!!) + listOf(e.v)
            }
        }

        return ConvexIsland(backtrack(maxEdge), maxEdge.weight!!)
    }

    return points
        .map { computeLargestConvexIslandAt(it) }
        .maxWithOrNull(compareBy({ it.weight }, { it.points.size })) ?: ConvexIsland.EMPTY
}

fun computeIslandPartition(points: List<Point>): List<ConvexIsland> {
    return buildList {
        val islands = this@buildList
        var pts = points
        while (pts.isNotEmpty()){
            val island = computeLargestConvexIsland(pts, islands)
            if (island == ConvexIsland.EMPTY) break
            add(island)
            pts = pts.filter { it !in island.points && (island.weight <= island.points.size || it.pos !in ShapeContour.fromPoints(island.points.map { it.pos }, true)) }
        }
    }
}

fun Map<Int, Int>.hasType(t: Int) = keys.all { it == t || get(it) == 0 }

fun ShapeContour.overlaps(other: ShapeContour) =
    intersections(other).isNotEmpty() || position(0.0) in other || other.position(0.0) in this
