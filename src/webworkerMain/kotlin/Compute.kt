import patterns.Point
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.draw.LineJoin
import org.openrndr.shape.*
import kotlin.math.roundToInt

fun compute(set: Settings, points: List<Point>): String {
    val blue = rgb(0.651, 0.807, 0.89) to rgb(0.121, 0.47, 0.705)
    val red = rgb(0.984, 0.603, 0.6) to rgb(0.89, 0.102, 0.109)
    val green = rgb(0.698, 0.874, 0.541) to rgb(0.2, 0.627, 0.172)
    val orange = rgb(0.992, 0.749, 0.435) to rgb(1.0, 0.498, 0.0)
    val purple = rgb(0.792, 0.698, 0.839) to rgb(0.415, 0.239, 0.603)

    val colorPairs = listOf(blue, red, green, orange, purple)
    val lightColors = colorPairs.map { it.first }
    val darkColors = colorPairs.map { it.second }

    val problemInstance = ProblemInstance(
        points,
        set.expandRadius,
        set.clusterRadius,
        set.bendDistance,
        set.bendInflection,
        set.maxBendAngle,
        set.maxTurningAngle
    )

    val sol = Solution.compute(problemInstance, set)

    val composition = drawComposition(CompositionDimensions(0.0.pixels, 0.0.pixels, 800.0.pixels, 800.0.pixels)) {
        if (set.showVoronoi) {
            isolated {
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GRAY.opacify(0.3)
                contours(sol.voronoiCells)
            }
        }

        if (set.showClusterCircles && set.clusterRadius > 0) {
            fill = ColorRGBa.GRAY.opacify(0.3)
            stroke = null
            circles(points.map { it.pos }, set.clusterRadius)
        }

        if (set.showBendDistance) {
            fill = ColorRGBa.GRAY.opacify(0.3)
            stroke = null
            circles(points.map { it.pos }, set.bendDistance)
        }

        if (sol.obstacles.size > 1) {
            if (set.showBridges) {
                for (bridge in sol.bridges) {
                    isolated {
                        fill = null
                        stroke = ColorRGBa.BLACK
                        strokeWeight *= 4
                        lineJoin = LineJoin.ROUND
                        contour(bridge.contour)

                        strokeWeight /= 3
                        stroke = lightColors[sol.islands[bridge.island1].type]
                        contour(bridge.contour)
                    }
                }
            }
        }

        val end = (set.subset * sol.islands.size).roundToInt()

        for (i in 0 until end) {
            val island = sol.islands[i]
            strokeWeight = set.contourStrokeWeight
            stroke = ColorRGBa.BLACK
            fill = lightColors[island.type].opacify(0.3)

            val mi = sol.mergedIndex[i]
            when {
                mi != null -> {
                    if (sol.mergedIslands[mi].second.first() == i) {
                        lineJoin = LineJoin.ROUND
                        contour(sol.mergedIslands[mi].first)
                    }
                }

                i in sol.clippedIslands.indices -> contour(sol.clippedIslands[i])
                else -> contour(island.contour)
            }

            if (set.showVisibilityContours) {
                isolated {
                    stroke = darkColors[island.type].opacify(0.3)
                    strokeWeight *= 4
                    fill = null

                    if (i in sol.voronoiCells.indices)
                        shapes(sol.visibilityContours[i].map { it.intersection(sol.voronoiCells[i].shape) })
                    else if (i in sol.visibilityContours.indices)
                        contours(sol.visibilityContours[i])
                }
            }
        }

        if (set.showVisibilityGraph) {
            isolated {
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.BLACK
                circles(
                    sol.visibilityGraph.vertices.filterIsInstance<PointVertex>().map { it.pos },
                    set.pSize / 5.0
                )
            }

            isolated {
                stroke = ColorRGBa.BLACK.opacify(0.5)
                strokeWeight *= 0.5
                fill = null
                contours(sol.visibilityEdges)
            }
        }

        isolated {
            stroke = ColorRGBa.BLACK
            strokeWeight = set.pointStrokeWeight
            for (p in points) {
                fill = lightColors[p.type]
                circle(p.pos, set.pSize)
            }
        }
    }

    return composition.toSVG()
}