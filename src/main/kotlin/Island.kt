import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour

interface Island {
    val type: Int
    val circles: List<Circle>
    val segments: List<LineSegment>
    val circularArcs: List<ShapeContour>
    val contour: ShapeContour
}