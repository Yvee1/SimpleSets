import geometric.voronoiDiagram
import highlights.Highlight
import highlights.toHighlight
import highlights.visibilityContours
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
import patterns.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

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
        var type = 0
        var partitionInstance: PartitionInstance
        var partition: Partition = Partition.EMPTY
        var previousPartition: Partition = Partition.EMPTY
        var partitionCost: Double = 0.0
        var deltaCost = 0.0
        var highlights = listOf<Highlight>()
        var drawing = listOf<ShapeContour>()
        var visibilityContours = listOf<List<ShapeContour>>()
        var voronoiCells = listOf<ShapeContour>()
        var visibilityGraph = Graph(emptyList(), emptyList(), emptyList())
        var visibilityEdges = listOf<ShapeContour>()
        var bridges = listOf<Bridge>()
        var composition: (Boolean) -> Composition = { _ -> drawComposition { } }
        var calculating = false

        var selectedPattern: Int? = null

        fun getPatternContour(i: Int) =
            drawing.getOrNull(i) ?: highlights.getOrNull(i)?.contour ?: partition.patterns[i].contour

        fun asyncCompute(block: () -> Unit) {
            launch {
                GlobalScope.launch {
                    try {
                        calculating = true
                        block()
                    }
                    catch(e: Throwable) {
                        e.printStackTrace()
                        calculating = false
                    }
                    calculating = false
                }.join()
            }
        }

        fun clearData(clearPartition: Boolean = true){
            if (clearPartition) {
                partition = Partition.EMPTY
                partitionInstance = PartitionInstance(emptyList())
                partitionCost = 0.0
            }
            highlights = emptyList()
            drawing = emptyList()
            visibilityContours = emptyList()
            voronoiCells = emptyList()
            visibilityGraph = Graph(emptyList(), emptyList(), emptyList())
            visibilityEdges = emptyList()
            bridges = emptyList()
            composition = { _ -> drawComposition { } }
        }

        val gui = GUI(GUIAppearance(ColorRGBa.BLUE_STEEL))

        val ds = DrawSettings()
        val cps = ComputePartitionSettings()
        val cds = ComputeDrawingSettings()
        val cbs = ComputeBridgesSettings()

        val ps = object {
            @BooleanParameter("Auto compute drawing")
            var computeDrawing = true

            @ActionParameter("Compute drawing")
            fun computeDrawing() {
//                asyncCompute {
                highlights = partition.patterns.map { it.toHighlight(cds.expandRadius) }
                if (cds.intersectionResolution == IntersectionResolution.Overlap) {
                    drawing = highlights.withIndex().map { (i, isle) ->
                        morphIsland(null, isle, highlights.subList(0, i))
                    }
                } else {
                    drawing = highlights.map { it.contour }
                }
//                }
            }

            fun modifiedPartition() {
                clearData(false)
                deltaCost = partition.updateCost(cps.singleDouble, cps.partitionClearance,
                    previousPartition.patterns - partition.patterns.toSet(),
                    partition.patterns - previousPartition.patterns.toSet()
                )
                partitionCost = partition.cost(cps.singleDouble, cps.partitionClearance)
                previousPartition = partition.copy()

                if (computeDrawing) {
                    computeDrawing()
                }
            }

            @BooleanParameter("Auto compute bridges")
            var computeBridges = false

            @ActionParameter("Greedy partition", order = 1)
            fun computeGreedy() {
                asyncCompute {
                    partitionInstance = PartitionInstance(partition.points, cps)
                    partition = partitionInstance.computePartition()
                    modifiedPartition()
                }
            }

            @ActionParameter("Mutate")
            fun mutate() {
                partition.mutate(cps)
                modifiedPartition()
            }

            @ActionParameter("Simulated annealing")
            fun doAnnealing() {
                asyncCompute {
                    partition = simulatedAnnealing(partition, cps, 1000, Random.Default)
                    modifiedPartition()
                }
            }

            @ActionParameter("Repartition close patterns")
            fun repartitionClose() {
                asyncCompute {
                    partition = repartitionClosePatterns(partition, cps, 20, Random.Default)
                    modifiedPartition()
                }
            }

            @ActionParameter("Compute bridges")
            fun computeBridges() {
                asyncCompute {
                    val obstacles = highlights.map { it.scale(1 + cbs.clearance / it.circles.first().radius) }
                    visibilityContours = highlights.map { i1 ->
                        highlights.filter { i2 -> i2.type == i1.type }
                            .flatMap { i2 -> i1.visibilityContours(i2) }
                    }
                    voronoiCells =
                        voronoiDiagram(
                            partition.patterns.map { it.original() },
                            cds.expandRadius + cbs.clearance,
//                                        approxFactor = 1.0
                        )

                    if (ds.showVisibilityGraph || ds.showBridges) {
                        visibilityGraph = Graph(highlights, obstacles, voronoiCells, cbs.smoothBridges)
                        visibilityEdges = visibilityGraph.edges.map { it.contour }
                        bridges = visibilityGraph.spanningTrees()
                    }
                }
            }
        }

        val s = object {
            @DoubleParameter("Avoid overlap", 0.0, 1.0, order = 8000)
            var avoidOverlap: Double = 0.25

            @BooleanParameter("Grid", order = 1)
            var useGrid = true

            @DoubleParameter("Grid size", 1.0, 100.0, order = 2)
            var gridSize = 20.0

            @OptionParameter("Example input", order = 10)
            var exampleInput = ExampleInput.NYC

            @ActionParameter("Load example input", order = 11)
            fun loadExample() {
                clearData()
                partition = Partition(getExampleInput(exampleInput).toMutableList())
                ps.modifiedPartition()
            }

            @TextParameter("Input ipe file (no file extension)", order = 14)
            var inputFileName = "nyc"

            @ActionParameter("Load input file", order = 15)
            fun loadInput() {
                clearData()
                try {
                    val ipe = File("input-output/$inputFileName.ipe").readText()
                    partition = Partition(ipeToPoints(ipe).toMutableList())
                    ps.modifiedPartition()
                } catch (e: IOException) {
                    println("Could not read input file")
                    e.printStackTrace()
                }
            }

            @TextParameter("Output file (no file extension)", order = 20)
            var outputFileName = "output"

            @ActionParameter("Save points", order = 23)
            fun savePoints() {
                writeToIpe(partition.points, "input-output/$outputFileName-points.ipe")
                gui.saveParameters(File("input-output/$outputFileName-parameters.json"))
            }

            @ActionParameter("Save output", order = 25)
            fun saveOutput() {
                val svg = composition(false).toSVG()
                File("input-output/$outputFileName.svg").writeText(svg)
                gui.saveParameters(File("input-output/$outputFileName-parameters.json"))
                "py svgtoipe.py input-output/$outputFileName.svg".runCommand(File("."))
            }

        }

        val ts = object {
            @OptionParameter("Tool")
            var tool: Tool = Tool.ModifyPartition
        }

        cds.alignExpandRadius(ds.pSize)
        cps.alignPartitionClearance(s.avoidOverlap, ds.pSize)
        cps.alignSingleDouble()

        gui.add(s, "General")
        gui.add(ts, "Tools")
        gui.add(ps, "Pipeline")
        gui.add(ds, "Draw settings")
        gui.add(cps, "Compute partition")
        gui.add(cds, "Compute drawing")
        gui.add(cbs, "Compute bridges")

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
        gui.onChange { varName, _ ->
            if (varName == "gridSize")
                grid = Grid(s.gridSize, drawer.bounds.center)

            if (varName == "pSize") {
                cds.alignExpandRadius(ds.pSize)
                if (ps.computeDrawing) ps.computeDrawing()
            }

            if (varName == "bendDistance" || varName == "clusterRadius") {
                cps.alignSingleDouble()
            }

            if (varName == "pSize" || varName == "avoidOverlap")
                cps.alignPartitionClearance(s.avoidOverlap, ds.pSize)
        }

        fun flip(v: Vector2) = Vector2(v.x, drawer.bounds.height - v.y)

        fun transformMouse(mp: Vector2): Vector2 {
            val v = flip((view.inversed * Vector4(mp.x, mp.y, 0.0, 1.0)).xy)
            return if (s.useGrid) grid.snap(v) else v
        }

        fun Pattern.nearby(p: Vector2): Boolean =
            when(this) {
                is SinglePoint -> point.pos.distanceTo(p) < 3 * ds.pSize
                else -> p in contour || (contour.nearest(p).position - p).length < 3 * ds.pSize
            }

        fun findPattern(mp: Vector2): Int? {
            val useHighlights = highlights.isNotEmpty()
            var foundPattern: Int? = null
            if (useHighlights) {
                for ((i, high) in highlights.withIndex()) {
                    if (mp in (drawing.getOrNull(i) ?: high.contour)) {
                        foundPattern = i
                    }
                }
            } else {
                for ((i, patt) in partition.patterns.withIndex()) {
                    if (patt.nearby(mp)) {
                        foundPattern = i
                    }
                }
            }
            return foundPattern
        }

        fun selectPattern(mp: Vector2) {
            val foundPattern = findPattern(mp)
            selectedPattern = if (foundPattern == null || foundPattern == selectedPattern) {
                null
            } else {
                foundPattern
            }
        }

        mouse.buttonDown.listen { mouseEvent ->
            if (!mouseEvent.propagationCancelled) {
                val mp = transformMouse(mouseEvent.position)
                when (ts.tool) {
                    Tool.AddPoint -> {
                        if (mouseEvent.button == MouseButton.LEFT) {
                            if (partition.points.none { it.pos == mp }) {
                                partition.add(Point(mp, type))
                                ps.modifiedPartition()
                            }
                        } else if (mouseEvent.button == MouseButton.RIGHT) {
                            val closest = partition.points.withIndex().minByOrNull { (_, p) ->
                                ((p.originalPoint ?: p).pos - mp).squaredLength
                            } ?: return@listen
                            partition.removeAt(closest.index)
                            ps.modifiedPartition()
                        }
                    }
                    Tool.ModifyPartition -> {
                        if (mouseEvent.button == MouseButton.LEFT) {
                            selectPattern(mp)
                        } else if (mouseEvent.button == MouseButton.RIGHT) {
                            if (selectedPattern != null) {
                                val singlePoints = partition.patterns.filterIsInstance<SinglePoint>()

                                var foundSp: SinglePoint? = null
                                for (sp in singlePoints) {
                                    if (partition.patterns[selectedPattern!!] == sp) continue
                                    if (mp.distanceTo(sp.point.pos) < max(3 * ds.pSize, cds.expandRadius)) {
                                        foundSp = sp
                                    }
                                }
                                if (foundSp != null) {
                                    partition.maybeAddSinglePoint(selectedPattern!!, cps, foundSp, singlePoints)
                                    ps.modifiedPartition()
                                    selectedPattern = null
                                }
                            } else {
                                var closePt: Point? = null
                                for (pt in partition.points) {
                                    if (mp.distanceTo(pt.pos) < max(3 * ds.pSize, cds.expandRadius)) {
                                        closePt = pt
                                    }
                                }
                                if (closePt != null) {
                                    partition.breakPatterns2(closePt)
                                    ps.modifiedPartition()
//                                    partition.add(closePt)
                                }
                            }
                        }
                    }
                    Tool.Repartition -> {
                        if (mouseEvent.button == MouseButton.LEFT) {
                            selectPattern(mp)
                        } else if (mouseEvent.button == MouseButton.RIGHT) {
                            if (selectedPattern == null) return@listen
                            val foundPattern = findPattern(mp)
                            if (foundPattern == null) return@listen
                            val subset = if (selectedPattern!! == foundPattern) listOf(partition.patterns[foundPattern])
                                else listOf(partition.patterns[selectedPattern!!], partition.patterns[foundPattern])
                            partition = repartitionPatterns(partition, subset, 1000, cps)
                            ps.modifiedPartition()
                            selectedPattern = null
                        }
                    }
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

                    clearData(clearPartition = false)
                    partitionInstance = PartitionInstance(partition.points, cps)
                    asyncCompute {
                        ps.computeGreedy()
                        ps.modifiedPartition()
//                        ps.doAnnealing()
//                        ps.computeDrawing()
//                        ps.computeBridges()
                    }
                }

                if (keyEvent.name == "c") {
                    keyEvent.cancelPropagation()
                    clearData()
                }

                if (keyEvent.key == KEY_BACKSPACE) {
                    keyEvent.cancelPropagation()
                    if (partition.points.isNotEmpty()){
                        partition.removeLast()
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
                        is HighlightVertex -> if (tmp in highlights[v.highlight].contour) 0.0 else (highlights[v.highlight].contour.nearest(tmp).position - tmp).squaredLength
                    }
                }

            composition = { showMouse -> drawComposition {
                translate(0.0, height.toDouble())
                scale(1.0, -1.0)

                // Draw grid
                strokeWeight = 0.5
                stroke = ColorRGBa.GRAY.opacify(0.3)
                if (s.useGrid) grid.draw(this)

                if (ds.showVoronoi) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.GRAY.opacify(0.1)
                        contours(voronoiCells)
                    }
                }

                if (ds.showBridges) {
                    for (bridge in bridges) {
                        if (bridge.contour.empty) continue
                        isolated {

                            if (ds.shadows) {
                                val shadows = contourShadows(bridge.contour, 0.5 * ds.pSize, 10, 0.08 * ds.pSize)
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
                            stroke = lightColors[highlights[bridge.island1].type]
                            contour(bridge.contour)
                        }
                    }
                }

                val end = (ds.subset * partition.patterns.size).roundToInt()

                for (i in 0 until end) {
                    if (highlights.isNotEmpty() && ds.showVisibilityContours) {
                        isolated {
                            stroke = darkColors[partition.patterns[i].type].opacify(0.3)
                            strokeWeight = ds.contourStrokeWeight
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
                    val pattern = partition.patterns[i]
                    strokeWeight = ds.contourStrokeWeight
                    stroke = ColorRGBa.BLACK
//                    fill = lightColors[island.type].opacify(0.3)
                    fill = lightColors[pattern.type].mix(ColorRGBa.WHITE, 0.7)

                    val patternContour = getPatternContour(i)

                    if (ds.showIslands && !patternContour.empty) {
                        if (ds.shadows) {
                            val shadows = contourShadows(patternContour, 0.08 * ds.pSize, 10, 0.08 * ds.pSize)
                            stroke = null
                            for ((i, shadow) in shadows.withIndex()) {
                                fill =
                                    ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                                contour(shadow)
                            }
                        }
                        strokeWeight = ds.contourStrokeWeight
                        stroke = ColorRGBa.BLACK
                        if (selectedPattern == i) {
                            strokeWeight *= 1.5
//                            stroke = ColorRGBa.ORANGE
                            stroke = darkColors[pattern.type]
                        }
                        fill = lightColors[pattern.type].mix(ColorRGBa.WHITE, 0.7)
                        contour(patternContour)

                        if (ds.showVoronoiCells && pattern is Island) {
                            isolated {
                                fill = null
                                contours(pattern.voronoiCells)
                            }
                        }
                    }
                }

                if (ds.showBendDistance) {
                    fill = ColorRGBa.GRAY.opacify(0.1)
                    stroke = null
                    circles(partition.points.map { it.pos }, cps.bendDistance)
                }

                if (ds.showClusterCircles && cps.clusterRadius > 0) {
                    fill = ColorRGBa.GRAY.opacify(0.1)
                    stroke = null
                    circles(partition.points.map { it.pos }, cps.clusterRadius)
                }

//                }
//                else {
//                    for (i in 0 until end) {
//                        val pattern = partition.patterns[i]
//                        strokeWeight = ds.contourStrokeWeight
//                        stroke = ColorRGBa.BLACK
////                    fill = lightColors[island.type].opacify(0.3)
//                        fill = lightColors[pattern.type].mix(ColorRGBa.WHITE, 0.7)
//
//                        if (ds.showIslands && !pattern.contour.empty) {
//                            strokeWeight = ds.contourStrokeWeight
//                            stroke = ColorRGBa.BLACK
//                            fill = lightColors[pattern.type].mix(ColorRGBa.WHITE, 0.7)
//                            contour(pattern.contour)
//                        }
//                    }
//                }

                if (ds.showVisibilityGraph) {
                    isolated {
                        stroke = ColorRGBa.BLACK
                        fill = ColorRGBa.BLACK
                        circles(visibilityGraph.vertices.filterIsInstance<PointVertex>().map { it.pos }, ds.pSize / 5.0)
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
                                    circle(v.pos, ds.pSize / 5.0)
                                else if (v is HighlightVertex) {
                                    isolated {
                                        fill = null
                                        contour(highlights[v.highlight].contour)
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
                    if (ds.showPoints) {
                        stroke = ColorRGBa.BLACK

                        strokeWeight = ds.pointStrokeWeight
                        for ((i, pattern) in partition.patterns.withIndex()) {
                            for (p in pattern.points) {
                                fill = lightColors[p.type]
                                isolated {
                                    if (pattern is SinglePoint && highlights.isEmpty() && selectedPattern == i) {
                                        strokeWeight *= 1.5
                                        stroke = darkColors[pattern.type]
                                    }
                                    circle(p.pos, ds.pSize)
                                }
                            }
                        }
                    }
                }

                if (ts.tool == Tool.AddPoint) {
                    // Draw preview of point placement
                    strokeWeight = ds.pointStrokeWeight
                    stroke = ColorRGBa.BLACK.opacify(0.5)
                    fill = lightColors[type].opacify(0.5)
                    if (showMouse) circle(transformMouse(mouse.position), ds.pSize)
                }
            }}

            drawer.composition(composition(true))
                drawer.isolated {
                    ortho()
                    model = Matrix44.IDENTITY
                    this.view = Matrix44.IDENTITY
                    if (calculating)
                        text("Computing...", Vector2(width - 85.0, height - 5.0))
                    if (!calculating) {
                        selectedPattern?.let { text("Selected cost: %.1f".format(partition.patterns[it].cost(cps.singleDouble)), Vector2(width - 200.0, height - 35.0)) }
                        text("Delta cost: %.1f".format(deltaCost), Vector2(width - 200.0, height - 20.0))
                        text("Cost: %.1f".format(partitionCost), Vector2(width - 200.0, height - 5.0))
                    }
                    if (ds.showVisibilityGraph && vertex != null) {
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

enum class Tool {
    AddPoint,
    ModifyPartition,
    Repartition
}
