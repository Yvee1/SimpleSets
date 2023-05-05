package components

import web.cssom.Float
import emotion.react.css
import react.FC
import react.dom.html.InputHTMLAttributes
import react.dom.html.ReactHTML
import web.html.HTMLInputElement
import web.html.InputType

external interface CheckboxProps: InputHTMLAttributes<HTMLInputElement> {
    var label: String
}

val Checkbox = FC<CheckboxProps> { props ->
    ReactHTML.div {
        ReactHTML.label {
            +props.label

            ReactHTML.input {
                +props
                css {
                    float = Float.right
                }
                type = InputType.checkbox
            }
        }
    }
}