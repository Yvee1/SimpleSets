import components.*
import contexts.*
import web.cssom.*
import web.cssom.Auto.Companion.auto
import web.cssom.Globals.Companion.initial
import web.cssom.None.Companion.none
import emotion.react.css
import js.core.jso
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.openrndr.math.IntVector2
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import patterns.Point
import react.*
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.svg
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import react.dom.events.MouseEvent
import react.dom.events.NativeMouseEvent
import react.dom.events.PointerEvent
import sideWindow.Divider
import sideWindow.DraggableDivider
import sideWindow.SelectExample
import sideWindow.SideWindow
import sideWindow.settings.BankSettingsPanel
import sideWindow.settings.ColorSettingsPanel
import sideWindow.settings.PointSettingsPanel
import web.buffer.Blob
import web.buffer.BlobPart
import web.dom.Element
import web.dom.document
import web.html.*
import web.url.URL
import kotlinx.browser.window
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.progress
import sideWindow.settings.GrowSettingsPanel

enum class Tool {
    None,
    PlacePoints
}

val worker = Worker("worker.js")

// Card border radius
val cardBr = 20.px

val App = FC<Props> {
    var useGrid: Boolean by useState(true)

    val (pointSize, pointSizeSetter) = useState(5.0)
    val pointSettings = object: PointSettings {
        override var pointSize: Double
            get() = pointSize
            set(v) = pointSizeSetter(v)
    }

    var cover: Double by useState(3.0)

    val (bendInflection, bendInflectionSetter) = useState(true)
    val (maxBendAngle, maxBendAngleSetter) = useState(120.0)
    val (maxTurningAngle, maxTurningAngleSetter) = useState(60.0)

    val bendSettings = object: BendSettings {
        override var bendInflection: Boolean
            get() = bendInflection
            set(v) = bendInflectionSetter(v)
        override var maxBendAngle: Double
            get() = maxBendAngle
            set(v) = maxBendAngleSetter(v)
        override var maxTurningAngle: Double
            get() = maxTurningAngle
            set(v) = maxTurningAngleSetter(v)
    }

    val generalSettings = GeneralSettings(
        bendInflection = bendInflection,
        maxBendAngle = maxBendAngle,
        maxTurningAngle = maxTurningAngle,
        pSize = pointSize
    )

    val computeDrawingSettings = ComputeDrawingSettings(
        intersectionResolution = IntersectionResolution.Overlap
    )

    val (postponeIntersections, postponeIntersectionsSetter) = useState(false)
    val (forbidTooClose, forbidTooCloseSetter) = useState(0.5)

    val grow = object: Grow {
        override var postponeIntersections: Boolean
            get() = postponeIntersections
            set(v) = postponeIntersectionsSetter(v)
        override var forbidTooClose: Double
            get() = forbidTooClose
            set(v) = forbidTooCloseSetter(v)
    }

    val growSettings = GrowSettings(
        postponeIntersections = grow.postponeIntersections,
        forbidTooClose = grow.forbidTooClose
    )

    val blue = ColorRGB(0.651, 0.807, 0.89)// to ColorRGB(0.121, 0.47, 0.705)
    val red = ColorRGB(0.984, 0.603, 0.6)// to ColorRGB(0.89, 0.102, 0.109)
    val green = ColorRGB(0.698, 0.874, 0.541)// to ColorRGB(0.2, 0.627, 0.172)
    val orange = ColorRGB(0.992, 0.749, 0.435)// to ColorRGB(1.0, 0.498, 0.0)
    val purple = ColorRGB(0.792, 0.698, 0.839)// to ColorRGB(0.415, 0.239, 0.603)
    val yellow = ColorRGB(251 / 255.0, 240 / 255.0, 116 / 255.0)
    val brown = ColorRGB(234 / 255.0, 189 / 255.0, 162 / 255.0)
    val defaultColors = listOf(blue, red, green, orange, purple, yellow, brown)

    val (colors, colorsSetter) = useState(defaultColors)
    val colorsObj = object: Colors {
        override val defaultColors: List<ColorRGB>
            get() = defaultColors
        override var colors: List<ColorRGB>
            get() = colors
            set(v) = colorsSetter(v)

    }
    val drawSettings = DrawSettings(colors = colors)

    var viewMatrix: Matrix44 by useState(Matrix44.IDENTITY)
    val svgContainerRef: MutableRefObject<HTMLDivElement> = useRef(null)
    var svgSize by useState(IntVector2(svgContainerRef.current?.clientWidth ?: 0, svgContainerRef.current?.clientHeight ?: 0))
    val bottomLeft = viewMatrix * Vector2.ZERO
    val topRight = viewMatrix * svgSize
    val viewBoxTransform = "${bottomLeft.x} ${bottomLeft.y} ${topRight.x - bottomLeft.x} ${topRight.y - bottomLeft.y}"

    val emptySvg = ""
    var svg: String by useState(emptySvg)

    var tool: Tool by useState(Tool.PlacePoints)
    var points: List<Point> by useState(emptyList())

    var computing: Boolean by useState(false)

    var lastPoints: List<Point> by useState(emptyList())
    var lastGeneralSettings: GeneralSettings by useState(generalSettings)
    var lastComputeDrawingSettings: ComputeDrawingSettings by useState(computeDrawingSettings)
    var lastGrowSettings: GrowSettings by useState(growSettings)
    var lastSentPoints: List<Point> by useState(emptyList())
    var lastSentGeneralSettings: GeneralSettings by useState(generalSettings)
    var lastSentDrawingSettings: ComputeDrawingSettings by useState(computeDrawingSettings)
    var lastSentGrowSettings: GrowSettings by useState(growSettings)
    val changedProblem = points != lastPoints
            || generalSettings != lastGeneralSettings
            || computeDrawingSettings != lastComputeDrawingSettings
            || growSettings != lastGrowSettings

    var currentType: Int by useState(0)

    var evCache: List<PointerEvent<HTMLDivElement>> by useState(emptyList())
    var prevDiff: Double? = null

    val (sideWindowRatio, setSideWindowRatio) = useState(0.382)

    val sideWindowContainer: MutableRefObject<HTMLDivElement> = useRef(null)

    val windowSize = useWindowSize()
    val horizontal = windowSize.x > windowSize.y

    var progress: Double by useState(0.0)

    useEffect(windowSize, sideWindowRatio) {
        val svgContainer = svgContainerRef.current ?: return@useEffect
        svgSize = IntVector2(svgContainer.clientWidth, svgContainer.clientHeight)
    }

    worker.onmessage = { m: MessageEvent ->
        val answer: Answer = Json.decodeFromString(m.data as String)
        when (answer) {
            is CompletedWork -> {
                progress = 0.0
                svg = answer.svg
                computing = false
                lastPoints = lastSentPoints
                lastGeneralSettings = lastSentGeneralSettings
                lastComputeDrawingSettings = lastSentDrawingSettings
                lastGrowSettings = lastSentGrowSettings
            }

            is Progress -> {
                progress = answer.progress
            }
        }

        Unit
    }

    fun recomputeSvg() {
        val assignment: Assignment = DrawSvg(drawSettings)
        worker.postMessage(Json.encodeToString(assignment))
    }

    useEffect(colors) {
        recomputeSvg()
    }

    useEffect(cover) {
        val assignment: Assignment = ChangeCover(cover)
        worker.postMessage(Json.encodeToString(assignment))
    }

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            width = 100.pct
            height = 100.pct
        }

        div {
            ref = sideWindowContainer
            css {
                background = NamedColor.white
                boxShadow = ("0px 6px 3px -3px rgba(0,0,0,0.2),0px 3px 3px 0px rgba(0,0,0,0.14)," +
                        "0px 3px 9px 0px rgba(0,0,0,0.12),0px -2px 3px -3px rgba(0,0,0,0.2)").unsafeCast<BoxShadow>()
                borderRadius = cardBr
                margin = Margin(10.px, 10.px)
                boxSizing = BoxSizing.borderBox
                display = Display.flex
                width = 100.pct - 20.px
                height = 100.pct - 20.px
                flexDirection = if (horizontal) FlexDirection.row else FlexDirection.column

            }

            SideWindow {
                isHorizontal = horizontal
                size = (100 * sideWindowRatio).pct

                BendSettingsContext.Provider {
                    value = bendSettings
                    BankSettingsPanel {
                        ptSize = pointSize
                        ptStrokeWeight = drawSettings.pointStrokeWeight(generalSettings)
                        lineStrokeWeight = drawSettings.contourStrokeWeight(generalSettings)
                        expandRadius = generalSettings.expandRadius
                        color = colors[currentType].toHex()
                    }
                }
                Divider()
                PointSettingsContext.Provider {
                    value = pointSettings
                    PointSettingsPanel {
                        strokeWeight = drawSettings.pointStrokeWeight(generalSettings)
                        fillColor = colors[currentType].toSvgString()
                    }
                }
                Divider()
                GrowSettingsContext.Provider {
                    value = grow
                    GrowSettingsPanel()
                }
                Divider()
                ColorsContext.Provider {
                    value = colorsObj
                    ColorSettingsPanel()
                }
                Divider()
                SelectExample {
                    onLoadExample = {
                        window
                            .fetch("example-input/${getFileName(it)}.ipe")
                            .then { it.text() }
                            .then { ipe ->
                                points = ipeToPoints(ipe).map { p ->
                                    p.copy(pos = p.pos.copy(y = svgSize.y - p.pos.y))
                                }
                            }
                    }
                }
            }

            DraggableDivider {
                isHorizontal = horizontal
                windowContainer = sideWindowContainer
                valueSetter = setSideWindowRatio
            }

            div {
                css {
                    position = Position.relative
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    boxSizing = BoxSizing.borderBox
                    fontFamily = FontFamily.sansSerif
                    fontSize = (13 + 1.0 / 3.0).px
                    flex = auto
                    margin = 10.px
                    if (horizontal) {
                        marginLeft = 0.px
                        width = (100 * (1 - sideWindowRatio)).pct - 20.px
                    } else {
                        marginTop = 0.px
                        height = (100 * (1 - sideWindowRatio)).pct - 20.px
                    }
                    overflow = Overflow.hidden
                }

                tabIndex = 0
                onKeyDown = {
                    if (it.key in (1..7).map { it.toString() }) {
                        currentType = it.key.toInt() - 1
                    }
                }

                if (computing) {
                    div {
                        css {
                            position = Position.absolute
                            width = 100.pct
                            height = 100.pct
                            background = rgb(255, 255, 255, 0.9)
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
                            label {
                                div {
                                    +"Computing..."
                                }
                                progress {
                                    value = progress
                                }
                            }
                        }
                    }
                }

                div {
                    css {
                        zIndex = integer(20)
                        margin = Margin(0.px, 10.px)
                    }
                    Toolbar {
                        val whiteSpace = div.create {
                            css {
                                width = 16.px
                                flex = initial
                                flexShrink = number(10000.0)
                            }
                        }

                        +whiteSpace

//                        Grid {
//                            showGrid = useGrid
//                            onClick = {
//                                useGrid = !useGrid
//                            }
//                        }

                        IconButton {
                            buttonProps = jso {
                                title = "Clear"
//                        ariaLabel = "clear"
                                onClick =
                                {
                                    svg = emptySvg
                                    points = emptyList()
                                }
                            }
                            Clear()
//                            +"C"
                        }

                        IconButton {
                            buttonProps = jso {
                                title = "Run computations"
                                onClick = {
                                    if (changedProblem) {
                                        val assignment: Assignment = Compute(
                                            points, generalSettings, growSettings,
                                            computeDrawingSettings, drawSettings, cover
                                        )
                                        worker.postMessage(Json.encodeToString(assignment))
                                        computing = true
                                        lastSentPoints = points
                                        lastSentGeneralSettings = generalSettings
                                        lastSentDrawingSettings = computeDrawingSettings
                                        lastSentGrowSettings = growSettings
                                    }
                                }
                            }
                            Run()
                        }

                        +whiteSpace

                        for (i in 1..7) {
                            IconButton {
                                buttonProps = jso {
                                    title = "Add points of color $i with mouse"
                                    onClick = {
                                        tool =
                                            if (tool == Tool.PlacePoints && currentType == i - 1) Tool.None else Tool.PlacePoints
                                        currentType = i - 1
                                    }
                                }
                                isPressed = currentType == i - 1 && tool == Tool.PlacePoints
                                +"$i"
                            }
                        }

                        +whiteSpace

                        IconButton {
                            buttonProps = jso {
                                title = "Download output as an SVG file"
                                onClick = {
                                    // Adapted from: https://stackoverflow.com/a/38019175
                                    val downloadee: BlobPart =
                                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                "<svg version=\"1.2\" baseProfile=\"tiny\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"$viewBoxTransform\">" +
                                                svg +
                                                "</svg>"
                                    val svgBlob =
                                        Blob(arrayOf(downloadee), jso { type = "image/svg+xml;charset=utf-8" })
                                    val svgUrl = URL.createObjectURL(svgBlob)
                                    val downloadLink = document.createElement("a").unsafeCast<HTMLAnchorElement>()
                                    downloadLink.href = svgUrl
                                    downloadLink.download = "output.svg"
//                            downloadLink.style = jso { display = "none" }
                                    document.body.appendChild(downloadLink)
                                    downloadLink.click()
                                    document.body.removeChild(downloadLink)
                                }
                            }
                            DownloadSvg()
//                            +"D"
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
                        borderBottomLeftRadius = if (horizontal) 0.px else 20.px
                        borderBottomRightRadius = 20.px
                        border = Border(1.px, LineStyle.solid, rgb(200, 200, 200))
                        position = Position.relative
                        overflow = Overflow.hidden
                        boxSizing = BoxSizing.borderBox

                        if (tool == Tool.PlacePoints) {
                            cursor = Cursor.pointer
                        }
                        if (evCache.isNotEmpty() && (tool == Tool.None || evCache[0].button != 0)) {
                            cursor = Cursor.move
                        }
                    }
                    div {
                        css {
                            height = 100.pct
                            width = 100.pct
                            position = Position.relative
                            overscrollBehavior = OverscrollBehavior.contain
                            touchAction = none
                        }

                        ref = svgContainerRef

                        onContextMenu = { ev ->
                            ev.preventDefault()
                        }

                        onPointerDown = { ev ->
                            ev.preventDefault()
                            ev.stopPropagation()
                            ev.currentTarget.setPointerCapture(ev.pointerId)

                            if (tool == Tool.PlacePoints && ev.button == 0) {
                                points += Point(viewMatrix * ev.offset, currentType)
                            } else {
                                evCache += ev
                            }
                        }

                        onPointerUp = { ev ->
                            ev.preventDefault()
                            ev.stopPropagation()
                            evCache = evCache.filterNot {
                                it.pointerId == ev.pointerId
                            }
                        }

                        onPointerMove = { ev ->
                            ev.preventDefault()
                            ev.stopPropagation()
                            val prevEv = evCache.find {
                                it.pointerId == ev.pointerId
                            }

                            if (evCache.size == 2) {
                                val other = (evCache - prevEv).first()!!
                                val curDiff = ev.offset.distanceTo(other.offset)
                                if (prevDiff != null) {
                                    val diff = curDiff - prevDiff!!
                                    val middle = (ev.offset + other.offset) * 0.5
                                    // Pinched diff amount
                                    viewMatrix *= transform {
                                        translate(middle)
                                        scale(1 + diff / 1000)
                                        translate(-middle)
                                    }
                                }
                                prevDiff = curDiff
                            }

                            if (evCache.size == 1 && prevEv != null) {
//                                if (tool == Tool.None) {
                                    viewMatrix *= transform {
                                        translate(-(ev.clientX - prevEv.clientX), -(ev.clientY - prevEv.clientY))
                                    }
//                                }
                            }

                            if (prevEv != null) {
                                evCache = evCache - prevEv + ev
                            }
                        }

                        onWheel = { ev ->
                            ev.preventDefault()
                            val pos = Vector2(ev.nativeEvent.offsetX, ev.nativeEvent.offsetY)
                            viewMatrix *= transform {
                                translate(pos)
                                scale(1 + ev.deltaY / 1000)
                                translate(-pos)
                            }
                        }

                        div {
                            css {
                                height = 100.pct
                                width = 100.pct
                                position = Position.absolute
                                zIndex = integer(2)
                                borderBottomRightRadius = cardBr
                                if (changedProblem)
                                    background = rgb(255, 255, 255, 0.9)
                            }
                        }

                        svg {
                            css {
                                height = 100.pct
                                width = 100.pct
                                position = Position.absolute
                                display = if (changedProblem) Display.block else none
                                zIndex = integer(3)
                                borderBottomRightRadius = cardBr
                            }

                            viewBox = viewBoxTransform

                            for (p in points) {
                                circle {
                                    cx = p.pos.x
                                    cy = p.pos.y
                                    r = pointSize
                                    fill = colors[p.type].toSvgString()
                                    stroke = "black"
                                    strokeWidth = drawSettings.pointStrokeWeight(generalSettings)
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
                                    borderBottomRightRadius = cardBr
                                }

                                viewBox = viewBoxTransform

                                dangerouslySetInnerHTML = jso {
                                    __html = svg
                                }
                            }
                        }
                    }

                    if (viewMatrix != Matrix44.IDENTITY) {
                        div {
                            css {
                                position = Position.absolute
                                zIndex = integer(100)
                                padding = 10.px
                                bottom = 0.px
                                right = 0.px
                                userSelect = none
                                cursor = Cursor.pointer
                                background = rgb(253, 253, 253, 0.975)
                                borderRadius = 10.px
                                fontSize = 1.rem
                                margin = 10.px
                                border = Border(1.px, LineStyle.solid, rgb(200, 200, 200))
                            }
                            onClick = {
                                viewMatrix = Matrix44.IDENTITY
                            }
                            +"Reset"
                        }
                    }

                    div {
                        css {
                            position = Position.absolute
                            zIndex = integer(100)
                            padding = 10.px
                            top = 0.px
                            left = 0.px
                            userSelect = none
                            cursor = Cursor.pointer
                            background = rgb(253, 253, 253, 0.975)
                            borderRadius = 10.px
                            fontSize = 1.rem
                            margin = 10.px
                            border = Border(1.px, LineStyle.solid, rgb(200, 200, 200))
                        }

                        ThrottledSlider {
                            label = "Cover"
                            title = "Cover"
                            unit = ""
//                            step = "any".unsafeCast<Double>()
                            step = 0.1
                            min = 1.0
                            max = 8.0
                            defaultValue = cover
                            labelValue = cover
                            onThrottledChange = { v ->
                                cover = v
                            }
                        }
                    }
                }
            }
        }
    }
}

val <T: Element, E : NativeMouseEvent> MouseEvent<T, E>.offset: Vector2
    get() = Vector2(nativeEvent.offsetX, nativeEvent.offsetY)
