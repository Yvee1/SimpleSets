import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.GUI
import org.openrndr.svg.saveToFile
import java.io.File

fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
    }

    program {
//        val pts = getExampleInput(ExampleInput.NYC)
        val pts = parsePoints(File("input-output/diseasome.txt").readText())
        val s = ClusterSetsSettings()
        val gs = GeneralSettings(pSize = 3.0)
        val ds = DrawSettings()

        val gui = GUI()
        gui.add(s, "ClusterSets")
        gui.add(gs, "General settings")
        gui.add(ds, "Draw settings")

        var skeleton = betaSkeleton(pts, s.beta)
        var forest = greedy(pts, skeleton)
        var final = augment(pts, forest, skeleton)

//        gui.onChange { _, _ ->
//            skeleton = betaSkeleton(pts, s.beta)
//            forest = greedy(pts, skeleton)
//            final = augment(pts, forest, skeleton)
//        }

        val comp = drawComposition {
            stroke = ColorRGBa.BLACK.opacify(0.5)
            strokeWeight = 0.5
            lineSegments(skeleton)
            stroke = ColorRGBa.BLACK
            strokeWeight = 1.0
            lineSegments(final)
            stroke = null
            fill = ColorRGBa.GRAY.opacify(0.1)
            coloredPoints(pts, gs, ds)
        }

        comp.saveToFile(File("clustersets-diseasome.svg"))
        "py svgtoipe.py clustersets-diseasome.svg".runCommand(File("."))

        extend(gui)
        extend(Camera2D())
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
//                scale(1.0, -1.0)
                composition(comp)
            }
        }
    }
}