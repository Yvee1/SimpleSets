package sideWindow

import emotion.react.css
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import web.cssom.*

external interface SideWindowProps: PropsWithChildren {
    var width: Width?
    var height: Height?
}

val SideWindow = FC<SideWindowProps> { props ->
    div {
        css {
            width = props.width
            height = props.height
            overflow = Overflow.hidden
        }

        div {
            css {
                width = 100.pct
                height = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                boxSizing = BoxSizing.borderBox
                padding = Padding(20.px, 30.px)
                paddingRight = 0.px
                minWidth = 250.px
            }
            ReactHTML.h1 {
                css {
                    marginTop = 0.px

                }
                +"Islands and bridges"
            }
            +props.children
        }
    }
}