// Adapted from https://github.com/openrndr/openrndr/blob/master/openrndr-svg/src/jvmMain/kotlin/org/openrndr/svg/SVGWriter.kt
import org.openrndr.shape.*

fun Composition.toSVG() = writeSVG(this)

private val CompositionNode.svgId: String
    get() = when (val tempId = id) {
        "" -> ""
        null -> ""
        else -> "id=\"$tempId\""
    }

private val CompositionNode.svgAttributes: String
    get() {
        return attributes.map {
            if (it.value != null && it.value != "") {
                "${it.key}=\"${it.value}\""
            } else {
                it.key
            }
        }.joinToString(" ")
    }

private fun Styleable.serialize(parentStyleable: Styleable? = null): String {
    val sb = StringBuilder()

    val filtered = this.properties.filter {
        it.key != AttributeOrPropertyKey.SHADESTYLE
    }
    // Inheritance can't be checked without a parentStyleable
    when (parentStyleable) {
        null -> filtered.forEach { (t, u) ->
            if (u.toString().isNotEmpty()) {
                sb.append("$t=\"${u.toString()}\" ")
            }
        }
        else -> filtered.forEach { (t, u) ->
            if (u.toString().isNotEmpty() && !this.isInherited(parentStyleable, t)) {
                sb.append("$t=\"${u.toString()}\" ")
            }
        }

    }

    return sb.trim().toString()
}


fun writeSVG(
    composition: Composition,
    topLevelId: String = "openrndr-svg"
): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

    val defaultNamespaces = mapOf(
        "xmlns" to "http://www.w3.org/2000/svg",
        "xmlns:xlink" to "http://www.w3.org/1999/xlink"
    )

    val namespaces = (defaultNamespaces + composition.namespaces).map { (k, v) ->
        "$k=\"$v\""
    }.joinToString(" ")

//    val styleSer = composition.style.serialize()
    val styleSer = "style=width:100%;height:100%"
    val docStyleSer = composition.documentStyle.serialize()

    sb.append("<svg version=\"1.2\" baseProfile=\"tiny\" id=\"$topLevelId\" $namespaces $styleSer $docStyleSer>")

    var textPathID = 0
    process(composition.root) { stage ->
        if (stage == VisitStage.PRE) {

            val styleSerialized = this.style.serialize(this.parent?.style)

            when (this) {
                is GroupNode -> {
                    val attributes = listOf(svgId, styleSerialized, svgAttributes)
                        .filter(String::isNotEmpty)
                        .joinToString(" ")
                    sb.append("<g${" $attributes"}>\n")
                }
                is ShapeNode -> {
                    val pathAttribute = "d=\"${shape.svg}\""

                    val attributes = listOf(
                        svgId,
                        styleSerialized,
                        svgAttributes,
                        pathAttribute
                    )
                        .filter(String::isNotEmpty)
                        .joinToString(" ")

                    sb.append("<path $attributes/>\n")
                }

                else -> error("Not supported")
            }
        } else {
            if (this is GroupNode) {
                sb.append("</g>\n")
            }
        }
    }
    sb.append("</svg>")
    return sb.toString()
}

val Shape.svg: String
    get() {
        val sb = StringBuilder()
        contours.forEach {
            it.segments.forEachIndexed { index, segment ->
                if (index == 0) {
                    sb.append("M ${segment.start.x} ${segment.start.y}")
                }
                sb.append(
                    when (segment.control.size) {
                        1 -> "Q${segment.control[0].x} ${segment.control[0].y} ${segment.end.x} ${segment.end.y}"
                        2 -> "C${segment.control[0].x} ${segment.control[0].y} ${segment.control[1].x} ${segment.control[1].y} ${segment.end.x} ${segment.end.y}"
                        else -> "L${segment.end.x} ${segment.end.y}"
                    }
                )
            }
            if (it.closed) {
                sb.append("z")
            }
        }
        return sb.toString()
    }

val ShapeContour.svg: String
    get() {
        val sb = StringBuilder()
        segments.forEachIndexed { index, segment ->
            if (index == 0) {
                sb.append("M ${segment.start.x} ${segment.start.y}")
            }
            sb.append(
                when (segment.control.size) {
                    1 -> "C${segment.control[0].x}, ${segment.control[0].y} ${segment.end.x} ${segment.end.y}"
                    2 -> "C${segment.control[0].x}, ${segment.control[0].y} ${segment.control[1].x} ${segment.control[1].y} ${segment.end.x} ${segment.end.y}"
                    else -> "L${segment.end.x} ${segment.end.y}"
                }
            )
        }
        if (closed) {
            sb.append("z")
        }
        return sb.toString()
    }

private enum class VisitStage {
    PRE,
    POST
}

private fun process(compositionNode: CompositionNode, visitor: CompositionNode.(stage: VisitStage) -> Unit) {
    compositionNode.visitor(VisitStage.PRE)
    if (compositionNode is GroupNode) {
        compositionNode.children.forEach { process(it, visitor) }
    }
    compositionNode.visitor(VisitStage.POST)
}