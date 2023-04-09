import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    var pSize: Double = 10.0,
    var useGrid: Boolean = true,
    var gridSize: Double = 40.0,
    var disjoint: Boolean = true,
    var bendDistance: Double = 20.0,
    var bendInflection: Boolean = true,
    var maxBendAngle: Double = 180.0,
    var maxTurningAngle: Double = 180.0,
    var clusterRadius: Double = 50.0,
    var clearance: Double = 5.0,
    var showVisibilityContours: Boolean = true,
    var showBridges: Boolean = true,
    var showClusterCircles: Boolean = false,
    var showBendDistance: Boolean = false,
    var showVisibilityGraph: Boolean = false,
    var showVoronoi: Boolean = false,
    var subset: Double = 1.0,
) {
    val expandRadius get() = pSize * 3
    val pointStrokeWeight get() = pSize / 3
    val contourStrokeWeight get () = pSize / 3.5
}