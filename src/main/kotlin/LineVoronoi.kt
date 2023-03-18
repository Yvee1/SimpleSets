import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.shape.*
import org.rogach.jopenvoronoi.*
import patterns.Cluster
import patterns.Pattern
val Point.v2: Vector2
    get() = Vector2(x, y)
val org.rogach.jopenvoronoi.Vertex.v2: Vector2
    get() = position.v2
fun Edge.toContour(): ShapeContour {
    if (type == EdgeType.PARABOLA) {
        val points = mutableListOf<Point>()
        val t_src: Double = source.dist()
        val t_trg: Double = target.dist()
        val t_min = Math.min(t_src, t_trg)
        val t_max = Math.max(t_src, t_trg)
        val nmax = 40

        for (n in 0 until nmax) {
            val t = t_min + (t_max - t_min) / ((nmax - 1) * (nmax - 1)).toDouble() * n.toDouble() * n.toDouble()
            points.add(point(t))
        }

        val forward = ShapeContour.fromPoints(points.map { it.v2 }, closed = false)

        return listOf(forward, forward.reversed).minBy {
            it.segments.first().start.squaredDistanceTo(source.v2)
        }
    }
    return LineSegment(source.position.v2, target.position.v2).contour
}
val Vector2.point: Point
    get() = Point(x, y)
fun voronoiDiagram(patterns: List<Pattern>): List<ShapeContour> {
    val vd = VoronoiDiagram()

    val boundingRect = patterns.flatMap { p -> p.boundaryPoints.map { it.pos } }.bounds
    val m = transform {
        scale(1.0 / boundingRect.width, 1.0 / boundingRect.height)
        scale(0.75)
        translate(-boundingRect.corner - Vector2(0.45, 0.45))
    }

    val vertices = patterns.map { p ->
        p.boundaryPoints.map {
            val transformedPoint = (m * it.pos.xy01).xy.point
            vd.insert_point_site(transformedPoint)
        }
    }

    patterns.zip(vertices) { p, verts ->
        (verts + if (p is Cluster) listOf(verts.first()) else emptyList()).zipWithNext { a, b ->
            vd.insert_line_site(a, b)
        }
    }

    val regions = vertices.map { verts ->
        val faces = mutableSetOf<Face>()
        verts.forEach { v ->
            fun doStuff(f: Face) {
                faces.add(f)
                val start = f.edge
                var currentEdge = start
                do {
                    val found =
                        listOf(currentEdge.source, currentEdge.target).filter { it.v2.squaredDistanceTo(v.v2) < 1e-9 }
                    if (found.isNotEmpty()) {
                        faces.addAll(found[0].out_edges.map { it.face })
                    }
                    currentEdge = currentEdge.next
                } while (currentEdge != start)
            }
            doStuff(v.face)
            if (v.null_face != null)
                doStuff(v.null_face)
        }
        val contours = faces.map { f ->
            val start = f.edge
            val segments = start.toContour().transform(m.inversed).segments.toMutableList()
            var currentEdge = start.next
            while (currentEdge != start) {
                val next = currentEdge.toContour().transform(m.inversed)
                segments += next.segments
                currentEdge = currentEdge.next
            }
            ShapeContour(segments.filter { it.length > 1e-6 }, closed=true)
        }
        contours.filter { it.segments.isNotEmpty() }.fold(Shape.EMPTY) { acc, x -> x.shape.union(acc) }.contours.last()
    }

    return regions
}