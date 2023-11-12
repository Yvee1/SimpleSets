import js.core.asList
import patterns.Point
import web.dom.NamedNodeMap
import web.dom.parsing.DOMParser
import web.dom.parsing.DOMParserSupportedType

fun getExampleInput(e: ExampleInput): List<Point> {
    val ipe = when(e) {
        ExampleInput.NYC -> require("example-input/nyc.ipe")
        ExampleInput.Amsterdam -> require("example-input/amsterdam.ipe")
        ExampleInput.KelpFusion -> require("example-input/kelp-fusion.ipe")
        ExampleInput.SmallExample -> require("example-input/small-example.ipe")
        ExampleInput.FiveColors -> require("example-input/5-colors.ipe")
        ExampleInput.OverlapExample -> require("example-input/overlap-example.ipe")
        ExampleInput.OverlapExample2 -> require("example-input/overlap-example-2.ipe")
        ExampleInput.HexaSwirl -> require("example-input/hexa-swirl.ipe")
        ExampleInput.Bonn -> require("example-input/bonn.ipe")
        ExampleInput.BadGodesberg -> require("example-input/BadGodesberg.ipe")
    }
    return ipeToPoints(ipe)
}

fun ipeToPoints(ipe: String): List<Point> {
    val parser = DOMParser()
    val doc = parser.parseFromString(ipe, DOMParserSupportedType.applicationXml)
    val parseError = doc.querySelector("parsererror")
    if (parseError != null) {
        error(parseError)
    }
    val nodes = doc.getElementsByTagName("use").asList().map { it.attributes.asMap() }

    return nodesToPoints(nodes)
}

internal fun NamedNodeMap.asMap(): Map<String, String> = buildMap(length) {
    for (i in 0 until length){
        put(item(i)!!.name, item(i)!!.value)
    }
}