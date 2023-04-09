import kotlinx.serialization.Serializable
import patterns.Point

//This source is shared between client and worker

@Serializable
data class Assignment (
    val settings: Settings,
    val points: List<Point>
)

@Serializable
data class CompletedWork (
    val svg: String
)