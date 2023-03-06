import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

sealed class Pattern {
    abstract val weight: Int
    abstract val contour: ShapeContour
    abstract fun original(): Pattern
    abstract fun isEmpty(): Boolean
    abstract operator fun contains(v: Vector2): Boolean
}

fun ProblemInstance.computePattern(uncovered: List<Point>, obstacles: List<Pattern>): Pattern {
    val lonelyPoint = ConvexIsland(listOf(uncovered.first()), 1)
    val island = largestConvexIsland(uncovered, obstacles)
    val bend = if (bendInflection) largestInflectionBend(Orientation.RIGHT, uncovered, obstacles)
               else largestMonotoneBend(Orientation.RIGHT, uncovered, obstacles)
    if (island.weight > 2) return island
    return listOf(lonelyPoint, island, bend).maxBy { it.weight }
}

fun ProblemInstance.computePartition(disjoint: Boolean = true): List<Pattern> {
    return buildList {
        val patterns = this@buildList
        var uncovered = points
        while (uncovered.isNotEmpty()){
            val pattern = computePattern(uncovered, if (disjoint) patterns.filter { it.weight > 1 } else emptyList())
            if (pattern.isEmpty()) break
            patterns.add(pattern)
            uncovered = uncovered.filter { it.pos !in pattern }
        }
    }
}
