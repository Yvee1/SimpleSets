package patterns

import geometric.Orientation
import PartitionInstance
import org.openrndr.shape.*
import geometric.orientation
import geometric.overlaps

/**
 * Returns the largest monochromatic convex polygon in O(n^3 log n) time.
 *
 * Reference
 * ---------
 * Computing optimal islands, by
 * C. Bautista-Santiago, J.M. Díaz-Báñez, D. Lara, P. Pérez-Lantero, J. Urrutia ,and I. Ventura (2011)
 *
 * @param uncovered a list of `points` with distinct x-coordinates
 * @throws error if `points` do not have distinct x-coordinates
 */
fun PartitionInstance.largestIsland(uncovered: List<Point> = points, obstacles: List<Pattern> = emptyList()): Island {
    if (density <= 0) return Island.EMPTY
    val uncoveredStripeData = StripeData(uncovered)
    val colored = uncovered.groupBy {
        it.type
    }
    return colored.toList().asSequence().map { (_, pts) ->
        val freeSpace: Shape? =
            if (density < Double.MAX_VALUE)
                pts.fold(Shape.EMPTY) { acc, x -> Circle(x.pos, density).shape.union(acc) }
            else null
        pts.asSequence()
            .map { largestIslandAt(it, uncovered, uncoveredStripeData, obstacles, freeSpace) }
            .maxWithOrNull(compareBy({ it.weight }, { -it.contour.shape.area })) ?: Island.EMPTY
    }.maxWithOrNull(compareBy({ it.weight }, { -it.contour.shape.area })) ?: Island.EMPTY
}

private fun compatible(q: Point, r: Point, s: Point) = orientation(q.pos, r.pos, s.pos) != Orientation.LEFT

private data class Edge(val u: Point, val v: Point, var weight: Int? = null, var prev: Edge? = null)

fun PartitionInstance.largestIslandAt(p: Point,
                                      uncovered: List<Point> = points,
                                      uncoveredStripeData: StripeData = stripeData,
                                      obstacles: List<Pattern> = emptyList(),
                                      providedFreeSpace: Shape? = null,
                                      recLevel: Int = 0): Island {
    val t = p.type

    val P = uncovered
        .filter { it.pos.x <= p.pos.x && it != p && it.type == t }
        .sortedWith(compareAround(p, 90.0, Orientation.RIGHT).reversed().then(awayFrom(p)))

    if (P.isEmpty()) return Island(listOf(p), 1)

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

    val freeSpace: Shape? = providedFreeSpace
        ?: if (density < Double.MAX_VALUE)
            (P + p).fold(Shape.EMPTY) { acc, x -> Circle(x.pos, density).shape.union(acc) }
        else null

    val Ai = P.associateWith { mutableListOf<Point>() }
    val Bi = P.associateWith { mutableListOf<Point>() }
    val edges = mutableMapOf<Pair<Point, Point>, Edge>()

    for (i in P.indices) {
        for (j in i + 1 until P.size) {
            val triAll = stripeData.triangle(p, P[i], P[j])
            if (!triAll.hasType(t)) continue
            val triUncov = uncoveredStripeData.triangle(p, P[i], P[j])
            val triContour = ShapeContour.fromPoints(listOf(p.pos, P[i].pos, P[j].pos), true)
            val intersects = obstacles.any { triContour.overlaps(it.contour) }
            if (intersects) continue
            val pointNearby = capsuleData.capsule.getF(P[i] to P[j])
                    || capsuleData.capsule.getF(p to P[i])
                    || capsuleData.capsule.getF(p to P[j])
            if (pointNearby) continue
            val inFreeSpace = freeSpace == null ||
                    coverRadiusTriangle(p.pos, P[i].pos, P[j].pos) <= density ||
                    triContour in freeSpace
            if (!inFreeSpace) continue

            // We store edge P[i] -> P[j], add P[i] to the predecessors of P[j], and add P[j] to successors of P[i].
            edges[P[i] to P[j]] = Edge(P[i], P[j], if (i == 0) triUncov.get0(t) else null) // i == 0: base case of DP
            Ai[P[j]]!!.add(P[i])
            Bi[P[i]]!!.add(P[j])

            // If p, P[i], and P[j] are collinear, then add an edge in the other direction as well.
            if (orientation(p.pos, P[i].pos, P[j].pos) == Orientation.STRAIGHT) {
                edges[P[j] to P[i]] = Edge(P[j], P[i], if (i == 0) triUncov.get0(t) else null)
                Ai[P[i]]!!.add(P[j])
                Bi[P[j]]!!.add(P[i])
            }
        }
    }

    // If there are no edges, then check any segments
    if (edges.isEmpty()) {
        for (q in P){
            val segAll = stripeData.segment.getE(q to p)
            val segUncov = uncoveredStripeData.segment.getE(q to p)
            val segContour = LineSegment(p.pos, q.pos).contour
            val intersects = obstacles.any { segContour.intersections(it.contour).isNotEmpty() }
            val pointNearby = capsuleData.capsule.getF(p to q)
            val inFreeSpace = freeSpace == null || segContour.length <= 2 * density
                    || segContour in freeSpace
            if (segAll.hasType(t) && !intersects && inFreeSpace && !pointNearby){
                return Island(listOf(q, p), 2 + segUncov.get0(t))
            }
        }
        return Island(listOf(p), 1)
    }

    for ((pi, l) in Ai) {
        l.sortWith(compareAround(pi, 90.0, Orientation.RIGHT).reversed().then(awayFrom(pi)))
    }
    for ((pi, l) in Bi) {
        l.sortWith(compareAround(pi, 90.0, Orientation.RIGHT).reversed().then(awayFrom(pi)))
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
                val w = uncoveredStripeData.triangle(p, pi, B[m]).get0(t)
                edges[pi to B[m]]!!.weight = w
            } else {
                val w = edges[A[z[s[m]]] to pi]!!.weight!! +
                        uncoveredStripeData.triangle(p, pi, B[m]).get0(t) - 2 -
                        uncoveredStripeData.segment.getE(pi to p).get0(t)
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

    fun trace(e: Edge): List<Point> {
        return if (e.prev == null) {
            listOf(p, e.u, e.v)
        } else {
            trace(e.prev!!) + listOf(e.v)
        }
    }

    val boundaryPoints = trace(maxEdge)
    val contour = ShapeContour.fromPoints(boundaryPoints.map { it.pos }, true)
    val pointsInLargest = (P + p).filter { it in boundaryPoints || (maxEdge.weight!! > boundaryPoints.size && it.pos in contour) }
    if (pointsInLargest.size != maxEdge.weight!!) {
        println("Island size does not match the calculated weight: ${pointsInLargest.size} != ${maxEdge.weight!!}")
    }
    val largest = Island(pointsInLargest, maxEdge.weight!!)
    if (recLevel < 2 && coverRadius(pointsInLargest.map { it.pos }) > density + 0.1) {
        return largestIslandAt(p, pointsInLargest, StripeData(pointsInLargest), obstacles, recLevel = recLevel + 1)
    }
    return largest
}

fun Map<Int, Int>.hasType(t: Int) = keys.all { it == t || get(it) == 0 }

operator fun Shape.contains(other: ShapeContour) =
    intersections(other).isEmpty() &&
            other.position(0.0) in this &&
            contours.count { it.overlaps(other) } == 1
