import islands.toIsland
import islands.visibilityContours
import patterns.computePartition

fun main() {
    val points = getExampleInput(ExampleInput.NYC)
    val instance = ProblemInstance(points, 6.0, 18.634, 50.64, true, 107.32, 54.783)
//    val instance = ProblemInstance(points, 6.0, 0.634, 0.64, true, 107.32, 54.783)
    val patterns = instance.computePartition(disjoint=true)
    val islands = patterns.map { it.toIsland(instance.expandRadius) }
    val obstacles = islands.map { it.scale(2.0) }
    islands.map { i1 -> islands.filter { i2 -> i2.type == i1.type }.flatMap { i2 -> i1.visibilityContours(i2) } }
    val voronoiCells = approximateVoronoiDiagram(patterns.map { it.original() }, instance.expandRadius)
    Graph(islands, obstacles, voronoiCells)
}