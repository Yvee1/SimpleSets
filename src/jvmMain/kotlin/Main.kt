import geometric.approximateVoronoiDiagram
import islands.Island
import islands.toIsland
import islands.visibilityContours
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import patterns.Pattern
import patterns.Point
import patterns.computePartition
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.LineJoin
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUIAppearance
import org.openrndr.extra.parameters.*
import org.openrndr.math.*
import org.openrndr.shape.*
import org.openrndr.svg.toSVG
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.round
import kotlin.math.roundToInt

fun fakeMain() {
    val c = Circle(Vector2.ZERO, 100.0)
    val pts = c.contour.equidistantPositions(100000)
    val c2 = ShapeContour.fromPoints(pts, closed = true)
    c.shape.difference(c2.shape)
}

@OptIn(DelicateCoroutinesApi::class)
fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
        title = "Islands and bridges"
    }

    program {
        var points = mutableListOf<Point>()
        var type = 0
        var problemInstance: ProblemInstance
        var patterns: List<Pattern>
        var islands = listOf<Island>()
        var clippedIslands = listOf<ShapeContour>()
        var mergedIndex = mutableMapOf<Int, Int>()
        var mergedIslands = listOf<Pair<ShapeContour, List<Int>>>()
        var overlapIslands = listOf<ShapeContour>()
        var visibilityContours = listOf<List<ShapeContour>>()
        var voronoiCells = listOf<ShapeContour>()
        var obstacles = listOf<Island>()
        var visibilityGraph = Graph(emptyList(), emptyList(), emptyList())
        var visibilityEdges = listOf<ShapeContour>()
        var bridges = listOf<Bridge>()
        var composition: (Boolean) -> Composition = { _ -> drawComposition { } }
        var calculating = false

        fun clearData(clearPoints: Boolean = true){
            if (clearPoints)
                points.clear()
                problemInstance = ProblemInstance(points)
            patterns = emptyList()
            islands = emptyList()
            clippedIslands = emptyList()
            mergedIndex = mutableMapOf()
            mergedIslands = emptyList()
            overlapIslands = emptyList()
            visibilityContours = emptyList()
            voronoiCells = emptyList()
            obstacles = emptyList()
            visibilityGraph = Graph(emptyList(), emptyList(), emptyList())
            visibilityEdges = emptyList()
            bridges = emptyList()
            composition = { _ -> drawComposition { } }
        }

        val gui = GUI(GUIAppearance(ColorRGBa.BLUE_STEEL))

        val s = object {
            @DoubleParameter("Point size", 0.1, 10.0, order = 0)
            var pSize = 3.0

            @BooleanParameter("Expand to islands", order = 1)
            var expand = true

            @BooleanParameter("Intersect islands with voronoi", order = 1)
            var voronoiIntersect = false

            @BooleanParameter("Merge islands", order = 1)
            var mergeIslands = false

            @BooleanParameter("Overlap islands (*)", order = 1)
            var overlapIslands = true

            @BooleanParameter("Shadows", order = 1)
            var shadows = true

            val expandRadius get() = if (expand) pSize * 3 else 0.0001

            val pointStrokeWeight get() = pSize / 3

            val contourStrokeWeight get () = pSize / 3.5

            @BooleanParameter("Grid", order = 1)
            var useGrid = true

            @DoubleParameter("Grid size", 1.0, 100.0, order = 2)
            var gridSize = 20.0

            @BooleanParameter("Disjoint", order = 5)
            var disjoint = true

            @OptionParameter("Example input", order = 10)
            var exampleInput = ExampleInput.NYC

            @ActionParameter("Load example input", order = 11)
            fun loadExample() {
                clearData()
                points = getExampleInput(exampleInput).toMutableList()
            }

            @TextParameter("Input ipe file (no file extension)", order = 14)
            var inputFileName = "nyc"

            @ActionParameter("Load input file", order = 15)
            fun loadInput() {
                clearData()
                try {
                    val ipe = File("input-output/$inputFileName.ipe").readText()
                    points = ipeToPoints(ipe).toMutableList()
                } catch (e: IOException) {
                    println("Could not read input file")
                    e.printStackTrace()
                }
            }

            @TextParameter("Output file (no file extension)", order = 20)
            var outputFileName = "output"

            @ActionParameter("Save points", order = 23)
            fun savePoints() {
                writeToIpe(points, "input-output/$outputFileName-points.ipe")
                gui.saveParameters(File("input-output/$outputFileName-parameters.json"))
            }

            @ActionParameter("Save output", order = 25)
            fun saveOutput() {
                val svg = composition(false).toSVG()
                File("input-output/$outputFileName.svg").writeText(svg)
                gui.saveParameters(File("input-output/$outputFileName-parameters.json"))
                "py svgtoipe.py input-output/$outputFileName.svg".runCommand(File("."))
            }

            @DoubleParameter("Bend distance", 1.0, 500.0, order=1000)
            var bendDistance = 20.0

            @BooleanParameter("Inflection", order=2000)
            var bendInflection = true

            @DoubleParameter("Max bend angle", 0.0, 180.0, order=3000)
            var maxBendAngle = 180.0

            @DoubleParameter("Max turning angle", 0.0, 180.0, order=4000)
            var maxTurningAngle = 180.0

            @DoubleParameter("Cluster radius", 0.0, 100.0, order=5000)
            var clusterRadius = 50.0

            @DoubleParameter("Clearance", 2.0, 20.0, order = 7000)
            var clearance = 5.0

            @DoubleParameter("Avoid overlap", 0.0, 1.0, order = 8000)
            var avoidOverlap = 0.25

            @BooleanParameter("Show points", order = 8980)
            var showPoints = true

            @BooleanParameter("Show islands", order = 8990)
            var showIslands = true

            @BooleanParameter("Show visibility contours", order = 9000)
            var showVisibilityContours = true

            @BooleanParameter("Show bridges", order = 9010)
            var showBridges = true

            @BooleanParameter("Smooth bridges", order = 9015)
            var smoothBridges = true

            @BooleanParameter("Show cluster circles", order = 10000)
            var showClusterCircles = false

            @BooleanParameter("Show bend distance", order = 10005)
            var showBendDistance = false

            @BooleanParameter("Show visibility graph", order=10010)
            var showVisibilityGraph = false

            @BooleanParameter("Show voronoi", order=10020)
            var showVoronoi = false

            @DoubleParameter("Show subset based on computation", 0.0, 1.0, order=10000000)
            var subset = 1.0
        }

        gui.add(s, "Settings")

        extend(gui)
        gui.visible = false
        gui.compartmentsCollapsedByDefault = false

        class Grid(val cellSize: Double, val center: Vector2){
            fun snap(p: Vector2): Vector2 = (p - center).mapComponents { round(it / cellSize) * cellSize } + center

            fun draw(compositionDrawer: CompositionDrawer){
                val r = drawer.bounds
                val vLines = buildList {
                    var x = r.corner.x + (center.x.mod(cellSize))
                    while (x <= r.corner.x + r.width){
                        add(LineSegment(x, r.corner.y, x, r.corner.y + r.height))
                        x += cellSize
                    }
                }
                val hLines = buildList {
                    var y = r.corner.y + (center.y.mod(cellSize))
                    while (y <= r.corner.y + r.height){
                        add(LineSegment(r.corner.x, y, r.corner.x + r.width, y))
                        y += cellSize
                    }
                }
                compositionDrawer.isolated {
                    lineSegments(vLines + hLines)
                }
            }
        }

        var view = drawer.view

        var grid = Grid(s.gridSize, drawer.bounds.center)
        gui.onChange { _, _ -> grid = Grid(s.gridSize, drawer.bounds.center) }

        fun flip(v: Vector2) = Vector2(v.x, drawer.bounds.height - v.y)

        fun transformMouse(mp: Vector2): Vector2 {
            val v = flip((view.inversed * Vector4(mp.x, mp.y, 0.0, 1.0)).xy)
            return if (s.useGrid) grid.snap(v) else v
        }

        mouse.buttonDown.listen { mouseEvent ->
            if (!mouseEvent.propagationCancelled) {
                if (mouseEvent.button == MouseButton.LEFT) {
                    val p = transformMouse(mouseEvent.position)
                    if (points.none { it.pos == p })
                        points.add(Point(p, type))
                }
                else if (mouseEvent.button == MouseButton.RIGHT) {
                    val mp = transformMouse(mouseEvent.position)
                    val closest = points.withIndex().minByOrNull { (_, p) -> ((p.originalPoint ?: p).pos - mp).squaredLength } ?: return@listen
                    points.removeAt(closest.index)
                }
            }

        }

        keyboard.keyDown.listen { keyEvent ->
            if (!keyEvent.propagationCancelled) {
                if (keyEvent.name in (1..5).map { it.toString() }) {
                    type = keyEvent.name.toInt() - 1
                }

                if (keyEvent.key == KEY_SPACEBAR) {
                    keyEvent.cancelPropagation()
                    if (calculating) return@listen

                    clearData(clearPoints = false)
                    problemInstance = ProblemInstance(
                        points,
                        s.expandRadius,
                        s.clusterRadius,
                        s.bendDistance,
                        s.bendInflection,
                        s.maxBendAngle,
                        s.maxTurningAngle,
                        s.avoidOverlap
                    )
                    launch {
                        GlobalScope.launch {
                            try {
                                calculating = true
                                patterns = problemInstance.computePartition(s.disjoint)
                                islands = patterns.map { it.toIsland(s.expandRadius) }
                                obstacles = islands.map { it.scale(1 + s.clearance / it.circles.first().radius) }
                                visibilityContours = islands.map { i1 ->
                                    islands.filter { i2 -> i2.type == i1.type }
                                        .flatMap { i2 -> i1.visibilityContours(i2) }
                                }
                                voronoiCells =
                                    approximateVoronoiDiagram(
                                        patterns.map { it.original() },
                                        s.expandRadius + s.clearance,
                                        approxFactor = 1.0
                                    )
                                if (s.overlapIslands) {
                                    overlapIslands = islands.withIndex().map { (i, isle) ->
                                        morphIsland(null, isle, islands.subList(0, i))
                                    }
                                } else {
                                    clippedIslands =
                                        if (!s.disjoint || !s.voronoiIntersect) islands.map { it.contour } else islands.withIndex()
                                            .map { (i, island) ->
                                                intersection(island.contour, voronoiCells[i]).outline
                                            }
                                    val tmp = mutableMapOf<Int, Pair<ShapeContour, List<Int>>>()
                                    mergedIslands = if (!s.mergeIslands) emptyList() else buildList {
                                        for (i1 in islands.indices) {
                                            for (i2 in i1 + 1 until islands.size) {
                                                if (islands[i1].type != islands[i2].type
                                                    || !islands[i1].contour.bounds.intersects(islands[i2].contour.bounds)
                                                ) continue
                                                if (islands[i1].contour.intersections(islands[i2].contour)
                                                        .isNotEmpty()
                                                ) {
                                                    val (c1, c2) = listOf(i1, i2).map { i ->
                                                        tmp[i] ?: (clippedIslands[i] to listOf(i))
                                                    }
                                                    if (c1 == c2) continue
                                                    removeIf { it == c1 || it == c2 }
                                                    val entry =
                                                        union(c1.first, c2.first).outline to (c1.second + c2.second)
                                                    add(entry)
                                                    (c1.second + c2.second).forEach { i -> tmp[i] = entry }
                                                }
                                            }
                                        }
                                    }
                                    mergedIndex = tmp.map { (i, target) ->
                                        i to mergedIslands.withIndex().find { it.value == target }!!.index
                                    }.toMap().toMutableMap()
                                }
                                if (s.showVisibilityGraph || s.showBridges) {
                                    visibilityGraph = Graph(islands, obstacles, voronoiCells, s.smoothBridges)
                                    visibilityEdges = visibilityGraph.edges.map { it.contour }
                                    bridges = visibilityGraph.spanningTrees()
                                }
                            }
                            catch(e: Throwable) {
                                e.printStackTrace()
                                calculating = false
                            }
                            calculating = false
                        }.join()
                    }
                }

                if (keyEvent.name == "c") {
                    keyEvent.cancelPropagation()
                    clearData()
                }

                if (keyEvent.key == KEY_BACKSPACE) {
                    keyEvent.cancelPropagation()
                    if (points.isNotEmpty()){
                        points.removeLast()
                    }
                }
            }
        }
        val font = loadFont("data/fonts/default.otf", 16.0)
        extend(Camera2D())
        extend {
            view = drawer.view
            drawer.fontMap = font
            drawer.fill = ColorRGBa.BLACK
            drawer.clear(ColorRGBa.WHITE)

            val vertex =
                visibilityGraph.vertices.minByOrNull { v ->
                    val tmp = transformMouse(mouse.position)
                    when(v) {
                        is PointVertex -> (v.pos - tmp).squaredLength
                        is IslandVertex -> if (tmp in islands[v.island].contour) 0.0 else (islands[v.island].contour.nearest(tmp).position - tmp).squaredLength
                    }
                }

            composition = { showMouse -> drawComposition {
                translate(0.0, height.toDouble())
                scale(1.0, -1.0)

                // Draw grid
                strokeWeight = 0.5
                stroke = ColorRGBa.GRAY.opacify(0.3)
                if (s.useGrid) grid.draw(this)

                // Draw preview of point placement
                strokeWeight = s.pSize / 4.5
                stroke = ColorRGBa.BLACK.opacify(0.5)
                fill = lightColors[type].opacify(0.5)
                if (showMouse) circle(transformMouse(mouse.position), s.pSize)

                if (s.showVoronoi) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.GRAY.opacify(0.1)
                        contours(voronoiCells)
                    }
                }

                if (s.showClusterCircles && s.clusterRadius > 0) {
                    fill = ColorRGBa.GRAY.opacify(0.3)
                    stroke = null
                    circles(points.map { it.pos }, s.clusterRadius)
                }

                if (s.showBendDistance) {
                    fill = ColorRGBa.GRAY.opacify(0.3)
                    stroke = null
                    circles(points.map { it.pos }, s.bendDistance)
                }

                if (obstacles.size > 1) {
                    if (s.showBridges) {
                        for (bridge in bridges) {
                            if (bridge.contour.empty) continue
                            isolated {

                                if (s.shadows) {
                                    val shadows = contourShadows(bridge.contour, 0.5 * s.pSize, 10, 0.08 * s.pSize)
                                    stroke = null
                                    for ((i, shadow) in shadows.withIndex()) {
                                        fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                                        contour(shadow)
                                    }
                                }

                                fill = null
                                stroke = ColorRGBa.BLACK
                                strokeWeight *= 4
                                lineJoin = LineJoin.ROUND
                                contour(bridge.contour)

                                strokeWeight /= 3
                                stroke = lightColors[islands[bridge.island1].type]
                                contour(bridge.contour)
                            }
                        }
                    }
                }

                val end = (s.subset * islands.size).roundToInt()

                for (i in 0 until end) {
                    if (s.showVisibilityContours) {
                        isolated {
                            stroke = darkColors[islands[i].type].opacify(0.3)
                            strokeWeight = s.contourStrokeWeight
                            strokeWeight *= 6
                            fill = null

                            if (i in voronoiCells.indices)
                                shapes(visibilityContours[i].map { it.intersection(voronoiCells[i].shape) })
                            else if (i in visibilityContours.indices)
                                contours(visibilityContours[i])
                        }
                    }
                }

                for (i in 0 until end) {
                    val island = islands[i]
                    strokeWeight = s.contourStrokeWeight
                    stroke = ColorRGBa.BLACK
//                    fill = lightColors[island.type].opacify(0.3)
                    fill = lightColors[island.type].mix(ColorRGBa.WHITE, 0.7)

                    val mi = mergedIndex[i]
                    val islandContour: ShapeContour = when {
                        mi != null && mergedIslands[mi].second.first() == i -> {
//                            if (m) {
                                lineJoin = LineJoin.ROUND
                                mergedIslands[mi].first
//                            }
                        }
                        i in clippedIslands.indices -> clippedIslands[i]
                        i in overlapIslands.indices -> overlapIslands[i]
                        else -> island.contour
                    }

                    if (s.showIslands && !islandContour.empty) {
                        if (s.shadows) {
                            val shadows = contourShadows(islandContour, 0.08 * s.pSize, 10, 0.08 * s.pSize)
                            stroke = null
                            for ((i, shadow) in shadows.withIndex()) {
                                fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                                contour(shadow)
                            }
                        }
                        strokeWeight = s.contourStrokeWeight
                        stroke = ColorRGBa.BLACK
                        fill = lightColors[island.type].mix(ColorRGBa.WHITE, 0.7)
                        contour(islandContour)
                    }
                }

                if (s.showVisibilityGraph) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.BLACK
                        circles(visibilityGraph.vertices.filterIsInstance<PointVertex>().map { it.pos }, s.pSize / 5.0)
                    }

                    isolated {
                        stroke = ColorRGBa.BLACK.opacify(0.5)
                        strokeWeight *= 0.5
                        fill = null
                        contours(visibilityEdges)

                        if (showMouse && vertex != null) {
                            strokeWeight *= 2.0

                            stroke = ColorRGBa.BLUE
                            fill = ColorRGBa.BLUE

                            fun drawVertex(v: Vertex) {
                                if (v is PointVertex)
                                    circle(v.pos, s.pSize / 5.0)
                                else if (v is IslandVertex) {
                                    isolated {
                                        fill = null
                                        contour(islands[v.island].contour)
                                    }
                                }
                            }

                           drawVertex(vertex)

                            stroke = ColorRGBa.GREEN
                            fill = ColorRGBa.GREEN
                            vertex.edges.forEach {
                                drawVertex(it.theOther(vertex))
                            }

                            stroke = ColorRGBa.ORANGE
                            fill = ColorRGBa.ORANGE
                            contours(vertex.edges.map { it.contour })
                        }
                    }
                }

                isolated {
                    if (s.showPoints) {
                        stroke = ColorRGBa.BLACK
                        strokeWeight = s.pointStrokeWeight
                        for (p in points) {
                            fill = lightColors[p.type]
                            circle(p.pos, s.pSize)
                        }
                    }
                }
            }}

            drawer.composition(composition(true))
            if (s.showVisibilityGraph && vertex != null) {
                drawer.isolated {
                    ortho()
                    model = Matrix44.IDENTITY
                    this.view = Matrix44.IDENTITY
                    text("${vertex::class} $vertex", Vector2(0.0, 12.0))
                }
            }
        }
    }
}

fun Vector2.mapComponents(f: (Double) -> Double) = Vector2(f(x), f(y))

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}