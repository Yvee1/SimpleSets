import kotlinx.serialization.Serializable
import patterns.Point

//This source is shared between client and worker

@Serializable
sealed class Assignment

@Serializable
data class Compute(val computeSettings: ComputeSettings, val points: List<Point>, val drawSettings: DrawSettings) :
    Assignment()

@Serializable
data class DrawSvg(val drawSettings: DrawSettings) : Assignment()

@Serializable
data class CompletedWork (
    val svg: String
)