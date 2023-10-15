import highlights.Highlight
import highlights.toHighlight
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUIAppearance
import org.openrndr.extra.parameters.*
import org.openrndr.math.*
import org.openrndr.shape.*
import org.openrndr.svg.toSVG
import patterns.*
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(DelicateCoroutinesApi::class)
fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
        title = "Islands and bridges"
    }

    program {
        var partition: Partition = Partition.EMPTY
        var filtration: List<Pair<Double, Partition>> = emptyList()
        var highlights = listOf<Highlight>()
        var drawing = listOf<ShapeContour>()
        var composition: (Boolean) -> Composition = { _ -> drawComposition { } }
        var calculating = false

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
                filtration = emptyList()
            }
            highlights = emptyList()
            drawing = emptyList()
            composition = { _ -> drawComposition { } }
        }

        val gui = GUI(GUIAppearance(ColorRGBa.BLUE_STEEL))

        val ds = DrawSettings()
        val cps = ComputePartitionSettings()
        val cds = ComputeDrawingSettings()
        val tgs = TopoGrowSettings()

        val ps = object {
            @BooleanParameter("Auto compute drawing")
            var computeDrawing = true

            @ActionParameter("Compute drawing")
            fun computeDrawing() {
//                asyncCompute {
                highlights = partition.patterns.map { it.toHighlight(cds.expandRadius) }
                if (cds.intersectionResolution == IntersectionResolution.Overlap) {
                    drawing = highlights.withIndex().map { (i, isle) ->
                        morphHighlight(null, isle, highlights.subList(0, i), cds)
                    }
                } else {
                    drawing = highlights.map { it.contour }
                }
//                }
            }

            fun modifiedPartition() {
                clearData(false)

                if (computeDrawing) {
                    computeDrawing()
                }
            }

            var computeBridges = false

            fun computeFiltration() {
                asyncCompute {
                    filtration = topoGrow(partition.points, cps, tgs)
                    val newPartition = filtration.takeWhile { it.first < cps.coverRadius }.lastOrNull()?.second
                    if (newPartition != null) {
                        partition = newPartition
                        modifiedPartition()
                    }
                }
            }
        }

        val inputOutputSettings = object {
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
        
        val uiSettings = object {
            @BooleanParameter("Grid", order = 1)
            var useGrid = false

            @DoubleParameter("Grid size", 1.0, 100.0, order = 2)
            var gridSize = 20.0
        }

        // Simple compute settings
        val scs = object {
            @DoubleParameter("Point size", 0.1, 10.0, order = 0)
            var pSize: Double = 2.5

            @DoubleParameter("Cover radius", 2.5, 6.0, order = 8000)
            var coverRadius: Double = 3.5

//            @DoubleParameter("Avoid overlap", 0.0, 1.0, order = 8000)
//            var avoidOverlap: Double = 0.25
        }

        ds.pSize = scs.pSize
        cds.alignExpandRadius(ds.pSize)
        cps.alignPartitionClearance(1.0, cds.expandRadius)
        cps.coverRadius = scs.coverRadius * cds.expandRadius
        cps.alignSingleDouble()

        gui.add(inputOutputSettings, "Input output settings")
//        gui.add(uiSettings, "UI Settings")
        gui.add(scs, "Compute settings")
        gui.add(ps, "Pipeline")
        gui.add(tgs, "Topo grow settings")

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

        var grid = Grid(uiSettings.gridSize, drawer.bounds.center)
        gui.onChange { varName, _ ->
            if (varName == "gridSize")
                grid = Grid(uiSettings.gridSize, drawer.bounds.center)

            if (varName == "pSize") {
                ds.pSize = scs.pSize
                cds.alignExpandRadius(ds.pSize)
                cps.alignPartitionClearance(1.0, cds.expandRadius)
                if (ps.computeDrawing) ps.computeDrawing()
            }

            if (varName == "bendDistance" || varName == "clusterRadius") {
                cps.alignSingleDouble()
            }

            if (varName == "coverRadius" || varName == "pSize") {
                cps.coverRadius = scs.coverRadius * cds.expandRadius
                val newPartition = filtration.takeWhile { it.first < cps.coverRadius }.lastOrNull()?.second
                if (newPartition != null) {
                    partition = newPartition
                    ps.modifiedPartition()
                }
            }
        }

        fun flip(v: Vector2) = Vector2(v.x, drawer.bounds.height - v.y)

        fun transformMouse(mp: Vector2): Vector2 {
            val v = flip((view.inversed * Vector4(mp.x, mp.y, 0.0, 1.0)).xy)
            return if (uiSettings.useGrid) grid.snap(v) else v
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

        keyboard.keyDown.listen { keyEvent ->
            if (!keyEvent.propagationCancelled) {
                if (keyEvent.key == KEY_SPACEBAR) {
                    keyEvent.cancelPropagation()
                    if (calculating) return@listen

                    clearData(clearPartition = false)
                    asyncCompute {
                        ps.computeFiltration()
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

            composition = { showMouse -> drawComposition {
                translate(0.0, height.toDouble())
                scale(1.0, -1.0)

                // Draw grid
                strokeWeight = 0.5
                stroke = ColorRGBa.GRAY.opacify(0.3)
                if (uiSettings.useGrid) grid.draw(this)

                val end = min((ds.subset * partition.patterns.size).roundToInt(), partition.patterns.size)

                for (i in 0 until end) {
                    val pattern = partition.patterns[i]
                    strokeWeight = ds.contourStrokeWeight
                    stroke = ColorRGBa.BLACK
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

//                fill = ColorRGBa.GRAY.opacify(0.1)
//                stroke = null
//                circles(partition.points.map { it.pos }, cps.coverRadius)

                isolated {
                    if (ds.showPoints) {
                        stroke = ColorRGBa.BLACK

                        strokeWeight = ds.pointStrokeWeight
                        for ((i, pattern) in partition.patterns.withIndex()) {
                            for (p in pattern.points) {
                                fill = lightColors[p.type]
                                isolated {
                                    circle(p.pos, ds.pSize)
                                }
                            }
                        }
                    }
                }
            }}
            drawer.composition(composition(true))
        }
    }
}
