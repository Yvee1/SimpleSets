package geometric

import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import org.openrndr.shape.intersections

fun ShapeContour.overlaps(other: ShapeContour) =
    intersections(other).isNotEmpty() || position(0.0) in other || other.position(0.0) in this