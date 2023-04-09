import islands.Island
import org.openrndr.shape.ShapeContour
import patterns.Pattern
import islands.toIsland
import islands.visibilityContours
import patterns.computePartition
import org.openrndr.shape.*

data class Solution(
    var patterns: List<Pattern> = listOf<Pattern>(),
    var islands: List<Island> = listOf<Island>(),
    var clippedIslands: List<ShapeContour> = listOf<ShapeContour>(),
    var mergedIndex: MutableMap<Int, Int> = mutableMapOf<Int, Int>(),
    var mergedIslands: List<Pair<ShapeContour, List<Int>>> = emptyList(),
    var visibilityContours: List<List<ShapeContour>> = listOf<List<ShapeContour>>(),
    var voronoiCells: List<ShapeContour> = listOf<ShapeContour>(),
    var obstacles: List<Island> = listOf<Island>(),
    var visibilityGraph: Graph = Graph(emptyList(), emptyList(), emptyList()),
    var visibilityEdges: List<ShapeContour> = listOf<ShapeContour>(),
    var bridges: List<Bridge> = listOf<Bridge>(),
) {
    fun compute(problemInstance: ProblemInstance, s: Settings) {
        try {
            patterns = problemInstance.computePartition(s.disjoint)
            islands = patterns.map { it.toIsland(s.expandRadius) }
            obstacles = islands.map { it.scale(1 + s.clearance / it.circles.first().radius) }
            visibilityContours = islands.map { i1 ->
                islands.filter { i2 -> i2.type == i1.type }
                    .flatMap { i2 -> i1.visibilityContours(i2) }
            }
            voronoiCells =
                approximateVoronoiDiagram(
                    patterns.map { it.original() },
                    s.expandRadius + s.clearance
                )
            clippedIslands = islands.withIndex().map { (i, island) ->
                intersection(island.contour, voronoiCells[i]).outline
            }
            val tmp = mutableMapOf<Int, Pair<ShapeContour, List<Int>>>()
            mergedIslands = buildList {
                for (i1 in islands.indices) {
                    for (i2 in i1 + 1 until islands.size) {
                        if (islands[i1].type != islands[i2].type
                            || !islands[i1].contour.bounds.intersects(islands[i2].contour.bounds)
                        ) continue
                        if (islands[i1].contour.intersections(islands[i2].contour)
                                .isNotEmpty()
                        ) {
                            val (c1, c2) = listOf(i1, i2).map { i ->
                                tmp[i] ?: (clippedIslands[i] to listOf(i))
                            }
                            if (c1 == c2) continue
                            removeAll(filter { it == c1 || it == c2 })
                            val entry =
                                union(c1.first, c2.first).outline to (c1.second + c2.second)
                            add(entry)
                            (c1.second + c2.second).forEach { i -> tmp[i] = entry }
                        }
                    }
                }
            }
            mergedIndex = tmp.map { (i, target) ->
                i to mergedIslands.withIndex().find { it.value == target }!!.index
            }.toMap().toMutableMap()
            if (s.showVisibilityGraph || s.showBridges) {
                visibilityGraph = Graph(islands, obstacles, voronoiCells)
                visibilityEdges = visibilityGraph.edges.map { it.contour }
                bridges = visibilityGraph.spanningTrees()
            }
        }
        catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    companion object {
        fun compute(problemInstance: ProblemInstance, s: Settings): Solution {
            val sol = Solution()
            sol.compute(problemInstance, s)
            return sol
        }
    }
}