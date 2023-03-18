import geometric.overlaps
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

class ChainVertex(pos: Vector2, edges: MutableList<VisEdge> = mutableListOf()): Vertex(pos, edges) {
    override val inIsland = false
}

data class VisEdge(val v: Vertex, val w: Vertex, val contour: ShapeContour) {
    fun theOther(u: Vertex) = if (u == v) w else v
}

data class Graph(val islands: List<Island>, val clearance: Double = 0.0, val vertices: MutableList<Vertex> = mutableListOf(), val edges: MutableList<VisEdge> = mutableListOf()) {
    val obstacles = islands.map { it.scale((it.circles.first().radius + clearance) / it.circles.first().radius) }
    val islandVertices = List(islands.size) { mutableListOf<IslandVertex>() }

    val islandTangentVertices = List(islands.size) { mutableListOf<TangentVertex>() }
    val smallerIslands = islands.map { it.scale(0.9) }
    val smallerObstacles = obstacles.map { it.scale(0.95) }

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

        // Connect the vertices
        for (i1 in islands.indices) {
            for (i2 in i1 + 1 until islands.size) {
                if (islands[i1].type != islands[i2].type) continue
                for (v1 in islandVertices[i1]) {
                    for (v2 in islandVertices[i2]) {
                        val segment = freeSegment(v1, v2)
                        if (segment != null) {
                            createEdge(v1, v2, segment.contour)
                        }
                    }
                }
            }
        }

        for (i1 in islands.indices) {
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
                                        obstaclesOrIslands(i1).none { it.contour.overlaps(ls.contour) }
                            }
                            .forEach { ls ->
                                val tv = createTangentVertex(i2, ls.end)
                                createEdge(v1, tv, ls.contour)
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
                                createEdge(tv1, tv2, ls.contour)
                            }
                    }
                }
            }
        }

        for ((i, vertices) in islandTangentVertices.withIndex()) {
            val island = obstacles[i]
            if (vertices.size < 2) continue
            val tValues = vertices
                .map { it to (island.contour.nearest(it.pos).contourT) }
                .sortedBy { it.second }
            tValues.zipWithNext { (v1, t1), (v2, t2) ->
                createEdge(v1, v2, island.contour.sub(t1, t2))
            }
            val (lastV, lastT) = tValues.last()
            val (firstV, firstT) = tValues.first()
            val lastContour = island.contour.sub(lastT, 1.0) + island.contour.sub(0.0, firstT)
            createEdge(lastV, firstV, lastContour)
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
        val overlapsObstacle = obstaclesAndIslandsExcept(v1.island, v2.island).any { (island, obstacle) ->
            val obstacleIntersections = ls.contour.intersections(obstacle.contour)
            // If no intersections, then check whether the line segment lies within the obstacle.
            if (obstacleIntersections.isEmpty()) return@any ls.position(0.0) in obstacle.contour
            // Check if the line segment intersects the island
            if (ls.contour.overlaps(island.contour)) return@any true
            // Lastly check if there is any obstacle-intersection that is not close to the source and target island.
            obstacleIntersections.any {
                it.position !in obstacles[v1.island].contour.shape.difference(islands[v1.island].contour.shape)
                        && it.position !in obstacles[v2.island].contour.shape.difference(islands[v2.island].contour.shape)
            }
        }
        return if (overlapsEndIsland || overlapsObstacle) null else ls
    }

    private fun obstaclesOrIslands(vararg ints: Int): List<Island> =
        ints.map { smallerIslands[it] } + smallerObstacles.withIndex().filter { it.index !in ints }.map { it.value }

    private fun obstaclesAndIslandsExcept(vararg ints: Int): List<Pair<Island, Island>> =
        islands.zip(smallerObstacles).withIndex().filter { it.index !in ints }.map { it.value }

    private fun createCircleVertex(island: Int, circle: Circle) {
        val v = CircleVertex(island, circle, clearance)
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

    private fun createEdge(v: Vertex, w: Vertex, c: ShapeContour) {
        val e = VisEdge(v, w, c)
        v.edges.add(e)
        w.edges.add(e)
        edges.add(e)
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
                if (newDist < distances[v]!!) {
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
    var c = nonEmpty.first().contour
    for (e in nonEmpty.subList(1, nonEmpty.size)) {
        val edgeContour = listOf(e.contour, e.contour.reversed).minBy {
            it.segments.first().start.squaredDistanceTo(c.segments.first().end)
        }
        c += edgeContour
    }
    return c
}