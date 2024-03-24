import geometric.Orientation
import geometric.orientation
import highlights.Highlight
import org.openrndr.application
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Shape
import org.openrndr.shape.union
import patterns.Point
import patterns.coverRadiusVoronoi
import kotlin.math.abs

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        extend {
            // todo.
            // 1. load input points
            // 2. run SimpleSets with appropriate parameters to obtain patterns
            // 3. for every algorithm load svg and parse it to patterns/highlights: shape and color and input points contained in it
            // 4. implement measures


        }
    }
}

fun measure(highlights: List<Highlight>, points: List<Point>, boundingBox: Rectangle) {

}

fun inflectionPoints(h: Highlight): Int {
    return h.contour.equidistantPositions(100).windowed(3) { (a, b, c) ->
        orientation(a, b, c)
    }.zipWithNext { o1, o2 -> if (o1 != o2 && o2 != Orientation.STRAIGHT) 1 else 0 }.sum()
}

fun areaCovered(highlights: List<Highlight>): Double {
    var total = Shape.EMPTY

    for (h in highlights) {
        total = total.union(h.contour.shape)
    }

    return total.area
}

fun densityDistortion(highlights: List<Highlight>, points: List<Point>): Double {
    val totalCovered = areaCovered(highlights)

    var total = 0.0

    for ((t, hs) in highlights.groupBy { it.type }) {
        val coveredArea = areaCovered(hs)
        val tNumPoints = hs.sumOf { it.allPoints.size }
        total += abs(coveredArea / totalCovered - tNumPoints / points.size)
    }

    return total
}

fun maxCoverRadius(highlights: List<Highlight>): Double {
    return highlights.maxOf { h ->
        coverRadiusVoronoi(h.allPoints.map { it.pos }, shape = h.contour)
    }
}
