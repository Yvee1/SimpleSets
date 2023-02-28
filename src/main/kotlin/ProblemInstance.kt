class ProblemInstance(originalPoints: List<Point>, val bendDistance: Double = Double.MAX_VALUE, val bendAngle: Double = 180.0) {
    init {
        require(bendAngle in 0.0..180.0) {
            "Bend angle should be between 0 and 180 degrees."
        }
        require(bendDistance > 0) {
            "Bend distance should be a positive number"
        }
    }
    val points = transformPoints(originalPoints)
    val stripeData = StripeData(points)
}