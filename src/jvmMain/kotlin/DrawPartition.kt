import geometric.distanceTo
import highlights.ContourHighlight
import highlights.Highlight
import highlights.PointHighlight
import org.locationtech.jts.geom.Geometry
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import org.openrndr.shape.contour
import org.openrndr.svg.toSVG
import patterns.Point
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val vis = "BubbleSets"
    val ipe = File("nyc-$vis.ipe").readText()

    application {
        program {
            val gs = GeneralSettings(pSize = 2.05)
//            val gs = GeneralSettings(pSize = 1.25)
            val ds = DrawSettings()
            val cds = ComputeDrawingSettings()
            val (points, highlights) = ipeToContourHighlights(ipe, gs.expandRadius)
            val xGraph = XGraph(highlights, gs, cds)
//            { comp, fileName ->
//                val timeStamp = ZonedDateTime
//                    .now( ZoneId.systemDefault() )
//                    .format( DateTimeFormatter.ofPattern( "uuuu.MM.dd.HH.mm.ss" ) )
//                val f = File("${fileName}.svg")
//
//                f.writeText(comp.toSVG())
//                "py svgtoipe.py ${fileName}.svg".runCommand(File("."))
//            }
            val c = drawComposition {
                translate(0.0, 1.25 * height)
                scale(1.0, -1.0)
                xGraph.draw(this, ds)
                coloredPoints(points, gs, ds)
            }

            val outputFile = File("input-output/nyc-$vis-drawing.svg")
            outputFile.writeText(c.toSVG())
            "py svgtoipe.py input-output/nyc-$vis-drawing.svg".runCommand(File("."))

            extend {
                drawer.apply {
                    clear(ColorRGBa.WHITE)
                    composition(c)
                }
            }
        }
    }
}

fun ipeToContourHighlights(ipe: String, expandRadius: Double): Pair<List<Point>, List<Highlight>> {
    val ipeXML = ipe.lines().filterNot { it.contains("""<!DOCTYPE ipe SYSTEM "ipe.dtd">""") }.joinToString("\n")
    val doc = loadXMLFromString(ipeXML)
    val nodeList = doc.getElementsByTagName("path").asList()

    val points = nodesToPoints(doc.getElementsByTagName("use").asList().map { it.attributes.asMap() })

    val polygons = mutableMapOf<Int, MutableList<ShapeContour>>()

    for (m in nodeList) {
        val type = when(m.attributes.asMap()["fill"]) {
            "CB light blue"  -> 0
            "CB light red"   -> 1
            "CB light green" -> 2
            else -> when(m.attributes.asMap()["stroke"]) {
                "CB dark blue"  -> 0
                "CB dark red"   -> 1
                "CB dark green" -> 2
                else -> null
            }
        }
        if (type != null) {
            val list = polygons[type]
            if (list != null) {
                list.add(m.textContent.ipePathToContour())
            } else {
                polygons[type] = mutableListOf(m.textContent.ipePathToContour())
            }
        }
    }

    return points to buildList {
        val isolatedPoints = points.toMutableSet()
        for ((_, cs) in polygons) {
            for (c in cs) {
                val pts = points.filter { it.pos in c || c.distanceTo(it.pos) < 1E-3 }
                isolatedPoints.removeAll(pts)
                add(ContourHighlight(c.buffer(expandRadius), pts, expandRadius))
            }
        }
        for (p in isolatedPoints) {
            add(PointHighlight(p, expandRadius))
        }
    }
}

fun String.ipePathToContour(): ShapeContour =
    contour {
        for (l in lines()) {
            if (l.isEmpty()) continue
            if (l == "h")
                close()
            else {
                val words = l.split(' ')
                val x = words[0].toDouble()
                val y = words[1].toDouble()
                if (words[2] == "m")
                    moveTo(x, y)
                else
                    lineTo(x, y)
            }
        }
    }