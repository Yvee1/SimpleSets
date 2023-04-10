import components.IconButton
import components.Toolbar
import csstype.*
import csstype.Auto.Companion.auto
import csstype.Globals.Companion.initial
import csstype.None.Companion.none
import emotion.react.css
import js.core.jso
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.openrndr.math.Vector2
import patterns.Point
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.svg
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker

enum class Tool {
    None,
    PlacePoints
}

val worker = Worker("worker.js")

val App = FC<Props> {
    var pSize: Double by useState(5.0)
    var useGrid: Boolean by useState(true)
    val computeSettings = ComputeSettings(expandRadius = 3 * pSize)

    val blue = ColorRGB(0.651, 0.807, 0.89) to ColorRGB(0.121, 0.47, 0.705)
    val red = ColorRGB(0.984, 0.603, 0.6) to ColorRGB(0.89, 0.102, 0.109)
    val green = ColorRGB(0.698, 0.874, 0.541) to ColorRGB(0.2, 0.627, 0.172)
    val orange = ColorRGB(0.992, 0.749, 0.435) to ColorRGB(1.0, 0.498, 0.0)
    val purple = ColorRGB(0.792, 0.698, 0.839) to ColorRGB(0.415, 0.239, 0.603)

    val colorSettings = ColorSettings(listOf(blue, red, green, orange, purple))
    val drawSettings = DrawSettings(pSize = pSize, colorSettings = colorSettings)

    val emptySvg = """
        <!--?xml version="1.0" encoding="utf-8"?-->
        <svg version="1.2" baseProfile="tiny" id="openrndr-svg" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0" y="0" style="width:100%;height:100%">
        </svg>
    """.trimIndent()
    var svg: String by useState(emptySvg)

    var tool: Tool by useState(Tool.PlacePoints)
    var points: List<Point> by useState(emptyList())

    var computing: Boolean by useState(false)
    var pointsModified: Boolean by useState(false)

    var currentType: Int by useState(0)

    worker.onmessage = { m: MessageEvent ->
        val completedWork :CompletedWork = Json.decodeFromString(m.data as String)
        svg = completedWork.svg
        computing = false
        pointsModified = false
        Unit
    }

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            width = 100.pct
            height= 100.pct
        }

        h1 {
            +"Islands and bridges"
        }

        div {
            css {
                position = Position.relative
                display = Display.flex
                flexDirection = FlexDirection.column
                val marge = 10.px
                margin = marge
                width = 100.pct - 2 * marge
                height = 100.pct - 2 * marge
                border = Border(1.px, LineStyle.solid, NamedColor.black)
            }

            if (computing) {
                div {
                    css {
                        position = Position.absolute
                        width = 100.pct
                        height = 100.pct
                        background = rgba(255, 255, 255, 0.9)
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        zIndex = integer(10)
                    }
                    div {
                        css {
                            padding = 20.px
                            border = Border(1.px, LineStyle.solid, NamedColor.black)
                            background = NamedColor.white
                        }
                        +"Computing..."
                    }
                }
            }

            div {
                css {
                    borderBottom = Border(1.px, LineStyle.solid, NamedColor.black)
                }
                Toolbar {
                    PointSize {
                        pointSize = pSize
                        min = 1.0
                        max = 10.0
                        onChange = {
                            pSize = it
                        }
                        strokeWeight = drawSettings.pointStrokeWeight
                        fillColor = colorSettings.lightColors[currentType].toSvgString()
                    }

                    Grid {
                        showGrid = useGrid
                        onClick = {
                            useGrid = !useGrid
                        }
                    }

                    IconButton {
                        title = "Clear"
                        onClick = {
                            svg = emptySvg
                            points = emptyList()
                        }
                        +"C"
                    }

                    IconButton {
                        title = "Run computations"
                        onClick = {
                            val assignment: Assignment = Compute(computeSettings, points, drawSettings)
                            worker.postMessage(Json.encodeToString(assignment))
                            computing = true
                        }
                        +"R"
                    }

                    div {
                        css {
                            width = 16.px
                            flex = initial
                        }
                    }

                    for (i in 1..5) {
                        IconButton {
                            title = "Add points of color $i with mouse"
                            checked = currentType == i - 1 && tool == Tool.PlacePoints
                            onClick = {
                                tool = if (tool == Tool.PlacePoints && currentType == i - 1) Tool.None else Tool.PlacePoints
                                currentType = i - 1
                            }
                            +"$i"
                        }
                    }

                    div {
                        css {
                            flex = auto
                        }
                    }
                }
            }

            div {
                css {
                    height = 100.pct
                    width = 100.pct
                    if (tool == Tool.PlacePoints) {
                        cursor = Cursor.pointer
                    }
                    position = Position.relative
                }

                onClick = {
                    if (tool == Tool.PlacePoints) {
                        points += Point(Vector2(it.nativeEvent.offsetX, it.nativeEvent.offsetY), currentType)
                        pointsModified = true
                    }
                }

                div {
                    css {
                        height = 100.pct
                        width = 100.pct
                        position = Position.absolute
                        zIndex = integer(2)
                        if (pointsModified)
                            background = rgba(255, 255, 255, 0.9)
                    }
                }

                svg {
                    css {
                        height = 100.pct
                        width = 100.pct
                        position = Position.absolute
                        display = if (pointsModified) Display.block else none
                        zIndex = integer(3)
                    }

                    for (p in points) {
                        circle {
                            cx = p.pos.x
                            cy = p.pos.y
                            r = pSize
                            fill = colorSettings.lightColors[p.type].toSvgString()
                            stroke = "black"
                            strokeWidth = drawSettings.pointStrokeWeight
                        }
                    }
                }

                div {
                    css {
                        height = 100.pct
                        width = 100.pct
                        position = Position.absolute
                    }
                    dangerouslySetInnerHTML = jso {
                        __html = svg
                    }
                }
            }
        }
    }
}
