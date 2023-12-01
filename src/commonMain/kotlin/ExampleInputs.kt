import org.openrndr.math.Vector2
import patterns.Point

enum class ExampleInput {
    Mills,
    NYC,
    Hotels,
    SimpleScatterPlot,
    OverlapExample,
    OverlapExample2,
    OverlapExample3,
    HexaSwirl,
    Bonn,
    BadGodesberg,
    Diseasome
}

fun getFileName(e: ExampleInput): String = when(e) {
    ExampleInput.Mills -> "mills"
    ExampleInput.NYC -> "nyc"
    ExampleInput.Hotels -> "hotels"
    ExampleInput.SimpleScatterPlot -> "simple-scatter-plot"
    ExampleInput.OverlapExample -> "overlap-example"
    ExampleInput.OverlapExample2 -> "overlap-example-2"
    ExampleInput.OverlapExample3 -> "overlap-example-3"
    ExampleInput.HexaSwirl -> "hexa-swirl"
    ExampleInput.Bonn -> "Bonn"
    ExampleInput.BadGodesberg -> "Bad-Godesberg"
    ExampleInput.Diseasome -> "diseasome"
}

fun nodesToPoints(nodes: List<Map<String, String>>): List<Point> {
    val colorMap = mutableMapOf<String, Int>(
        "CB light blue" to 0,
        "CB light red" to 1,
        "CB light green" to 2,
        "CB light orange" to 3,
        "CB light purple" to 4,
        "CB yellow" to 5,
        "CB brown" to 6,
//        "CART 1" to 1,
//        "CART 2" to 2,
//        "CART 3" to 3,
//        "CART 4" to 4,
//        "CART 5" to 5,
//        "CART 6" to 6,
//        "CART 7" to 7,
//        "CART 8" to 8,
//        "CART 9" to 9,
//        "CART 10" to 10,
//        "CART 11" to 11,
//        "CART 12" to 12,
    )
    var last = 7
    return nodes.map { m ->
        val posString = m["pos"]!!.split(' ')
        val x = posString[0].toDouble()
        val y = posString[1].toDouble()
        val matrixString = m["matrix"]?.split(' ')
        val a = matrixString?.get(0)?.toDouble() ?: 1.0
        val b = matrixString?.get(1)?.toDouble() ?: 0.0
        val c = matrixString?.get(2)?.toDouble() ?: 0.0
        val d = matrixString?.get(3)?.toDouble() ?: 1.0
        val e = matrixString?.get(4)?.toDouble() ?: 0.0
        val f = matrixString?.get(5)?.toDouble() ?: 0.0
        val pos = Vector2(a * x + c * y + e, b * x + d * y + f)

        val fill = m["fill"]!!
        val type = colorMap[fill] ?: run {
            colorMap[fill] = last
            last++
            last - 1
        }
        Point(pos, type)
    }
}