import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.triangulation.voronoiDiagram
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import kotlin.math.min

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val pts = MutableList(200) {
            drawer.bounds.uniform(distanceToEdge = 100.0)
        }

        mouse.buttonDown.listen { e ->
            val removing = pts.minBy { p ->
                p.squaredDistanceTo(e.position)
            }
            pts.remove(removing)
        }

        extend {
            val maxRadius = 10.0
            val circles = growCircles(pts, maxRadius)
            val vd = pts.voronoiDiagram(drawer.bounds)

            drawer.apply {
                clear(ColorRGBa.WHITE)
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GRAY.opacify(0.5)
                for ((p, r) in circles) {
                    circle(p, r)
                }
                contours(vd.cellPolygons())
            }
        }
    }
}

fun growCircles(pts: List<Vector2>, maxRadius: Double): List<Circle> {
    val growingCircles = pts.map { p ->
        GrowingCircle(p, 0.0)
    }

    while(growingCircles.any { !it.frozen }) {
        var minD = Double.POSITIVE_INFINITY
        lateinit var minPair: Pair<GrowingCircle, GrowingCircle>
        for ((i, c1) in growingCircles.withIndex()) {
            for (c2 in growingCircles.subList(i + 1, growingCircles.size)) {
                if (c1.frozen && c2.frozen) continue
                val d = c1.center.distanceTo(c2.center)

                val realD = when {
                    !c1.frozen && !c2.frozen -> d / 2
                    c1.frozen -> d - c1.r
                    else -> d - c2.r
                }

                if (realD < minD) {
                    minD = min(realD, maxRadius)
                    minPair = c1 to c2
                }
            }
        }

        val c = minPair.toList().first { !it.frozen }
        c.r = minD
        c.frozen = true
    }

    return growingCircles.map {
        Circle(it.center, it.r)
    }
}

fun growCircles(pts1: List<Vector2>, pts2: List<Vector2>, maxRadius: Double): Pair<List<Circle>, List<Circle>> {
    val growingCircles1 = pts1.map { p ->
        TypedGrowingCircle(p, 0.0, type = 1)
    }
    val growingCircles2 = pts2.map { p ->
        TypedGrowingCircle(p, 0.0, type = 2)
    }
    val growingCircles = growingCircles1 + growingCircles2

    if (growingCircles1.isNotEmpty() && growingCircles2.isNotEmpty()) {
        while (growingCircles.any { !it.frozen }) {
            var minD = Double.POSITIVE_INFINITY
            lateinit var minPair: Pair<TypedGrowingCircle, TypedGrowingCircle>
            for ((i, c1) in growingCircles.withIndex()) {
                for (c2 in growingCircles.subList(i + 1, growingCircles.size)) {
                    if (c1.frozen && c2.frozen || c1.type == c2.type) continue
                    val d = c1.center.distanceTo(c2.center)

                    val realD = when {
                        !c1.frozen && !c2.frozen -> d / 2
                        c1.frozen -> d - c1.r
                        else -> d - c2.r
                    }

                    if (realD < minD) {
                        minD = min(realD, maxRadius)
                        minPair = c1 to c2
                    }
                }
            }

            val c = minPair.toList().first { !it.frozen }
            c.r = minD
            c.frozen = true
        }
    } else {
        growingCircles.forEach {
            it.r = maxRadius
        }
    }

    return growingCircles1.map { Circle(it.center, it.r) } to growingCircles2.map { Circle(it.center, it.r) }
}

data class GrowingCircle(val center: Vector2, var r: Double, var frozen: Boolean = false)

data class TypedGrowingCircle(val center: Vector2, var r: Double, val type: Int, var frozen: Boolean = false)

fun <T> Pair<T, T>.theOther(t: T): T = when {
    first == t -> second
    second == t -> first
    else -> error("Value $t not part of pair $this")
}