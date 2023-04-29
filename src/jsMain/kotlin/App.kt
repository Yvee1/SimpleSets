import components.*
import web.cssom.*
import web.cssom.Auto.Companion.auto
import web.cssom.Globals.Companion.initial
import web.cssom.Length.Companion.maxContent
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
import react.dom.html.ReactHTML.h1
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.svg
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import react.dom.events.MouseEvent
import react.dom.events.NativeMouseEvent
import react.dom.events.PointerEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.span
import web.buffer.Blob
import web.buffer.BlobPart
import web.dom.Element
import web.dom.document
import web.html.HTMLAnchorElement
import web.html.HTMLDivElement
import web.html.InputType
import web.url.URL

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
    val originalColors = listOf(blue, red, green, orange, purple)
    var colors: List<Pair<ColorRGB, ColorRGB>> by useState(originalColors)

    val colorSettings = ColorSettings(colors)
    val drawSettings = DrawSettings(pSize = pSize, colorSettings = colorSettings)

    var viewMatrix: Matrix44 by useState(Matrix44.IDENTITY)
    val svgContainerRef: MutableRefObject<HTMLDivElement> = useRef(null)
    var svgSize by useState(IntVector2(svgContainerRef.current?.clientWidth ?: 0, svgContainerRef.current?.clientHeight ?: 0))
    val bottomLeft = viewMatrix * Vector2.ZERO
    val topRight = viewMatrix * svgSize
    val viewBoxTransform = "${bottomLeft.x} ${bottomLeft.y} ${topRight.x - bottomLeft.x} ${topRight.y - bottomLeft.y}"

    val windowSize = useWindowSize()

    useEffect(windowSize) {
        val svgContainer = svgContainerRef.current ?: return@useEffect
        svgSize = IntVector2(svgContainer.clientWidth, svgContainer.clientHeight)
    }
    val emptySvg = ""
    var svg: String by useState(emptySvg)

    var tool: Tool by useState(Tool.PlacePoints)
    var points: List<Point> by useState(emptyList())

    var computing: Boolean by useState(false)

    var lastPoints: List<Point> by useState(emptyList())
    var lastComputeSettings: ComputeSettings by useState(computeSettings)
    var lastSentPoints: List<Point> by useState(emptyList())
    var lastSentSettings: ComputeSettings by useState(computeSettings)
    val changedProblem = points != lastPoints || computeSettings != lastComputeSettings

    var currentType: Int by useState(0)

    var evCache: List<PointerEvent<HTMLDivElement>> by useState(emptyList())
    var prevDiff: Double? = null

    worker.onmessage = { m: MessageEvent ->
        val completedWork: CompletedWork = Json.decodeFromString(m.data as String)
        svg = completedWork.svg
        computing = false
        lastPoints = lastSentPoints
        lastComputeSettings = lastSentSettings
        Unit
    }

    fun recomputeSvg() {
        val assignment: Assignment = DrawSvg(drawSettings)
        worker.postMessage(Json.encodeToString(assignment))
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
                padding = 10.px
                boxSizing = BoxSizing.borderBox
                display = Display.grid
                gap = 10.px
                width = 100.pct
                height = 100.pct
                gridTemplateColumns = "auto max-content".unsafeCast<GridTemplateColumns>()
            }
            div {
                css {
                    position = Position.relative
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    boxSizing = BoxSizing.borderBox
                    border = Border(1.px, LineStyle.solid, NamedColor.black)
                    fontFamily = FontFamily.sansSerif
                    fontSize = (13 + 1.0 / 3.0).px
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
//                        ariaLabel = "clear"
                            onClick = {
                                svg = emptySvg
                                points = emptyList()
                            }
                            Clear()
                        }

                        IconButton {
                            title = "Run computations"
                            onClick = {
                                if (changedProblem) {
                                    val assignment: Assignment = Compute(computeSettings, points, drawSettings)
                                    worker.postMessage(Json.encodeToString(assignment))
                                    computing = true
                                    lastSentPoints = points
                                    lastSentSettings = computeSettings
                                }
                            }
                            Run()
                        }

                        +whiteSpace

                        for (i in 1..5) {
                            Expandable {
                                expander = IconButton.create {
                                    title = "Add points of color $i with mouse"
                                    checked = currentType == i - 1 && tool == Tool.PlacePoints
                                    onClick = {
                                        tool =
                                            if (tool == Tool.PlacePoints && currentType == i - 1) Tool.None else Tool.PlacePoints
                                        currentType = i - 1
                                    }
                                    +"$i"
                                }

                                expandee = div.create {
                                    css {
                                        padding = Padding(7.5.px, 10.px)
                                        display = Display.flex
                                        flexDirection = FlexDirection.column
                                        alignItems = AlignItems.center
                                        rowGap = 8.px
                                    }
                                    label {
                                        css {
                                            display = Display.flex
                                            flexDirection = FlexDirection.column
                                            alignItems = AlignItems.center
                                        }
                                        div {
                                            css {
                                                width = maxContent
                                                marginBottom = 4.px
                                            }
                                            +"Light color"
                                        }
                                        input {
                                            type = InputType.color
                                            value = colors[i - 1].first.toHex()
                                            onChange = { ev ->
                                                colors = colors.replace(i-1) { it.copy(first = ColorRGB.fromHex(ev.currentTarget.value)) }
                                                recomputeSvg()
                                            }
                                        }
                                    }
                                    label {
                                        css {
                                            display = Display.flex
                                            flexDirection = FlexDirection.column
                                            alignItems = AlignItems.center
                                        }
                                        div {
                                            css {
                                                width = maxContent
                                                marginBottom = 4.px
                                            }
                                            +"Dark color"
                                        }
                                        input {
                                            type = InputType.color
                                            value = colors[i - 1].second.toHex()
                                            onChange = { ev ->
                                                colors = colors.replace(i - 1) { it.copy(second = ColorRGB.fromHex(ev.currentTarget.value)) }
                                                recomputeSvg()
                                            }
                                        }
                                    }
                                    button {
                                        onClick = {
                                            colors = colors.replace(i-1, originalColors[i - 1])
                                            recomputeSvg()
                                        }
                                        +"Reset"
                                    }
                                }
                            }
                        }

                        +whiteSpace

                        IconButton {
                            title = "Download output as an SVG file"
                            onClick = {
                                // Adapted from: https://stackoverflow.com/a/38019175
                                val downloadee: BlobPart =
                                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                            "<svg version=\"1.2\" baseProfile=\"tiny\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">" +
                                            svg +
                                            "</svg>"
                                val svgBlob = Blob(arrayOf(downloadee), jso { type = "image/svg+xml;charset=utf-8" })
                                val svgUrl = URL.createObjectURL(svgBlob)
                                val downloadLink = document.createElement("a").unsafeCast<HTMLAnchorElement>()
                                downloadLink.href = svgUrl
                                downloadLink.download = "output.svg"
//                            downloadLink.style = jso { display = "none" }
                                document.body.appendChild(downloadLink)
                                downloadLink.click()
                                document.body.removeChild(downloadLink)
                            }
                            DownloadSvg()
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
                    }
                    div {
                        css {
                            height = 100.pct
                            width = 100.pct
                            if (tool == Tool.PlacePoints) {
                                cursor = Cursor.pointer
                            }
                            if (tool == Tool.None && evCache.isNotEmpty()) {
                                cursor = Cursor.move
                            }
                            position = Position.relative
                            overscrollBehavior = OverscrollBehavior.contain
                            touchAction = none
                        }

                        ref = svgContainerRef

                        onClick = { ev ->
                            ev.preventDefault()
                            if (tool == Tool.PlacePoints) {
                                points += Point(viewMatrix * ev.offset, currentType)
                            }
                        }

                        onPointerDown = { ev ->
                            ev.preventDefault()
                            ev.currentTarget.setPointerCapture(ev.pointerId)
                            evCache += ev
                        }
                        onPointerUp = { ev ->
                            ev.preventDefault()
                            evCache = evCache.filterNot {
                                it.pointerId == ev.pointerId
                            }
                        }

                        onPointerMove = { ev ->
                            ev.preventDefault()
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
                                if (tool == Tool.None && evCache.isNotEmpty()) {
                                    viewMatrix *= transform {
                                        translate(-(ev.clientX - prevEv.clientX), -(ev.clientY - prevEv.clientY))
                                    }
                                }
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
                                if (changedProblem)
                                    background = rgba(255, 255, 255, 0.9)
                            }
                        }

                        svg {
                            css {
                                height = 100.pct
                                width = 100.pct
                                position = Position.absolute
                                display = if (changedProblem) Display.block else none
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
                    if (viewMatrix != Matrix44.IDENTITY) {
                        div {
                            css {
                                position = Position.absolute
                                borderLeft = Border(1.px, LineStyle.solid, NamedColor.black)
                                borderTop = Border(1.px, LineStyle.solid, NamedColor.black)
                                zIndex = integer(100)
                                padding = 5.px
                                bottom = 0.px
                                right = 0.px
                                userSelect = none
                                cursor = Cursor.pointer
                                background = NamedColor.white
                            }
                            onClick = {
                                viewMatrix = Matrix44.IDENTITY
                            }
                            +"Reset"
                        }
                    }
                }
            }
            div {
                css {
                    display = Display.flex
                    border = Border(1.px, LineStyle.solid, NamedColor.black)
                }
                div {
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                    }
                    span {
                        css {
                            userSelect = none
                            cursor = Cursor.pointer
                        }
                        +"<"
                    }
                }
                div {
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                    }
//                    MuiSelectExample {
//                        onLoadExample = {
//                            points = getExampleInput(it)
//                        }
//                    }
                    SelectExample {
                        onLoadExample = {
                            points = getExampleInput(it)
                        }
                    }
                }
            }
        }
    }
}

private fun <E> List<E>.replace(i: Int, function: (E) -> E): List<E> =
    withIndex().map {
        if (it.index == i) function(it.value) else it.value
    }

private fun <E> List<E>.replace(i: Int, new: E): List<E> =
    withIndex().map {
        if (it.index == i) new else it.value
    }

val <T: Element, E : NativeMouseEvent> MouseEvent<T, E>.offset: Vector2
    get() = Vector2(nativeEvent.offsetX, nativeEvent.offsetY)
