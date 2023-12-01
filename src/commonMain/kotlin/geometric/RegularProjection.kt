package geometric

import org.openrndr.math.Vector2
import org.openrndr.math.asDegrees
import patterns.Point
import kotlin.math.atan2

// Reference
// ---------
// On Removing Extrinsic Degeneracies in Computational Geometry
// Francisco Gómez, Suneeta Ramaswami, Godfried Toussaint

fun regularProjection(points: List<Vector2>): Double {
    if (points.size < 3) return 0.0

    // Compute angles of lines between any pair of points and sort them
    val angles = buildList {
        for (p in points) {
            for (q in points) {
                if (p == q) continue
                val d = p - q
                if (d != Vector2.ZERO)
                    add(atan2(d.x, d.y))
            }
        }
    }.sorted()

    // Find the largest gap in the sorted list.
    val ab = angles.zipWithNext { a, b -> Pair(Pair(a, b), b - a) }.maxBy { it.second }.first
    // Midpoint of the angles is a good rotation angle.
    val rotationAngle = (ab.first + ab.second)/2

    return rotationAngle
}

fun transformPoints(pts: List<Point>): List<Point> {
    val rotationAngle = regularProjection(pts.map { it.pos })
    return pts.map { Point(it.pos.rotate(rotationAngle.asDegrees), it.type, it) }
}
