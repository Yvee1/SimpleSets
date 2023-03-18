import io.ExampleInput
import io.getExampleInput
import io.writeToIpe
import islands.Island
import islands.toIsland
import islands.visibilityContours
import patterns.Pattern
import patterns.Point
import patterns.computePartition
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUIAppearance
import org.openrndr.extra.parameters.*
import org.openrndr.math.*
import org.openrndr.shape.*
import kotlin.math.round
import kotlin.math.roundToInt

fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
        title = "Islands and bridges"
    }

    program {
        val blue = rgb(0.651, 0.807, 0.89) to rgb(0.121, 0.47, 0.705)
        val red = rgb(0.984, 0.603, 0.6) to rgb(0.89, 0.102, 0.109)
        val green = rgb(0.698, 0.874, 0.541) to rgb(0.2, 0.627, 0.172)

//        val colors = listOf(ColorRGBa.BLUE, ColorRGBa.RED, ColorRGBa.GREEN).map { it.mix(ColorRGBa.WHITE, 0.5).shade(0.95) }
        val colorPairs = listOf(blue, red, green)
        val lightColors = colorPairs.map { it.first }
        val darkColors = colorPairs.map { it.second }
        var points = mutableListOf<Point>()
        var problemInstance = ProblemInstance(points)
        var patterns = listOf<Pattern>()
        var islands = listOf<Island>()
        var visibilityContours = listOf<List<ShapeContour>>()
        var obstacleUnion = Shape.EMPTY
        var voronoiCells = listOf<ShapeContour>()
        var obstacles = listOf<Island>()
        var visibilityGraph = Graph(emptyList())
        var visibilityEdges = listOf<ShapeContour>()
        var bridges = listOf<Bridge>()

        fun clearData(){
            points.clear()
            problemInstance = ProblemInstance(points)
            patterns = emptyList()
            islands = emptyList()
            visibilityContours = emptyList()
            obstacleUnion = Shape.EMPTY
            voronoiCells = emptyList()
            obstacles = emptyList()
            visibilityGraph = Graph(emptyList())
            visibilityEdges = emptyList()
            bridges = emptyList()
        }

        val s = object {
            @DoubleParameter("Point size", 0.1, 10.0, order = 0)
            var pSize = 3.0

            @BooleanParameter("Grid", order = 1)
            var useGrid = true

            @DoubleParameter("Grid size", 1.0, 100.0, order = 2)
            var gridSize = 20.0

            @BooleanParameter("Disjoint", order = 5)
            var disjoint = true

            @OptionParameter("Example input", order = 10)
            var exampleInput = ExampleInput.LowerBound

            @ActionParameter("Load input", order = 11)
            fun load() {
                clearData()
                points = getExampleInput(exampleInput).toMutableList()
            }

            @ActionParameter("Save as ipe file", order = 20)
            fun save() = writeToIpe(problemInstance, patterns, fileName)

            @TextParameter("File name", order = 21)
            var fileName = "output.ipe"

            @BooleanParameter("Island offset", order=200)
            var offset = true

            @DoubleParameter("Bend distance", 1.0, 1000.0, order=1000)
            var bendDistance = 20.0

            @BooleanParameter("Inflection", order=2000)
            var bendInflection = true

            @DoubleParameter("Max bend angle", 0.0, 180.0, order=3000)
            var maxBendAngle = 180.0

            @DoubleParameter("Max turning angle", 0.0, 180.0, order=4000)
            var maxTurningAngle = 180.0

            @DoubleParameter("Cluster radius", 0.0, 100.0, order=8000)
            var clusterRadius = 50.0

            @BooleanParameter("Show visibility contours", order = 9000)
            var showVisibilityContours = true

            @BooleanParameter("Show bridges", order = 9010)
            var showBridges = true

            @BooleanParameter("Show cluster circles", order = 10000)
            var showClusterCircles = false

            @BooleanParameter("Show visibility graph", order=10010)
            var showVisibilityGraph = false

            @BooleanParameter("Show voronoi", order=10020)
            var showVoronoi = false

            @DoubleParameter("Show subset based on computation", 0.0, 1.0, order=10000000)
            var subset = 1.0
        }

        val gui = GUI(GUIAppearance(ColorRGBa.BLUE_STEEL))
        gui.add(s, "Settings")

        extend(gui)
        gui.visible = false
        gui.compartmentsCollapsedByDefault = false

        class Grid(val cellSize: Double, val center: Vector2){
            fun snap(p: Vector2): Vector2 = (p - center).mapComponents { round(it / cellSize) * cellSize } + center

            fun draw(drawer: Drawer){
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
                drawer.isolated {
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
                when (mouseEvent.button) {
                    MouseButton.LEFT -> {
                        mouseEvent.cancelPropagation()
                        0
                    }
                    MouseButton.RIGHT -> {
                        mouseEvent.cancelPropagation()
                        1
                    }
                    else -> null
                }?.let { t ->
                    val p = transformMouse(mouseEvent.position)
                    if (points.none { it.pos == p })
                        points.add(Point(p, t))
                }
            }

        }

        keyboard.keyDown.listen {
            if (!it.propagationCancelled) {
                if (it.key == KEY_SPACEBAR) {
                    it.cancelPropagation()
                    problemInstance = ProblemInstance(points, s.pSize * 5 / 2, s.clusterRadius, s.bendDistance, s.bendInflection, s.maxBendAngle, s.maxTurningAngle)
                    patterns = problemInstance.computePartition(s.disjoint)
                    islands = patterns.map { it.toIsland(s.pSize * 5 / 2) }
                    obstacles = patterns.map { it.toIsland(s.pSize * 10 / 2) }
                    visibilityContours = islands.map { i1 -> islands.filter { i2 -> i2.type == i1.type }.flatMap { i2 -> i1.visibilityContours(i2) } }
                    obstacleUnion = obstacles.fold(Shape.EMPTY) { acc, x ->
                        x.contour.shape.union(acc)
                    }
                    val islandUnion = islands.fold(Shape.EMPTY) { acc, x ->
                        x.contour.shape.union(acc)
                    }
                    voronoiCells = voronoiDiagram(patterns.map { it.original() })
                    visibilityGraph = Graph(islands, s.pSize * 5 / 2)
                    visibilityEdges = visibilityGraph.edges.map { it.contour }
                    bridges = visibilityGraph.spanningTrees()
                }

                if (it.name == "c") {
                    it.cancelPropagation()
                    clearData()
                }

                if (it.key == KEY_BACKSPACE) {
                    it.cancelPropagation()
                    if (points.isNotEmpty()){
                        points.removeLast()
                    }
                }
            }
        }

        val font = loadFont("data/fonts/default.otf", 64.0)

        extend(Camera2D())
        extend {
            view = drawer.view
            drawer.clear(ColorRGBa.WHITE)
            drawer.apply {
                translate(0.0, height.toDouble())
                scale(1.0, -1.0)
                fontMap = font

                // Draw grid
                strokeWeight = 0.5
                stroke = ColorRGBa.GRAY.opacify(0.3)
                if (s.useGrid) grid.draw(this)

                // Draw preview of point placement
                strokeWeight = s.pSize / 4.5
                stroke = ColorRGBa.BLACK.opacify(0.5)
                fill = ColorRGBa.GRAY.opacify(0.5)
                circle(transformMouse(mouse.position), s.pSize)

                if (s.showVoronoi) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.GRAY.opacify(0.3)
                        contours(voronoiCells)
                    }
                }

                if (s.showClusterCircles) {
                    fill = ColorRGBa.GRAY.opacify(0.3)
                    stroke = null
                    circles(points.map { it.pos }, s.clusterRadius)
                }

                if (obstacles.size > 1) {
                    if (s.showBridges) {
                        for (bridge in bridges) {
                            isolated {
                                stroke = ColorRGBa.BLACK
                                strokeWeight *= 4
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
                    val island = islands[i]
                    stroke = ColorRGBa.BLACK
                    fill = lightColors[island.type].opacify(0.3)
                    shape(voronoiCells[i].intersection(island.contour.shape))

                    if (s.showVisibilityContours) {
                        isolated {
                            stroke = darkColors[island.type].opacify(0.3)
                            strokeWeight *= 4
                            shapes(visibilityContours[i].map { it.intersection(voronoiCells[i].shape) })
                        }
                    }
                }

                if (s.showVisibilityGraph) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.BLACK
                        circles(visibilityGraph.vertices.map { it.pos }, s.pSize / 5.0)
                    }

                    isolated {
                        stroke = ColorRGBa.BLACK.opacify(0.5)
                        strokeWeight *= 0.5
                        contours(visibilityEdges)

                        val vertex =
                            visibilityGraph.vertices.minByOrNull { (it.pos - transformMouse(mouse.position)).squaredLength }
                        if (vertex != null) {
                            strokeWeight *= 2.0

                            stroke = ColorRGBa.BLUE
                            fill = ColorRGBa.BLUE
                            circle(vertex.pos, s.pSize / 5.0)

                            stroke = ColorRGBa.GREEN
                            fill = ColorRGBa.GREEN
                            circles(vertex.edges.map { it.theOther(vertex).pos }, s.pSize / 5.0)

                            stroke = ColorRGBa.ORANGE
                            fill = ColorRGBa.ORANGE
                            contours(vertex.edges.map { it.contour })
                        }
                    }
                }

                isolated {
                    stroke = ColorRGBa.BLACK
                    strokeWeight = s.pSize / 4
                    for (p in points) {
                        fill = lightColors[p.type]
                        circle(p.pos, s.pSize)
                    }
                }
            }
        }
    }
}

fun Vector2.mapComponents(f: (Double) -> Double) = Vector2(f(x), f(y))