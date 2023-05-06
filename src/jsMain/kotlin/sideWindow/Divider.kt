package sideWindow

import emotion.react.css
import offset
import react.*
import react.dom.html.ReactHTML
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLElement
import kotlin.math.max

external interface DividerProps: Props {
    var isHorizontal: Boolean
    var windowContainer: RefObject<HTMLDivElement>
    var valueSetter: StateSetter<Double>
}

val Divider = FC<DividerProps> { props ->
    val horizontal = props.isHorizontal
    var sideWindowMovingStart: Double? by useState(null)
    val sideWindowMoving: Boolean = sideWindowMovingStart != null

    ReactHTML.div {
        css {
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            if (horizontal) {
                width = 20.px
                height = 100.pct
            } else {
                height = 20.px
                width = 100.pct
            }
            overscrollBehavior = OverscrollBehavior.contain
            cursor = if (horizontal) Cursor.colResize else Cursor.rowResize
            flexShrink = number(0.0)
        }

        onPointerDown = { ev ->
            val target = ev.target as HTMLElement
            val diff = target.getBoundingClientRect().left - ev.currentTarget.getBoundingClientRect().left
            sideWindowMovingStart = (if (horizontal) ev.offset.x else ev.offset.y) + diff
            ev.currentTarget.setPointerCapture(ev.pointerId)
        }

        onPointerMove = { ev ->
            if (sideWindowMoving) {
                props.windowContainer.current?.let { container ->
                    if (horizontal) {
                        val left = container.getBoundingClientRect().left
                        props.valueSetter(max(
                            0.0,
                            (ev.clientX - sideWindowMovingStart!! - left) / container.clientWidth
                        ))
                    } else {
                        val top = container.getBoundingClientRect().top
                        props.valueSetter(max(
                            0.0,
                            (ev.clientY - sideWindowMovingStart!! - top) / container.clientHeight
                        ))
                    }
                }
            }
        }

        onPointerUp = { ev ->
            sideWindowMovingStart = null
            ev.stopPropagation()
        }

        ReactHTML.div {
            css {
                if (horizontal) {
                    height = 66.pct
                } else {
                    width = 66.pct
                }
                border = Border(1.px, LineStyle.solid, rgb(200, 200, 200))

            }
        }
    }
}