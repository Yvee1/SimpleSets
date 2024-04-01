package highlights

import geometric.Arc
import patterns.Point
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

class PointHighlight(val point: Point, expandRadius: Double): Highlight() {
    override val type: Int = point.type
    override val points: List<Point> = listOf(point)
    override val allPoints = points
    override val circles: List<Circle> = listOf(Circle(point.pos, expandRadius))
    override val segments: List<LineSegment> = emptyList()
    override val arcs: List<Arc> = emptyList()
    override val contour: ShapeContour = circles.first().contour
    override fun scale(s: Double) = PointHighlight(point, circles.first().radius * s)
}