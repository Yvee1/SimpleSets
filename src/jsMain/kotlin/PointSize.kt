import components.Expandable
import components.IconNonButton
import components.Slider
import csstype.Display
import csstype.FlexDirection
import csstype.Padding
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.svg

external interface PointSizeProps : Props {
    var min: Double
    var max: Double
    var pointSize: Double
    var onChange: (Double) -> Unit
    var strokeWeight: Double
    var fillColor: String
}

val PointSize = FC<PointSizeProps> { props ->
    Expandable {
        expander = IconNonButton.create {
            svg {
                val svgSize = props.max * 2.0 + 6.0
                width = svgSize
                height = svgSize
                fill = props.fillColor
                stroke = "black"
                strokeWidth = props.strokeWeight
                circle {
                    cx = svgSize / 2
                    cy = svgSize / 2
                    r = props.pointSize
                }
            }
        }
        expandee = div.create {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                padding = Padding(7.5.px, 10.px)
            }
            Slider {
                title = "Change point size"
                step = "any".unsafeCast<Double>()
                min = props.min
                max = props.max
                value = props.pointSize
                label = "Point size"
                onChange = {
                    props.onChange(it.currentTarget.valueAsNumber)
                }
            }
        }
    }
}