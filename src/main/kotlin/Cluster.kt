import org.openrndr.extra.triangulation.delaunayTriangulation
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import org.openrndr.shape.intersection

data class Cluster(val points: List<Point>, val weightI: Int): Pattern() {
    override val type = points.firstOrNull()?.type ?: -1
    override val weight = weightI
    override val contour by lazy {
        ShapeContour.fromPoints(points.map { it.pos }, true)
    }
    private val vecs by lazy {
        points.map { it.pos }
    }

    override operator fun contains(v: Vector2) = v in vecs || (weight > vecs.size && v in contour)

    operator fun contains(p: Point) = p in points || (weight > points.size && p.pos in contour)

    companion object {
        val EMPTY = Cluster(listOf(), 0)
    }

    override fun original() = copy(points=points.map { it.originalPoint ?: it })
    override fun isEmpty() = points.isEmpty()
}

fun coverRadius(vecs: List<Vector2>): Double {
    if (vecs.size < 2) return 0.0
    if (vecs.size == 2) {
        return vecs[0].distanceTo(vecs[1]) / 2
    }
    val delaunay = vecs.delaunayTriangulation()
    val ch = delaunay.hull()
    val voronoi = delaunay.voronoiDiagram(ch.bounds)
    val cells = voronoi.cellPolygons().map { it.shape.intersection(ch.reversed.shape).contours.firstOrNull() ?: ShapeContour.EMPTY }
    var r = 0.0
    for (i in cells.indices) {
        if (cells[i] == ShapeContour.EMPTY) continue
        val cellVerts = cells[i].segments.map { it.start }
        val c = vecs[i]
        r = r.coerceAtLeast(cellVerts.maxOfOrNull { it.distanceTo(c) }!!)
    }
    return r
}
