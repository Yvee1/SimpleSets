package patterns

import ComputePartitionSettings
import geometric.Orientation
import PartitionInstance
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour

sealed class Pattern {
    abstract val weight: Int
    abstract val contour: ShapeContour
    abstract val type: Int
    abstract val points: List<Point>
    abstract val boundaryPoints: List<Point>
    abstract fun original(): Pattern
    abstract fun isEmpty(): Boolean
    abstract operator fun contains(v: Vector2): Boolean
    abstract fun isValid(cps: ComputePartitionSettings): Boolean
}

fun PartitionInstance.computePattern(uncovered: List<Point>, obstacles: List<Pattern>): Pattern {
    val lonelyPoint = SinglePoint(uncovered.first())
    val cluster = largestIsland(uncovered, obstacles)
    if (cluster.weight > 2 && !clusterIsMonotoneBend(cluster)) return cluster
    val bend = if (bendInflection) largestInflectionReef(uncovered, obstacles)
               else largestMonotoneReef(Orientation.RIGHT, uncovered, obstacles)
    if (bend.weight == 0 && cluster.weight == 0) return lonelyPoint
    if (bend.weight >= cluster.weight) return bend
    return cluster
}

fun PartitionInstance.computePartition(disjoint: Boolean = true): List<Pattern> {
    return buildList {
        val patterns = this@buildList
        var uncovered = points
        while (uncovered.isNotEmpty()){
            val pattern = computePattern(uncovered, if (disjoint) patterns.filter { it.weight > 1 } else emptyList())
            if (pattern.isEmpty()) break
            if (pattern.weight == 1) {
                patterns.addAll(uncovered.map { SinglePoint(it) })
                break
            }
            patterns.add(pattern)
            uncovered = uncovered.filter { it.pos !in pattern }
        }
    }
}
