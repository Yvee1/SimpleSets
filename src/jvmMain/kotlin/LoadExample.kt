import patterns.Point

fun getExampleInput(e: ExampleInput): List<Point> {
    val path = "/example-input/${getFileName(e)}.ipe"
    val ipe = getResourceAsText(path)
    return ipeToPoints(ipe)
}

fun getResourceAsText(path: String): String =
    object {}.javaClass.getResource(path)?.readText() ?: error("Unknown resource: '$path'")
