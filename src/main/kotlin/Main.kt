import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.draw.loadFont
import org.openrndr.extra.color.presets.BLUE_STEEL
import org.openrndr.extra.gui.GUI
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
        val colors = listOf(ColorRGBa.RED, ColorRGBa.BLUE).map { it.mix(ColorRGBa.WHITE, 0.5).shade(0.95) }
        var points = mutableListOf<Point>()
        var tPoints = transformPoints(points)
        var stripeData = StripeData(tPoints)
        var islands = listOf<ConvexIsland>()

        fun clearData(){
            points.clear()
            tPoints = listOf()
            stripeData = StripeData(points)
            islands = listOf()
        }

        val s = object {
            @DoubleParameter("Point size", 0.1, 10.0, order = 0)
            var pSize = 3.0

            @BooleanParameter("Grid", order = 1)
            var useGrid = true

            @DoubleParameter("Grid size", 1.0, 100.0, order = 2)
            var gridSize = 20.0

            @OptionParameter("Example input", order = 10)
            var exampleInput = ExampleInput.LowerBound

            @ActionParameter("Load input", order = 11)
            fun load() {
                clearData()
                points = getExampleInput(exampleInput).toMutableList()
            }
        }

        val gui = GUI(ColorRGBa.BLUE_STEEL)
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
                    tPoints = transformPoints(points)
                    stripeData = StripeData(tPoints)
                    islands = computeIslandPartition(tPoints)
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

        fun Drawer.drawStripe(p: Point, q: Point){
            isolated {
                stroke = null
                contour(
                    ShapeContour.fromPoints(
                        listOf(
                            p.pos,
                            q.pos,
                            Vector2(q.pos.x, bounds.height),
                            Vector2(p.pos.x, bounds.height)
                        ), true
                    )
                )
                stroke = ColorRGBa.BLACK
                lineSegment(p.pos, q.pos)
                fill = ColorRGBa.BLACK
                text(stripeData.stripe.getOrDefault(p to q, 0).toString(), (p.pos + q.pos)/2.0 + Vector2(0.0, (bounds.height - max(p.pos.y, q.pos.y))/2))
            }
        }

        fun Drawer.drawTriangle(i: Int, j: Int, k: Int){
            fun doo(p: Point, q: Point, r: Point) {
                isolated {
                    stroke = null
                    contour(
                        ShapeContour.fromPoints(
                            listOf(
                                p.pos,
                                q.pos,
                                r.pos
                            ), true
                        )
                    )
                    stroke = ColorRGBa.BLACK
                    lineSegment(p.pos, q.pos)
                    lineSegment(q.pos, r.pos)
                    lineSegment(p.pos, r.pos)
                }
            }

            doo(points[i], points[j], points[k])
            doo(tPoints[i], tPoints[j], tPoints[k])
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
                strokeWeight = 1.0
                stroke = ColorRGBa.BLACK.opacify(0.5)
                fill = ColorRGBa.GRAY.opacify(0.5)
                circle(transformMouse(mouse.position), s.pSize)
                for (island in islands) {
                    fill = colors[island.points[0].type].opacify(0.5)
                    if (island.points.size > 1) {
                        contour(ShapeContour.fromPoints(island.points.map { it.originalPoint!!.pos }, true))
                    } else if (island.points.size == 1) {
                        circle(island.points[0].originalPoint!!.pos, s.pSize * 2)
                    }
                }

//                if (s.i in tPoints.indices && s.j in tPoints.indices && s.k in tPoints.indices
//                    && s.i in points.indices && s.j in points.indices && s.k in points.indices
//                    && s.i != s.j && s.j != s.k && s.k != s.i) {
//                    drawStripe(tPoints[s.i], tPoints[s.j])
//                    drawStripe(tPoints[s.j], tPoints[s.k])
//                    drawStripe(tPoints[s.i], tPoints[s.k])
//                    drawTriangle(s.i, s.j, s.k)
//                }
                isolated {
                    stroke = ColorRGBa.BLACK
                    strokeWeight = s.pSize / 4.5
                    for (p in points) {
                        fill = colors[p.type]
                        circle(p.pos, s.pSize)
                    }
                }

//                for (p in tPoints) {
//                    stroke = ColorRGBa.BLACK.opacify(0.3)
//                    fill = colors[p.type].opacify(0.3)
//                    circle(p.pos, s.pSize)
//                }
            }
        }
    }
}

fun Vector2.mapComponents(f: (Double) -> Double) = Vector2(f(x), f(y))