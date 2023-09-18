package highlights

import geometric.Arc
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import patterns.*

sealed class Highlight {
    abstract val type: Int
    abstract val points: List<Point>
    abstract val allPoints: List<Point>
    abstract val circles: List<Circle>
    abstract val segments: List<LineSegment>
    abstract val arcs: List<Arc>
    abstract val contour: ShapeContour
    abstract fun scale(s: Double): Highlight
}

fun Pattern.toHighlight(expandRadius: Double) = when(this) {
    is Island -> toHighlight(expandRadius)
    is Reef -> toHighlight(expandRadius)
    is SinglePoint -> toHighlight(expandRadius)
    is Matching -> toHighlight(expandRadius)
}

/**
 * Shortest line segment(s) connecting two highlights.
 */
fun Highlight.connector(other: Highlight): List<LineSegment> {
    lateinit var best: LineSegment
    var bestDistance = Double.MAX_VALUE

    for (ls in segments) {
        val conn = ls.connector(other)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    for (circle in circles) {
        val conn = circle.connector(other)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    return listOf(best)
}

fun Pattern.connector(other: Pattern): LineSegment {
    if (this is SinglePoint && other is SinglePoint) {
        return LineSegment(this.point.pos, other.point.pos)
    }

    if (this is SinglePoint) {
        return LineSegment(point.pos, other.contour.nearest(point.pos).position)
    }

    if (other is SinglePoint) {
        return LineSegment(contour.nearest(other.point.pos).position, other.point.pos)
    }

    lateinit var best: LineSegment
    var bestDistance = Double.MAX_VALUE

    for (ls1 in segments) {
        for (ls2 in other.segments) {
            val cand = ls1.connector(ls2)
            if (cand.length < bestDistance) {
                best = cand
                bestDistance = cand.length
            }
        }
    }

    return best
}

fun LineSegment.connector(other: LineSegment): LineSegment {
    val cands = listOf(nearest(other.start) to other.start, nearest(other.end) to other.end, start to other.nearest(start), end to other.nearest(end))
    val conn = cands.minBy { (it.second - it.first).squaredLength }
    return LineSegment(conn.first, conn.second)
}

fun Circle.connector(other: LineSegment) =
    other.connector(this)

fun LineSegment.connector(other: Circle): LineSegment {
    val x = nearest(other.center)
    // Direction vector
    val d = (other.center - x).normalized
    return LineSegment(x, other.center - d * other.radius)
}

fun Circle.connector(other: Circle): LineSegment {
    // Direction vector
    val d = (other.center - center).normalized
    return LineSegment(center + d * radius, other.center - d * other.radius)
}

fun LineSegment.connector(highlight: Highlight): LineSegment {
    lateinit var best: LineSegment
    var bestDistance = Double.MAX_VALUE

    for (ls in highlight.segments) {
        val conn = connector(ls)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    for (circle in highlight.circles) {
        val conn = connector(circle)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    return best
}

fun Circle.connector(highlight: Highlight): LineSegment {
    lateinit var best: LineSegment
    var bestDistance = Double.MAX_VALUE

    for (ls in highlight.segments) {
        val conn = connector(ls)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    for (circle in highlight.circles) {
        val conn = connector(circle)
        val d = conn.length
        if (d < bestDistance) {
            best = conn
            bestDistance = d
        }
    }

    return best
}