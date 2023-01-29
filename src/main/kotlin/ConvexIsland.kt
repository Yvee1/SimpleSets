data class ConvexIsland(val points: List<Point>, val weight: Int){
    companion object {
        val EMPTY = ConvexIsland(listOf(), 0)
    }
}