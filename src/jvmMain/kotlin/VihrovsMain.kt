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
        val pts = parsePoints(File("input-output/diseasome.txt").readText())
        val s = VihrovsSettings()
        val gs = GeneralSettings(pSize = 5.2)
        val ds = DrawSettings()
        var hs = vihrovs(pts, s)
        var contours = hs.associateWith { h ->
//            hobbyCurve(simplify(h.contour.segments.map { it.start } + h.contour.end, 0.1), closed=true)
                hobbyCurve(
//                    ShapeContour.fromPoints(
                        simplify(h.contour.segments.map { it.start } , 0.175)
//                        , closed=true)
                    , closed=true)
//                h.contour
        }
        var r = pts.bounds.offsetEdges(s.vertexRadius)
        var comp = drawComposition {
//            scale(-1.0, -1.0)
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


//        gui.onChange { _, _ ->
//            r = pts.bounds.offsetEdges(s.vertexRadius)
//            s.influenceRadius = 2 * s.vertexRadius
//            hs = vihrovs(pts, s)
//            contours = hs.associateWith { h ->
//                hobbyCurve(
////                    ShapeContour.fromPoints(
//                        simplify(h.contour.segments.map { it.start } , 0.05)
//                        , closed=true)
////                    , closed=true)
////                h.contour
//            }
//
//            comp = drawComposition {
//                scale(1.0, -1.0)
//                for (h in hs) {
//                    if (h.contour.shape.area < 0.85 * r.area) {
//                        fill = ds.colors[h.type].toColorRGBa().whiten(ds.whiten)
//                        stroke = ColorRGBa.BLACK
//                        strokeWeight = ds.contourStrokeWeight(gs)
////                        if (contours[h]!!.segments.any { it.start.x.isNaN() }) {
////
////                        } else {
//                            contour(contours[h]!!)
////                        }
//                    }
//                }
//                coloredPoints(pts, gs, ds)
//            }
//
//            comp.saveToFile(File("vihrovs.svg"))
//            "py svgtoipe.py vihrovs.svg".runCommand(File("."))
//        }

//        println(hs.filter { it.type == 0 }.size)


        extend(gui)
        extend(Camera2D())
        extend {
//            println(hs.map { it.contour })
            drawer.apply {
                clear(ColorRGBa.WHITE)
                composition(comp)
            }
        }
    }
}