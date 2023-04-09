import components.IconButton
import components.Toolbar
import csstype.*
import emotion.react.css
import js.core.jso
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import patterns.Point
import patterns.p
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
    val settings = Settings(pSize = pSize, useGrid = useGrid)
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

    worker.onmessage = { m: MessageEvent ->
        val completedWork :CompletedWork = Json.decodeFromString(m.data as String)
        svg = completedWork.svg
        computing = false
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
                        strokeWeight = settings.pointStrokeWeight
                    }

                    Grid {
                        showGrid = useGrid
                        onClick = {
                            useGrid = !useGrid
                        }
                    }

                    IconButton {
                        title = "Add points with mouse"
                        checked = tool == Tool.PlacePoints
                        onClick = {
                            tool = if (tool == Tool.PlacePoints) Tool.None else Tool.PlacePoints
                        }
                        +"+"
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
                            val assignment = Assignment(settings, points)
                            worker.postMessage(Json.encodeToString(assignment))
                            computing = true
                            pointsModified = false
                        }
                        +"R"
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
                        points += it.nativeEvent.offsetX p it.nativeEvent.offsetY
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

                    svg {
                        css {
                            height = 100.pct
                            width = 100.pct
                            position = Position.absolute
                        }

                        for (p in points) {
                            circle {
                                cx = p.pos.x
                                cy = p.pos.y
                                r = pSize
                                fill = "#a6cde2"
                                stroke = "black"
                                strokeWidth = settings.pointStrokeWeight
                            }
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
