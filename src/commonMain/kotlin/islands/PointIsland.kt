package islands

import patterns.Point
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

class PointIsland(val point: Point, expandRadius: Double): Island() {
    override val type: Int = point.type
    override val points: List<Point> = listOf(point)
    override val allPoints = points
    override val circles: List<Circle> = listOf(Circle(point.pos, expandRadius))
    override val segments: List<LineSegment> = emptyList()
    override val contour: ShapeContour = circles.first().contour
    override fun scale(s: Double) = PointIsland(point, circles.first().radius * s)
}