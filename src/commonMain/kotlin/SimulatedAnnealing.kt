import geometric.overlaps
import highlights.connector
import org.openrndr.shape.contains
import patterns.*
import kotlin.math.*
import kotlin.random.Random

fun repartitionClosePatterns(initial: Partition, cps: ComputePartitionSettings, iters: Int, rng: Random): Partition {
    var current: Partition = initial.copy()

    repeat(iters) {
        println(it)
        val pattern = current.patterns.random(rng)
        val other = (current.patterns - pattern).minBy {
            pattern.connector(it).squaredLength
        }
        current = repartitionPatterns(current, listOf(pattern, other), 100, cps, rng)
    }

    return current
}

fun simulatedAnnealing(initial: Partition, cps: ComputePartitionSettings, kmax: Int, rng: Random): Partition {
    var current = initial.copy()

    val pEnd = 1E-9
    val tEnd = -current.minDist / ln(pEnd)
    println("Ending temperature $tEnd")
    var temperature = 0.5//startingTemperature(current.copy(), cps, 100, rng)
    println("Starting temperature: ${temperature}")
    val c = (tEnd / temperature).pow(1.0 / kmax)

    var best: Partition = initial.copy()
    var bestCost: Double = initial.cost(cps.singleDouble, cps.partitionClearance)

    println(current.cost(cps.singleDouble, cps.partitionClearance))
    for (k in 0 until kmax) {
        if (k % 10 == 0)
            println(current.cost(cps.singleDouble, cps.partitionClearance))

        val candidate = current.copy()
        candidate.mutate(cps, rng=rng)
        if (rng.nextDouble() < prob(current.cost(cps.singleDouble, cps.partitionClearance), candidate.cost(cps.singleDouble, cps.partitionClearance), temperature)) {
            current = candidate
            if (current.cost(cps.singleDouble, cps.partitionClearance) < bestCost) {
                best = current.copy()
                bestCost = current.cost(cps.singleDouble,cps.partitionClearance)
            }
        }

        temperature *= c
    }

    return best
//    return current
}

fun prob(currentCost: Double, candidateCost: Double, temp: Double): Double =
    min(1.0, exp((currentCost - candidateCost) / temp))

fun startingTemperature(partition: Partition, cps: ComputePartitionSettings, iters: Int, rng: Random): Double {
    var costChange = 0.0

    for (k in 0 until iters) {
        val cost1 = partition.cost(cps.singleDouble, cps.partitionClearance)
        partition.mutate(cps, rng=rng)
        val cost2 = partition.cost(cps.singleDouble, cps.partitionClearance)
        costChange += abs(cost2 - cost1)
    }

    return costChange / iters
}

sealed class Mutation

data class PointSplit(val originalPattern: Pattern, val splitOffPattern: Pattern, val shrunkPattern: Pattern): Mutation()

data class PatternMerge(val oldPatterns: List<Pattern>, val newPattern: Pattern): Mutation()

data object Failed: Mutation()

fun Partition.mutate(cps: ComputePartitionSettings, subset: List<Pattern> = patterns, rng: Random = Random.Default): Mutation {
    var iters = 0
    while (iters < 1000) {
        iters++

//        val i = subset?.random(rng) ?: rng.nextInt(patterns.indices)
//        val pattern = patterns[i]
        val pattern = subset.random(rng)
        val remove = rng.nextBoolean()

        if (remove && pattern.weight > 1) {
            val result = pattern.removeRandomPoint(cps, rng)
            if (result != null) {
                val (newPattern, p) = result
                removedPointFromPattern(p, pattern, newPattern)
                return PointSplit(pattern, patterns.last(), newPattern)
            }
        } else {
            val singlePoints = subset.filter { it is SinglePoint }
            val candSinglePoints = singlePoints.filter { it.type == pattern.type }
            if (candSinglePoints.isEmpty() || candSinglePoints.size == 1 && candSinglePoints[0] == pattern) continue
            val pt =
                (if (pattern is SinglePoint) (candSinglePoints.filter { it != pattern }) else candSinglePoints).random(rng)
            val merge = maybeAddSinglePoint(pattern, cps, pt as SinglePoint, singlePoints.map { it as SinglePoint })
            if (merge != null) {
                return merge
            } else continue
        }
    }

    return Failed
//        error("Too many iterations trying to find neighbour of partition")
}

fun repartitionPatterns(initial: Partition, patterns: List<Pattern>, iters: Int,
                        cps: ComputePartitionSettings, rng: Random = Random.Default): Partition {
    val initialCost = initial.cost(cps.singleDouble, cps.partitionClearance)
    var best: Partition = initial.copy()
    var bestCost: Double = initialCost

    val current = initial.copy()
    var currentCost = initialCost

    val currentPatterns = patterns.toMutableList()

    // Random walk
    repeat(iters) {
        val mutation = current.mutate(cps, currentPatterns, rng)
        val removedPatterns = mutableListOf<Pattern>()
        val newPatterns = mutableListOf<Pattern>()
        when (mutation) {
            is PointSplit -> {
                removedPatterns.add(mutation.originalPattern)
                newPatterns.add(mutation.splitOffPattern)
                newPatterns.add(mutation.shrunkPattern)
            }
            is PatternMerge -> {
                removedPatterns.addAll(mutation.oldPatterns)
                newPatterns.add(mutation.newPattern)
            }
            Failed -> {}
        }
        currentPatterns.removeAll(removedPatterns)
        currentPatterns.addAll(newPatterns)
        val newCost = currentCost + current.updateCost(cps.singleDouble,
            cps.partitionClearance, removedPatterns, newPatterns)
        if (abs(newCost - current.cost(cps.singleDouble, cps.partitionClearance)) > 0.001) {
            error("Problem!")
        }
            //current.cost(cps.singleDouble, cps.partitionClearance)
//        println("New cost: $newCost")
//        println("Calculated difference: ${current.updateCost(cps.singleDouble,
//            cps.partitionClearance, removedPatterns, newPatterns)}")
        if (newCost < bestCost) {
            best = current.copy()
            bestCost = newCost
            println(bestCost)
        }
        currentCost = newCost
    }

    return best
}

fun Partition.maybeAddSinglePoint(pattern: Pattern, cps: ComputePartitionSettings, pt: SinglePoint,
                                  singlePoints: List<SinglePoint> = patterns.filterIsInstance<SinglePoint>()): PatternMerge? {
    return maybeAddSinglePoint(index(pattern), cps, pt, singlePoints)
}

fun Partition.maybeAddSinglePoint(patternIndex: Int, cps: ComputePartitionSettings, pt: SinglePoint,
                                  singlePoints: List<SinglePoint> = patterns.filterIsInstance<SinglePoint>()): PatternMerge? {
    val pattern = patterns[patternIndex]
    var result = pattern.addPoint(cps, pt.point)
    if (result != null) {
        val additionalRemovals = mutableListOf<Pattern>()

        // TODO: Collinearity reef edge case..
        if (result is Island) {
            val accidentalAdds = singlePoints.filter {
                it != pt && it.point.pos in (result as Island).contour
            }
            if (accidentalAdds.isNotEmpty()) {
                if (accidentalAdds.any { it.type != result!!.type }) return null
                result = (result as Island).addPoints(cps, accidentalAdds.map { it.point })
                additionalRemovals.addAll(accidentalAdds)
            }
        }

        if (result !is SinglePoint) {
            var intersections = false
            for (p in patterns) {
                if (p == pattern || p is SinglePoint) continue
                if (p.contour.overlaps(result!!.contour)) {
                    intersections = true
                    break
                }
            }
            if (intersections) return null
        }

        patterns[patternIndex] = result!!
        patterns.remove(pt)
        patterns.removeAll(additionalRemovals)
        result.points.forEach {
            pointToPattern[it] = result
        }
        return PatternMerge(additionalRemovals + pt + pattern, result)
    } else {
        return null
    }
}

private fun Island.addPoints(cps: ComputePartitionSettings, pts: List<Point>): Pattern? =
    if (coverRadius((points + pts).map { it.pos }) <= cps.clusterRadius) {
        Island(points + pts, weight + 1)
    } else {
        null
    }

fun Pattern.addPoint(cps: ComputePartitionSettings, pt: Point): Pattern? =
    when (this) {
        is SinglePoint -> {
            if (point.pos.distanceTo(pt.pos) <= max(cps.bendDistance, cps.clusterRadius * 2)) {
                Matching(point, pt)
            } else {
                null
            }
        }

        is Island -> {
            if (coverRadius((points + pt).map { it.pos }) <= cps.clusterRadius) {
                Island(points + pt, weight + 1)
            } else {
                null
            }
        }

        is Reef -> {
            // Check whether pt is within bend distance
            val dStart2 = pt.pos.squaredDistanceTo(start.pos)
            val dEnd2 = pt.pos.squaredDistanceTo(end.pos)

            if (dStart2 < dEnd2) {
                if (dStart2 < cps.bendDistance.pow(2)) {
                    Reef(listOf(pt) + points, weight + 1)
                } else {
                    null
                }
            } else {
                if (dEnd2 < cps.bendDistance.pow(2)) {
                    // TODO: other criteria..
                    Reef(points + pt, weight + 1)
                } else {
                    null
                }
            }
        }

        is Matching -> {
            if (coverRadius((points + pt).map { it.pos }) <= cps.clusterRadius) {
                Island(points + pt, weight + 1)
            } else {
                // TODO: Reef..
                null
            }
        }
    }

fun Pattern.removeRandomPoint(cps: ComputePartitionSettings, rng: Random): Pair<Pattern, Point>? {
    val (newPattern, p) = when (this) {
        is Island -> removeRandomPoint(rng)
        is Reef -> removeRandomPoint(rng)
        is Matching -> removeRandomPoint(rng)
        is SinglePoint -> error("Cannot remove point from SinglePoint")
    }

    return if (this is Island && !newPattern.isValid(cps)) {
        null
    } else {
        newPattern to p
    }
}

// Remove random boundary point
fun Island.removeRandomPoint(rng: Random): Pair<Pattern, Point> {
    val pt = boundaryPoints.random(rng)
    val newPoints = points - pt

    if (newPoints.size == 2) {
        return Matching(newPoints[0], newPoints[1]) to pt
    }

    return Island(newPoints, weight - 1) to pt
}

// Remove random end point
fun Reef.removeRandomPoint(rng: Random): Pair<Pattern, Point> {
    val removeStart = rng.nextBoolean()

    if (points.size == 3) {
        return if (removeStart) {
            Matching(points[1], points[2]) to points[0]
        } else {
            Matching(points[0], points[1]) to points[2]
        }
    }

    return if (removeStart) {
        copy(points = points.drop(1), weight = weight - 1) to points.first()
    } else {
        copy(points = points.dropLast(1), weight = weight - 1) to points.last()
    }
}

fun Matching.removeRandomPoint(rng: Random): Pair<SinglePoint, Point> {
    val removeFirst = rng.nextBoolean()
    return if (removeFirst) {
        SinglePoint(point2) to point1
    } else {
        SinglePoint(point1) to point2
    }
}