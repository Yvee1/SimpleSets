import patterns.CapsuleData
import patterns.Point
import patterns.StripeData
import geometric.transformPoints

class PartitionInstance(val originalPoints: List<Point>,
                        val density: Double = Double.MAX_VALUE,
                        val bendDistance: Double = Double.MAX_VALUE,
                        val bendInflection: Boolean = true,
                        val maxBendAngle: Double = 180.0,
                        val maxTurningAngle: Double = 180.0,
                        val partitionClearance: Double = 0.0,
                        transformPoints: Boolean = true) {
    init {
        require(maxBendAngle in 0.0..180.0) {
            "Bend angle should be between 0 and 180 degrees."
        }
        require(bendDistance > 0) {
            "Bend distance should be a positive number."
        }
    }
    val points = if (transformPoints) transformPoints(originalPoints) else originalPoints
    val stripeData = StripeData(points)
    val capsuleData = CapsuleData(points, partitionClearance)

//    constructor(originalPoints: List<Point>, set: ComputePartitionSettings) :
//            this(originalPoints,
//                set.coverRadius,
//                set.bendDistance,
//                set.bendInflection,
//                set.maxBendAngle,
//                set.maxTurningAngle,
//                set.partitionClearance
//            )
}