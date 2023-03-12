package islands

import patterns.Point
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour

class PointIsland(point: Point, expandRadius: Double): Island() {
    override val type: Int = point.type
    override val circles: List<Circle> = listOf(Circle(point.pos, expandRadius))
    override val contour: ShapeContour = circles.first().contour
}