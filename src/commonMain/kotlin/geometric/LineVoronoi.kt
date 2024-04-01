package geometric

//import org.openrndr.extra.triangulation.voronoiDiagram
//import org.openrndr.shape.*
//import patterns.Pattern
//import kotlin.math.min
//import kotlin.math.max
//import kopenvoronoi.VoronoiDiagram
//import kopenvoronoi.geometry.Edge
//import kopenvoronoi.geometry.EdgeType
//import kopenvoronoi.geometry.Face
//import kopenvoronoi.geometry.Point
//import kopenvoronoi.vertex.Vertex
//import org.openrndr.math.Vector2
//import patterns.Island
//
//val Point.v get() = Vector2(x, y)
//val Vertex.v get() = position.v
//
//val Edge.c get(): ShapeContour {
//    if (type == EdgeType.PARABOLA) {
//        val points = mutableListOf<Point>()
//        val t_src: Double = source.dist()
//        val t_trg: Double = target.dist()
//        val t_min = min(t_src, t_trg)
//        val t_max = max(t_src, t_trg)
//        val nmax = 40
//
//        for (n in 0 until nmax) {
//            val t = t_min + (t_max - t_min) / ((nmax - 1) * (nmax - 1)).toDouble() * n.toDouble() * n.toDouble()
//            val next = point(t)
//            if (points.size == 0 || next != points.last())
//                points.add(next)
//        }
//
//        val forward = ShapeContour.fromPoints(points.map { it.v }, closed = false)
//
//        return listOf(forward, forward.reversed).minBy {
//            it.segments.first().start.squaredDistanceTo(source.v)
//        }
//    }
//    return LineSegment(source.position.v, target.position.v).contour
//}
//
//fun cell(faces: List<Face>, v: Vertex): List<Edge> {
//    fun Edge.adj() = type == EdgeType.NULLEDGE || face in faces && twin!!.face in faces
//
//    var e = v.face!!.edge
//    while (e.adj()) {
//        e = e.next
//    }
//
//    val start = e
//
//    val edges = mutableListOf(start)
//    do {
//        val cand = e.next
//        e = if (!cand.adj()) {
//            cand
//        } else {
//            cand.twin!!.next
//        }
//        edges.add(e)
//    } while (e != start)
//
//    return edges
//}
//
//fun cellToContour(edges: List<Edge>): ShapeContour {
//    var c = edges[0].c
//    for (i in 1 until edges.size) {
//        val cand = edges[i].c
//        if (cand.length > 1E-6)
//            c += cand
//    }
//    return c.close()
//}
//
//fun voronoiDiagram(patterns: List<Pattern>): List<ShapeContour> {
//    val voronoi = VoronoiDiagram()
//    val vertss = patterns.map { p ->
//        p.boundaryPoints.map {
//            voronoi.insert_point_site(it.pos.x, it.pos.y)!!
//        }
//    }
//
//    val edgess = vertss.withIndex().map { (i, verts) ->
//        buildList {
//            for (j in 0 until verts.size - 1) {
//                add(voronoi.insert_line_site(verts[j], verts[j + 1]))
//            }
//            if (verts.size > 2 && patterns[i] is Island) {
//                add(voronoi.insert_line_site(verts.last(), verts.first()))
//            }
//        }
//    }
//
//    val facess = vertss.zip(edgess) { verts, edges ->
//        verts.map { it.face!! } + edges.flatMap { it.toList().map { it.face } }
//    }
//
//    return patterns.indices.map { cellToContour(cell(facess[it], vertss[it][0])) }.filter { it.length > 1E-6 }
//}
//
//fun approximateVoronoiDiagram(patterns: List<Pattern>, expandRadius: Double, approxFactor: Double = 0.05): List<ShapeContour> {
//    val points = patterns.map {
//        (it.boundaryPoints.map { it.pos } +
//                if (it.contour.segments.isNotEmpty()) it.contour.equidistantPositions((it.contour.length * approxFactor).toInt()) else emptyList()).toSet().toList()
//    }
//    val allPoints = points.flatten()
//    val bounds = allPoints.bounds.offsetEdges(expandRadius*1.1)
//    val voronoi = allPoints.voronoiDiagram(bounds)
//
//    var i = 0
//    val cells = points.map { group ->
//        var patternCell = Shape.EMPTY
//        group.forEach { _ ->
//            val cell = voronoi.cellPolygon(i++)
//            if (cell.segments.isNotEmpty()) {
//                patternCell = cell.shape.union(patternCell)
//            }
//        }
//        patternCell.contours.maxByOrNull { it.shape.area } ?: bounds.contour
//    }
//    return cells
//}
