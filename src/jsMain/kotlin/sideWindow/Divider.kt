package sideWindow

import emotion.react.css
import react.VFC
import react.dom.html.ReactHTML.div
import web.cssom.*

val Divider = VFC {
    div {
        css {
            border = Border(1.px, LineStyle.solid, rgb(200, 200, 200))
            margin = Margin(20.px, 10.px)
            maxWidth = 500.px
        }
    }
}