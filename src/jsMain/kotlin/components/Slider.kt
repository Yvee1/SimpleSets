package components

import patterns.roundToDecimals
import react.FC
import react.dom.html.InputHTMLAttributes
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.input
import web.html.HTMLInputElement
import web.html.InputType

external interface SliderProps: InputHTMLAttributes<HTMLInputElement> {
    var label: String
    var unit: String
}

val Slider = FC<SliderProps> { props ->
    div {
        label {
            val value = "${props.value.unsafeCast<Double>().roundToDecimals(1)}${props.unit}"
            div {
                +"${props.label}: $value"
            }
            input {
                +props
                type = InputType.range
            }
        }
    }
}