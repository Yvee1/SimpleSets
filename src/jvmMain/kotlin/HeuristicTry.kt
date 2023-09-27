import org.openrndr.application
import org.openrndr.color.ColorRGBa
import patterns.*
import kotlin.random.Random

fun main() {
    val points = getExampleInput(ExampleInput.NYC)
    val cps = ComputePartitionSettings(
        bendDistance=50.64,
        bendInflection=true,
        maxBendAngle=107.32,
        maxTurningAngle=54.783,
        clusterRadius=18.634,
        partitionClearance = 6.0
    )
    val instance = PartitionInstance(points, cps)
    val greedy = instance.computePartition()
    val rng = Random.Default
    repartitionClosePatterns(greedy, cps, 5, rng)
}

fun veryFakeMain() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val drawSettings = DrawSettings(pSize = 5.0)
        val computeDrawingSettings = ComputeDrawingSettings(expandRadius = drawSettings.pSize * 3)
        val cps = ComputePartitionSettings()

        val pts =
            listOf(
                30 p0 30,
                50 p0 30,
                60 p0 50,
                20 p0 50,
                40 p0 60,
                40 p0 45,
            )

        val r = Random.Default
        var p = Partition(pts.toMutableList(), listOf(Island(pts, pts.size)).toMutableList())

        p = simulatedAnnealing(p, cps, 100, r)

        keyboard.keyDown.listen {
            p.mutate(cps, rng=r)
        }

        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)

                stroke = ColorRGBa.BLACK
                fill = drawSettings.colorSettings.lightColors[0].toColorRGBa().mix(ColorRGBa.WHITE, 0.5)
                strokeWeight = drawSettings.contourStrokeWeight

                for (part in p.patterns) {
                    when(part) {
                        is Island -> contour(part.contour)
                        is Reef -> contour(part.contour)
                        is SinglePoint -> circle(part.point.pos, 10.0)
                        is Matching -> contour(part.contour)
                    }

                }

                strokeWeight = drawSettings.pointStrokeWeight
                circles(pts.map { it.pos }, drawSettings.pSize)
            }
        }
    }
}