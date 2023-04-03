import geometric.PRECISION
import org.openrndr.extra.triangulation.voronoiDiagram
import org.openrndr.shape.*
import patterns.Pattern
import kotlin.math.max

fun approximateVoronoiDiagram(patterns: List<Pattern>, expandRadius: Double): List<ShapeContour> {
    val points = patterns.map {
        (it.boundaryPoints.map { it.pos } +
                if (it.contour.segments.isNotEmpty()) it.contour.equidistantPositions((it.contour.length * 0.5).toInt()) else emptyList()).toSet().toList()
    }
    val allPoints = points.flatten()
    val bounds = allPoints.bounds.offsetEdges(expandRadius*1.1)
    val voronoi = allPoints.voronoiDiagram(bounds)

    var i = 0
    val cells = points.map { group ->
        var patternCell = Shape.EMPTY
        group.forEach { _ ->
            val cell = voronoi.cellPolygon(i++)
            if (cell.segments.isNotEmpty()) {
                patternCell = cell.shape.union(patternCell)
            }
        }
        patternCell.contours.maxByOrNull { it.shape.area } ?: bounds.contour
    }
    return cells
}

fun approximateVoronoiDiagramAlternative(patterns: List<Pattern>, expandRadius: Double): List<ShapeContour> {
    val points = patterns.map {
        (it.boundaryPoints.map { it.pos } +
                if (it.contour.segments.isNotEmpty())
                    it.contour.equidistantPositions(it.contour.length.toInt())
                else emptyList()
        ).toSet().toList()
    }
    val allPoints = points.flatten()
    val bounds = allPoints.bounds.offsetEdges(expandRadius*1.1)
    val voronoi = allPoints.voronoiDiagram(bounds)

    var i = 0
    val cells = points.map { group ->
        val patternSegs = mutableListOf<Segment>()
        group.forEach { _ ->
            val cell = voronoi.cellPolygon(i++)
            patternSegs.addAll(cell.segments)
        }
        patternSegs.removeAll(patternSegs.map { s ->
            val closeSegs = patternSegs
                .asSequence()
                .filter { it != s && max(it.start.squaredDistanceTo(s.end), it.end.distanceTo(s.start)) < PRECISION }.toList()
            if (closeSegs.isNotEmpty()) closeSegs + s else emptyList()
        }.flatten())
        patternSegs.dropWhile { it.length < PRECISION }
        val orderedSegs = mutableListOf(patternSegs.first())
        patternSegs.removeFirst()
        while (patternSegs.isNotEmpty()) {
            val next = patternSegs.asSequence().map { it to it.start.squaredDistanceTo(orderedSegs.last().end) }.minBy { it.second }
            patternSegs.remove(next.first)
            if (next.first.length < PRECISION || next.second > PRECISION) continue
            orderedSegs.add(next.first)
        }
        ShapeContour.fromSegments(orderedSegs, closed = true)
    }
    return cells
}
