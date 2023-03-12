package islands

import patterns.Bend
import patterns.Cluster
import patterns.Pattern
import patterns.SinglePoint
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour

sealed class Island {
    abstract val type: Int
    abstract val circles: List<Circle>
    abstract val contour: ShapeContour
}

fun Pattern.toIsland(expandRadius: Double) = when(this) {
    is Cluster -> toIsland(expandRadius)
    is Bend -> toIsland(expandRadius)
    is SinglePoint -> toIsland(expandRadius)
}