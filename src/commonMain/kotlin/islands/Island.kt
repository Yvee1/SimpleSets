package islands

import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import patterns.*

sealed class Island {
    abstract val type: Int
    abstract val points: List<Point>
    abstract val allPoints: List<Point>
    abstract val circles: List<Circle>
    abstract val segments: List<LineSegment>
    abstract val contour: ShapeContour
    abstract fun scale(s: Double): Island
}

fun Pattern.toIsland(expandRadius: Double) = when(this) {
    is Cluster -> toIsland(expandRadius)
    is Bend -> toIsland(expandRadius)
    is SinglePoint -> toIsland(expandRadius)
}