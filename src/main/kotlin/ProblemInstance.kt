class ProblemInstance(originalPoints: List<Point>, val bendDistance: Double = Double.MAX_VALUE) {
    val points = transformPoints(originalPoints)
    val stripeData = StripeData(points)
}