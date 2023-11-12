package components

import web.cssom.px
import emotion.react.css
import js.core.jso
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import require

fun createIcon(rawSvg: String) = FC<Props> {
    div {
        css {
            width = 24.px
            height = 24.px
        }
        dangerouslySetInnerHTML = jso {
            __html = rawSvg
        }
    }
}

val Clear = createIcon(require("clear.svg"))
val Run = createIcon(require("run.svg"))
val DownloadSvg = createIcon(require("download-svg.svg"))