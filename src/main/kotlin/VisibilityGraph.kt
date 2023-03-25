import geometric.end
import geometric.overlaps
import geometric.start
import islands.Island
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.shape.*
import patterns.angleBetween
import java.util.PriorityQueue
import kotlin.math.abs

sealed class Vertex(val pos: Vector2, val edges: MutableList<VisEdge>) {
    abstract val inIsland: Boolean
    override fun hashCode() = pos.hashCode()
    override fun equals(other: Any?) = if (other is Vertex) pos == other.pos else false
    override fun toString() = pos.toString()
}

sealed class IslandVertex(val island: Int, pos: Vector2, edges: MutableList<VisEdge>): Vertex(pos, edges) {
    abstract fun pointInDir(other: Vector2): Vector2?
}

class CircleVertex(island: Int, val circle: Circle, private val clearance: Double, edges: MutableList<VisEdge> = mutableListOf())
    : IslandVertex(island, circle.center, edges) {
    override val inIsland = true
    override fun pointInDir(other: Vector2): Vector2 {
        val r = circle.radius
        return pos + (other - pos).normalized * r
    }
    val clearanceCircle by lazy { circle.copy(radius = circle.radius + clearance) }
}

val LineSegment.center get() = (start + end)/2.0

class SegmentVertex(island: Int, val lineSegment: LineSegment, edges: MutableList<VisEdge> = mutableListOf())
    : IslandVertex(island, lineSegment.center, edges) {
    override val inIsland = true
    override fun pointInDir(other: Vector2): Vector2? =
        if (abs(angleBetween(lineSegment.direction.perpendicular(), other - pos).asDegrees.let { if (it > 90) 180 - it else it }) <= 45.0) {
            pos
        } else {
            null
        }
}

class TangentVertex(pos: Vector2, edges: MutableList<VisEdge> = mutableListOf()): Vertex(pos, edges) {
    override val inIsland = false
}

class ChainVertex(pos: Vector2, val islands: List<Int>, edges: MutableList<VisEdge> = mutableListOf()): Vertex(pos, edges) {
    override val inIsland = false
}

data class Endpoint(val vertex: Vertex, val position: Vector2)

open class VisEdge(val v: Vertex, val w: Vertex, val contour: ShapeContour) {
    val start = Endpoint(v, if (contour.empty) v.pos else contour.start)
    val end = Endpoint(w, if (contour.empty) v.pos else contour.end)
    fun theOther(u: Vertex) = if (u == v) w else if (u == w) v else error("Edge $this does not contain vertex $u")
    fun replace(u: Vertex, uNew: Vertex) = VisEdge(uNew, theOther(u), contour)
}

class ChainEdge(v: Vertex, w: Vertex, contour: ShapeContour): VisEdge(v, w, contour)

data class Graph(val islands: List<Island>, val obstacles: List<Island>, val voronoiCells: List<ShapeContour>) {
    val vertices: MutableList<Vertex> = mutableListOf()
    val edges: MutableList<VisEdge> = mutableListOf()
    val islandVertices = List(islands.size) { mutableListOf<IslandVertex>() }
    val islandTangentVertices = List(islands.size) { mutableListOf<TangentVertex>() }
    val chainVertices = mutableListOf<ChainVertex>()
    val smallerIslands = islands.map { it.scale(0.975) }
    val largerIslands = islands.map { it.scale(1.1) }
    val smallerObstacles = obstacles.map { it.scale(0.975) }

    init {
        // Create vertices inside islands: circle and segment vertices.
        for ((i, island) in islands.withIndex()) {
            for (circle in island.circles) {
                createCircleVertex(i, circle)
            }
            for (segment in island.segments) {
                createSegmentVertex(i, segment)
            }
        }

        // Create 0-length edges from
        for (i1 in islands.indices) {
            for (i2 in i1 + 1 until islands.size) {
                if (islands[i1].type != islands[i2].type
                    || !islands[i1].contour.bounds.intersects(islands[i2].contour.bounds)) continue
                if (islands[i1].contour.intersections(islands[i2].contour).isNotEmpty()) {
                    createEdge(islandVertices[i1].first(), islandVertices[i2].first(), ShapeContour.EMPTY, checkEmpty = false)
                }
            }
        }

        // Create chain vertices and pathways using the provided voronoi cells.
        for (i1 in obstacles.indices) {
            for (i2 in i1 + 1 until obstacles.size) {
                val ovc1 = voronoiCells[i1].intersection(obstacles[i1].contour.shape).contours.first()
                val ovc2 = voronoiCells[i2].intersection(obstacles[i2].contour.shape).contours.first()

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

        mergeChainVertices()

        // Connect the vertices
        islands.indices.toList().parallelStream().flatMap { i1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()
            for (i2 in i1 + 1 until islands.size) {
                if (islands[i1].type != islands[i2].type) continue
                for (v1 in islandVertices[i1]) {
                    for (v2 in islandVertices[i2]) {
                        val segment = freeSegment(v1, v2)
                        if (segment != null) {
                            newEdges.add(v1 to v2 to segment.contour)
                        }
                    }
                }
            }
            for (v1 in islandVertices[i1]) {
                for (v2 in chainVertices) {
                    val segment = freeSegment(v1, v2)
                    if (segment != null) {
                        newEdges.add(v1 to v2 to segment.contour)
                    }
                }
            }
            newEdges.stream()
        }.toList().forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }

        chainVertices.parallelStream().flatMap { v1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()
            for (v2 in chainVertices) {
                val segment = freeSegment(v1, v2)
                if (segment != null) {
                    newEdges.add(v1 to v2 to segment.contour)
                }
            }
            newEdges.stream()
        }.toList().forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }

        // Create chainVertex--circle tangents
        chainVertices.parallelStream().flatMap { v1 ->
            val tangentEdges = mutableListOf<Pair<Pair<ChainVertex, Int>, LineSegment>>()
            for (i2 in islands.indices) {
                for (v2 in islandVertices[i2]) {
                    if (v2 !is CircleVertex) continue
                    v2.clearanceCircle.tangents(v1.pos)
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
            tangentEdges.stream()
        }.toList().forEach { (verts, ls) ->
            val (v1, i2) = verts
            val tv = createTangentVertex(i2, ls.end)
            createEdge(v1, tv, ls.contour)
        }

        islands.indices.toList().parallelStream().flatMap { i1 ->
            val newEdges = mutableListOf<Pair<Pair<Vertex, Vertex>, ShapeContour>>()
            for (i2 in islands.indices) {
                if (i1 == i2) continue
                for (v1 in islandVertices[i1]) {
                    for (v2 in islandVertices[i2]) {
                        if (v2 !is CircleVertex) continue

                        // Create point--circle tangents
                        v2.clearanceCircle.tangents(v1.pos)
                            .toList()
                            .mapNotNull { tp -> v1.pointInDir(tp)?.let { LineSegment(it, tp) } }
                            .filter { ls ->
                                ls.end.x.isFinite() && ls.end.y.isFinite() &&
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
                            }
                            .forEach { ls ->
                                val tv = createTangentVertex(i2, ls.end)
                                newEdges.add(v1 to tv to ls.contour)
                            }

                        // Create circle--circle tangents
                        if (v1 !is CircleVertex || i2 > i1) continue
                        (v1.clearanceCircle.tangents(v2.clearanceCircle, isInner = false) + v1.clearanceCircle.tangents(
                            v2.clearanceCircle,
                            isInner = true
                        ))
                            .map { LineSegment(it.first, it.second) }
                            .filter { ls -> smallerObstacles.none { it.contour.overlaps(ls.contour) } }
                            .forEach { ls ->
                                val tv1 = createTangentVertex(i1, ls.start)
                                val tv2 = createTangentVertex(i2, ls.end)
                                newEdges.add(tv1 to tv2 to ls.contour)
                            }
                    }
                }
            }
            newEdges.stream()
        }.toList().forEach { (verts, contour) ->
            val (v1, v2) = verts
            createEdge(v1, v2, contour)
        }

        mergeTangentVertices()

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
        val bp1 = v1.pointInDir(v2.pos)
        val bp2 = v2.pointInDir(v1.pos)
        if (bp1 == null || bp2 == null) return null
        val ls = LineSegment(bp1, bp2)
        val overlapsEndIsland = listOf(smallerIslands[v1.island], smallerIslands[v2.island]).any {
            ls.contour.overlaps(it.contour)
        }
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
        return if (overlapsEndIsland || overlapsObstacle) null else ls
    }

    private fun freeSegment(v1: ChainVertex, v2: ChainVertex): LineSegment? {
        val ls = LineSegment(v1.pos, v2.pos)
        val overlapsObstacle = smallerObstacles.any { obstacle ->
            ls.contour.intersections(obstacle.contour).isNotEmpty()
        }
        return if (overlapsObstacle) null else ls
    }

    private fun freeSegment(v1: IslandVertex, v2: ChainVertex): LineSegment? {
        val p1 = v1.pointInDir(v2.pos) ?: return null
        val ls = LineSegment(p1, v2.pos)
        if (smallerIslands[v1.island].contour.overlaps(ls.contour)) return null
        val intersectsObstacle = islands.zip(smallerObstacles).except(v1.island).any { (island, obstacle) ->
            var obstacleShape = obstacle.contour.shape
            if (obstacleShape.bounds.intersects(obstacles[v1.island].contour.bounds)) {
                obstacleShape = obstacleShape.difference(obstacles[v1.island].contour.shape)
            }
            intersection(ls.contour, obstacleShape).contours.firstOrNull()?.let { it.length > 0.01 } ?: false ||
                    ls.contour.overlaps(island.contour)
        }
        if (intersectsObstacle) return null
        return ls
    }

    private fun obstaclesOrIslands(vararg ints: Int): List<Island> =
        ints.map { smallerIslands[it] } + smallerObstacles.except(*ints)

    private fun <T> List<T>.except(vararg ints: Int): List<T> =
        withIndex().filter { it.index !in ints }.map { it.value }

    private fun createCircleVertex(island: Int, circle: Circle) {
        val v = CircleVertex(island, circle, obstacles[island].circles.first().radius - circle.radius)
        vertices.add(v)
        islandVertices[island].add(v)
    }

    private fun createSegmentVertex(island: Int, segment: LineSegment) {
        val v = SegmentVertex(island, segment)
        vertices.add(v)
        islandVertices[island].add(v)
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
        return v
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

    fun islandToIsland(island1: Int, island2: Int) =
        islandVertices[island1].map { v ->
            val (distances, previous) = dijkstra(v)
            val closest = islandVertices[island2].map {
                it to distances[it]!!
            }.minBy { it.second }
            previous to closest
        }.filter { it.second.second.isFinite() }
            .minByOrNull { it.second.second }
            ?.let { (previous, closest) ->
                val edges = trace(previous, closest.first)
                Bridge(island1, island2, edgesToContour(edges)) to closest.second
            }

    // Prim's algorithm
    fun spanningTrees(): List<Bridge> {
        val costs = MutableList(islands.size) { Double.POSITIVE_INFINITY }
        val bridges = MutableList<Bridge?>(islands.size) { null }
        val left = PriorityQueue<Int>(vertices.size, compareBy { costs[it] })
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

fun edgesToContour(edges: List<VisEdge>): ShapeContour {
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
    val pieces = chaikin(contours, sharpCorners, 5)
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