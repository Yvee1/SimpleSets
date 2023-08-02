import geometric.*
import islands.Island
import islands.toIsland
import org.openrndr.MouseButton
import org.openrndr.MouseEvent
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.shape.*
import org.openrndr.svg.toSVG
import patterns.*
import java.io.File

fun main() = application {
    configure {
        width = 800
        height = 800
        windowAlwaysOnTop = true
    }

    program {
        val drawSettings = DrawSettings(pointSize = 5.0)
        val computeSettings = ComputeSettings(expandRadius = drawSettings.pointSize * 3)

        val s = 2.5
        val mat = transform {
            translate(-250.0, -250.0)
            scale(s)
        }

//        val points = mutableListOf(
//            200 p0 200,
//            250 p0 210,
//            230 p0 230,
//            270 p0 225,
//            250 p0 245,
//            350 p1 180,
//            290 p1 215,
//            260 p1 240,
//            270 p1 300,
//            330 p1 210,
//            274.8 p2 201.2,
//        )
        val points = getExampleInput(ExampleInput.OverlapExample).map {
            it.copy(pos = it.pos.copy(y = height - it.pos.y - 250.0))
        }.toMutableList()

//        points.addAll(listOf(
//            350 p4 350
//
//        ))

        var dragging: IndexedValue<Point>? = null

        fun MouseEvent.transformedPosition(): Vector2 =
            mat.inversed * position

        mouse.buttonDown.listen { e ->
            if (e.button == MouseButton.LEFT) {
                val closest = points
                    .withIndex()
                    .filter { (_, p) -> p.pos.squaredDistanceTo(e.transformedPosition()) < 100.0 }
                    .minByOrNull { (_, p) -> p.pos.squaredDistanceTo(e.transformedPosition()) } ?: return@listen

                dragging = closest
            }
        }

        mouse.dragged.listen { e ->
            if (dragging == null) return@listen
            val p = e.transformedPosition()
            points[dragging!!.index] = dragging!!.value.copy(pos = p)
        }

        mouse.buttonUp.listen {
            if (it.button == MouseButton.LEFT) {
                dragging = null
            }
        }

//        fun islandOverlaps(island: Island, overlaps: List<IslandOverlap>): IslandOverlaps {
//
//        }



        val f = File("overlapping-islands-trying.svg")
//        f.writeText(c.toSVG())

        extend {
            val pts1 = points.filter { it.type == 0 }
            val pattern1 = Cluster(pts1, pts1.size)
            val island1 = pattern1.toIsland(computeSettings.expandRadius)

            val pts2 = points.filter { it.type == 1 }
            val pattern2 = Cluster(pts2, pts2.size)
            val island2 = pattern2.toIsland(computeSettings.expandRadius)

            val pts3 = points.filter { it.type == 2 }
            print("$pts3\r")
            val pattern3 = Cluster(pts3, pts3.size)
            val island3 = pattern3.toIsland(computeSettings.expandRadius)

            val pts4 = points.filter { it.type == 3 }
            val ch4 = convexHull(pts4)
            val bigGap = (ch4 + ch4.first()).zipWithNext { a, b -> a.pos.squaredDistanceTo(b.pos) }.withIndex().maxBy { it.value }.index
            val bendPts = ch4.subList((bigGap + 1) % ch4.size, ch4.size) + ch4.subList(0, (bigGap + 1) % ch4.size)
            val pattern4 = Bend(bendPts, bendPts.size)
            val island4 = pattern4.toIsland(computeSettings.expandRadius)

            val c = drawComposition {
//                model *= mat
                stroke = ColorRGBa.BLACK
                strokeWeight = drawSettings.contourStrokeWeight// * s
//                contour(m)
                if (island2.contour.segments.isNotEmpty()) {
                    val shadows = contourShadows(island2.contour, 0.5, 10, 0.4)
                    stroke = null
                    for ((i, shadow) in shadows.withIndex()) {
                        fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                        contour(shadow)
                    }
                }
                fill = drawSettings.colorSettings.lightColors[1].toColorRGBa().mix(ColorRGBa.WHITE, 0.5)
                stroke = ColorRGBa.BLACK
                contour(island2.contour)

                if (island3.contour.segments.isNotEmpty()) {
                    val shadows = contourShadows(island3.contour, 0.5, 10, 0.4)
                    stroke = null
                    for ((i, shadow) in shadows.withIndex()) {
                        fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                        contour(shadow)
                    }
                }
                fill = drawSettings.colorSettings.lightColors[2].toColorRGBa().mix(ColorRGBa.WHITE, 0.5)
                stroke = ColorRGBa.BLACK
                contour(island3.contour)

                val m4 = morphIsland(null, island4, listOf(island2))
                if (m4.segments.isNotEmpty()) {
                    val shadows = contourShadows(m4, 0.5, 10, 0.4)
                    stroke = null
                    for ((i, shadow) in shadows.withIndex()) {
                        fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                        contour(shadow)
                    }
                }
                fill = drawSettings.colorSettings.lightColors[3].toColorRGBa().mix(ColorRGBa.WHITE, 0.5)
                stroke = ColorRGBa.BLACK
                contour(m4)

                fill = null
//                lineLoop(island1.points.map { it.pos })
//                contour(island2.contour)
//                val m =  morphIsland(null, island2, listOf(IslandOverlap(island2, island1, 1.0)))
                val m =  morphIsland(null, island1, listOf(island2, island3, island4))
                if (m.segments.isNotEmpty()) {
                    val shadows = contourShadows(m, 0.5, 10, 0.4)
                    stroke = null
                    for ((i, shadow) in shadows.withIndex()) {
                        fill = ColorRGBa.GRAY.opacify(0.02 + (shadows.lastIndex - i.toDouble()) / shadows.size * 0.2)
                        contour(shadow)
                    }
                }
                fill = drawSettings.colorSettings.lightColors[0].toColorRGBa().mix(ColorRGBa.WHITE, 0.5)
                stroke = ColorRGBa.BLACK
                contour(m)
                fill = null
//                lineLoop(island2.points.map { it.pos })
                strokeWeight = drawSettings.pointStrokeWeight// * s
                fill = drawSettings.colorSettings.lightColors[0].toColorRGBa()
                circles(pts1.map { it.pos }, drawSettings.pointSize)
                fill = drawSettings.colorSettings.lightColors[1].toColorRGBa()
                circles(pts2.map { it.pos }, drawSettings.pointSize)
                fill = drawSettings.colorSettings.lightColors[2].toColorRGBa()
                circles(pts3.map { it.pos }, drawSettings.pointSize)
                fill = drawSettings.colorSettings.lightColors[3].toColorRGBa()
                circles(pts4.map { it.pos }, drawSettings.pointSize)


//                fill = ColorRGBa.WHITE.opacify(0.9)
//                stroke = null
//                rectangle(drawer.bounds)
//                morphIsland(this, island1, listOf(IslandOverlap(island1, island2, 1.0), IslandOverlap(island1, island3, 1.0)))

//                morphIsland(this, island2, listOf(IslandOverlap(island2, island1, 1.0)))
//                morphIsland(this, island1, listOf(IslandOverlap(island1, island2, 1.0)))
//            morphIsland(this, island2, listOf(IslandOverlap(island2, island1, 1.0)))
            }
            f.writeText(c.toSVG())

            drawer.apply {
//                scale(2.5)
//                translate(-250.0, -250.0)
                view *= mat
                clear(ColorRGBa.WHITE)
                composition(c)
            }
        }
    }
}

fun contourShadows(c: ShapeContour, startGrad: Double, steps: Int, stepSize: Double): List<ShapeContour> =
    List(steps) {
        val r = startGrad + it * stepSize
        c.buffer(r)
    }

//fun overlapOrder(island1: Island, island2: Island): IslandOverlap {
//
//    return IslandOverlap(island1, island2, 5.0)
//}

data class IslandOverlap(val overlapper: Island, val overlapee: Island, val weight: Double) {
//    fun theOther(island: Island): Island = when {
//        island1 == island -> island2
//        island2 == island -> island1
//        else -> error("Claimed island is not part of island overlap\nIsland: $island\nIslandOverlap: $this")
//    }
}

data class OverlapVertex(val island: Island, val outEdges: List<OverlapEdge>)

typealias OverlapEdge = IslandOverlap

class OverlapGraph(val vertices: List<OverlapVertex>, val edges: List<OverlapEdge>)

sealed class TangentOrSharedPoint {
    abstract val start: Vector2
    abstract val end: Vector2
    abstract val dir: Vector2

    data class Tangent(val t: Pair<Vector2, Vector2>): TangentOrSharedPoint() {
        override val start: Vector2 get() = t.first
        override val end: Vector2 get() = t.second
        override val dir: Vector2 get() = end - start
    }
    data class SharedPoint(val pos: Vector2, override val dir: Vector2): TangentOrSharedPoint() {
        override val start: Vector2 = pos
        override val end: Vector2 = pos
    }
}

sealed class CircleOrEndpoint {
    abstract fun tangent(other: CircleOrEndpoint, orient: Orientation): TangentOrSharedPoint

    data class Endpoint(val pos: Vector2): CircleOrEndpoint() {
        override fun tangent(other: CircleOrEndpoint, orient: Orientation): TangentOrSharedPoint {
            return when(other) {
                is Endpoint -> TangentOrSharedPoint.Tangent(pos to other.pos)
                is BCircle -> {
                    val v = pos
                    val (c, b) = other
                    circlePointTangent(v, c, b, orient, reverse = false)
                }
            }
        }
    }

    data class BCircle(val circle: Circle, val include: Boolean): CircleOrEndpoint() {
        override fun tangent(other: CircleOrEndpoint, orient: Orientation): TangentOrSharedPoint {
            return when(other) {
                is Endpoint -> {
                    val (c, b) = this
                    val v = other.pos
                    circlePointTangent(v, c, b, orient, reverse = true)
                }

                is BCircle -> {
                    val (c1, b1) = this
                    val (c2, b2) = other
                    circleCircleTangent(c1, b1, c2, b2, orient)
                }
            }
        }
    }
}

fun circlePointTangent(v: Vector2, c: Circle, b: Boolean, orient: Orientation, reverse: Boolean): TangentOrSharedPoint {
    val cts = c.tangents(v).toList()
    if (v.squaredDistanceTo(c.center) <= c.radius * c.radius + 0.1 || cts.any { it.x.isInfinite() }) {
        val dir = c.contour.normal(c.contour.nearest(v).contourT)
            .perpendicular(if (b) orient.polarity.opposite else orient.polarity)
        return TangentOrSharedPoint.SharedPoint(v, dir)
    }

    val candidates = cts.filter {
        val o = if (reverse) orientation(c.center, it, v) else orientation(v, it, c.center)
        if (b)
            o != orient.opposite()
        else
            o != orient
    }
    if (candidates.isEmpty()) {
        error("No tangent with the correct orientation")
    } else if (candidates.size > 1) {
//        println("Multiple tangents with the correct orientation")
    }
    return TangentOrSharedPoint.Tangent(if (reverse) candidates[0] to v else v to candidates[0])
}

fun circleCircleTangent(c1: Circle, b1: Boolean, c2: Circle, b2: Boolean, orient: Orientation): TangentOrSharedPoint {
    val tngnts = c1.tangents(c2, isInner = b1 != b2)

    if (tngnts.isNotEmpty() && tngnts.all { it.first.squaredDistanceTo(it.second) > 0.1 }) {
        val candidateTs = tngnts.filter { (a, b) ->
            val o = orientation(c1.center, a, b)
            a.x.isFinite() && b.x.isFinite() &&
            if (b1)
                o != orient.opposite()
            else
                o != orient
        }
        if (candidateTs.isEmpty()) {
            error("No tangent with the correct orientation (c1: $c1, c2: $c2)")
        } else if (candidateTs.size > 1) {
//            println("Multiple tangents with the correct orientation")
        }
        val t = candidateTs[0]
        return TangentOrSharedPoint.Tangent(t)
    } else {
        val p = (c1.center * c2.radius + c2.center * c1.radius) / (c1.radius + c2.radius)
        val dir = c1.contour.normal(c1.contour.nearest(p).contourT)
            .perpendicular(if (b1) orient.polarity.opposite else orient.polarity)
        return TangentOrSharedPoint.SharedPoint(p, dir)
    }
}

data class IslandOverlaps(val overlapper: Island, val overlapees: List<Island>, )

fun separatingCurve(drawer: CompositionDrawer?, interC: ShapeContour, gCircles: List<Circle>, bCircles: List<Circle>): ShapeContour {
    val gClosest = interC.nearest(gCircles[0].center)
    val gClosestDir = interC.normal(gClosest.contourT).perpendicular(interC.polarity.opposite)
    val orient = orientation(gClosest.position, gClosest.position + gClosestDir, gCircles[0].center)

    val interCE = interC.extend(0.1)
    val circles = gCircles.map { it to true } + bCircles.map { it to false }
    if (circles.size <= 1) {
        return interC
    }

//            val (gSmallerCircles, bSmallerCircles) = growCircles(gCircles.map { it.center }, bCircles.map { it.center },
//                circles[0].first.radius)
    val smallerCircles = gCircles.map { CircleOrEndpoint.BCircle(it, true) } +
            bCircles.map { CircleOrEndpoint.BCircle(it, false) }
    val endEnd = CircleOrEndpoint.Endpoint(interC.end)
    val evenSmallerCircles = smallerCircles.map { it.copy(circle = it.circle.copy(radius = it.circle.radius * 0.98)) }
    val remaining = (smallerCircles + endEnd).toMutableList()
    var current: CircleOrEndpoint = CircleOrEndpoint.Endpoint(interC.start)
    var currentPos = interC.start
    var currentDir = (interC.start - interCE.start).normalized
    val tngnts = mutableListOf<TangentOrSharedPoint>()
    val seenCircles = mutableListOf<CircleOrEndpoint.BCircle>()

    fun next(): Boolean {
        remaining.remove(current)
        if (current is CircleOrEndpoint.BCircle) {
            seenCircles.add(current as CircleOrEndpoint.BCircle)
        }

        val tangents = remaining
            .map {
                it to current.tangent(it, orient)
            }.filter { (cp, tp) ->
                if (cp is CircleOrEndpoint.Endpoint) true else {
                    val notWrongDirection =
                        if (current is CircleOrEndpoint.Endpoint || (current as CircleOrEndpoint.BCircle).include) {
                            orientation(currentPos, currentPos + currentDir, tp.end) != orient.opposite()
                        } else {
                            orientation(currentPos, currentPos + currentDir, tp.end) != orient
                        }
                    notWrongDirection
                }
            }

        val pComp = compareAround(currentPos, -currentDir, orient)
        val comp =
            Comparator<TangentOrSharedPoint> { tp1, tp2 ->
                if (current is CircleOrEndpoint.Endpoint) {
                    pComp.compare(tp1.end, tp2.end)
                } else {
                    val (c, b) = (current as CircleOrEndpoint.BCircle)
                    val circleComp = compareAround(c.center, (currentPos - c.center).rotate(if (orient == Orientation.RIGHT) -90.0 else 90.0), orient)
                    circleComp.compare(tp1.start, tp2.start)
                }
            }

        val compAlt = Comparator<Pair<CircleOrEndpoint, TangentOrSharedPoint>> { (_, a1), (_, a2) ->
            comp.compare(a1, a2)
        }

        val largestIncluded = tangents
            .filter { (a, _) -> if (a is CircleOrEndpoint.BCircle) a.include else true }
            .maxWithOrNull(compAlt)

        val largestExcluded = tangents
            .filter { (a, _) -> if (a is CircleOrEndpoint.BCircle) !a.include else true }
            .minWithOrNull(compAlt)

        val freeTangents = tangents.filter { (c1, t) ->
            if (t is TangentOrSharedPoint.Tangent) {
                evenSmallerCircles.filter { it !in listOf(c1, current) }.none { (c, _) ->
                    LineSegment(t.start, t.end).contour.overlaps(c.contour)
                }
            } else true
        }

        val validTangents = freeTangents.filter { ct ->
            val (_, tp) = ct
            if (tp is TangentOrSharedPoint.Tangent) {
                val perp = (tp.end - tp.start).perpendicular(orient.polarity.opposite)
                val (included, excluded) = remaining.filterIsInstance<CircleOrEndpoint.BCircle>().partition { it.include }
                val excludedNotIncluded = excluded.none { (c, _) ->
                    orientation(tp.start, tp.end, c.center) == orient &&
                            orientation(tp.end, tp.end + perp, c.center) == orient &&
                            orientation(tp.start, tp.start + perp, c.center) == orient.opposite()
                }
                val includedNotExcluded = included.none { (c, _) ->
                    orientation(tp.start, tp.end, c.center) == orient.opposite() &&
                            orientation(tp.end, tp.end + perp, c.center) == orient &&
                            orientation(tp.start, tp.start + perp, c.center) == orient.opposite()
                }
                val betweenExtremes = compAlt.compare(ct, largestExcluded) >= 0 && compAlt.compare(ct, largestIncluded) <= 0
                includedNotExcluded && excludedNotIncluded && betweenExtremes
            } else {
                true
            }
        }.sortedWith(compAlt)

        if (validTangents.isEmpty()) {
            //println("Stopped early because there are no valid tangents")
            return false
        }

        val (cp, tp) = if (validTangents.size == 1)
            validTangents[0]
        else {
            if (largestIncluded!! in validTangents)
                largestIncluded
            else if (largestExcluded!! in validTangents)
                largestExcluded
            else {
                val liT = largestIncluded.second as TangentOrSharedPoint.Tangent
                val leC = largestExcluded.first as CircleOrEndpoint.BCircle
                val iBeforeE = LineSegment(liT.start, liT.end).contour.intersections(leC.circle.contour).isEmpty()
                //  except after c
                if (iBeforeE) {
                    validTangents.last()
                } else {
                    validTangents.first()
                }
            }
        }
        current = cp
        currentPos = tp.end
        currentDir = tp.dir
        tngnts.add(tp)
        return current != endEnd
    }

    while(currentPos.squaredDistanceTo(interC.end) > 0.1) {
        if (!next()) break
    }
//            next()
//            next()
//            next()
//            next()
//            next()

    var final: ShapeContour = ShapeContour.EMPTY

    if (tngnts.size == 1 && bCircles.isNotEmpty()) {
        val gC = gCircles.firstOrNull { LineSegment(tngnts[0].start, tngnts[0].end).contour.intersections(it.contour).isNotEmpty() }
        val bC = bCircles.firstOrNull { LineSegment(tngnts[0].start, tngnts[0].end).contour.intersections(it.contour).isNotEmpty() }

        final = if (gC != null && bC != null)
            hobbyCurve(listOf(interC.start, interC.extend(-0.1).start,  (gC.center * bC.radius + bC.center * gC.radius) / (gC.radius + bC.radius), interC.extend(-0.1).end, interC.end), closed = false)
        else
            hobbyCurve(listOf(interC.start, interC.extend(-0.1).start, interC.extend(-0.1).end, interC.end), closed = false)
    }

    if (tngnts.size > 1 && seenCircles.size == tngnts.size - 1) {
        val cas = seenCircles.indices.map { i ->
            val (c, b) = seenCircles[i]
            if (tngnts[i].end.squaredDistanceTo(tngnts[i+1].start) < 1.0)
                ShapeContour.EMPTY
            else
                c.subVO(tngnts[i].end, tngnts[i+1].start, if (b) orient else orient.opposite())
        }

        val tp0 = tngnts[0].end
        val firstTangentPointE = LineSegment(tngnts[0].start, tngnts[0].end).extend(-0.1).end
        val startHobby = if (interC.start.squaredDistanceTo(firstTangentPointE) < 0.1) ShapeContour.EMPTY else hobbyCurve(listOf(interC.start, interC.extend(-0.1).start, firstTangentPointE, tp0), closed = false)
        val tpL = tngnts.last().start
        val tpE = LineSegment(tngnts.last().start, tngnts.last().end).extend(-0.1).start
        val endHobby = if (tpL.squaredDistanceTo(interC.end) < 0.1) ShapeContour.EMPTY else
            hobbyCurve(listOf(tpL, tpE, interC.extend(-0.1).end, interC.end), closed = false)

        final = startHobby
        for (i in seenCircles.indices) {
            if (!cas[i].empty && cas[i].start.x.isFinite())
                final += cas[i]
            if (i + 1 in 1 until seenCircles.lastIndex) {
                val t = tngnts[i + 1]
                if (t is TangentOrSharedPoint.Tangent && t.start.x.isFinite() && t.end.x.isFinite() && t.start.squaredDistanceTo(t.end) > 0.1) {
                    final += LineSegment(t.start, t.end).contour
                }
            }
        }
        final += endHobby
    }

    fun CompositionDrawer.tsp(t: TangentOrSharedPoint, r: Double = 4.0) = when (t) {
        is TangentOrSharedPoint.SharedPoint -> {
            //lineSegment(t.pos, t.pos + t.dir.normalized * 5.0)
            circle(t.pos, r)
        }
        is TangentOrSharedPoint.Tangent -> lineSegment(t.start, t.end)
    }

    fun CompositionDrawer.cep(c: CircleOrEndpoint, r: Double = 2.0) = when (c) {
        is CircleOrEndpoint.BCircle -> circle(c.circle)
        is CircleOrEndpoint.Endpoint -> circle(c.pos, r)
    }

    drawer?.apply {
        stroke = ColorRGBa.BLACK
        fill = ColorRGBa.GRAY
        fill = null
        strokeWeight /= 2
        circles(circles.map { it.first })
        strokeWeight *= 2
        for ((i, sc) in smallerCircles.withIndex()) {
            val (c, b) = sc
            val o = 0.1 + 0.5
            fill = if (b) ColorRGBa.GREEN.opacify(o) else ColorRGBa.RED.opacify(o)
            circle(c)
        }
        fill = null

        val tangents = remaining
            .filter { it != current }
            .map {
                it to current.tangent(it, orient)
            }
//                    .filter { (cp, tp) ->
//                        if (cp is CircleOrEndpoint.Endpoint) true else {
//                            val notWrongDirection =
//                                if (current is CircleOrEndpoint.Endpoint || (current as CircleOrEndpoint.BCircle).include) {
//                                    orientation(currentPos, currentPos + currentDir, tp.end) != orient.opposite()
//                                } else {
//                                    orientation(currentPos, currentPos + currentDir, tp.end) != orient
//                                }
//                            notWrongDirection
//                        }
//                    }

        val pComp = compareAround(currentPos, -currentDir, orient)
        val comp =
            Comparator<TangentOrSharedPoint> { tp1, tp2 ->
                if (current is CircleOrEndpoint.Endpoint) {
                    pComp.compare(tp1.end, tp2.end)
                } else {
                    val (c, b) = (current as CircleOrEndpoint.BCircle)
                    val circleComp = compareAround(c.center, (currentPos - c.center).rotate(if (orient == Orientation.RIGHT) -90.0 else 90.0), if (b) orient else orient)
                    val pts = c.contour.equidistantPositions(10).sortedWith(circleComp)
                    for ((i, p) in pts.withIndex()) {
                        strokeWeight = 1.0
                        fill = ColorRGBa.PINK.opacify(0.2 * i / (pts.size - 1.0))
                        circle(p, 4.0)
                    }
                    circleComp.compare(tp1.start, tp2.start)
                }
            }

        val compAlt = Comparator<Pair<CircleOrEndpoint, TangentOrSharedPoint>> { (_, a1), (_, a2) ->
            comp.compare(a1, a2)
        }

        val largestIncluded = tangents
            .filter { (a, _) -> if (a is CircleOrEndpoint.BCircle) a.include else true }
            .maxWithOrNull(compAlt)
        val largestExcluded = tangents
            .filter { (a, _) -> if (a is CircleOrEndpoint.BCircle) !a.include else true }
            .minWithOrNull(compAlt)
        val freeTangents = tangents.filter { (c1, t) ->
            if (t is TangentOrSharedPoint.Tangent) {
                evenSmallerCircles.filter { it !in listOf(c1, current) }.none { (c, _) ->
                    LineSegment(t.start, t.end).contour.overlaps(c.contour)
                }
            } else true
        }
        val validTangents = freeTangents.filter { ct ->
            val (_, tp) = ct
            if (tp is TangentOrSharedPoint.Tangent) {
                val perp = (tp.end - tp.start).perpendicular(orient.polarity.opposite)
                val (included, excluded) = remaining.filterIsInstance<CircleOrEndpoint.BCircle>().partition { it.include }
                val excludedNotIncluded = excluded.none { (c, _) ->
                    orientation(tp.start, tp.end, c.center) == orient &&
                            orientation(tp.end, tp.end + perp, c.center) == orient &&
                            orientation(tp.start, tp.start + perp, c.center) == orient.opposite()
                }
                val includedNotExcluded = included.none { (c, _) ->
                    orientation(tp.start, tp.end, c.center) == orient.opposite() &&
                            orientation(tp.end, tp.end + perp, c.center) == orient &&
                            orientation(tp.start, tp.start + perp, c.center) == orient.opposite()
                }
                val betweenExtremes = compAlt.compare(ct, largestExcluded) >= 0 && compAlt.compare(ct, largestIncluded) <= 0
                includedNotExcluded && excludedNotIncluded && betweenExtremes
            } else {
                true
            }
        }.sortedWith(compAlt)

        val tpnext = if (validTangents.isEmpty() || largestExcluded == null) null
        else if (validTangents.size == 1)
            validTangents[0]
        else {
//            if (largestIncluded!! in validTangents)
//                largestIncluded
//            else if (largestExcluded!! in validTangents){
            val bsIndex = validTangents.binarySearch(largestExcluded!!, compAlt)
            if (bsIndex >= 0) {
                largestExcluded
            } else {
                val invIndex = -bsIndex - 1
                if (invIndex > validTangents.lastIndex)
                    validTangents[invIndex - 1]
                else {
                    validTangents[invIndex]
                }
            }
//                }
        }
        fill = null
        stroke = ColorRGBa.ORANGE.opacify(0.8)
        for ((_, t) in validTangents) {
            tsp(t)
        }
        stroke = ColorRGBa.PINK.opacify(0.8)
        for (t in tngnts) {
            tsp(t)
        }

//                strokeWeight = 4.0
        if (largestIncluded != null) {
            stroke = ColorRGBa.GREEN.opacify(0.8)
            tsp(largestIncluded.second)
        }

        if (largestExcluded != null) {
            stroke = ColorRGBa.RED.opacify(0.8)
            tsp(largestExcluded.second)
        }

//                contour(LineSegment(currentPos, currentPos + 50.0 * currentDir.rotate(0.0)).contour.extend(20.0))

//                for (r in remaining) {
//                    stroke = ColorRGBa.PURPLE
//                    cep(r)
//                }

        stroke = ColorRGBa.BLUE
        cep(current, 2.0)
//                contour(final)
        stroke = ColorRGBa.CYAN
        tpnext?.let {
            tsp(it.second)
        }
//                fill = ColorRGBa.CYAN.opacify(0.6)
//                lineSegment(currentPos, currentPos + currentDir.normalized * 10.0)
//                circle(currentPos, 4.0)
    }

    return final
}

fun morphIsland(drawer: CompositionDrawer?, island: Island, overlapees: List<Island>): ShapeContour {
//    require(overlaps.all { it.overlapper == island }) {
//        "Provided island in function ${::morphIsland.name} is not the overlapper for all provided overlaps."
//    }
    if (overlapees.isEmpty()) return island.contour

    var piece = Shape(listOf(island.contour.open))
    for (overlapee in overlapees) {
        piece = difference(piece, overlapee.contour)
    }
    val pieces = piece.contours.mergeAdjacent()
    if (pieces.isEmpty()) return island.contour

    if (pieces.size == 1 && pieces[0].start.squaredDistanceTo(pieces[0].end) < 1E-6) {
        return island.contour
    }

    val broken = (pieces + pieces[0]).zipWithNext { c1, c2 ->
        island.contour.subVC(c1.end, c2.start)
    }

    drawer?.apply {
        for ((i, c) in pieces.withIndex()) {
            stroke = ColorRGBa.ORANGE.opacify(0.2 * (i + 1))
            fill = null
            contour(c)
        }
        for (c in broken) {
            stroke = ColorRGBa.BLUE
            contour(c)
        }
    }

    val expandRadius = island.circles[0].radius

    val gCircles = island.allPoints.map {
        Circle(it.pos, expandRadius)
    }
    val bCircles = overlapees.flatMap {
        it.allPoints.map { Circle(it.pos, expandRadius) }
    }
    val circles = gCircles + bCircles
    val (gSmallerCircles, bSmallerCircles) = growCircles(gCircles.map { it.center }, bCircles.map { it.center },
        circles[0].radius)

    val mends = broken.map { interC ->
        if (interC.segments.isEmpty()) ShapeContour.EMPTY else {
            val interCE = interC.extend(0.1)
            val relevantBCircles = bSmallerCircles.filter { intersections(it.contour, interCE).isNotEmpty() }
            separatingCurve(drawer, interC, gSmallerCircles, relevantBCircles)
        }
    }

    var result = ShapeContour.EMPTY
    for (i in pieces.indices) {
        result += pieces[i]
        if (!mends[i].empty)
            result += mends[i]
    }

    return result.close()
}