import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.MessageEvent

external val self: DedicatedWorkerGlobalScope

fun main() {
    var lastFiltration: List<Pair<Double, Partition>>? = null
    var lastComputeAssignment: Compute? = null

    self.onmessage = { m: MessageEvent ->
        val assignment: Assignment = Json.decodeFromString(m.data as String)
        val svg = when (assignment) {
            is Compute -> {
                val filtration = topoGrow(assignment.points, assignment.gs, assignment.tgs)
                lastFiltration = filtration
                lastComputeAssignment = assignment
                createSvg(
                    assignment.gs, assignment.cds, assignment.ds, filtration,
                    3.0 * assignment.gs.expandRadius
                ) // TODO
            }

            is DrawSvg -> {
                if (lastFiltration == null || lastComputeAssignment == null) {
                    ""
                } else {
                    createSvg(
                        lastComputeAssignment!!.gs, lastComputeAssignment!!.cds, assignment.drawSettings,
                        lastFiltration!!, 3.0 * lastComputeAssignment!!.gs.expandRadius
                    )
                }
            }
        }

        val completedWork = CompletedWork(svg)
        self.postMessage(Json.encodeToString(completedWork))
    }
}