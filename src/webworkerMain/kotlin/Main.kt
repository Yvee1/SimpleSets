import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.MessageEvent

external val self: DedicatedWorkerGlobalScope

fun main() {
    self.onmessage = { m: MessageEvent ->
        val assignment:Assignment = Json.decodeFromString(m.data as String)
        val svg = compute(assignment.settings, assignment.points)
        val completedWork = CompletedWork(svg)

        self.postMessage(Json.encodeToString(completedWork))
    }
}