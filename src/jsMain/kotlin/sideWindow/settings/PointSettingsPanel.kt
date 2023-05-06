package sideWindow.settings

import components.Slider
import contexts.PointSettingsContext
import react.*
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.circle
import sideWindow.PanelHeader

external interface PointSizeProps : Props {
    var min: Double
    var max: Double
    var strokeWeight: Double
    var fillColor: String
}

val PointSize = FC<PointSizeProps> { props ->
    with(useContext(PointSettingsContext)!!) {
        div {
            PanelHeader {
                title = "Points"
            }

            Slider {
                title = "Change point size"
                step = "any".unsafeCast<Double>()
                min = props.min
                max = props.max
                value = pointSize
                unit = ""
                label = "Point size"
                onChange = {
                    pointSize = it.currentTarget.valueAsNumber
                }
            }

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
                    r = pointSize
                }
            }
        }
    }
}

external interface PointSettingsPanelProps: Props {
    var strokeWeight: Double
    var fillColor: String
}

val PointSettingsPanel = FC<PointSettingsPanelProps> { props ->
    PointSize {
        +props
        min = 1.0
        max = 10.0
    }
}