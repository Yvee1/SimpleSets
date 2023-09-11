import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.MessageEvent

external val self: DedicatedWorkerGlobalScope

fun main() {
    var lastSolution: Solution? = null
    var lastComputeAssignment: Compute? = null

    self.onmessage = { m: MessageEvent ->
        val assignment: Assignment = Json.decodeFromString(m.data as String)
        val svg = when (assignment) {
            is Compute -> {
                val solution = Solution.compute(assignment.points, assignment.computePartitionSettings,
                    assignment.computeDrawingSettings, assignment.computeBridgesSettings)
                lastSolution = solution
                lastComputeAssignment = assignment
                createSvg(assignment.points, assignment.computePartitionSettings, assignment.drawSettings, solution)
            }

            is DrawSvg -> {
                if (lastSolution == null || lastComputeAssignment == null) {
                    ""
                } else {
                    createSvg(lastComputeAssignment!!.points, lastComputeAssignment!!.computePartitionSettings, assignment.drawSettings, lastSolution!!)
                }
            }
        }

        val completedWork = CompletedWork(svg)
        self.postMessage(Json.encodeToString(completedWork))
    }
}