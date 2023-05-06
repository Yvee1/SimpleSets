package sideWindow.settings

import components.Checkbox
import components.Slider
import contexts.BendSettingsContext
import emotion.react.css
import react.VFC
import react.dom.html.ReactHTML.div
import react.useContext
import web.cssom.Display
import web.cssom.FlexDirection

val BendSettingsPanel = VFC {
    with(useContext(BendSettingsContext)!!) {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
            }
            Checkbox {
                title = "Allow bend inflection"
                checked = bendInflection
                label = "Bend inflection"
                onChange = {
                    bendInflection = !bendInflection
                }
            }
            Slider {
                title = "Change bend distance"
                step = "any".unsafeCast<Double>()
                min = 1.0
                max = 500.0
                value = bendDistance
                unit = ""
                label = "Bend distance"
                onChange = {
                    bendDistance = it.currentTarget.valueAsNumber
                }
            }
            Slider {
                title = "Change max bend angle"
                step = "any".unsafeCast<Double>()
                min = 0.0
                max = 180.0
                value = maxBendAngle
                unit = "°"
                label = "Max bend angle"
                onChange = {
                    maxBendAngle = it.currentTarget.valueAsNumber
                }
            }
            Slider {
                title = "Change max turning angle"
                step = "any".unsafeCast<Double>()
                min = 0.0
                max = 180.0
                value = maxTurningAngle
                unit = "°"
                label = "Max turning angle"
                onChange = {
                    maxTurningAngle = it.currentTarget.valueAsNumber
                }
            }
        }
    }
}