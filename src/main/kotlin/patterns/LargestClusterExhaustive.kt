package patterns

import ProblemInstance
import geometric.convexHull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.uniform
import org.openrndr.launch
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains

fun ProblemInstance.largestClusterExhaustive(): Cluster {
    if (clusterRadius <= 0) return Cluster.EMPTY

    val nSubsets = 1 shl points.size
//    val largest = (0 until nSubsets).toList().parallelStream().map { i ->
//        points.withIndex().filter { (j, _) ->
//            (i shr j) and 1 == 1
//        }.map { it.value }
//    }.filter { subset ->
//        val ch = ShapeContour.fromPoints(convexHull(subset).map { it.pos }, closed = true)
//        val chPoints = points.filter { it in ch }
//        chPoints.isMonochromatic() && coverRadius(chPoints.map { it.pos }) <= clusterRadius
//    }.max(
//        compareBy<List<Point>> {
//            it.size
//        }
//    ).get()

    var largest = listOf(points.first())
    for (i in 0 until nSubsets) {
        val subset = points.withIndex().filter { (j, _) ->
            ((i shr j) and 1) == 1
        }.map { it.value }
        val chPoints = if (subset.size > 3) {
            val chBoundary = convexHull(subset)
            val ch = ShapeContour.fromPoints(chBoundary.map { it.pos }, closed = true)
            points.filter { it.pos in ch }
        } else {
            subset
        }
        if (chPoints.size <= largest.size) continue
        if (chPoints.isMonochromatic() && coverRadius(chPoints.map { it.pos }) <= clusterRadius) {
            largest = chPoints
        }
    }

    return Cluster(convexHull(largest), largest.size)
}

fun List<Point>.isMonochromatic(): Boolean {
    if (size < 2) return true
    return all { it.type == first().type }
}

fun main() = application {
    configure {
        width = 800
        height = 800
    }
    program {
        val bounds = drawer.bounds
        var bluePoints = List(15) { bounds.uniform(200.0) }
        var redPoints = List(0) { bounds.uniform(200.0) }
        var points = bluePoints.map { Point(it, 0) } + redPoints.map { Point(it, 1) }
        var problemInstance = ProblemInstance(points, clusterRadius = 50.0, transformPoints = false)

        var optimal = problemInstance.largestClusterExhaustive()
        println("Optimal: ${optimal.weight} $optimal")
        var heuristic = problemInstance.largestCluster()
        println("Heuristic: ${heuristic.weight} $heuristic")

        launch {
            GlobalScope.launch {
                while (optimal.weight == heuristic.weight) {
                    bluePoints = List(15) { bounds.uniform(200.0) }
                    redPoints = List(0) { bounds.uniform(200.0) }
                    points = bluePoints.map { Point(it, 0) } + redPoints.map { Point(it, 1) }
                    problemInstance = ProblemInstance(points, clusterRadius = 50.0, transformPoints = false)

                    optimal = problemInstance.largestClusterExhaustive()
                    println("Optimal: ${optimal.weight} $optimal")
                    heuristic = problemInstance.largestCluster()
                    println("Heuristic: ${heuristic.weight} $heuristic")
                }
            }.join()
        }

        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)

                stroke = ColorRGBa.BLACK

                fill = ColorRGBa.BLUE.opacify(0.25)
                circles(bluePoints, problemInstance.clusterRadius)
                fill = ColorRGBa.RED.opacify(0.25)
                circles(redPoints, problemInstance.clusterRadius)
                fill = ColorRGBa.BLUE
                circles(bluePoints, 5.0)
                fill = ColorRGBa.RED
                circles(redPoints, 5.0)

                fill = null
                strokeWeight = 3.0
                stroke = ColorRGBa.GREEN.opacify(0.5)
                contour(optimal.original().contour)
                stroke = ColorRGBa.BLUE.opacify(0.5)
                contour(heuristic.original().contour)
            }
        }
    }
}