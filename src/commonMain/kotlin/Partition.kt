import highlights.toHighlight
import org.openrndr.shape.intersection
import patterns.*
import kotlin.math.min
import kotlin.math.sqrt

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

    val pointToPattern: MutableMap<Point, Pattern> = buildMap {
        for (pattern in patterns) {
            for (pt in pattern.points) {
                put(pt, pattern)
            }
        }
    }.toMutableMap()


    fun removedPointFromPattern(p: Point, i: Int, newPattern: Pattern) {
        patterns[i] = newPattern
        newPattern.points.forEach { pt ->
            pointToPattern[pt] = newPattern
        }
        val singlePoint = SinglePoint(p)
        pointToPattern[p] = singlePoint
        patterns.add(singlePoint)
    }

    fun index(pattern: Pattern): Int {
        val maybe = patterns.withIndex().find { it.value == pattern }
        if (maybe == null) {
            println(pattern)
            error("Pattern not present")
        } else {
            return maybe.index
        }
    }

    fun removedPointFromPattern(p: Point, pattern: Pattern, newPattern: Pattern) {
        patterns.remove(pattern)
        patterns.add(newPattern)
        newPattern.points.forEach { pt ->
            pointToPattern[pt] = newPattern
        }
        val singlePoint = SinglePoint(p)
        pointToPattern[p] = singlePoint
        patterns.add(singlePoint)
    }

    fun add(p: Point) {
        points.add(p)
        val pattern = SinglePoint(p)
        patterns.add(pattern)
        pointToPattern[p] = pattern
    }

    fun removeAt(index: Int) {
        val pt = points.removeAt(index)
        pointToPattern.remove(pt)
        breakPatterns(pt)
    }

    fun removeLast() {
        val pt = points.removeLast()
        pointToPattern.remove(pt)
        breakPatterns(pt)
    }

    fun breakPatterns(pt: Point) {
        val pattern = patterns.find { pt in it.points }!!
        patterns.remove(pattern)
        val newPatterns = (pattern.points - pt).map { SinglePoint(it) }
        patterns.addAll(newPatterns)
        newPatterns.forEach {
            pointToPattern[it.point] = it
        }
    }

    fun breakPatterns2(pt: Point) {
        val pattern = patterns.find { pt in it.points }!!
        patterns.remove(pattern)
        val newPatterns = (pattern.points).map { SinglePoint(it) }
        patterns.addAll(newPatterns)
        newPatterns.forEach {
            pointToPattern[it.point] = it
        }
    }

    fun cost(singleDouble: Double, partitionClearance: Double): Double {
        var intersectionArea = 0.0
        if (partitionClearance > 0) {
            for (i in patterns.indices) {
                for (j in i + 1 until patterns.size) {
                    val p = patterns[i]
                    val q = patterns[j]
                    val common = intersection(
                        p.toHighlight(partitionClearance).contour,
                        q.toHighlight(partitionClearance).contour
                    )
                    intersectionArea += common.area
                }
            }
        }
        return patterns.sumOf { it.cost(singleDouble) }// + intersectionArea
    }

    // Assumption: newPatterns are a subset of patterns, and removePatterns is disjoint from patterns.
    fun updateCost(singleDouble: Double, partitionClearance: Double,
                   removedPatterns: List<Pattern>, newPatterns: List<Pattern>): Double {
        var delta = 0.0

        for (p in removedPatterns) {
            delta -= p.cost(singleDouble)
        }

        for (p in newPatterns) {
            delta += p.cost(singleDouble)
        }

        if (partitionClearance == 0.0) return delta

        for (p in patterns - newPatterns) {
            for (q in removedPatterns) {
                if (p.points.intersect(q.points.toSet()).isEmpty()) {
                    val common = intersection(
                        p.toHighlight(partitionClearance).contour,
                        q.toHighlight(partitionClearance).contour
                    )
                    delta -= common.area
                }
            }
        }

        for (i in removedPatterns.indices) {
            for (j in i + 1 until removedPatterns.size) {
                val p = removedPatterns[i]
                val q = removedPatterns[j]
                val common = intersection(
                    p.toHighlight(partitionClearance).contour,
                    q.toHighlight(partitionClearance).contour
                )
                delta -= common.area
            }
        }

        for (p in patterns - newPatterns.toSet()) {
            for (q in newPatterns) {
                val common = intersection(
                    p.toHighlight(partitionClearance).contour,
                    q.toHighlight(partitionClearance).contour
                )
                delta += common.area
            }
        }

        for (i in newPatterns.indices) {
            for (j in i + 1 until newPatterns.size) {
                val p = newPatterns[i]
                val q = newPatterns[j]

                val common = intersection(
                    p.toHighlight(partitionClearance).contour,
                    q.toHighlight(partitionClearance).contour
                )
                delta += common.area
            }
        }

        return delta
    }

    fun copy(): Partition {
        return copy(points = points, patterns = patterns.toMutableList())
    }

    companion object {
        val EMPTY = Partition(mutableListOf(), mutableListOf())
    }
}

fun Pattern.cost(singleDouble: Double): Double =
    when(this) {
        is SinglePoint -> singleDouble / 2
        is Matching -> point1.pos.distanceTo(point2.pos)
        is Island -> coverRadius(points.map { it.pos }) * 2.0
        is Reef -> maxDistance
    }