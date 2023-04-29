package components

import web.cssom.Float
import emotion.react.css
import patterns.roundToDecimals
import react.FC
import react.dom.html.InputHTMLAttributes
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import web.html.HTMLInputElement
import web.html.InputType

external interface SliderProps: InputHTMLAttributes<HTMLInputElement> {
    var label: String
    var unit: String
}

val Slider = FC<SliderProps> { props ->
    div {
        label {
            +props.label
            span {
                css {
                    float = Float.right
                }
                +"${props.value.unsafeCast<Double>().roundToDecimals(1)}${props.unit}"
            }
            input {
                +props
                type = InputType.range
            }
        }
    }
}