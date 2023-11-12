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
import java.util.concurrent.TimeUnit
import kotlin.math.round

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
        var composition: (Boolean) -> Composition = { _ -> drawComposition { } }
        var calculating = false

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

        val gui = GUI(GUIAppearance(ColorRGBa.BLUE_STEEL))

        val ds = DrawSettings()
        val gs = GeneralSettings()
        val cds = ComputeDrawingSettings()
        val tgs = TopoGrowSettings()

        var xGraph = XGraph(emptyList(), gs, cds)

        fun clearData(clearPartition: Boolean = true){
            if (clearPartition) {
                partition = Partition.EMPTY
                filtration = emptyList()
            }
            highlights = emptyList()
            xGraph = XGraph(emptyList(), gs, cds)
            composition = { _ -> drawComposition { } }
        }

        val cs = object {
            @DoubleParameter("Cover", 1.0, 8.0)
            var cover: Double = 3.0
        }

        val ps = object {
            @BooleanParameter("Auto compute drawing")
            var computeDrawing = true

            @ActionParameter("Compute drawing")
            fun computeDrawing() {
                try {
                    highlights = partition.patterns.map { it.toHighlight(gs.expandRadius) }
                    xGraph = XGraph(highlights, gs, cds)
                }
                catch(e: Throwable) {
                    e.printStackTrace()
                }
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
                    filtration = topoGrow(partition.points, gs, tgs)
                    val newPartition = filtration.takeWhile { it.first < cs.cover * gs.expandRadius }.lastOrNull()?.second
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
//        val scs = object {
//            @DoubleParameter("Point size", 0.1, 10.0, order = 0)
//            var pSize: Double = 2.5
//
//            @DoubleParameter("Cover radius", 2.5, 6.0, order = 8000)
//            var coverRadius: Double = 3.5
//
//            @DoubleParameter("Smoothing", 0.001, 0.3, order = 10000)
//            var smoothing: Double = 0.2
//            @DoubleParameter("Avoid overlap", 0.0, 1.0, order = 8000)
//            var avoidOverlap: Double = 0.25
//        }


        gui.add(inputOutputSettings, "Input output settings")
//        gui.add(uiSettings, "UI Settings")
        gui.add(gs, "General settings")
        gui.add(tgs, "Grow settings")
        gui.add(cs, "Cover settings")
        gui.add(cds, "Compute drawing settings")
        gui.add(ps, "Pipeline")

        extend(gui)
        gui.visible = false
        gui.compartmentsCollapsedByDefault = false

//        ds.pSize = scs.pSize
//        cds.alignExpandRadius(ds.pSize)
//        cps.alignPartitionClearance(1.0, cds.expandRadius)
//        cps.coverRadius = scs.coverRadius * cds.expandRadius
//        cps.alignSingleDouble()

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
//                ds.pSize = scs.pSize
//                cds.alignExpandRadius(ds.pSize)
//                cps.alignPartitionClearance(1.0, cds.expandRadius)
                if (ps.computeDrawing) ps.computeDrawing()
            }

//            if (varName == "bendDistance" || varName == "clusterRadius") {
//                cps.alignSingleDouble()
//            }

            if (varName == "cover" || varName == "pSize") {
//                cps.coverRadius = scs.coverRadius * cds.expandRadius
                val newPartition = filtration.takeWhile { it.first < cs.cover * gs.expandRadius }.lastOrNull()?.second
                if (newPartition != null) {
                    partition = newPartition
                    ps.modifiedPartition()
                }
            }

            if (varName == "smoothing") {
                ps.computeDrawing()
            }
        }

        fun flip(v: Vector2) = Vector2(v.x, drawer.bounds.height - v.y)

        fun transformMouse(mp: Vector2): Vector2 {
            val v = flip((view.inversed * Vector4(mp.x, mp.y, 0.0, 1.0)).xy)
            return if (uiSettings.useGrid) grid.snap(v) else v
        }

        fun Pattern.nearby(p: Vector2): Boolean =
            when(this) {
                is SinglePoint -> point.pos.distanceTo(p) < 3 * gs.pSize
                else -> p in contour || (contour.nearest(p).position - p).length < 3 * gs.pSize
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

                xGraph.draw(this, ds)

                isolated {
                    if (ds.showPoints) {
                        stroke = ColorRGBa.BLACK

                        strokeWeight = ds.pointStrokeWeight(gs)
                        for ((i, pattern) in partition.patterns.withIndex()) {
                            for (p in pattern.points) {
                                fill = lightColors[p.type]
                                isolated {
                                    circle(p.pos, gs.pSize)
                                }
                            }
                        }
                    }
                }

//                stroke = null
//                fill = ColorRGBa.BLACK.opacify(0.1)
//                circles(partition.points.map { it.pos }, cps.coverRadius)
            }}
            drawer.composition(composition(true))
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
