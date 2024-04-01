import geometric.end
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.simplify
import org.openrndr.svg.saveToFile
import patterns.bounds
import java.io.File

fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
    }

    program {
//        val pts = getExampleInput(ExampleInput.Mills)
//        val pts = getExampleInput(ExampleInput.NYC)
//        val pts = parsePoints(File("input-output/diseasome.txt").readText())
        val pts = ipeToPoints(File("example-pts.ipe").readText())


        val s = VihrovsSettings()
//        val gs = GeneralSettings(pSize = 5.2)
        val gs = GeneralSettings(pSize = goodSettings(ExampleInput.NYC).first.gs.pSize)
        val ds = DrawSettings(whiten = 0.1)
        var hs = vihrovs(pts, s)
        var contours = hs.associateWith { h ->
                hobbyCurve(
                        simplify(h.contour.segments.map { it.start } , 0.175)
                    , closed=true)
        }
        var r = pts.bounds.offsetEdges(s.vertexRadius)
        var comp = drawComposition {
            scale(-1.0, -1.0)
            for (h in hs) {
                if (h.contour.shape.area < 0.8 * r.area) {
                    fill = ds.colors[h.type].toColorRGBa().whiten(ds.whiten)
                    stroke = ColorRGBa.BLACK
                    strokeWeight = ds.contourStrokeWeight(gs)
                    contour(contours[h]!!)
                }
            }
            coloredPoints(pts, gs, ds)
        }
        comp.saveToFile(File("vihrovs.svg"))
        "py svgtoipe.py vihrovs.svg".runCommand(File("."))

        val gui = GUI()
        gui.add(s)
        gui.add(gs, "General settings")
        gui.add(ds, "Draw settings")


        gui.onChange { _, _ ->
            r = pts.bounds.offsetEdges(s.vertexRadius)
            s.influenceRadius = 2 * s.vertexRadius
            hs = vihrovs(pts, s)
            contours = hs.associateWith { h ->
                hobbyCurve(
                        simplify(h.contour.segments.map { it.start } , 0.05)
                        , closed=true)
            }

            comp = drawComposition {
                scale(1.0, -1.0)
                for (h in hs) {
                    if (h.contour.shape.area < 0.85 * r.area) {
                        fill = ds.colors[h.type].toColorRGBa().whiten(0.7)
                        stroke = ColorRGBa.BLACK
                        strokeWeight = ds.contourStrokeWeight(gs)
                        contour(contours[h]!!)
                    }
                }
                coloredPoints(pts, gs, ds)
            }

            comp.saveToFile(File("vihrovs.svg"))
            "py svgtoipe.py vihrovs.svg".runCommand(File("."))
        }

        extend(gui)
        extend(Camera2D())
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                composition(comp)
            }
        }
    }
}