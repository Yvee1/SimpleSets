package sideWindow.settings

import contexts.ColorsContext
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.useContext
import react.useState
import sideWindow.PanelHeader
import web.cssom.*
import web.html.InputType
import web.timers.Timeout
import web.timers.clearTimeout
import web.timers.setTimeout
import kotlin.time.Duration.Companion.milliseconds

val ColorSettingsPanel = FC<Props> {
    PanelHeader {
        title = "Colors"
    }
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            columnGap = 8.px
            rowGap = 10.px
            flexWrap = FlexWrap.wrap
        }

        for (i in 1..7) {
            ColorPicker {
                this.i = i
            }
        }
    }
}

external interface ColorPickersProps: Props {
    var i: Int
}

val ColorPicker = FC<ColorPickersProps> { props ->
    val i = props.i

    var apiTimeout: Timeout? by useState(null)

    with(useContext(ColorsContext)!!) {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                rowGap = 5.px
            }
            ReactHTML.label {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                }
                ReactHTML.div {
                    css {
                        width = Length.maxContent
                        marginBottom = 4.px
                    }
                    +"$i"
                }
                ReactHTML.input {
                    type = InputType.color
                    value = colors[i - 1].toHex()
                    onChange = { ev ->
                        apiTimeout?.let { clearTimeout(it) }

                        val v = ev.currentTarget.value
                        apiTimeout = setTimeout(100.milliseconds) {
                            colors = colors.replace(i - 1) { ColorRGB.fromHex(v) }
                        }
                    }
                }
            }

            ReactHTML.button {
                onClick = {
                    colors = colors.replace(i - 1, defaultColors[i - 1])
                }
                +"Reset"
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