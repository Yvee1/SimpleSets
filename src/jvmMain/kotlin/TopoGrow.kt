import geometric.distanceTo
import geometric.overlaps
import highlights.toHighlight
import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.BooleanParameter
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.intersection
import patterns.*
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val ds = DrawSettings(pSize = 2.5)
        val cds = ComputeDrawingSettings(expandRadius = ds.pSize * 3)
        val cps = ComputePartitionSettings(partitionClearance = cds.expandRadius)
        val tgs = TopoGrowSettings()

        val points = getExampleInput(ExampleInput.NYC).map {
            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
        }

        var history = topoGrow(points, cps, tgs)

        println(angleBetween(Vector2(1.0, 0.0), Vector2(-1.0, 0.01)))

        extend(Camera2D()) {
            view = Matrix44(
                2.0576200100601754, 0.0, 0.0, 122.08451003137796,
                0.0, 2.0576200100601754, 0.0, -85.79571180636515,
                0.0, 0.0, 2.0576200100601754, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        }

        val gui = GUI()
        val set = object {
            @DoubleParameter("Filtration", 0.0, 1.0)
            var filtration: Double = 1.0
        }
        gui.add(set)
        gui.add(tgs)

        keyboard.keyDown.listen { keyEvent ->
            if (keyEvent.key == KEY_SPACEBAR) {
                history = topoGrow(points, cps, tgs)
            }
        }

        extend(gui)
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                for (p in history[ceil(set.filtration * history.lastIndex).toInt()].second.patterns) {
                    pattern(p, ds)
                }
            }
        }
    }
}

data class PossibleMergeEvent(val time: Double, val p1: Pattern, val p2: Pattern, val mergeResult: Pattern)

data class TopoGrowSettings(
    @BooleanParameter("Banks")
    var banks: Boolean = true,

    @BooleanParameter("Islands")
    var islands: Boolean = true,

    @BooleanParameter("Postpone cover radius increase")
    var postponeCoverRadiusIncrease: Boolean = true,

    @BooleanParameter("Postpone intersections")
    var postponeIntersections: Boolean = true,

    @DoubleParameter("Forbid too close", 0.0, 1.0)
    var forbidTooClose: Double = 0.1
)

fun intersectionDelay(partition: Partition, p: Pattern, q: Pattern, newPattern: Pattern,
                      cps: ComputePartitionSettings, tgs: TopoGrowSettings): Double {
    if (!tgs.postponeIntersections) return 0.0
    var intersectionArea = 0.0
    for (pt in partition.points) {
        if (pt.type != newPattern.type && pt !in newPattern.points && newPattern.contour.distanceTo(pt.pos) < cps.partitionClearance * 2) {
            val ptShape = Circle(pt.pos, cps.partitionClearance).shape
            val npShape = newPattern.toHighlight(cps.partitionClearance).contour.shape
            val newTotalArea = npShape.intersection(ptShape).area
            val pShape = p.toHighlight(cps.partitionClearance).contour.shape
            val qShape = q.toHighlight(cps.partitionClearance).contour.shape
            val oldAreaP = pShape.intersection(ptShape).area
            val oldAreaQ = qShape.intersection(ptShape).area
            intersectionArea += newTotalArea - oldAreaP - oldAreaQ
        }
    }
    return sqrt(max(intersectionArea, 0.0)) / 2 // TODO: Explain this factor half... or change it

//    if (!tgs.postponeIntersections) return 0.0
//    var intersectionArea = 0.0
//    for (pt in partition.points) {
//        if (pt.type != newPattern.type && pt !in newPattern.points && newPattern.contour.distanceTo(pt.pos) < cps.partitionClearance * 2) {
//            val ptShape = Circle(pt.pos, cps.partitionClearance).shape
//            val npShape = newPattern.toHighlight(cps.partitionClearance).contour.shape
//            val newTotalArea = npShape.intersection(ptShape).area
//            val pShape = Circle(p.point.pos, cps.partitionClearance).shape
//            val qShape = Circle(q.point.pos, cps.partitionClearance).shape
//            val oldAreaP = pShape.intersection(ptShape).area
//            val oldAreaQ = qShape.intersection(ptShape).area
//            println(newTotalArea)
//            intersectionArea += newTotalArea - oldAreaP - oldAreaQ
//        }
//    }
//    return sqrt(max(intersectionArea, 0.0)) / 2
}

fun topoGrow(points: List<Point>, cps: ComputePartitionSettings, tgs: TopoGrowSettings): List<Pair<Double, Partition>> {
    // 1. Add SinglePoint -- SinglePoint merges
    // 2. Repeat the following
    // 3. Pick next event
    // 4. Check if merge is actually possible
    // 5. If so, do the merge and compute new possible merge events for this new pattern

    // Step 1. takes O(n^2 log(n)) time

    // O(n^2) events
    // Step 3. takes O(log(n)) time
    // Step 4.
    // - O(n) to check if patterns still exist
    // - O(n^2) to check for intersections
    // Step 5.
    // - O(n) to create the new pattern
    // - O(n^2) probably to create new merge events
    // O(n^4) in total


    val partition = Partition(points)

    val history = mutableListOf(0.0 to partition.copy())

    val events = PriorityQueue<PossibleMergeEvent>(compareBy { it.time })

    // 1. Add SinglePoint -- SinglePoint merges
    for (i in partition.patterns.indices) {
        val p = partition.patterns[i] as SinglePoint
        for (j in i + 1 until partition.patterns.size) {
            val q = partition.patterns[j] as SinglePoint
            if (p.type != q.type) continue

            val newPattern = Matching(p.point, q.point)

            val tooClose: Boolean = run {
                for (pt in partition.points) {
                    if (pt !in newPattern.points && newPattern.contour.distanceTo(pt.pos) < tgs.forbidTooClose * cps.partitionClearance) {
                        return@run true
                    }
                }
                return@run false
            }

            if (tooClose) continue

            val t = newPattern.coverRadius + intersectionDelay(partition, p, q, newPattern, cps, tgs)
            val ev = PossibleMergeEvent(t, p, q, newPattern)
            events.add(ev)
        }
    }

    while (events.isNotEmpty()) {
        // 3. Pick next event
        val ev = events.poll()

        // 4. Check if merge is actually possible
        // - Check if patterns still exist
        if (ev.p1 !in partition.patterns || ev.p2 !in partition.patterns) continue

        // - Check for intersections
        val newPattern = ev.mergeResult

        val intersects = partition.patterns.any {
            it != ev.p1 && it != ev.p2 && it !is SinglePoint && it.contour.overlaps(newPattern.contour)
        }

        if (intersects) continue

        val tooClose: Boolean = run {
            for (pt in partition.points) {
                if (pt !in newPattern.points && newPattern.contour.distanceTo(pt.pos) < tgs.forbidTooClose * cps.partitionClearance) {
                    return@run true
                }
            }
            return@run false
        }

        if (tooClose) continue

        // 5. If so, do the merge and compute new possible merge events for this new pattern
        partition.merge(ev.p1, ev.p2, newPattern)

        history.add(ev.time to partition.copy())

        for (p in partition.patterns) {
            if (p == newPattern || p.type != newPattern.type) continue

            if (tgs.islands) {
                val pts = p.points + newPattern.points
                val coverRadius = coverRadius(pts.map { it.pos })
                val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
                    else coverRadius - max(p.coverRadius, newPattern.coverRadius)
                val freshPattern = Island(pts)
                val t = coverRadius + delay + intersectionDelay(partition, p, newPattern, freshPattern, cps, tgs)

                events.add(PossibleMergeEvent(t, newPattern, p, freshPattern))
            }

            if (tgs.banks && newPattern is Matching && p is SinglePoint) {
                val result = newPattern.extension(p.point, cps)

                if (result != null) {
                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
                        else result.second.coverRadius -
                            newPattern.coverRadius
                    val t = result.first + delay + intersectionDelay(partition, p, newPattern, result.second, cps, tgs)
                    events.add(PossibleMergeEvent(t, newPattern, p, result.second))
                }
            }

//            if (tgs.banks && newPattern is Bank && p is SinglePoint) {
//                val result = newPattern.extension(p.point, cps)
//                if (result != null) {
//                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
//                    else result.second.coverRadius -
//                            newPattern.coverRadius
//                    val t = result.first + delay + intersectionDelay(partition, p, newPattern, result.second, cps, tgs)
//                    events.add(PossibleMergeEvent(t, newPattern, p, result.second))
//                }
//            }
//
//            if (tgs.banks && newPattern is Bank && p is Bank) {
//                val result = newPattern.extension(p, cps)
//                if (result != null) {
//                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
//                    else result.second.coverRadius -
//                            max(newPattern.coverRadius, p.coverRadius)
//                    val t = result.first + delay + intersectionDelay(partition, p, newPattern, result.second, cps, tgs)
//                    events.add(PossibleMergeEvent(t, newPattern, p, result.second))
//                }
//            }

            if (tgs.banks && newPattern is Matching && p is Matching) {
//                val result = newPattern.toBank().extension(p, cps)
                val b1 = Bank(newPattern.points + p.points)
                val b2 = Bank(newPattern.points.reversed() + p.points)
                val b3 = Bank(p.points + newPattern.points)
                val b4 = Bank(p.points + newPattern.points.reversed())
                val b = listOf(b1, b2, b3, b4).filter { it.isValid(cps) }.minByOrNull { it.coverRadius }

                if (b != null) {
                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
                    else b.coverRadius -
                            max(newPattern.coverRadius, p.coverRadius)
                    val t = b.coverRadius + delay + intersectionDelay(partition, p, newPattern, b, cps, tgs)
                    events.add(PossibleMergeEvent(t, newPattern, p, b))
                }
            }

            if (tgs.banks && newPattern is Matching && p is Bank) {
                val result = p.extension(newPattern, cps)

                if (result != null) {
                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
                    else result.second.coverRadius -
                            max(newPattern.coverRadius, p.coverRadius)
                    val t = result.first + delay + intersectionDelay(partition, p, newPattern, result.second, cps, tgs)
                    events.add(PossibleMergeEvent(t, newPattern, p, result.second))
                }
            }

            if (tgs.banks && newPattern is Bank && p !is Island) {
                val result = when(p) {
                    is Bank -> newPattern.extension(p, cps)
                    is Matching -> newPattern.extension(p, cps)
                    is SinglePoint -> newPattern.extension(p.point, cps)
                    is Island -> error("Impossible")
                }

                if (result != null) {
                    val delay = if (!tgs.postponeCoverRadiusIncrease) 0.0
                    else result.second.coverRadius -
                            max(newPattern.coverRadius, p.coverRadius)
                    val t = result.first + delay + intersectionDelay(partition, p, newPattern, result.second, cps, tgs)
                    events.add(PossibleMergeEvent(t, newPattern, p, result.second))
                }
            }

            // TODO:
            //   - Refactor.
            //   - Make bank cover radius delay different from island cover radius delay (square island?)

        }
    }

    return history
}