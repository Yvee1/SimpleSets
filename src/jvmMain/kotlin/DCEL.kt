import geometric.*
import highlights.Highlight
import highlights.toHighlight
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.draw.shadeStyle
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Matrix44
import org.openrndr.shape.ContourIntersection
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.intersections
import patterns.Island
import patterns.Reef

fun main() = application {
    configure {
        width = 800
        height = 800
        windowAlwaysOnTop = true
        windowResizable = true
    }

    oliveProgram {
        val ds = DrawSettings(pSize = 5.0)
        val cds = ComputeDrawingSettings(expandRadius = ds.pSize * 3)

        val points = getExampleInput(ExampleInput.OverlapExample2).map {
            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
        }

        val pts1 = points.filter { it.type == 0 }
        val pattern1 = Island(pts1, pts1.size)
        val island1 = pattern1.toHighlight(cds.expandRadius)

        val pts2 = points.filter { it.type == 1 }
        val pattern2 = Island(pts2, pts2.size)
        val island2 = pattern2.toHighlight(cds.expandRadius)

        val pts3 = points.filter { it.type == 2 }
        val pattern3 = Island(pts3, pts3.size)
        val island3 = pattern3.toHighlight(cds.expandRadius)

        val pts4 = points.filter { it.type == 3 }
        val ch4 = convexHull(pts4)
        val bigGap = (ch4 + ch4.first()).zipWithNext { a, b -> a.pos.squaredDistanceTo(b.pos) }.withIndex().maxBy { it.value }.index
        val bendPts = ch4.subList((bigGap + 1) % ch4.size, ch4.size) + ch4.subList(0, (bigGap + 1) % ch4.size)
        val pattern4 = Reef(bendPts, bendPts.size)
        val island4 = pattern4.toHighlight(cds.expandRadius)

        val highlights = listOf(island1, island2, island3, island4)

        val xGraph = XGraph(highlights)

//        val eStar = xGraph.edges[1]//.next().prev().next().prev()//.next()
        val heStart = xGraph.edges[1].halfEdges!!.first

        val visited = mutableListOf(heStart)
        var current = heStart
        var faceContour = heStart.contour

        while (current.next != heStart) {
            current = current.next
            visited.add(current)
            faceContour += current.contour
        }

        faceContour = faceContour.close()

        extend(Camera2D()) {
            view = Matrix44(
                3.126776096345932, 0.0, 0.0, -18.31962803200547,
                0.0, 3.126776096345932, 0.0, -1623.3146267543102,
                0.0, 0.0, 3.126776096345932, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        }
        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)

                for (f in xGraph.faces) {
//                    for (origin in f.origins) {
                    if (f.origins.size == 1) {
                        fill = ds.colorSettings.lightColors[f.origins.first().type].toColorRGBa().opacify(0.5)
                        stroke = null
                        contour(f.contour!!)
                    }
                }

                xGraph.hEdgesMap.forEachIndexed { i, es ->
                    stroke = ds.colorSettings.darkColors[i].toColorRGBa()

                    for (e in es) {
                        isolated {
//                            if (e == heStar.original)
//                                strokeWeight *= 2
                            shadeStyle = shadeStyle {
                                fragmentTransform = """
                                x_stroke.rgb *= vec3(c_contourPosition / p_length);
                            """.trimIndent()
                                parameter("length", e.contour.length)
                            }
                            contour(e.contour)
                        }
                    }
                }
//                shadeStyle = null
//                contour(eStar.contour)
                stroke = ColorRGBa.BLACK
                xGraph.vertices.forEach { v ->
                    fill = ColorRGBa.WHITE
                    strokeWeight = 0.5
                    circle(v.x.position, 1.5)
                }

                coloredPoints(island1.points, ds)
                coloredPoints(island2.points, ds)
                coloredPoints(island3.points, ds)
                coloredPoints(island4.points, ds)
            }
        }
    }
}

data class XEdge(val h: Highlight, val source: XVertex, val target: XVertex, val contour: ShapeContour) {
    val start get() = contour.start
    val end get() = contour.end

    var halfEdges: Pair<XHalfEdge, XHalfEdge>? = null

    fun split(): Pair<XHalfEdge, XHalfEdge> {
        val hes = XHalfEdge(source, target, contour, this) to XHalfEdge(target, source, contour.reversed, this)
        hes.first.twin = hes.second
        hes.second.twin = hes.first
        halfEdges = hes
        return hes
    }

    fun splitAndAdd(): Pair<XHalfEdge, XHalfEdge> {
        split()
        source.edges.add(halfEdges!!.first)
        target.edges.add(halfEdges!!.second)
        return halfEdges!!
    }

//    fun next(): XEdge =
//        target.edges.first {
//            it != this && it.end.squaredDistanceTo(end) < 1E-6
//        }
//
//    fun prev(): XEdge =
//        source.edges.first {
//            it != this && it.start.squaredDistanceTo(start) < 1E-6
//        }
}

data class XVertex(val x: ContourIntersection, val h1: Highlight, val h2: Highlight, val edges: MutableList<XHalfEdge> = mutableListOf())

data class XHalfEdge(val source: XVertex, val target: XVertex, val contour: ShapeContour, val original: XEdge) {
    val start get() = contour.start
    val end get() = contour.end

    lateinit var twin: XHalfEdge

    lateinit var face: XFace

    val next by lazy {
        target.edges.first {
            val y = target.x.position
            val x = y + it.contour.direction(0.0)
            val z = y - contour.direction(1.0)
            orientation(x, y, z) == Orientation.RIGHT
        }
    }
}

data class XFace(val edge: XHalfEdge, val origins: Set<Highlight>, val contour: ShapeContour?) {

}

data class XGraph(val hs: List<Highlight>) {
    val hVertsMap = List(hs.size) {
        mutableListOf<Pair<XVertex, Double>>()
    }

    val vertices = buildList {
        for (i in hs.indices) {
            val h1 = hs[i]
            for (j in i + 1 until hs.size) {
                val h2 = hs[j]
                val inters = h1.contour.intersections(h2.contour)
                for (inter in inters) {
                    val v = XVertex(inter, h1, h2)
                    add(v)
                    hVertsMap[i].add(v to inter.a.contourT)
                    hVertsMap[j].add(v to inter.b.contourT)
                }
            }
        }
    }

    val hEdgesMap = List(hs.size) {
        mutableListOf<XEdge>()
    }

    val edges by lazy { hEdgesMap.flatten() }

    val halfEdges by lazy {
        edges.flatMap {
            it.halfEdges?.toList() ?: emptyList()
        }
    }

    val faces = mutableListOf<XFace>()

    init {
        createEdges()
        createFaces()
    }

    private fun createEdges() {
        for (i in hs.indices) {
            val h = hs[i]
            val tValues = hVertsMap[i].sortedBy { it.second }
            val middleEdges = tValues.zipWithNext { (v1, t1), (v2, t2) ->
                val e = XEdge(h, v1, v2, h.contour.sub(t1, t2))
                e.splitAndAdd()
                e
            }
            val (lastV, lastT) = tValues.last()
            val (firstV, firstT) = tValues.first()
            val lastEdge = XEdge(h, lastV, firstV, h.contour.sub(lastT, 1.0) + h.contour.sub(0.0, firstT))
            lastEdge.splitAndAdd()
            hEdgesMap[i].addAll(middleEdges)
            hEdgesMap[i].add(lastEdge)
        }
    }

    private fun createFaces() {
        val remainingHalfEdges = halfEdges.toMutableList()

        while(remainingHalfEdges.isNotEmpty()) {
            val heStart = remainingHalfEdges.first()
            val visited = mutableListOf(heStart)
            var current = heStart
            var faceContour = heStart.contour
            val origins = mutableSetOf<Highlight>()
            if (current.source == current.original.source) {
                origins.add(current.original.h)
            }

            while (current.next != heStart) {
                current = current.next
                visited.add(current)
                faceContour += current.contour
                if (current.source == current.original.source) {
                    origins.add(current.original.h)
                }
            }

            remainingHalfEdges.removeAll(visited)

            val finalFaceContour = if (origins.isNotEmpty())
                faceContour.close() else null

            val f = XFace(heStart, origins, finalFaceContour)
            faces.add(f)

            visited.forEach { e ->
                e.face = f
            }
        }
    }
}