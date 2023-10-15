import geometric.*
import highlights.Highlight
import highlights.toHighlight
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Matrix44
import org.openrndr.shape.*
import patterns.SinglePoint
import java.io.File

fun main() = application {
    configure {
        width = 800
        height = 800
        windowAlwaysOnTop = true
        windowResizable = true
    }

    program {
        val ds = DrawSettings(pSize = 5.0, whiten = 0.0)
        val cds = ComputeDrawingSettings(expandRadius = ds.pSize * 3, pointClearance = 0.625)

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

        val pts = pts1 + pts2 + pts3 + pts4

        val highlights = listOf(island1, island2, island3, island4)

        val xGraph = XGraph(highlights, cds)

        println(xGraph.hyperedges()[0].ordering())

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
                clear(ColorRGBa.BLACK)

                for (f in xGraph.faces) {
                    composition((f.fillDrawing ?: continue)(xGraph.hs, ds))
                }

                for (f in xGraph.faces) {
                    composition((f.strokeDrawing ?: continue)(xGraph.hs, ds))
                }

//                stroke = ColorRGBa.BLACK
//                xGraph.vertices.forEach { v ->
//                    fill = ColorRGBa.WHITE
//                    strokeWeight = 0.5
//                    circle(v.x.position, 1.5)
//                }

                coloredPoints(pts, ds)
//
//                fill = null
//                circles(pts.map { it.pos }, cds.pointClearance * cds.expandRadius)
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


    lateinit var twin: XHalfEdge

    lateinit var face: XFace

    var morphedContour: ShapeContour? = null

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

    data class Vertex(val i: Int) {
        val neighbors = mutableListOf<Vertex>()
        var mark = 0
    }

    val vertices = origins.associateWith { Vertex(it) }

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

    fun setMorphedEdge(i: Int, morphed: ShapeContour) {
        val e = run {
            var current = edge

            do {
                if (current.original.hIndex == i) return@run current
                current = current.next
            } while (current != edge)
            return
        }

        e.morphedContour = intersection(morphed, contour!!).contours.firstOrNull()
    }

    val fillDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
                stroke = null
                fill = ds.colorSettings.lightColors[hs[top].type].toColorRGBa().mix(ColorRGBa.WHITE, ds.whiten)
                contour(contour)
            }
        }

        ::draw
    }

    val strokeDrawing: ((List<Highlight>, DrawSettings) -> Composition)? by lazy {
        if (contour == null) return@lazy null

        val edgeContours = buildList {
            var current = edge

            do {
                if (current.original.hIndex == top) add(current.morphedContour ?: current.contour)
                current = current.next
            } while(current != edge)
        }

        fun draw(hs: List<Highlight>, ds: DrawSettings): Composition {
            return drawComposition {
                stroke = ColorRGBa.BLACK
                strokeWeight = ds.contourStrokeWeight
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
                val morphed = morphCurve(cont, inclCircles, exclCircles, cds)

                for (f in c.faces) {
                    f.setMorphedEdge(i, morphed)
                }
            }
        }
    }

    private fun createEdges() {
        for (i in hs.indices) {
            val h = hs[i]
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

            while (current.next != heStart) {
                current = current.next
                visited.add(current)
                faceContour += current.contour
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

        while (remainder.isNotEmpty()) {
            val component = mutableListOf<XFace>()
            val first = remainder.first()
            val q = mutableListOf(first)

            while (q.isNotEmpty()) {
                val f = q.removeFirst()
                component.add(f)
                val startEdge = f.edge
                var currentEdge = startEdge

                do {
                    val candidate = currentEdge.twin.face
                    if (candidate !in component && candidate in remainder && candidate !in q) {
                        q.add(candidate)
                    }
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge)
            }

            components.add(Component(component))
            remainder.removeAll(component)
        }

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

        return includedCircles.filterNot { it.radius > 0 && intersection(c.contour, it.shape).empty } to
                excludedCircles.filterNot { it.radius > 0 && intersection(c.contour, it.shape).empty }
    }

    fun relevantCircles(i: Int, avoidees: List<Int>, c: Component): Pair<List<Circle>, List<Circle>> {
        val r = cds.expandRadius
        val leftCircles = hs[i].allPoints.map { Circle(it.pos, r) }
        val rightCircles = avoidees.flatMap { j -> hs[j].allPoints.map { Circle(it.pos, r) } }

        val (includedCircles, excludedCircles) = growCircles(leftCircles.map { it.center }, rightCircles.map { it.center },
            r, r * cds.pointClearance)

        return includedCircles.filterNot { it.radius > 0 && intersection(c.contour, it.shape).empty } to
                excludedCircles.filterNot { it.radius > 0 && intersection(c.contour, it.shape).empty }
    }

    fun computePreference(i: Int, j: Int, c: Component): Relation {
        var ord = Ordering.EQ

        val (lics, rjcs) = relevantCircles(i, j, c)

        val (ljcs, rics) = relevantCircles(j, i, c)

        if (rics.isEmpty() && rjcs.isNotEmpty()) {
            ord = Ordering.LT
        }
        if (rjcs.isEmpty() && rics.isNotEmpty()) {
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
}

data class Component(val faces: List<XFace>) {
    val origins = buildSet {
        for (f in faces)
            addAll(f.origins)
    }

    val XHalfEdge.nextBoundaryEdge get() =
        if (next.twin.face !in faces) next else next.twin.next

    val XHalfEdge.prevBoundaryEdge get() =
        if (prev.twin.face !in faces) prev else prev.twin.prev

    val contour: ShapeContour by lazy {
        val boundaryEdge = run {
            for (f in faces) {
                val startEdge = f.edge
                var currentEdge = startEdge

                do {
                    if (currentEdge.twin.face !in faces) return@run currentEdge
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge)
            }
            error("Could not found a boundary edge of component $this")
        }

        var currentEdge = boundaryEdge
        var c = boundaryEdge.contour

        while (true) {
            currentEdge = currentEdge.nextBoundaryEdge
            if (currentEdge == boundaryEdge) break
            c += currentEdge.contour
        }

        c.close()
    }

    fun boundaryPart(i: Int): ShapeContour {
        val startEdge = run {
            var candidate: XHalfEdge? = null
            for (f in faces) {
                val startEdge = f.edge
                var currentEdge = startEdge

                do {
                    if (currentEdge.original.hIndex == i) {
                        candidate = currentEdge
                        if (currentEdge.prevBoundaryEdge.original.hIndex != i)
                            return@run currentEdge
                    }
                    currentEdge = currentEdge.next
                } while (currentEdge != startEdge)
            }
            candidate ?: error("Could not found a boundary edge of component $this")
        }

        var currentEdge = startEdge
        var c = startEdge.contour

        while (true) {
            currentEdge = currentEdge.nextBoundaryEdge
            if (currentEdge == startEdge || currentEdge.original.hIndex != i) break
            c += currentEdge.contour
        }

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
// Compute curves to avoid circles where needed, cut in pieces and store in each face?      In progress
// Compute drawing for each face    In progress
// Flesh out pairwise preferences   TODO
