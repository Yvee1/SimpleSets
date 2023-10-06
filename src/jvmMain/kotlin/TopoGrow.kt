import geometric.overlaps
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Matrix44
import patterns.*
import java.util.PriorityQueue
import kotlin.math.ceil

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val ds = DrawSettings(pSize = 2.5)
        val cds = ComputeDrawingSettings(expandRadius = ds.pSize * 3)

        val points = getExampleInput(ExampleInput.NYC).map {
            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
        }

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

        val history = mutableListOf<Partition>(partition.copy())

        val events = PriorityQueue<PossibleMergeEvent>(compareBy { it.time })

        // 1. Add SinglePoint -- SinglePoint merges
        for (i in partition.patterns.indices) {
            val p = partition.patterns[i]
            for (j in i + 1 until partition.patterns.size) {
                val q = partition.patterns[j]
                if (p.type != q.type) continue
                val t = p.points[0].pos.distanceTo(q.points[0].pos) / 2
                val ev = PossibleMergeEvent(t, p, q)
                events.add(ev)
            }
        }

        while (events.isNotEmpty()) {
            // 3. Pick next event
            val ev = events.poll()

            println(events.size)
            println(ev)

            // 4. Check if merge is actually possible
            // - Check if patterns still exist
            if (ev.p1 !in partition.patterns || ev.p2 !in partition.patterns) continue

            // - Check for intersections
            val newPattern = if (ev.p1 is SinglePoint && ev.p2 is SinglePoint) Matching(ev.p1.point, ev.p2.point)
            else Island(ev.p1.points + ev.p2.points)

            val intersects = partition.patterns.any {
                it != ev.p1 && it != ev.p2 && it !is SinglePoint && it.contour.overlaps(newPattern.contour)
            }

            if (intersects) continue

            // 5. If so, do the merge and compute new possible merge events for this new pattern
            partition.merge(ev.p1, ev.p2, newPattern)

            history.add(partition.copy())

            for (p in partition.patterns) {
                if (p == newPattern || p.type != newPattern.type) continue
                val t = coverRadius(p.points.map { it.pos } + newPattern.points.map { it.pos })
                events.add(PossibleMergeEvent(t, newPattern, p))
            }
        }

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
        extend(gui)
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                for (p in history[ceil(set.filtration * history.lastIndex).toInt()].patterns) {
                    pattern(p, ds)
                }
            }
        }
    }
}

data class PossibleMergeEvent(val time: Double, val p1: Pattern, val p2: Pattern)
