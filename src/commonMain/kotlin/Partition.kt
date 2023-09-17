import patterns.*
import kotlin.math.min
import kotlin.math.pow

data class Partition(val points: MutableList<Point>, val patterns: MutableList<Pattern>) {
    constructor(points: List<Point>): this(points.toMutableList(), points.map { SinglePoint(it) }.toMutableList())

    val minDist: Double = run {
        var minD = Double.MAX_VALUE
        for (p in points) {
            for (q in points) {
                if (p == q) continue
                minD = min(p.pos.distanceTo(q.pos), minD)
            }
        }
        minD
    }

    fun add(p: Point) {
        points.add(p)
        patterns.add(SinglePoint(p))
    }

    fun removeAt(index: Int) {
        val pt = points.removeAt(index)
        breakPatterns(pt)
    }

    fun removeLast() {
        val pt = points.removeLast()
        breakPatterns(pt)
    }

    fun breakPatterns(pt: Point) {
        val pattern = patterns.find { pt in it.points }!!
        patterns.remove(pattern)
        patterns.addAll((pattern.points - pt).map { SinglePoint(it) })
    }

    fun breakPatterns2(pt: Point) {
        val pattern = patterns.find { pt in it.points }!!
        patterns.remove(pattern)
        patterns.addAll((pattern.points).map { SinglePoint(it) })
    }

    fun cost(): Double {
        val cardinality = patterns.size
        return patterns.sumOf { it.cost() } + (cardinality * minDist).pow(2)
//        return cardinality * 1.0
    }

    fun copy(): Partition {
        return copy(points = points, patterns = patterns.toMutableList())
    }

    companion object {
        val EMPTY = Partition(mutableListOf(), mutableListOf())
    }
}

fun Pattern.cost(): Double =
    when(this) {
        is SinglePoint -> 0.0
        is Matching -> point1.pos.distanceTo(point2.pos)
        is Island -> coverRadius(points.map { it.pos }) * 2.0
        is Reef -> maxDistance
    }.pow(2)