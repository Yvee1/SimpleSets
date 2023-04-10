import patterns.Point
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.LineJoin
import org.openrndr.shape.*
import kotlin.math.roundToInt

fun createSvg(points: List<Point>, compSet: ComputeSettings, drawSet: DrawSettings, sol: Solution): String =
    drawComposition(CompositionDimensions(0.0.pixels, 0.0.pixels, 800.0.pixels, 800.0.pixels)) {
        if (drawSet.showVoronoi) {
            isolated {
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GRAY.opacify(0.3)
                contours(sol.voronoiCells)
            }
        }

        if (drawSet.showClusterCircles && compSet.clusterRadius > 0) {
            fill = ColorRGBa.GRAY.opacify(0.3)
            stroke = null
            circles(points.map { it.pos }, compSet.clusterRadius)
        }

        if (drawSet.showBendDistance) {
            fill = ColorRGBa.GRAY.opacify(0.3)
            stroke = null
            circles(points.map { it.pos }, compSet.bendDistance)
        }

        if (sol.obstacles.size > 1) {
            if (drawSet.showBridges) {
                for (bridge in sol.bridges) {
                    isolated {
                        fill = null
                        stroke = ColorRGBa.BLACK
                        strokeWeight *= 4
                        lineJoin = LineJoin.ROUND
                        contour(bridge.contour)

                        strokeWeight /= 3
                        stroke = drawSet.colorSettings.lightColors[sol.islands[bridge.island1].type].toColorRGBa()
                        contour(bridge.contour)
                    }
                }
            }
        }

        val end = (drawSet.subset * sol.islands.size).roundToInt()

        for (i in 0 until end) {
            val island = sol.islands[i]
            strokeWeight = drawSet.contourStrokeWeight
            stroke = ColorRGBa.BLACK
            fill = drawSet.colorSettings.lightColors[island.type].toColorRGBa().opacify(0.3)

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

            if (drawSet.showVisibilityContours) {
                isolated {
                    stroke = drawSet.colorSettings.darkColors[island.type].toColorRGBa().opacify(0.3)
                    strokeWeight *= 4
                    fill = null

                    if (i in sol.voronoiCells.indices)
                        shapes(sol.visibilityContours[i].map { it.intersection(sol.voronoiCells[i].shape) })
                    else if (i in sol.visibilityContours.indices)
                        contours(sol.visibilityContours[i])
                }
            }
        }

        if (drawSet.showVisibilityGraph) {
            isolated {
                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.BLACK
                circles(
                    sol.visibilityGraph.vertices.filterIsInstance<PointVertex>().map { it.pos },
                    drawSet.pSize / 5.0
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
            strokeWeight = drawSet.pointStrokeWeight
            for (p in points) {
                fill = drawSet.colorSettings.lightColors[p.type].toColorRGBa()
                circle(p.pos, drawSet.pSize)
            }
        }
    }.toSVG()