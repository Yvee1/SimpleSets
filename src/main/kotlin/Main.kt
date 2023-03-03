import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUIAppearance
import org.openrndr.extra.parameters.ActionParameter
import org.openrndr.extra.parameters.BooleanParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.OptionParameter
import org.openrndr.math.*
import org.openrndr.shape.*
import kotlin.math.max
import kotlin.math.round

fun main() = application {
    configure {
        width = 800
        height = 800
        windowResizable = true
        title = "Islands"
    }

    program {
        val colors = listOf(ColorRGBa.BLUE, ColorRGBa.RED, ColorRGBa.GREEN).map { it.mix(ColorRGBa.WHITE, 0.5).shade(0.95) }
        var points = mutableListOf<Point>()
        var problemInstance: ProblemInstance
        var patterns = listOf<Pattern>()

        fun clearData(){
            points.clear()
            problemInstance = ProblemInstance(points)
            patterns = emptyList()
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
            val v = (view.inversed * Vector4(mp.x, mp.y, 0.0, 1.0)).xy
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
                    val p = flip(transformMouse(mouseEvent.position))
                    if (points.none { it.pos == p })
                        points.add(Point(p, t))
                }
            }

        }

        keyboard.keyDown.listen {
            if (!it.propagationCancelled) {
                if (it.key == KEY_SPACEBAR) {
                    it.cancelPropagation()
                    problemInstance = ProblemInstance(points, s.bendDistance, s.bendInflection, s.maxBendAngle, s.maxTurningAngle)
                    patterns = problemInstance.computePartition(s.disjoint)
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

                for (pattern in patterns) {
                    if (pattern is ConvexIsland) {
                        val island = pattern
                        stroke = ColorRGBa.BLACK
                        fill = colors[island.points[0].type].opacify(0.5)
                        if (island.points.size > 1) {
//                            fill = ColorRGBa.RED
                            val c = ShapeContour.fromPoints(island.points.map { flip(it.originalPoint!!.pos) }, true)
                            contour(if (s.offset) c.buffer(s.pSize * 5 / 2) else c)
                        } else if (island.points.size == 1) {
                            circle(flip(island.points[0].originalPoint!!.pos), if (s.offset) s.pSize * 5 / 2 else s.pSize)
                        }
                    }
                    if (pattern is Bend) {
                        stroke = ColorRGBa.BLACK
                        fill = colors[pattern.points[0].type].opacify(0.5)
                        val c = ShapeContour.fromPoints(pattern.points.map { flip(it.originalPoint!!.pos) }, false)
                        contour(if (s.offset) c.buffer(s.pSize * 5 / 2) else c)
                    }
                }

                isolated {
                    stroke = ColorRGBa.BLACK
                    strokeWeight = s.pSize / 4.5
                    for (p in points) {
                        fill = colors[p.type]
                        circle(flip(p.pos), s.pSize)
                    }
                }
            }
        }
    }
}

fun Vector2.mapComponents(f: (Double) -> Double) = Vector2(f(x), f(y))