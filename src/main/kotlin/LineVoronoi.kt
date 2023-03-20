import org.openrndr.extra.triangulation.voronoiDiagram
import org.openrndr.shape.*
import patterns.Pattern

fun approximateVoronoiDiagram(patterns: List<Pattern>, expandRadius: Double): List<ShapeContour> {
    val points = patterns.map {
        (it.boundaryPoints.map { it.pos } +
                if (it.contour.segments.isNotEmpty()) it.contour.equidistantPositions((it.contour.length * 1.5).toInt()) else emptyList()).toSet().toList()
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
