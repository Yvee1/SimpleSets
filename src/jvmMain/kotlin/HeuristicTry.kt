import org.openrndr.application
import org.openrndr.color.ColorRGBa
import patterns.*
import kotlin.random.Random

fun main() = application {
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
        println(p.cost())

        p = simulatedAnnealing(p, cps, 100, r)

        keyboard.keyDown.listen {
            p.mutate(cps, r)
            println(p.cost())
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