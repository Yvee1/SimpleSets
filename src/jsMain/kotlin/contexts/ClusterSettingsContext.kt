package contexts

import react.createContext

interface ClusterSettings {
    var clusterRadius: Double
}

val ClusterSettingsContext = createContext<ClusterSettings>()