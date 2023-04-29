import org.openrndr.math.Vector2
import patterns.Point

enum class ExampleInput {
    NYC, Amsterdam, KelpFusion, SmallExample, FiveColors
}

fun getFileName(e: ExampleInput): String = when(e) {
    ExampleInput.NYC -> "nyc"
    ExampleInput.Amsterdam -> "amsterdam"
    ExampleInput.KelpFusion -> "kelp-fusion"
    ExampleInput.SmallExample -> "small-example"
    ExampleInput.FiveColors -> "5-colors"
}

fun nodesToPoints(nodes: List<Map<String, String>>): List<Point> =
    nodes.map { m ->
        val posString = m["pos"]!!.split(' ')
        val matrixString = m["matrix"]?.split(' ')
        val pos = Vector2(posString[0].toDouble() + (matrixString?.get(4)?.toDouble() ?: 0.0),
            posString[1].toDouble() + (matrixString?.get(5)?.toDouble() ?: 0.0))

        val f = m["fill"]!!
        val type = when(f) {
            "CB light blue" -> 0
            "CB light red" -> 1
            "CB light green" -> 2
            "CB light orange" -> 3
            "CB light purple" -> 4
            else -> {
                println("Unknown color: $f")
                5
            }
        }
        Point(pos, type)
    }