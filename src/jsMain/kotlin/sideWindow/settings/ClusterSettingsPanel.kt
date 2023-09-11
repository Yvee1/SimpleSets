package sideWindow.settings

import components.Slider
import contexts.ClusterSettingsContext
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useContext
import sideWindow.PanelHeader
import web.cssom.Display
import web.cssom.FlexDirection

val ClusterSettingsPanel = FC<Props> {
    with(useContext(ClusterSettingsContext)!!) {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
            }
            PanelHeader {
                title = "Clusters"
            }
            Slider {
                title = "Change cluster radius"
                step = "any".unsafeCast<Double>()
                min = 0.0
                max = 100.0
                value = clusterRadius
                unit = ""
                label = "Cluster radius"
                onChange = {
                    clusterRadius = it.currentTarget.valueAsNumber
                }
            }
        }
    }
}