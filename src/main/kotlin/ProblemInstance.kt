class ProblemInstance(originalPoints: List<Point>,
                      val expandRadius: Double = 0.0,
                      val clusterRadius: Double = Double.MAX_VALUE,
                      val bendDistance: Double = Double.MAX_VALUE,
                      val bendInflection: Boolean = true,
                      val maxBendAngle: Double = 180.0,
                      val maxTurningAngle: Double = 180.0) {
    init {
        require(maxBendAngle in 0.0..180.0) {
            "Bend angle should be between 0 and 180 degrees."
        }
        require(bendDistance > 0) {
            "Bend distance should be a positive number."
        }
    }
    val points = transformPoints(originalPoints)
    val stripeData = StripeData(points)
    val capsuleData = CapsuleData(points, expandRadius)
}