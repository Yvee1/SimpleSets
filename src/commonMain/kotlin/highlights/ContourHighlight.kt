package highlights

import geometric.Arc
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import patterns.Point

class ContourHighlight(override val contour: ShapeContour, override val allPoints: List<Point>, expandRadius: Double, override val type: Int = allPoints[0].type)
    : Highlight() {
    override val points: List<Point> = allPoints
    override val circles: List<Circle> = allPoints.map { Circle(it.pos, expandRadius) }
    override val segments: List<LineSegment> get() = error("Not implemented")
    override val arcs: List<Arc> get() = error("Not implemented")
    override fun scale(s: Double): Highlight {
        error("Not implemented")
    }
}