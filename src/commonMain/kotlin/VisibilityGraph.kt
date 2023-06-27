import geometric.end
import geometric.overlaps
import geometric.start
import islands.Island
import islands.visibilityIntervals
import org.openrndr.math.Vector2
import org.openrndr.shape.*

sealed class Vertex(val edges: MutableList<VisEdge>)

sealed class PointVertex(val pos: Vector2, edges: MutableList<VisEdge> = mutableListOf()): Vertex(edges) {
    override fun hashCode() = pos.hashCode()
    override fun equals(other: Any?) = if (other is PointVertex) pos == other.pos else false
    override fun toString() = pos.toString()
}

class IslandVertex(val island: Int, edges: MutableList<VisEdge> = mutableListOf()): Vertex(edges) {
    override fun hashCode() = island.hashCode()
    override fun equals(other: Any?) = if (other is IslandVertex) island == other.island else false
    override fun toString() = island.toString()
}

class TangentVertex(pos: Vector2, edges: MutableList<VisEdge> = mutableListOf()): PointVertex(pos, edges)

class ChainVertex(pos: Vector2, val islands: List<Int>, edges: MutableList<VisEdge> = mutableListOf()): PointVertex(pos, edges)

data class Endpoint(val vertex: Vertex, val position: Vector2)

open class VisEdge(val v: Vertex, val w: Vertex, val contour: ShapeContour) {
    val start by lazy { Endpoint(v, if (contour.empty && v is PointVertex) v.pos else contour.start) }
    val end by lazy { Endpoint(w, if (contour.empty && v is PointVertex) v.pos else contour.end) }
    fun theOther(u: Vertex) = if (u == v) w else if (u == w) v else error("Edge $this does not contain vertex $u")
    fun replace(u: Vertex, uNew: Vertex) = VisEdge(uNew, theOther(u), contour)
}

class ChainEdge(v: Vertex, w: Vertex, contour: ShapeContour): VisEdge(v, w, contour)

data class Graph(val islands: List<Island>, val obstacles: List<Island>, val voronoiCells: List<ShapeContour>,
                 val applySmoothing: Boolean = false) {
    val vertices: MutableList<Vertex> = mutableListOf()
    val edges: MutableList<VisEdge> = mutableListOf()
    val islandVertices = List(islands.size) { createIslandVertex(it) }
    val islandTangentVertices = List(islands.size) { mutableListOf<TangentVertex>() }
    val chainVertices = mutableListOf<ChainVertex>()
    val smallerIslands = islands.map { it.scale(0.975) }
    val largerIslands = islands.map { it.scale(1.1) }
    val smallerObstacles = obstacles.map { it.scale(0.975) }

    init {
        createAdjacentIslandEdges()
        createVoronoiChains()
        mergeChainVertices()
        connectIslandsAndVertices()
        createTangents()
        mergeTangentVertices()
        createObstacleBoundaryEdges()
    }

    private fun createAdjacentIslandEdges() {
        // Create 0-length edges between adjacent islands
        for (i1 in islands.indices) {
            for (i2 in i1 + 1 until islands.size) {
                if (islands[i1].type != islands[i2].type
                    || !islands[i1].contour.bounds.intersects(islands[i2].contour.bounds)) continue
                if (islands[i1].contour.intersections(islands[i2].contour).isNotEmpty()) {
                    createEdge(islandVertices[i1], islandVertices[i2], ShapeContour.EMPTY, checkEmpty = false)
                }
            }
        }
    }

    private fun createVoronoiChains() {
        // Create chain vertices and pathways using the provided voronoi cells.
        for (i1 in obstacles.indices) {
            val ovc1 = voronoiCells[i1].intersection(obstacles[i1].contour.shape).contours.first()
            for (i2 in i1 + 1 until obstacles.size) {
                val ovc2 = voronoiCells[i2].intersection(obstacles[i2].contour.shape).contours.first()
                if (!ovc1.bounds.intersects(ovc2.bounds)) continue
                val cellIntersections = ovc1.intersections(ovc2)
                if (cellIntersections.isEmpty()) continue
                if (cellIntersections.size == 1) {
                    createChainVertex(i1, i2, cellIntersections[0].position); continue
                }
                val sortedTs = cellIntersections.mapTo(mutableSetOf()) { it.a.contourT }.sorted()
                val splitIndex =
                    (sortedTs + sortedTs.first()).zipWithNext { a, b -> b - a }.withIndex().filter { it.value > 0.3 }
                        .maxByOrNull { it.value }?.index
                val ts = if (splitIndex == null) sortedTs else sortedTs.subList(
                    splitIndex + 1,
                    sortedTs.size
                ) + sortedTs.subList(0, splitIndex + 1)

                val pieces = ts.zipWithNext().filter { (a, b) -> b - a > 0 }.map { (a, b) ->
                    if (b > a)
                        ovc1.sub(a, b)
                    else
                        ovc1.sub(b, 1.0) + ovc1.sub(0.0, a)
                }
                if (pieces.isEmpty()) continue
                var border = pieces[0]
                for (i in 1 until pieces.size) {
                    border += pieces[i]
                }
                val intersectsIsland = largerIslands.any { island ->
                    island.contour.intersections(border).isNotEmpty()
                }
                val cv1 = createChainVertex(i1, i2, border.position(0.0))
                val cv2 = createChainVertex(i1, i2, border.position(1.0))
                if (!intersectsIsland) {
                    createChainEdge(cv1, cv2, border)
                }
            }
        }
    }

    private fun connectIslandsAndVertices() {
        // Connect the vertices
        islands.indices.toList().asSequence().flatMap { i1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()

            val v1 = islandVertices[i1]
            for (i2 in i1 + 1 until islands.size) {
                if (islands[i1].type != islands[i2].type) continue
                val v2 = islandVertices[i2]
                val segment = freeSegment(v1, v2)
                if (segment != null) {
                    newEdges.add(v1 to v2 to segment.contour)
                }
            }
            for (v2 in chainVertices) {
                val segment = freeSegment(v1, v2)
                if (segment != null) {
                    newEdges.add(v1 to v2 to segment.contour)
                }
            }
            newEdges
        }.forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }

        chainVertices.asSequence().flatMap { v1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()
            for (v2 in chainVertices) {
                val segment = freeSegment(v1, v2)
                if (segment != null) {
                    newEdges.add(v1 to v2 to segment.contour)
                }
            }
            newEdges
        }.forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }
    }

    private fun createTangents() {
        // Create chainVertex--circle tangents
        chainVertices.asSequence().flatMap { v1 ->
            val tangentEdges = mutableListOf<Pair<Pair<ChainVertex, Int>, LineSegment>>()
            for (i2 in obstacles.indices) {
                for (clearanceCircle in obstacles[i2].circles) {
                    clearanceCircle.tangents(v1.pos)
                        .toList()
                        .map { tp -> LineSegment(v1.pos, tp) }
                        .filter { ls ->
                            ls.end.x.isFinite() && ls.end.y.isFinite() && ls.squaredLength > 0.1 &&
                                    smallerObstacles.none { obstacle -> obstacle.contour.overlaps(ls.contour) }
                        }
                        .forEach { ls ->
                            tangentEdges.add(v1 to i2 to ls)
                        }
                }
            }
            tangentEdges
        }.forEach { (verts, ls) ->
            val (v1, i2) = verts
            val tv = createTangentVertex(i2, ls.end)
            createEdge(v1, tv, ls.contour)
        }

        islands.indices.asSequence().flatMap { i1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()
            val v1 = islandVertices[i1]
            for (i2 in islands.indices) {
                if (i1 == i2) continue

                // Create island--obstacle tangents
                islands[i1].visibilityIntervals(obstacles[i2])
                    .asSequence()
                    .flatMap { listOf(it.start, it.end) }
                    .map { bt ->
                        bt.end?.let { LineSegment(bt.start.position, it.position) }
                    }
                    .filterNotNull()
                    .filter { ls ->
                        !smallerIslands[i1].contour.overlaps(ls.contour) &&
                                islands.except(i1).none { obstacle ->
                                    obstacle.contour.overlaps(ls.contour)
                                } &&
                                smallerObstacles.except(i1).none { obstacle ->
                                    var obstacleShape = obstacle.contour.shape
                                    if (obstacleShape.bounds.intersects(obstacles[i1].contour.bounds)) {
                                        obstacleShape = obstacleShape.difference(obstacles[i1].contour.shape)
                                    }
                                    obstacleShape.contours.any { it.overlaps(ls.contour) }
                                }
                    }.forEach { ls ->
                        val tv = createTangentVertex(i2, ls.end)
                        newEdges.add(v1 to tv to ls.contour)
                    }

                // Create obstacle--obstacle tangents
                if (i2 > i1) continue
                for (c1 in obstacles[i1].circles) {
                    for (c2 in obstacles[i2].circles) {
                        (c1.tangents(c2, isInner = false) + c1.tangents(c2, isInner = true))
                            .map { LineSegment(it.first, it.second) }
                            .filter { ls -> smallerObstacles.none { it.contour.overlaps(ls.contour )} }
                            .forEach { ls ->
                                val tv1 = createTangentVertex(i1, ls.start)
                                val tv2 = createTangentVertex(i2, ls.end)
                                newEdges.add(tv1 to tv2 to ls.contour)
                            }
                    }
                }
            }
            newEdges
        }.forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }
    }

    private fun createObstacleBoundaryEdges() {
        for ((i, tangentVertices) in islandTangentVertices.withIndex()) {
            val verts = tangentVertices + chainVertices.filter { it.islands.size == 2 && i in it.islands }
            val island = obstacles[i]
            if (verts.size < 2) continue
            val tValues = verts
                .map { it to (island.contour.nearest(it.pos).contourT) }
                .sortedBy { it.second }
            tValues.zipWithNext { (v1, t1), (v2, t2) ->
                val c = island.contour.sub(t1, t2)
                val intersects = smallerObstacles.except(i).any { c.overlaps(it.contour) }
                if (!intersects) createEdge(v1, v2, c)
            }
            val (lastV, lastT) = tValues.last()
            val (firstV, firstT) = tValues.first()
            val lastContour = island.contour.sub(lastT, 1.0) + island.contour.sub(0.0, firstT)
            val intersects = smallerObstacles.except(i).any { lastContour.overlaps(it.contour) }
            if (!intersects) createEdge(lastV, firstV, lastContour)
        }
    }

    private fun freeSegment(v1: IslandVertex, v2: IslandVertex): LineSegment? {
        if (islands[v1.island].contour.length > islands[v2.island].contour.length) return freeSegment(v2, v1)
        val ls = islands[v1.island].contour.equidistantPositions(5000 + islands[v1.island].contour.length.toInt() + islands[v2.island].contour.length.toInt()).map { p1 ->
            p1 to islands[v2.island].contour.nearest(p1).position
//            islands[v2.island].contour.equidistantPositions(50 + islands[v2.island].contour.length.toInt() / 2).map { p2 ->
//                p1 to p2
//            }
        }.minBy { (p1, p2) -> (p2 - p1).squaredLength }.let { (p1, p2) -> LineSegment(p1, p2) }

        val overlapsEndIsland = listOf(smallerIslands[v1.island], smallerIslands[v2.island]).any {
            ls.contour.overlaps(it.contour)
        }
        if (overlapsEndIsland) return null

        val overlapsObstacle = islands.zip(smallerObstacles).except(v1.island, v2.island).any { (island, obstacle) ->
            var obstacleShape = obstacle.contour.shape
            if (obstacleShape.bounds.intersects(obstacles[v1.island].contour.bounds)) {
                obstacleShape = obstacleShape.difference(obstacles[v1.island].contour.shape)
            }
            if (obstacleShape.bounds.intersects(obstacles[v2.island].contour.bounds)) {
                obstacleShape = obstacleShape.difference(obstacles[v2.island].contour.shape)
            }
            obstacleShape.contours.any { it.overlaps(ls.contour) } ||
                    island.contour.overlaps(ls.contour)
        }
        return if (overlapsObstacle) null else ls
    }

    private fun freeSegment(v1: ChainVertex, v2: ChainVertex): LineSegment? {
        val ls = LineSegment(v1.pos, v2.pos)
        val overlapsObstacle = smallerObstacles.any { obstacle ->
            ls.contour.intersections(obstacle.contour).isNotEmpty()
        }
        return if (overlapsObstacle) null else ls
    }

    private fun freeSegment(v1: IslandVertex, v2: ChainVertex): LineSegment? {
        val p1 = islands[v1.island].contour.nearest(v2.pos).position
        val ls = LineSegment(p1, v2.pos)
        if (smallerIslands[v1.island].contour.overlaps(ls.contour)) return null
        val intersectsObstacle = islands.zip(smallerObstacles).except(v1.island).any { (island, obstacle) ->
            var obstacleShape = obstacle.contour.shape
            if (obstacleShape.bounds.intersects(obstacles[v1.island].contour.bounds)) {
                obstacleShape = obstacleShape.difference(obstacles[v1.island].contour.shape)
            }
            val intersectsObstacle = if (!ls.contour.bounds.intersects(obstacleShape.bounds)) false
                else intersection(ls.contour, obstacleShape).contours.firstOrNull()?.let { it.length > 0.01 } ?: false
            val overlapsIsland = if (!ls.contour.bounds.intersects(island.contour.bounds)) false else ls.contour.overlaps(island.contour)
            intersectsObstacle || overlapsIsland
        }
        if (intersectsObstacle) return null
        return ls
    }

    private fun <T> List<T>.except(vararg ints: Int): List<T> =
        withIndex().filter { it.index !in ints }.map { it.value }

    private fun createIslandVertex(island: Int): IslandVertex {
        val v = IslandVertex(island)
        vertices.add(v)
        return v
    }

    private fun createTangentVertex(island: Int, pos: Vector2): TangentVertex {
        val v = TangentVertex(pos)
        vertices.add(v)
        islandTangentVertices[island].add(v)
        return v
    }

    private fun createChainVertex(i1: Int, i2: Int, position: Vector2): ChainVertex {
        val v = ChainVertex(position, listOf(i1, i2))
        vertices.add(v)
        chainVertices.add(v)
        createBoundaryEdge(v, i1)
        createBoundaryEdge(v, i2)
        return v
    }

    private fun createBoundaryEdge(v: ChainVertex, i: Int) {
        val w = islandVertices[i]
        val ls = freeSegment(w, v) ?: return
        createEdge(w, v, ls.contour)
        vertices.add(w)
    }

    private fun createEdge(v: Vertex, w: Vertex, c: ShapeContour, checkEmpty: Boolean = true) {
        if (checkEmpty && c.empty) return
        val e = VisEdge(v, w, c)
        v.edges.add(e)
        w.edges.add(e)
        edges.add(e)
    }

    private fun createChainEdge(cv1: ChainVertex, cv2: ChainVertex, pathway: ShapeContour) {
        if (pathway.empty) return
        val e = ChainEdge(cv1, cv2, pathway)
        cv1.edges.add(e)
        cv2.edges.add(e)
        edges.add(e)
    }

    private fun mergeChainVertices() {
        for (u in vertices) {
            if (u !is ChainVertex) continue
            for (v in vertices) {
                if (v !is ChainVertex) continue
                if (u.pos.squaredDistanceTo(v.pos) < 1E-6) {
                    createChainEdge(u, v, LineSegment(u.pos, v.pos).contour)
                }
            }
        }
    }

    private fun mergeTangentVertices() {
        for (l in islandTangentVertices) {
            val newVertices = mutableListOf<TangentVertex>()
            val oldVertices = mutableListOf<TangentVertex>()
            for (ui in l.indices) {
                for (vi in ui + 1 until l.size) {
                    val u = l[ui]
                    val v = l[vi]
                    if (u.pos.squaredDistanceTo(v.pos) < 1E-6) {
                        // Prepare to remove u and v
                        oldVertices.add(u)
                        oldVertices.add(v)

                        // Create new vertex and add it
                        val new = TangentVertex(u.pos)
                        newVertices.add(new)

                        // Create new edges from those incident to u and v.
                        val edgesFromNew = u.edges.filter { it.theOther(u) != v }.map { it.replace(u, new) } +
                                v.edges.filter { it.theOther(v) != u }.map { it.replace(v, new) }

                        // Add edges to new
                        new.edges.addAll(edgesFromNew)
                        // Add edges to other
                        edgesFromNew.forEach { e ->
                            e.theOther(new).edges.add(e)
                        }
                        // Add edges to edge list
                        edges.addAll(edgesFromNew)

                        // Remove edges from old vertices and edge list
                        u.edges.forEach { e ->
                            e.theOther(u).edges.remove(e)
                        }
                        v.edges.forEach { e ->
                            e.theOther(v).edges.remove(e)
                        }
                        edges.removeAll(u.edges)
                        edges.removeAll(v.edges)
                    }
                }
            }
            // Actually add and remove vertices
            l.removeAll(oldVertices)
            vertices.removeAll(oldVertices)
            l.addAll(newVertices)
            vertices.addAll(newVertices)
        }
    }

    fun dijkstra(source: Vertex): Pair<Map<Vertex, Double>, Map<Vertex, VisEdge?>> {
        val distances = vertices.associateWith { Double.POSITIVE_INFINITY }.toMutableMap()
        distances[source] = 0.0
        val previous: MutableMap<Vertex, VisEdge?> = vertices.associateWith { null }.toMutableMap()
        val Q = PriorityQueue<Vertex>(compareBy { distances[it]!! })

        Q.add(source)
        while (Q.isNotEmpty()) {
            val u = Q.poll()
            if (u != source && u is IslandVertex) continue
            for (e in u.edges) {
                val v = e.theOther(u)
                val newDist = distances[u]!! + e.contour.length
                if (newDist < (distances[v] ?: continue)) {
                    distances[v] = newDist
                    previous[v] = e
                    Q.remove(v)
                    Q.add(v)
                }
            }
        }

        return distances to previous
    }

    fun islandToIsland(island1: Int, island2: Int): Pair<Bridge, Double>? {
        val v = islandVertices[island1]
        val (distances, previous) = dijkstra(v)
        val w = islandVertices[island2]
        if (distances[w]!!.isInfinite()) return null
        val edges = trace(previous, islandVertices[island2])
        return Bridge(island1, island2, edgesToContour(edges, applySmoothing)) to distances[w]!!
    }
//        islandVertices[island1].map { v ->
//            val (distances, previous) = dijkstra(v)
//            val closest = islandVertices[island2].map {
//                it to distances[it]!!
//            }.minBy { it.second }
//            previous to closest
//        }.filter { it.second.second.isFinite() }
//            .minByOrNull { it.second.second }
//            ?.let { (previous, closest) ->
//                val edges = trace(previous, closest.first)
//                Bridge(island1, island2, edgesToContour(edges)) to closest.second
//            }

    // Prim's algorithm
    fun spanningTrees(): List<Bridge> {
        val costs = MutableList(islands.size) { Double.POSITIVE_INFINITY }
        val bridges = MutableList<Bridge?>(islands.size) { null }
        val left = PriorityQueue<Int>(compareBy { costs[it] })
        left.addAll(islands.indices)
        while (left.isNotEmpty()) {
            val i1 = left.poll()
            for (i2 in islands.indices) {
                if (i1 == i2 || islands[i1].type != islands[i2].type || i2 !in left) continue
                val (bridge, d) = islandToIsland(i1, i2) ?: continue
                if (d >= costs[i2]) continue
                costs[i2] = d
                bridges[i2] = bridge
                left.remove(i2)
                left.add(i2)
            }
        }
        return bridges.filterNotNull()
    }
}

data class Bridge(val island1: Int, val island2: Int, val contour: ShapeContour)

fun trace(previous: Map<Vertex, VisEdge?>, v: Vertex): List<VisEdge> {
    val prev = previous[v]
    return if (prev != null) {
        listOf(prev) + trace(previous, prev.theOther(v))
    } else {
        emptyList()
    }
}

fun edgesToContour(edges: List<VisEdge>, applySmoothing: Boolean): ShapeContour {
    val nonEmpty = edges.filter { it.contour.segments.isNotEmpty() }
    if (nonEmpty.isEmpty()) return ShapeContour.EMPTY
    val contours = mutableListOf(nonEmpty.first().contour)
    val sharpCorners = if (nonEmpty.first().end.vertex is ChainVertex) mutableListOf(0) else mutableListOf()
    for (i in 1 until nonEmpty.size) {
        val e = nonEmpty[i]
        val edgeContour = listOf(e.contour, e.contour.reversed).minBy {
            it.start.squaredDistanceTo(contours[i-1].end)
        }
        val end = listOf(e.start, e.end).minBy { edgeContour.end.squaredDistanceTo(it.position) }
        contours.add(edgeContour)
        if (end.vertex is ChainVertex) {
            sharpCorners.add(i)
        }
    }
    val pieces = if (applySmoothing) chaikin(contours, sharpCorners, 5) else contours
    return pieces.reduce { acc, c -> acc + c }
}

fun chaikin(pieces: List<ShapeContour>, sharpCorners: List<Int>, n: Int): List<ShapeContour> {
    if (n <= 0) return pieces
    val newPieces = mutableListOf<ShapeContour>()
    val newSharpCorners = mutableListOf<Int>()
    var j = 0
    for (i in pieces.indices) {
        val startCut = i - 1 in sharpCorners
        val endCut = i in sharpCorners
        val p = pieces[i]
        val length = p.length
        val t1 = if (startCut) p.tForLength(0.25 * length) else 0.0
        val t2 = if (endCut) p.tForLength(0.75 * length) else 1.0
        val np = p.sub(t1, t2)
        newPieces.add(np)
        if (endCut) {
            val next = LineSegment(np.end, pieces[i+1].position(pieces[i+1].tForLength(0.25 * pieces[i+1].length))).contour
            newPieces.add(next)
            newSharpCorners.add(j)
            newSharpCorners.add(j + 1)
            j++
        }
        j++
    }
    return chaikin(newPieces, newSharpCorners, n - 1)
}