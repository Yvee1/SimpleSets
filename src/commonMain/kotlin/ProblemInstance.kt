import patterns.CapsuleData
import patterns.Point
import patterns.StripeData
import geometric.transformPoints

class ProblemInstance(originalPoints: List<Point>,
                      val expandRadius: Double = 0.0,
                      val clusterRadius: Double = Double.MAX_VALUE,
                      val bendDistance: Double = Double.MAX_VALUE,
                      val bendInflection: Boolean = true,
                      val maxBendAngle: Double = 180.0,
                      val maxTurningAngle: Double = 180.0,
                      val avoidOverlap: Double = 0.25,
                      transformPoints: Boolean = true) {
    init {
        require(maxBendAngle in 0.0..180.0) {
            "Bend angle should be between 0 and 180 degrees."
        }
        require(bendDistance > 0) {
            "Bend distance should be a positive number."
        }
        require(avoidOverlap in 0.0..1.0) {
            "The avoid overlap parameter should be between 0.0 and 1.0."
        }
    }
    val points = if (transformPoints) transformPoints(originalPoints) else originalPoints
    val stripeData = StripeData(points)
    val capsuleData = CapsuleData(points, expandRadius * avoidOverlap)

    constructor(originalPoints: List<Point>, set: ComputeSettings) :
            this(originalPoints,
                set.expandRadius,
                set.clusterRadius,
                set.bendDistance,
                set.bendInflection,
                set.maxBendAngle,
                set.maxTurningAngle
            )
}