import components.*
import csstype.*
import csstype.Auto.Companion.auto
import csstype.Globals.Companion.initial
import csstype.None.Companion.none
import emotion.react.css
import js.core.asList
import js.core.jso
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import patterns.Point
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.svg
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import web.html.HTMLDivElement
import web.uievents.Touch

enum class Tool {
    None,
    PlacePoints
}

val worker = Worker("worker.js")

val App = FC<Props> {
    var pSize: Double by useState(5.0)
    var useGrid: Boolean by useState(true)
    var bendDistance: Double by useState(75.0)
    var bendInflection: Boolean by useState(true)
    var maxBendAngle: Double by useState(180.0)
    var maxTurningAngle: Double by useState(90.0)
    var clusterRadius: Double by useState(50.0)
    val computeSettings = ComputeSettings(
        expandRadius = 3 * pSize,
        bendDistance = bendDistance,
        bendInflection = bendInflection,
        maxBendAngle = maxBendAngle,
        maxTurningAngle = maxTurningAngle,
        clusterRadius = clusterRadius,
    )

    val blue = ColorRGB(0.651, 0.807, 0.89) to ColorRGB(0.121, 0.47, 0.705)
    val red = ColorRGB(0.984, 0.603, 0.6) to ColorRGB(0.89, 0.102, 0.109)
    val green = ColorRGB(0.698, 0.874, 0.541) to ColorRGB(0.2, 0.627, 0.172)
    val orange = ColorRGB(0.992, 0.749, 0.435) to ColorRGB(1.0, 0.498, 0.0)
    val purple = ColorRGB(0.792, 0.698, 0.839) to ColorRGB(0.415, 0.239, 0.603)

    val colorSettings = ColorSettings(listOf(blue, red, green, orange, purple))
    val drawSettings = DrawSettings(pSize = pSize, colorSettings = colorSettings)

    var offset: Vector2 by useState(Vector2.ZERO)
    val svgContainerRef: MutableRefObject<HTMLDivElement> = useRef(null)
    var mouseDown: Boolean by useState(false)
    var svgSize by useState(IntVector2(svgContainerRef.current?.clientWidth ?: 0, svgContainerRef.current?.clientHeight ?: 0))
    val viewBoxTransform = "${-offset.x} ${-offset.y} ${svgSize.x} ${svgSize.y}"

    val windowSize = useWindowSize()

    useEffect(windowSize) {
        val svgContainer = svgContainerRef.current ?: return@useEffect
        svgSize = IntVector2(svgContainer.clientWidth, svgContainer.clientHeight)
    }

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

//    var previousTouch: Touch? by useState(null)
    var previousTouch: Touch? = null

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
            css {
                fontWeight = FontWeight.normal
            }
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
                fontFamily = FontFamily.sansSerif
                fontSize = (13 + 1.0/3.0).px
            }

            tabIndex = 0
            onKeyDown = {
                if (it.key in (1..5).map { it.toString() }) {
                    currentType = it.key.toInt() - 1
                }
                if (it.key == "R") {

                }
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
                    zIndex = integer(20)
                }
                Toolbar {
                    val whiteSpace = div.create {
                        css {
                            width = 16.px
                            flex = initial
                            flexShrink = number(10000.0)
                        }
                    }

                    Expandable {
                        expander = div.create {
                            css {
                                alignItems = AlignItems.center
                                justifyContent = JustifyContent.center
                                display = Display.flex
                                flexWrap = FlexWrap.nowrap
                                val padW = 8.px
                                padding = Padding(0.px, padW)
                                height = 100.pct
                                width = 100.pct - 2 * padW
                                cursor = Cursor.default
                            }
                            +"Bend"
                        }
                        expandee = div.create {
                            css {
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                padding = Padding(7.5.px, 10.px)
                            }
                            Checkbox {
                                title = "Allow bend inflection"
                                checked = bendInflection
                                label = "Bend inflection"
                                onClick = {
                                    bendInflection = !bendInflection
                                }
                            }
                            Slider {
                                title = "Change bend distance"
                                step = "any".unsafeCast<Double>()
                                min = 1.0
                                max = 500.0
                                value = bendDistance
                                unit = ""
                                label = "Bend distance"
                                onChange = {
                                    bendDistance = it.currentTarget.valueAsNumber
                                }
                            }
                            Slider {
                                title = "Change max bend angle"
                                step = "any".unsafeCast<Double>()
                                min = 0.0
                                max = 180.0
                                value = maxBendAngle
                                unit = "°"
                                label = "Max bend angle"
                                onChange = {
                                    maxBendAngle = it.currentTarget.valueAsNumber
                                }
                            }
                            Slider {
                                title = "Change max turning angle"
                                step = "any".unsafeCast<Double>()
                                min = 0.0
                                max = 180.0
                                value = maxTurningAngle
                                unit = "°"
                                label = "Max turning angle"
                                onChange = {
                                    maxTurningAngle = it.currentTarget.valueAsNumber
                                }
                            }
                        }
                    }

                    Expandable {
                        expander = div.create {
                            css {
                                alignItems = AlignItems.center
                                justifyContent = JustifyContent.center
                                display = Display.flex
                                flexWrap = FlexWrap.nowrap
                                val padW = 8.px
                                padding = Padding(0.px, padW)
                                height = 100.pct
                                width = 100.pct - 2 * padW
                                cursor = Cursor.default
                            }
                            +"Cluster"
                        }
                        expandee = div.create {
                            css {
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                padding = Padding(7.5.px, 10.px)
                            }
                            Slider {
                                title = "Change cluster radius"
                                step = "any".unsafeCast<Double>()
                                min = 0.0
                                max = 100.0
                                value = clusterRadius
                                unit = ""
                                label = "Cluster radius"
                                onChange = {
                                    clusterRadius = it.currentTarget.valueAsNumber
                                }
                            }
                        }
                    }

                    +whiteSpace

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

                    +whiteSpace

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

//                    +whiteSpace

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

                ref = svgContainerRef

                onClick = {
                    if (tool == Tool.PlacePoints) {
                        points += Point(Vector2(it.nativeEvent.offsetX, it.nativeEvent.offsetY) - offset, currentType)
                        pointsModified = true
                    }
                }

                onMouseDown = {
                    mouseDown = true
                }
                onTouchStart = {
                    mouseDown = true
                    previousTouch = it.touches[0]
                    println("Touch start")
                }
                onMouseUp = {
                    mouseDown = false
                }
                onTouchEnd = {
                    mouseDown = false
                    previousTouch = null
                    println("Touch end")
                }
                val moveListener = { dx: Double, dy: Double ->
                    if (tool == Tool.None && mouseDown) {
                        offset += Vector2(dx, dy)
                    }
                }
                onMouseMove = { moveListener(it.movementX, it.movementY) }
                onTouchMove = { e ->
                    if (previousTouch != null) {
                        val touch = e.touches.asList()
                            .map { it to Vector2(it.clientX, it.clientY).squaredDistanceTo(Vector2(previousTouch!!.clientX, previousTouch!!.clientY)) }
                            .filter { it.second < 20.0 }
                            .minByOrNull { it.second }?.first ?: previousTouch!!
                        val dx = touch.clientX - previousTouch!!.clientX
                        val dy = touch.clientY - previousTouch!!.clientY
                        println("dx: $dx     dy: $dy")
                        moveListener(dx, dy)
                        previousTouch = touch
                    }
                    previousTouch = e.touches[0]
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

                    viewBox = viewBoxTransform

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
                    svg {
                        css {
                            height = 100.pct
                            width = 100.pct
                        }

                        viewBox = viewBoxTransform

                        dangerouslySetInnerHTML = jso {
                            __html = svg
                        }
                    }
                }
            }
        }
    }
}
