package patterns

import ComputePartitionSettings
import geometric.convexHull
import org.openrndr.extra.triangulation.delaunayTriangulation
import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import org.openrndr.math.asRadians
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import org.openrndr.shape.intersection
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

data class Island(override val points: List<Point>, override val weight: Int): Pattern() {
    override val type = points.firstOrNull()?.type ?: -1
    override val boundaryPoints: List<Point> = convexHull(points)
    override val contour by lazy {
        ShapeContour.fromPoints(boundaryPoints.map { it.pos }, true)
    }
    private val vecs by lazy {
        points.map { it.pos }
    }
    override val segments: List<LineSegment>
        get() = (boundaryPoints + boundaryPoints.first()).zipWithNext { a, b -> LineSegment(a.pos, b.pos) }
    override operator fun contains(v: Vector2) = v in contour

    override fun isValid(cps: ComputePartitionSettings): Boolean {
        return coverRadius(vecs) <= cps.clusterRadius
    }

    companion object {
        val EMPTY = Island(listOf(), 0)
    }

    override fun original() = copy(points=points.map { it.originalPoint ?: it })
    override fun isEmpty() = points.isEmpty()
}

fun coverRadius(vecs: List<Vector2>): Double =
    if (vecs.size < 2) 0.0
    else if (vecs.size == 2) {
        vecs[0].distanceTo(vecs[1]) / 2
    }
    else if (vecs.size == 3) {
        coverRadiusTriangle(vecs[0], vecs[1], vecs[2])
    } else {
        coverRadiusVoronoi(vecs)
    }

fun coverRadiusVoronoi(vecs: List<Vector2>): Double {
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

private fun circumradius(p1: Vector2, p2: Vector2, p3: Vector2): Double {
    val a = (p2 - p1).length
    val b = (p3 - p2).length
    val c = (p1 - p3).length

    return (a * b * c) / sqrt((a + b + c) * (b + c - a) * (c + a - b) * (a + b - c))
}

fun angleBetween(v: Vector2, w: Vector2) = acos((v dot w) / (v.length * w.length))

// Based on the idea from https://math.stackexchange.com/a/2397393
// but corrected the obtuse formula.
fun coverRadiusTriangle(p: Vector2, q: Vector2, r: Vector2): Double {
    val cr = circumradius(p, q, r)
    val pq = q - p
    val pr = r - p
    val angleP = angleBetween(pq, pr).asDegrees
    val qp = p - q
    val qr = r - q
    val angleQ = angleBetween(qp, qr).asDegrees
    val angleR = 180.0 - angleP - angleQ
    val acute = angleP < 90.0 && angleQ < 90.0 && angleR < 90.0
    return if (acute) {
        cr
    } else {
        val (_, b, c) = listOf(angleP to qr.length, angleQ to pr.length, angleR to pq.length).sortedByDescending { it.second }
        b.second / (2 * cos(c.first.asRadians))
    }
}
