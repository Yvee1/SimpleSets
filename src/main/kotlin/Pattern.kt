import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

sealed class Pattern {
    abstract val contour: ShapeContour
    abstract fun original(): Pattern
    abstract fun isEmpty(): Boolean
    abstract operator fun contains(v: Vector2): Boolean
}

fun ProblemInstance.computePattern(uncovered: List<Point>, obstacles: List<Pattern>): Pattern {
    val island = ConvexIsland(listOf(uncovered.first()), 1) //computeLargestConvexIsland(uncovered, obstacles)
    val bend = computeLargestCwBend(uncovered, obstacles)
    return if (island.weight > bend.weight) island else bend
}

fun ProblemInstance.computePartition(disjoint: Boolean = true): List<Pattern> {
    return buildList {
        val patterns = this@buildList
        var uncovered = points
        while (uncovered.isNotEmpty()){
            val pattern = computePattern(uncovered, if (disjoint) patterns else emptyList())
            if (pattern.isEmpty()) break
            patterns.add(pattern)
            uncovered = uncovered.filter { it.pos !in pattern }
        }
    }
}
