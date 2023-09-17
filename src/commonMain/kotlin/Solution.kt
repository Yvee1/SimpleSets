import geometric.voronoiDiagram
import highlights.Highlight
import org.openrndr.shape.ShapeContour
import patterns.Pattern
import highlights.toHighlight
import highlights.visibilityContours
import patterns.computePartition
import org.openrndr.shape.*
import patterns.Point

data class Solution(
    var patterns: List<Pattern> = listOf<Pattern>(),
    var highlights: List<Highlight> = listOf<Highlight>(),
    var clippedIslands: List<ShapeContour> = listOf<ShapeContour>(),
    var mergedIndex: MutableMap<Int, Int> = mutableMapOf<Int, Int>(),
    var mergedIslands: List<Pair<ShapeContour, List<Int>>> = emptyList(),
    var visibilityContours: List<List<ShapeContour>> = listOf<List<ShapeContour>>(),
    var voronoiCells: List<ShapeContour> = listOf<ShapeContour>(),
    var obstacles: List<Highlight> = listOf<Highlight>(),
    var visibilityGraph: Graph = Graph(emptyList(), emptyList(), emptyList()),
    var visibilityEdges: List<ShapeContour> = listOf<ShapeContour>(),
    var bridges: List<Bridge> = listOf<Bridge>(),
) {
    var calculating: Boolean = false

    fun compute(points: List<Point>,
                cps: ComputePartitionSettings,
                cds: ComputeDrawingSettings,
                cbs: ComputeBridgesSettings,
    ) {
        try {
            val partitionInstance = PartitionInstance(points, cps)
            patterns = partitionInstance.computePartition().patterns
            highlights = patterns.map { it.toHighlight(cds.expandRadius) }
            obstacles = highlights.map { it.scale(1 + cbs.clearance / it.circles.first().radius) }
            visibilityContours = highlights.map { i1 ->
                highlights.filter { i2 -> i2.type == i1.type }
                    .flatMap { i2 -> i1.visibilityContours(i2) }
            }
            voronoiCells =
                voronoiDiagram(
                    patterns.map { it.original() },
                    cds.expandRadius + cbs.clearance
                )
            clippedIslands = highlights.withIndex().map { (i, island) ->
                intersection(island.contour, voronoiCells[i]).outline
            }
            val tmp = mutableMapOf<Int, Pair<ShapeContour, List<Int>>>()
            mergedIslands = buildList {
                for (i1 in highlights.indices) {
                    for (i2 in i1 + 1 until highlights.size) {
                        if (highlights[i1].type != highlights[i2].type
                            || !highlights[i1].contour.bounds.intersects(highlights[i2].contour.bounds)
                        ) continue
                        if (highlights[i1].contour.intersections(highlights[i2].contour)
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
//            if (s.showVisibilityGraph || s.showBridges) {
                visibilityGraph = Graph(highlights, obstacles, voronoiCells)
                visibilityEdges = visibilityGraph.edges.map { it.contour }
                bridges = visibilityGraph.spanningTrees()
//            }
        }
        catch(e: Throwable) {
            e.printStackTrace()
        }
//        try {
//            calculating = true
//            patterns = problemInstance.computePartition(cs.disjoint)
//            highlights = patterns.map { it.toHighlight(cs.expandRadius) }
//            obstacles = highlights.map { it.scale(1 + cs.clearance / it.circles.first().radius) }
//            visibilityContours = highlights.map { i1 ->
//                highlights.filter { i2 -> i2.type == i1.type }
//                    .flatMap { i2 -> i1.visibilityContours(i2) }
//            }
//            voronoiCells =
//                voronoiDiagram(
//                    patterns.map { it.original() },
//                    cs.expandRadius + cs.clearance,
////                                        approxFactor = 1.0
//                )
//            if (s.overlapIslands) {
//                overlapIslands = highlights.withIndex().map { (i, isle) ->
//                    morphIsland(null, isle, highlights.subList(0, i))
//                }
//            } else {
//                clippedIslands =
//                    if (!cs.disjoint || !s.voronoiIntersect) highlights.map { it.contour } else highlights.withIndex()
//                        .map { (i, island) ->
//                            intersection(island.contour, voronoiCells[i]).outline
//                        }
//                val tmp = mutableMapOf<Int, Pair<ShapeContour, List<Int>>>()
//                mergedIslands = if (!s.mergeIslands) emptyList() else buildList {
//                    for (i1 in highlights.indices) {
//                        for (i2 in i1 + 1 until highlights.size) {
//                            if (highlights[i1].type != highlights[i2].type
//                                || !highlights[i1].contour.bounds.intersects(highlights[i2].contour.bounds)
//                            ) continue
//                            if (highlights[i1].contour.intersections(highlights[i2].contour)
//                                    .isNotEmpty()
//                            ) {
//                                val (c1, c2) = listOf(i1, i2).map { i ->
//                                    tmp[i] ?: (clippedIslands[i] to listOf(i))
//                                }
//                                if (c1 == c2) continue
//                                removeIf { it == c1 || it == c2 }
//                                val entry =
//                                    union(c1.first, c2.first).outline to (c1.second + c2.second)
//                                add(entry)
//                                (c1.second + c2.second).forEach { i -> tmp[i] = entry }
//                            }
//                        }
//                    }
//                }
//                mergedIndex = tmp.map { (i, target) ->
//                    i to mergedIslands.withIndex().find { it.value == target }!!.index
//                }.toMap().toMutableMap()
//            }
//            if (ds.showVisibilityGraph || ds.showBridges) {
//                visibilityGraph = Graph(highlights, obstacles, voronoiCells, cs.smoothBridges)
//                visibilityEdges = visibilityGraph.edges.map { it.contour }
//                bridges = visibilityGraph.spanningTrees()
//            }
//        }
//        catch(e: Throwable) {
//            e.printStackTrace()
//            calculating = false
//        }
//        calculating = false
    }

    companion object {
        fun compute(points: List<Point>,
                    cps: ComputePartitionSettings,
                    cds: ComputeDrawingSettings,
                    cbs: ComputeBridgesSettings,
        ): Solution {
            val sol = Solution()
            sol.compute(points, cps, cds, cbs)
            return sol
        }
    }
}