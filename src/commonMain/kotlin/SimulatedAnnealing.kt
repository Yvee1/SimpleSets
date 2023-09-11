import geometric.overlaps
import patterns.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt

fun simulatedAnnealing(initial: Partition, cps: ComputePartitionSettings, kmax: Int, random: Random) {
    initial.mutate(cps, random)
}

fun Partition.mutate(cps: ComputePartitionSettings, rng: Random) {
    var found = false
    var iters = 0
    while (iters < 1000) {
        iters++

        val i = rng.nextInt(patterns.indices)
        val pattern = patterns[i]
        val remove = rng.nextBoolean()

        if (remove && pattern.weight > 1) {
            val result = pattern.removeRandomPoint(cps, rng)
            if (result != null) {
                val (newPattern, p) = result
                found = true
                patterns[i] = newPattern
                patterns.add(SinglePoint(p))
                break
            }
        } else {
            val singlePoints = patterns.filterIsInstance<SinglePoint>()
            if (singlePoints.isEmpty() || singlePoints.size == 1 && singlePoints[0] == pattern) continue
            val pt =
                (if (pattern is SinglePoint) (singlePoints.filter { it != pattern }) else singlePoints).random(rng)
            val result = pattern.addPoint(cps, pt.point)
            if (result != null) {
                if (result !is SinglePoint) {
                    var intersections = false
                    for (p in patterns) {
                        if (p == pattern || p is SinglePoint) continue
                        if (p.contour.overlaps(result.contour)) {
                            intersections = true
                            break
                        }
                    }
                    if (intersections) continue
                }

                found = true
                patterns[i] = result
                patterns.remove(pt)
                break
            }
        }
    }

    if (!found)
        error("Too many iterations trying to find neighbour of partition")
}

fun Pattern.addPoint(cps: ComputePartitionSettings, pt: Point): Pattern? =
    when (this) {
        is SinglePoint -> {
            if (point.pos.distanceTo(pt.pos) <= min(cps.bendDistance, cps.clusterRadius * 2)) {
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