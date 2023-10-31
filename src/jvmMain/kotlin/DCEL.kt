import geometric.*
import highlights.Highlight
import highlights.toHighlight
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Matrix44
import org.openrndr.shape.*
import org.openrndr.svg.toSVG
import patterns.Bank
import patterns.Island
import patterns.SinglePoint
import java.io.File
import kotlin.math.max
import kotlin.math.min

fun main() = application {
    configure {
        width = 800
        height = 800
        windowAlwaysOnTop = true
        windowResizable = true
    }

    program {
        val ds = DrawSettings(pSize = 5.0, whiten = 0.5)
        val cds = ComputeDrawingSettings(expandRadius = ds.pSize * 3, pointClearance = 0.7)

//        val points = getExampleInput(ExampleInput.OverlapExample2).map {
//            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
//        }
//
//        val pts1 = points.filter { it.type == 0 }
//        val pattern1 = Island(pts1, pts1.size)
//        val island1 = pattern1.toHighlight(cds.expandRadius)
//
//        val pts2 = points.filter { it.type == 1 }
//        val pattern2 = Island(pts2, pts2.size)
//        val island2 = pattern2.toHighlight(cds.expandRadius)
//
//        val pts3 = points.filter { it.type == 2 }
//        val pattern3 = Island(pts3, pts3.size)
//        val island3 = pattern3.toHighlight(cds.expandRadius)
//
//        val pts4 = points.filter { it.type == 3 }
//        val ch4 = convexHull(pts4)
//        val bigGap = (ch4 + ch4.first()).zipWithNext { a, b -> a.pos.squaredDistanceTo(b.pos) }.withIndex().maxBy { it.value }.index
//        val bendPts = ch4.subList((bigGap + 1) % ch4.size, ch4.size) + ch4.subList(0, (bigGap + 1) % ch4.size)
//        val pattern4 = Bank(bendPts, bendPts.size)
//        val island4 = pattern4.toHighlight(cds.expandRadius)

        val file = File("input-output/4pts.ipe")

        val points = ipeToPoints(file.readText()).map {
            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
        }

        val pts1 = points.filter { it.type == 0 }
        val pattern1 = SinglePoint(pts1[0])
        val island1 = pattern1.toHighlight(cds.expandRadius)

        val pts2 = points.filter { it.type == 1 }
        val pattern2 = SinglePoint(pts2[0])
        val island2 = pattern2.toHighlight(cds.expandRadius)

        val pts3 = points.filter { it.type == 2 }
        val pattern3 = SinglePoint(pts3[0])
        val island3 = pattern3.toHighlight(cds.expandRadius)

        val pts4 = points.filter { it.type == 3 }
        val pattern4 = SinglePoint(pts4[0])
        val island4 = pattern4.toHighlight(cds.expandRadius)

        val pts =
            pts1 +
            pts2 +
            pts3 +
//            pts4 +
            emptyList()

        val highlights = listOf(
            island1,
            island2,
            island3,
//            island4
        )

        val xGraph = XGraph(highlights, cds)

        extend(Camera2D()) {
            view = Matrix44(
                3.126776096345932, 0.0, 0.0, -18.31962803200547,
                0.0, 3.126776096345932, 0.0, -1623.3146267543102,
                0.0, 0.0, 3.126776096345932, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        }

        val comp = drawComposition {
            xGraph.draw(this, ds)

            coloredPoints(pts, ds)

            stroke = null
            fill = ColorRGBa.WHITE.opacify(0.9)
            rectangle(drawer.bounds)

            val i = 2
            val c = xGraph.intersectionComponents(i)[0]

            val avoidees = mutableSetOf<Int>()
            for (f in c.faces) {
                avoidees.addAll(f.ordering.takeWhile { it != i })
            }

            val cont = c.boundaryPart(i)
            val (inclCircles, exclCircles) = xGraph.relevantCircles(i, avoidees.toList(), c)

            val morphed = morphCurve(cont, inclCircles, exclCircles, cds, this)

            stroke = ColorRGBa.BLUE
            fill = null
            contour(morphed)
        }

        val svg = comp.toSVG()
        File("input-output/DCEL.svg").writeText(svg)
        "py svgtoipe.py input-output/DCEL.svg".runCommand(File("."))

        extend {


            drawer.apply {
                clear(ColorRGBa.WHITE)
                composition(comp)

//                var arcs = mutableListOf<Shape>()
//                val c = highlights[3].contour.sub(0.3, 0.5)
//
//                for (a in highlights[3].arcs) {
//                    arcs.add(intersection(c.shape, a.contour.shape))
//                }
//
//                stroke = ColorRGBa.BLUE
//                contour(c)
//                stroke = ColorRGBa.RED
//                shapes(arcs)
            }
        }
    }
}

data class XEdge(val hIndex: Int, val source: XVertex, val target: XVertex, val contour: ShapeContour) {
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
        source.outgoing.add(halfEdges!!.first)
        target.outgoing.add(halfEdges!!.second)
        source.incoming.add(halfEdges!!.second)
        target.incoming.add(halfEdges!!.first)
        return halfEdges!!
    }
}

data class XVertex(val x: ContourIntersection, val h1: Highlight, val h2: Highlight) {
    val outgoing = mutableListOf<XHalfEdge>()
    val incoming = mutableListOf<XHalfEdge>()
}

data class XHalfEdge(val source: XVertex, val target: XVertex, val contour: ShapeContour, val original: XEdge) {
    val start get() = contour.start
    val end get() = contour.end

    val contourE by lazy {
        contour.extend(0.001)
    }

    lateinit var twin: XHalfEdge

    lateinit var face: XFace

//    var morphedContours = mutableMapOf<Int, ShapeContour>()

    val next by lazy {
        target.outgoing.first {
            val y = target.x.position
            val z = y + it.contour.direction(0.0)
            val x = y - contour.direction(1.0)
            orientation(x, y, z) == Orientation.LEFT
        }
    }

    val prev by lazy {
        source.incoming.first {
            val y = source.x.position
            val x = y - it.contour.direction(1.0)
            val z = y + contour.direction(0.0)
            orientation(x, y, z) == Orientation.LEFT // TODO: Check
        }
    }
}

enum class Ordering {
    LT,
    EQ,
    GT
}

data class Relation(val left: Int, val right: Int, val preference: Ordering, var order: Ordering = preference) {
    val top get() = if (order != Ordering.LT) left else right
    val bottom get() = if (order != Ordering.LT) right else left

    val hyperedges = mutableListOf<Hyperedge>()
}

data class XFace(val edge: XHalfEdge, val origins: List<Int>, val contour: ShapeContour?) {
    val relations = mutableListOf<Relation>()

    val morphedContours = mutableMapOf<Int, ShapeContour>()
    val morphedEdge = mutableMapOf<Int, Shape>()
    var wasMorphed = mutableMapOf<Int, Boolean>()

    data class Vertex(val i: Int) {
        val neighbors = mutableListOf<Vertex>()
        var mark = 0
    }

    val vertices = origins.associateWith { Vertex(it) }

    val edges: List<XHalfEdge> by lazy {
        buildList {
            var current = edge
            do {
                add(current)
                current = current.next
            } while (current != edge)
        }
    }

    // Access only when ordering is final!
    val ordering: List<Int> by lazy {
        for (p in relations) {
            if (p.order == Ordering.EQ) continue
            val u = vertices[p.left]!!
            val v = vertices[p.right]!!

            if (p.order == Ordering.LT) {
                v.neighbors.add(u)
            } else {
                u.neighbors.add(v)
            }
        }

        val ordering = mutableListOf<Int>()

        fun visit(u: Vertex): Boolean {
            if (u.mark == 2) return true
            if (u.mark == 1) return false

            u.mark = 1

            for (v in u.neighbors) {
                visit(v)
            }

            u.mark = 2
            ordering.add(u.i)
            return true
        }

        for (v in vertices.values) {
            val success = visit(v)
            if (!success) error("No total order in face")
        }

        ordering
    }

    val top get() =
        ordering.last()

    fun setMorphedEdge(i: Int, morphed: ShapeContour, full: ShapeContour, drawer: CompositionDrawer? = null) {
//        val e = run {
//            var current = edge
//
//            do {
//                if (current.original.hIndex == i) return@run current
//                current = current.next
//            } while (current != edge)
//            return
//        }
//
//        val intersections = buildList {
//            var current = edge
//
//            do {
//                val inters = current.contourE.intersections(morphed)
//                addAll(inters)
//                current = current.next
//            } while (current != edge)
//        }

//        e.morphedContour = intersection(morphed, contour!!).contours.firstOrNull() ?: ShapeContour.EMPTY
//
//        if (intersections.size < 2) {
//            println("Do not know how to cut morphed contour")
//            return
//        }

//        val a = intersections.minBy { it.b.contourT }
//        val b = intersections.maxBy { it.b.contourT }

//        drawer?.apply {
//            stroke = ColorRGBa.BLACK
//            fill = ColorRGBa.WHITE
//            strokeWeight = 0.4
//            circle(a.position, 1.0)
//            circle(b.position, 1.0)
//        }

//        e.morphedContours[i] = morphed.sub(a.b.contourT, b.b.contourT)
        wasMorphed[i] = true
        morphedEdge[i] = intersection(morphed, contour!!.buffer(0.1))

//        run {
//            var current = edge
//
//            do {
//                if (current == e) {
//                    current = current.next
//                    continue
//                }
//
//                val eBefore = current.start.squaredDistanceTo(e.end) < current.end.squaredDistanceTo(e.start)
//
//                // TODO: Do not sub the contourE but transform the ts and sub the contour property
//                if (current.contourE == a.a.contour && current.contourE == b.a.contour) {
//                    val t0 = min(a.a.contourT, b.a.contourT)
//                    val t1 = max(a.a.contourT, b.a.contourT)
//                    current.morphedContours[i] = current.contourE.sub(t0, t1)
//                } else if (current.contourE == a.a.contour) {
//                    val t0 = if (eBefore) a.a.contourT else 0.0
//                    val t1 = if (!eBefore) a.a.contourT else 1.0
//                    println("(t0, t1): ($t0, $t1)")
//                    current.morphedContours[i] = current.contourE.sub(t0, t1)
//                } else if (current.contourE == b.a.contour) {
//                    val t0 = if (eBefore) b.a.contourT else 0.0
//                    val t1 = if (!eBefore) b.a.contourT else 1.0
//                    current.morphedContours[i] = current.contourE.sub(t0, t1)
//                }
//
//                current = current.next
//            } while (current != edge)
//        }

//        e.morphedContour = morphed

//        val morphedFaceContour = run {
//            var current = edge
//            var c = current.morphedContours[i] ?: current.contour
//
//            while (current.next != edge) {
//                current = current.next
//                c += current.morphedContours[i] ?: current.contour
//            }
//
//            c.close()
//        }

        val morphedFaceContour = intersection(full, contour).contours.firstOrNull() ?: ShapeContour.EMPTY

        morphedContours[i] = morphedFaceContour
    }

    val seamDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        val toDraw = buildList {
            for (i in ordering.asReversed()) {
                add( 0, i)
                if (morphedContours[last()] == null) break
            }
        }

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
                for (i in toDraw) {
                    val color = ds.colorSettings.lightColors[hs[i].type].toColorRGBa().mix(ColorRGBa.WHITE, ds.whiten)
                    stroke = color
                    strokeWeight = ds.contourStrokeWeight / 2
                    fill = null
                    contour(morphedContours[i] ?: contour)
                }
            }
        }

        ::draw
    }

    val fillDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        val toDraw = buildList {
            for (i in ordering.asReversed()) {
                add( 0, i)
                if (morphedContours[last()] == null) break
            }
        }

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
                for (i in toDraw) {
                    val color = ds.colorSettings.lightColors[hs[i].type].toColorRGBa().mix(ColorRGBa.WHITE, ds.whiten)
                    stroke = null
                    fill = color
                    contour(morphedContours[i] ?: contour)
                }
            }
        }

        ::draw
    }

//    val shadowDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
//        if (contour == null) return@lazy null
//
//        val toDraw = buildList {
//            for (i in ordering.asReversed()) {
//                add( 0, i)
//                if (morphedContours[last()] == null) break
//            }
//        }
//
//        val cs = toDraw.map { i ->
//            morphedContours[i] ?: contour
//        }
//
//        val shadows = cs.map { c ->
////            difference(c.buffer(1.0), c).contours[0]
//            difference(c.buffer(2.0).reversed, c.reversed)
//        }
//
//        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
//            return drawComposition {
//                for (shadow in shadows) {
////                    val color = ds.colorSettings.lightColors[hs[i].type].toColorRGBa().mix(ColorRGBa.WHITE, ds.whiten)
////                    stroke = null
//                    stroke = null
//                    fill = ColorRGBa.BLACK.whiten(0.9)
//                    shape(shadow)
//                }
//            }
//        }
//
//        ::draw
//    }

    val shadowDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        val toDraw = buildList {
            for (i in ordering.asReversed()) {
                add( 0, i)
                if (morphedContours[first()] == null) break
            }
        }

        val edgeContours = buildList {
            for ((iIndex, i) in toDraw.withIndex()) {
                val conts = mutableListOf<ShapeContour>()
                conts.addAll(morphedEdge[i]?.contours ?: emptyList())

                if (!wasMorphed.getOrDefault(i, false)) {
                    var current = edge

                    var iters = 0
                    do {
                        iters++
                        if (current.original.hIndex == i) {
                            conts.add(current.contour)

                        }
                        current = current.next
                    } while (current != edge && iters < 1000)
                    if (iters >= 1000) {
                        error("Problem")
                    }
                }

                for (cont in conts) {
                    var modified = Shape(listOf(cont))
                    for (jIndex in iIndex + 1 until toDraw.size) {
                        if (!morphedContours[toDraw[jIndex]]!!.empty)
                            modified = difference(modified, morphedContours[toDraw[jIndex]]!!.buffer(0.1))
                    }
                    addAll(modified.contours)
                }
            }
        }

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
//                stroke = ColorRGBa.BLACK
//                strokeWeight = ds.contourStrokeWeight
//                fill = null
//                    shape(shadow)
                for (ec in edgeContours) {
                    fill = ColorRGBa.BLACK.opacify(0.05)
                    stroke = null
//                    shape(difference(ec.buffer(3.0), contour))
                    shape(difference(ec.buffer(2.0).clockwise, contour.clockwise))
                }
            }
        }

        ::draw
    }

    val strokeDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        val toDraw = buildList {
            for (i in ordering.asReversed()) {
                add( 0, i)
                if (morphedContours[first()] == null) break
            }
        }

        val edgeContours = buildList {
            for ((iIndex, i) in toDraw.withIndex()) {
                val conts = mutableListOf<ShapeContour>()
                conts.addAll(morphedEdge[i]?.contours ?: emptyList())

                if (!wasMorphed.getOrDefault(i, false)) {
                    var current = edge
                    var iters = 0
                    do {
                        iters++
                        if (current.original.hIndex == i) {
                            conts.add(current.contour)

                        }
                        current = current.next
                    } while (current != edge && iters < 1000)
                    if (iters >= 1000) {
                        error("Problem")
                    }
                }

                for (cont in conts) {
                    var modified = Shape(listOf(cont))
                    for (jIndex in iIndex + 1 until toDraw.size) {
                        if (!morphedContours[toDraw[jIndex]]!!.empty)
                            modified = difference(modified, morphedContours[toDraw[jIndex]]!!.buffer(0.1))
                    }
                    addAll(modified.contours)
                }
            }
        }

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
                stroke = ColorRGBa.BLACK
                strokeWeight = ds.contourStrokeWeight
                fill = null
                for (ec in edgeContours) {
                    contour(ec)
                }
            }
        }

        ::draw
    }
}

data class XGraph(val hs: List<Highlight>, val cds: ComputeDrawingSettings) {
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

    val hFacesMap = List(hs.size) {
        mutableListOf<XFace>()
    }

    val pairComponents = mutableMapOf<Pair<Int, Int>, List<Component>>()

    init {
        createEdges()
        createFaces()

        for (i in hs.indices) {
            for (j in i + 1 until hs.size) {
                val cs = intersectionComponents(i, j)
                pairComponents[i to j] = cs
                for (c in cs) {
                    val rel = computePreference(i, j, c)
                    for (f in c.faces) {
                        f.relations.add(rel)
                    }
                }
            }
        }

        val hEdges = hyperedges()
        for (e in hEdges) {
            // TODO: resolve conflicting preferences
            e.setOrdering()
        }

        for (i in hs.indices) {
            val cs = intersectionComponents(i)
            for (c in cs) {
                val avoidees = mutableSetOf<Int>()
                for (f in c.faces) {
                    avoidees.addAll(f.ordering.takeWhile { it != i })
                }
                if (avoidees.isEmpty()) continue

                val cont = c.boundaryPart(i)
                val (inclCircles, exclCircles) = relevantCircles(i, avoidees.toList(), c)

                if (exclCircles.isEmpty()) continue

                val morphed = morphCurve(cont, inclCircles, exclCircles, cds)

                val restComp = Component(hFacesMap[i] - c.faces)
                val restCont = if (restComp.faces.isNotEmpty()) restComp.boundaryPart(i) else ShapeContour.EMPTY
                val full = (morphed + restCont).close()

                for (f in c.faces) {
                    f.setMorphedEdge(i, morphed, full)
                }
            }
        }
    }

    private fun createEdges() {
        for (i in hs.indices) {
            val h = hs[i]
            if (hVertsMap[i].isEmpty()) continue
            val tValues = hVertsMap[i].sortedBy { it.second }
            val middleEdges = tValues.zipWithNext { (v1, t1), (v2, t2) ->
                val e = XEdge(i, v1, v2, h.contour.sub(t1, t2))
                e.splitAndAdd()
                e
            }
            val (lastV, lastT) = tValues.last()
            val (firstV, firstT) = tValues.first()
            val lastEdge = XEdge(i, lastV, firstV, h.contour.sub(lastT, 1.0) + h.contour.sub(0.0, firstT))
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

            var iters = 0
            while (current.next != heStart && iters < 1000) {
                iters++
                current = current.next
                visited.add(current)
                faceContour += current.contour
            }
            if (iters >= 1000) {
                error("Problem with computing faces (a face seems to exist of more than 1000 half edges)")
            }

            remainingHalfEdges.removeAll(visited)

            val facePt = heStart.contour.position(0.5) + heStart.contour.normal(0.5) * -0.01
            val origins = hs.withIndex().filter { facePt in it.value.contour }.map { it.index }
            val finalFaceContour = if (origins.isNotEmpty())
                faceContour.close() else null

            val f = XFace(heStart, origins, finalFaceContour)
            faces.add(f)
            for (i in origins) {
                hFacesMap[i].add(f)
            }

            visited.forEach { e ->
                e.face = f
            }
        }
    }

    fun intersectionComponents(i: Int): List<Component> {
        val intersectedFaces = hFacesMap[i].filter { it.origins.size > 1 }.toMutableList()
        return ccFaces(intersectedFaces)
    }

    private fun ccFaces(subset: List<XFace>): List<Component> {
        val remainder = subset.toMutableList()
        val components = mutableListOf<Component>()

        var iters = 0
        while (remainder.isNotEmpty() && iters < 1000000 ) {
            iters++
            val component = mutableListOf<XFace>()
            val first = remainder.first()
            val q = mutableListOf(first)

            while (q.isNotEmpty() && iters < 1000000) {
                iters++
                val f = q.removeFirst()
                component.add(f)
                val startEdge = f.edge
                var currentEdge = startEdge

                do {
                    iters++
                    val candidate = currentEdge.twin.face
                    if (candidate !in component && candidate in remainder && candidate !in q) {
                        q.add(candidate)
                    }
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge && iters < 1000000)
            }

            components.add(Component(component))
            remainder.removeAll(component)
        }
        if (iters >= 1000000) error("Problem")

        return components.toList()
    }

    fun intersectionComponents(i: Int, j: Int): List<Component> {
        val commonFaces = faces.filter { i in it.origins && j in it.origins }
        return ccFaces(commonFaces)
    }

    fun relevantCircles(i: Int, j: Int, c: Component): Pair<List<Circle>, List<Circle>> {
        val r = cds.expandRadius
        val leftCircles = hs[i].allPoints.map { Circle(it.pos, r) }
        val rightCircles = hs[j].allPoints.map { Circle(it.pos, r) }

        val (includedCircles, excludedCircles) = growCircles(leftCircles.map { it.center }, rightCircles.map { it.center },
            r, r * cds.pointClearance)

        return includedCircles.filterNot { it.radius == 0.0 || intersection(c.contour, it.shape).empty } to
                excludedCircles.filterNot { it.radius == 0.0 || intersection(c.contour, it.shape).empty }
    }

    fun relevantCircles(i: Int, avoidees: List<Int>, c: Component): Pair<List<Circle>, List<Circle>> {
        val r = cds.expandRadius
        val leftCircles = hs[i].allPoints.map { Circle(it.pos, r) }
        val rightCircles = avoidees.flatMap { j -> hs[j].allPoints.map { Circle(it.pos, r) } }

        val (includedCircles, excludedCircles) = growCircles(leftCircles.map { it.center }, rightCircles.map { it.center },
            r, r * cds.pointClearance)

        return includedCircles to //.filterNot { it.radius == 0.0 || intersection(c.contour, it.shape).empty } to
                excludedCircles.filterNot { it.radius == 0.0 || intersection(c.contour, it.shape).empty }
    }

    fun computePreference(i: Int, j: Int, c: Component): Relation {
        var ord = Ordering.EQ

        val (lics, rjcs) = relevantCircles(i, j, c)
        val (ljcs, rics) = relevantCircles(j, i, c)

        // 3. Check if circular or straight part is covered. Prefer straight part being covered
        val es = c.faces.flatMap {
            it.edges
        }

        // TODO: check; use c.boundaryPart(..) here?
        val iStraight = es.filter {
            it.original.hIndex == i
        }.all { it.contour.isStraight() }

        val jStraight = es.filter {
            it.original.hIndex == j
        }.all { it.contour.isStraight() }

        // TODO: Check
        if (iStraight && !jStraight) {
            ord = Ordering.LT
        }

        if (jStraight && !iStraight) {
            ord = Ordering.GT
        }

        val ci = c.boundaryPart(i)
        val (_, brokenI) = breakCurve(ci, lics, rjcs, cds)
        // TODO: check; should this not be cj?
        val cj = c.boundaryPart(j)
        val (_, brokenJ) = breakCurve(cj, ljcs, rics, cds)

        // 2. Check if circular or straight part is indented. Highly prefer straight part being indented.
        if (brokenI.any { !it.isStraight() } && brokenJ.all { it.isStraight() }) {
            ord = Ordering.LT
        }

        if (brokenJ.any { !it.isStraight() } && brokenI.all { it.isStraight() }) {
            ord = Ordering.GT
        }

        // 1.
        if (rics.size < rjcs.size) {
            ord = Ordering.LT // Prefer i on top
        }

        if (rjcs.size < rics.size) {
            ord = Ordering.GT
        }

        return Relation(i, j, ord)
    }

    fun hyperedges(): List<Hyperedge> {
        val candidates = faces
            .filter { it.origins.size >= 3 }
            .groupBy { it.origins.size }
            .mapValues { it.value.map { Hyperedge(it.origins, it.relations.toList()) }.toMutableList() }

        val trashBin = mutableListOf<Pair<Int, Hyperedge>>()

        for ((i, edges) in candidates) {
            for (edge in edges) {
                for (larger in candidates[i+1] ?: break) {
                    if (larger.relations.containsAll(edge.relations)) {
                        trashBin.add(i to edge)
                    }
                }
            }
        }

        for ((i, e) in trashBin) {
            candidates[i]!!.remove(e)
        }

        val hEdges = candidates.values.flatten()

        for (e in hEdges) {
            for (r in e.relations) {
                r.hyperedges.add(e)
            }
        }

        return hEdges
    }

    fun draw(drawer: CompositionDrawer, ds: DrawSettings) {
        drawer.apply {
            for (f in faces) {
                composition((f.seamDrawing ?: continue)(hs, ds))
            }

            for (f in faces) {
                composition((f.fillDrawing ?: continue)(hs, ds))
            }

//            for (f in faces) {
//                composition((f.shadowDrawing ?: continue)(hs, ds))
//            }

            for (f in faces) {
                composition((f.strokeDrawing ?: continue)(hs, ds))
            }

            for (i in hs.indices) {
                if (hVertsMap[i].isEmpty()) {
                    highlightContour(hs[i], ds)
                }
            }
        }
    }
}

data class Component(val faces: List<XFace>) {
    val origins = buildSet {
        for (f in faces)
            addAll(f.origins)
    }

    val XHalfEdge.nextBoundaryEdge: XHalfEdge get() {
        var current = next
        var iters = 0
        while (current.twin.face in faces && iters < 1000) {
            iters++
            current = current.twin.next
        }
        if (iters >= 1000) error("Problem")
        return current
    }

    val XHalfEdge.prevBoundaryEdge get() =
        if (prev.twin.face !in faces) prev else prev.twin.prev

    val contour: ShapeContour by lazy {
        val boundaryEdge = run {
            for (f in faces) {
                val startEdge = f.edge
                var currentEdge = startEdge

                var iters = 0
                do {
                    iters++
                    if (currentEdge.twin.face !in faces) return@run currentEdge
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge && iters < 1000)
                if (iters >= 1000) error("Problem")
            }
            error("Could not found a boundary edge of component $this")
        }

        var currentEdge = boundaryEdge
        var c = boundaryEdge.contour

        var iters = 0
        while (iters < 1000) {
            iters++
            currentEdge = currentEdge.nextBoundaryEdge
            if (currentEdge == boundaryEdge) break
            c += currentEdge.contour
        }
        if (iters > 1000) error("Problem")

        c.close()
    }

    fun boundaryPart(i: Int): ShapeContour {
        val startEdge = run {
            var candidate: XHalfEdge? = null
            for (f in faces) {
                val startEdge = f.edge
                var currentEdge = startEdge

                var iters = 0
                do {
                    iters++
                    if (currentEdge.original.hIndex == i) {
                        candidate = currentEdge
                        if (currentEdge.prevBoundaryEdge.original.hIndex != i)
                            return@run currentEdge
                    }
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge && iters < 1000)
                if (iters >= 1000) error("Problem")
            }
            candidate ?: error("Could not found a boundary edge of component $this")
        }

        var currentEdge = startEdge
        var c = startEdge.contour

        var iters = 0
        while (iters < 1000) {
            iters++
            currentEdge = currentEdge.nextBoundaryEdge
            if (currentEdge == startEdge || currentEdge.original.hIndex != i) break
            c += currentEdge.contour
        }
        if (iters >= 1000) error("Problem")

        return c
    }
}

data class Hyperedge(val origins: List<Int>, val relations: List<Relation>) {
    data class Vertex(val i: Int) {
        val neighbors = mutableListOf<Vertex>()
        var mark = 0
    }

    val vertices = origins.associateWith { Vertex(it) }

    init {
        for (p in relations) {
            if (p.order == Ordering.EQ) continue
            val u = vertices[p.left]!!
            val v = vertices[p.right]!!

            if (p.order == Ordering.LT) {
                v.neighbors.add(u)
            } else {
                u.neighbors.add(v)
            }
        }
    }

    fun ordering(): List<Int>? {
        val ordering = mutableListOf<Int>()

        fun visit(u: Vertex): Boolean {
            if (u.mark == 2) return true
            if (u.mark == 1) return false

            u.mark = 1

            for (v in u.neighbors) {
                visit(v)
            }

            u.mark = 2
            ordering.add(u.i)
            return true
        }

        for (v in vertices.values) {
            val success = visit(v)
            if (!success) return null
        }

        return ordering
    }

    fun setOrdering() {
        val ordering = ordering() ?: return
        for (r in relations) {
            val i = ordering.indexOf(r.left)
            val j = ordering.indexOf(r.right)
            r.order = if (i < j) Ordering.LT else Ordering.GT
        }
    }
}

// TODO:
// Make hypergraph from preferences. Remove hyperedges that are subsets of other hyperedges     Done
// Construct directed graphs for every hyperedge    Done
// Check for cycles in every graph associated with a hyperedge      Done
// Violate preferences where necessary to remove cycles (probably pref violations that do not propagate changes to other hyperedges)    TODO
// Compute curves to avoid circles where needed, cut in pieces and store in each face?      Done
// Compute drawing for each face    Done
// Flesh out pairwise preferences   TODO
