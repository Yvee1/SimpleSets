package components

import react.FC
import react.dom.html.InputHTMLAttributes
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.input
import web.html.HTMLInputElement
import web.html.InputType

external interface SliderProps: InputHTMLAttributes<HTMLInputElement> {
    var label: String
}

val Slider = FC<SliderProps> { props ->
    div {
        label {
            +props.label
            input {
                +props
                type = InputType.range
            }
        }
    }
}