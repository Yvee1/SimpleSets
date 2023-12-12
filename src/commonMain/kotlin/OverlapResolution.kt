import geometric.*
import highlights.Highlight
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.math.Vector2
import org.openrndr.shape.*
import patterns.*
import kotlin.math.min
import kotlin.math.pow

sealed class TangentOrSharedPoint {
    abstract val start: Vector2
    abstract val end: Vector2
    abstract val dir: Vector2

    data class Tangent(val t: Pair<Vector2, Vector2>): TangentOrSharedPoint() {
        override val start: Vector2 get() = t.first
        override val end: Vector2 get() = t.second
        override val dir: Vector2 get() = end - start
        val ls: LineSegment get() = LineSegment(start, end)
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

data class IslandOverlaps(val overlapper: Highlight, val overlapees: List<Highlight>, )

//typealias Debug = (ShapeContour, List<Circle>, List<Circle>, CircleOrEndpoint, List<Pair<CircleOrEndpoint, TangentOrSharedPoint>>) -> Unit
typealias Debug = (Composition, String) -> Unit
val noDebug: Debug = { _, _, -> }

fun separatingCurve(interC: ShapeContour, gCircles: List<Circle>, bCircles: List<Circle>, gs: GeneralSettings,
                    cds: ComputeDrawingSettings, debug: Debug = noDebug): ShapeContour {
//    if (!bCircles.any { it.contour.overlaps(interC) }) return interC
    if (bCircles.isEmpty()) return interC
    val gClosest = interC.nearest(gCircles[0].center)
    val gClosestDir = interC.direction(gClosest.contourT)
    val orient = orientation(gClosest.position, gClosest.position + gClosestDir, gCircles[0].center)

    val interCE = interC.extend(0.1)
    val circles = gCircles.map { CircleOrEndpoint.BCircle(it, true) } +
            bCircles.map { CircleOrEndpoint.BCircle(it, false) }
    val endEnd = CircleOrEndpoint.Endpoint(interC.end)
    val smallerCircles = circles.map { it.copy(circle = it.circle.copy(radius = it.circle.radius * 0.98)) }
    val remaining = (circles + endEnd).toMutableList()
    var current: CircleOrEndpoint = CircleOrEndpoint.Endpoint(interC.start)
    var currentPos = interC.start
    var currentDir = (interC.start - interCE.start).normalized
    val tngnts = mutableListOf<TangentOrSharedPoint>()
    val seenCircles = mutableListOf<CircleOrEndpoint.BCircle>()

    val chPoints = convexHull(gCircles.map { it.center })
    val ch = ShapeContour.fromPoints(chPoints, true)
//    if (gCircles.size > 2 && bCircles.any { it.center in ch }) return interC

    fun next(): Boolean {
        remaining.remove(current)
        if (current is CircleOrEndpoint.BCircle) {
            seenCircles.add(current as CircleOrEndpoint.BCircle)
        }

        val tangents = remaining
            .map {
                it to current.tangent(it, orient)
            }
            .filter { (_, tp) ->
                val notWrongDirection =
                    if (current is CircleOrEndpoint.Endpoint || (current as CircleOrEndpoint.BCircle).include) {
                        orientation(currentPos, currentPos + currentDir, tp.end) != orient.opposite()
                    } else {
                        orientation(currentPos, currentPos + currentDir, tp.end) != orient
                    }

//                val crossesCh = if (ch.empty) false else when (tp) {
//                    is TangentOrSharedPoint.SharedPoint -> tp.pos in ch
//                    is TangentOrSharedPoint.Tangent -> LineSegment(tp.start, tp.end).contour.overlaps(ch)
//                }

                notWrongDirection //&&
//                        !crossesCh
            }

        val pComp = compareAround(currentPos, -currentDir, orient)
        val comp =
            Comparator<TangentOrSharedPoint> { tp1, tp2 ->
                if (current is CircleOrEndpoint.Endpoint) {
                    pComp.compare(tp1.end, tp2.end)
                } else {
                    val (c, _) = (current as CircleOrEndpoint.BCircle)
                    val circleComp = compareAround(c.center, (currentPos - c.center).rotate(if (orient == Orientation.RIGHT) -0.0 else 0.0), orient)
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
                smallerCircles.filter { it !in listOf(c1, current) }.none { (c, _) ->
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
                val betweenExtremes = (largestExcluded == null || compAlt.compare(ct, largestExcluded) >= 0)
                        && (largestIncluded == null || compAlt.compare(ct, largestIncluded) <= 0)
                includedNotExcluded && excludedNotIncluded && betweenExtremes
            } else {
                true
            }
        }.sortedWith(compAlt)

        if (validTangents.isEmpty()) {
            println("Stopped early because there are no valid tangents")
            fun CompositionDrawer.tsp(t: TangentOrSharedPoint, r: Double = 4.0) = when (t) {
                is TangentOrSharedPoint.SharedPoint -> {
                    circle(t.pos, r)
                }
                is TangentOrSharedPoint.Tangent -> lineSegment(t.start, t.end)
            }

            fun CompositionDrawer.cep(c: CircleOrEndpoint, r: Double = 2.0) = when (c) {
                is CircleOrEndpoint.BCircle -> circle(c.circle)
                is CircleOrEndpoint.Endpoint -> circle(c.pos, r)
            }

            val composition = drawComposition {
                stroke = ColorRGBa.BLACK
                contour(interC)

                fill = ColorRGBa.GREEN.opacify(0.4)
                circles(gCircles)

                fill = ColorRGBa.RED.opacify(0.4)
                circles(bCircles)

                fill = null
                stroke = ColorRGBa.BLUE
                cep(current)

                stroke = ColorRGBa.PINK
                for (t in tangents) {
                    tsp(t.second)
                }
            }
            debug(composition, "debug-tangents")
            return false
        }

        val (cp, tp) = if (validTangents.size == 1)
            validTangents[0]
        else {
            val endInSight = validTangents.find { it.first == endEnd }
            if (endInSight != null)
                endInSight
            else if (largestIncluded!! in validTangents)
                largestIncluded
            else if (largestExcluded!! in validTangents)
                largestExcluded
            else {
                val liT = largestIncluded.second as TangentOrSharedPoint.Tangent
                val iBeforeE = if (largestExcluded.first is CircleOrEndpoint.BCircle) {
                    val leC = largestExcluded.first as CircleOrEndpoint.BCircle
                    LineSegment(liT.start, liT.end).contour.intersections(leC.circle.contour).isEmpty()
                } else {
                    false
                }

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

    fun startEndCurve(bCircle: Circle, start: Boolean, one: Boolean): ShapeContour {
        val inters = bCircle.contour.intersections(interC)
        val bcClosest = interC.nearest(bCircle.center)

        val cIsInside = orientation(bcClosest.position, bcClosest.position + interC.direction(bcClosest.contourT), bCircle.center) == orient
        val d = bCircle.center.distanceTo(bcClosest.position)
        val cInside = 1 - (if (cIsInside) (gs.expandRadius - d) else d + gs.expandRadius) /
                ((1 + cds.pointClearance) * gs.expandRadius)
//            1.0 - bCircle.center.distanceTo(
//            if (ch.empty) chPoints[0] else ch.nearest(bCircle.center).position
//        )

        val smoothingRadius = min((1.1 * cInside).pow(4) + 0.1, 1.0) * gs.expandRadius

        if (start) {
            val a = inters.minByOrNull { it.b.contourT }
            val b = inters.maxByOrNull { it.b.contourT }
            val actualSmoothingRadius = min(smoothingRadius, (a?.position ?: bcClosest.position).distanceTo(interC.start))
            val circleA = Circle(a?.position ?: bcClosest.position, actualSmoothingRadius)
            val intersA = circleA.contour.intersections(interC)
            val fromA = if (intersA.size == 2)
                intersA.minBy { it.b.contourT }.let { it.position to it.b.contourT }
                else interC.start to 0.0
            val intersAB = circleA.contour.intersections(bCircle.contour)



            val toA = try {
                 intersAB.firstOrNull {
                    val nrst = interC.nearest(it.position)
                    val or = orientation(nrst.position, nrst.position + interC.direction(nrst.contourT), it.position)
                    or == orient &&
                            orientation(
                                bcClosest.position,
                                bCircle.center,
                                it.position
                            ) == orientation(bcClosest.position, bCircle.center, interC.start)
                } ?: return if (one) interC.sub(0.0, (a!!.b.contourT + b!!.b.contourT) / 2) else interC.sub(
                    0.0,
                    b!!.b.contourT
                )
            } finally {
                val comp = drawComposition {
                    fill = ColorRGBa.RED
                    circle(bCircle)

                    fill = ColorRGBa.GREEN
                    circles(gCircles)

                    fill = null
                    stroke = ColorRGBa.ORANGE
                    circle(circleA)

                    fill = null
                    stroke = ColorRGBa.BLACK
                    contour(interC)

                    fill = ColorRGBa.WHITE.opacify(0.4)
                    stroke = ColorRGBa.BLACK
                    circles(intersAB.map { it.position }, 3.0)

//                stroke = ColorRGBa.BLUE
//                contour(startHobby)
                }
                debug(comp, "debug-null")
            }

            val arc = Arc(bCircle, toA.position, LineSegment(bcClosest.position, bCircle.center).contour.intersections(bCircle.contour).firstOrNull()?.position ?: b!!.position)

            val startSegment = interC.sub(0.0, fromA.second)

            val startHobby = hobbyCurve(
                listOf(
                    fromA.first,
                    fromA.first + interC.direction(fromA.second) * 0.000001,
                    toA.position - arc.contour.direction(arc.contour.nearest(toA.position).contourT) * 0.000001,
                    toA.position
                )
            )

            return startSegment + startHobby
        } else {
            val a = inters.minByOrNull { it.b.contourT }
            val b = inters.maxByOrNull { it.b.contourT }
            val actualSmoothingRadius = min(smoothingRadius, (b?.position ?: bcClosest.position).distanceTo(interC.end))
            val circleB = Circle(b?.position ?: bcClosest.position, actualSmoothingRadius)
            val intersB = circleB.contour.intersections(interC)
            val toB = if (intersB.size == 2)
                intersB.maxBy { it.b.contourT }.let { it.position to it.b.contourT }
                else interC.end to 1.0

            val fromB = circleB.contour.intersections(bCircle.contour).firstOrNull {
                val nrst = interC.nearest(it.position)
                val or = orientation(nrst.position, nrst.position + interC.direction(nrst.contourT), it.position)
                or == orient &&
                        orientation(bcClosest.position, bCircle.center, it.position) == orientation(bcClosest.position, bCircle.center, interC.end)
            } ?: return if (one) interC.sub((a!!.b.contourT + b!!.b.contourT) / 2, 1.0) else interC.sub(a!!.b.contourT, 1.0)

            val arc = Arc(bCircle, LineSegment(bcClosest.position, bCircle.center).contour.intersections(bCircle.contour).firstOrNull()?.position ?: a!!.position, fromB.position)

            val endHobby = hobbyCurve(listOf(
                fromB.position,
                fromB.position + arc.contour.direction(arc.contour.nearest(fromB.position).contourT) * 0.000001,
                toB.first - interC.direction(toB.second) * 0.000001,
                toB.first
            ))

            val endSegment = interC.sub(toB.second, 1.0)

            val comp = drawComposition {
                fill = ColorRGBa.RED
                circle(bCircle)

                fill = null
                stroke = ColorRGBa.ORANGE
                circle(circleB)

                stroke = ColorRGBa.BLUE
                contour(endHobby)
            }
            debug(comp, "smoothing-debug-end")

            return endHobby + endSegment
        }
    }

    var final: ShapeContour = ShapeContour.EMPTY

    if (bCircles.size == 1) {
        val bCircle = bCircles.first()

        val startCurve = startEndCurve(bCircle, true, true)
        val endCurve = startEndCurve(bCircle, false, true)
        val arc = Arc(bCircle, startCurve.end, endCurve.start)

        val comp = drawComposition {
            stroke = ColorRGBa.BLUE
            contour(arc.contour)
        }
        debug(comp, "smoothing-debug-arc")

        final = startCurve
        if (startCurve.end.distanceTo(endCurve.start) > 1E-9)
            final += arc.contour
        final += endCurve

        val comp2 = drawComposition {
            stroke = ColorRGBa.BLUE
            contour(final)
        }
        debug(comp2, "smoothing-debug-final")

    } else if (seenCircles.any { !it.include }){
        val seenBCircles = seenCircles.filter { !it.include }
        val bCircleF = seenBCircles.first().circle
        val bCircleL = seenBCircles.last().circle

        val startCurve = startEndCurve(bCircleF, true, bCircleF == bCircleL)
        val endCurve = startEndCurve(bCircleL, false, bCircleF == bCircleL)

        val cas = seenCircles.indices.map { i ->
            val start = if (i == 0) startCurve.end else tngnts[i].end
            val end = if (i == seenCircles.lastIndex) endCurve.start else tngnts[i+1].start
            val (c, b) = seenCircles[i]
            if (end.squaredDistanceTo(start) < 0.01)
                ShapeContour.EMPTY
            else
                c.subVO(start, end, if (b) orient else orient.opposite())
        }

        final = startCurve
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
        final += endCurve
    }

    return final
}

//fun morphHighlight(highlight: Highlight, overlapees: List<Highlight>, cds: ComputeDrawingSettings): ShapeContour {
//    if (overlapees.isEmpty()) return highlight.contour
//
//    var piece = Shape(listOf(highlight.contour.open))
//    for (overlapee in overlapees) {
//        for (pt in overlapee.allPoints) {
//            piece = difference(piece, Circle(pt.pos, overlapee.circles[0].radius).contour)
//        }
//    }
//    val pieces = piece.contours.filter { it.length > 1.0 }.mergeAdjacent()
//    if (pieces.isEmpty()) return highlight.contour
//
//    if (pieces.size == 1 && pieces[0].start.squaredDistanceTo(pieces[0].end) < 1E-6) {
//        return highlight.contour
//    }
//
//    val broken = (pieces + pieces[0]).zipWithNext { c1, c2 ->
//        highlight.contour.subVC(c1.end, c2.start)
//    }
//
//    val expandRadius = highlight.circles[0].radius
//
//    val gCircles = highlight.allPoints.map {
//        Circle(it.pos, expandRadius)
//    }
//    val bCircles = overlapees.flatMap {
//        it.allPoints.map { Circle(it.pos, expandRadius) }
//    }
//    val circles = gCircles + bCircles
//    val r = circles[0].radius
//    val (gSmallerCircles, bSmallerCircles) = growCircles(gCircles.map { it.center }, bCircles.map { it.center },
//        r, r * cds.pointClearance)
//
//    var i = 0
//    val mends = broken.map { interC ->
//        i++
//        if (interC.segments.isEmpty()) ShapeContour.EMPTY else {
//            val relevantBCircles = bSmallerCircles.filterNot { it.radius == 0.0 || highlight.contour.intersection(it.shape).empty }
//            separatingCurve(
//                interC,
//                gSmallerCircles,
//                relevantBCircles,
//                cds.smoothingRadius
//            )
//        }
//    }
//
//    var result = ShapeContour.EMPTY
//    for (i in pieces.indices) {
//        result += pieces[i]
//        if (!mends[i].empty)
//            result += mends[i]
//    }
//
//    return result.close()
//}

fun breakCurve(c: ShapeContour, exclCircles: List<Circle>, gs: GeneralSettings)
    : Pair<List<ShapeContour>, List<ShapeContour>> {
    var piece = Shape(listOf(c))
    for (circle in exclCircles) {
        piece = difference(piece, circle.copy(radius = gs.expandRadius).contour)
    }

    val pieces = piece.contours.filter { it.length > 0.1 }.mergeAdjacent()

    fun ShapeContour.subV(t1: Double, v: Vector2): List<ShapeContour> {
        val t2 = nearest(v).contourT
        val cc = sub(t1, t2)
        return if (cc.length > 1E-6) return listOf(cc) else emptyList()
    }

    fun ShapeContour.subV(v: Vector2, t2: Double): List<ShapeContour> {
        val t1 = nearest(v).contourT
        val cc = sub(t1, t2)
        return if (cc.length > 1E-6) return listOf(cc) else emptyList()
    }

    val broken =
        if (pieces.isEmpty()) listOf(c) else
            c.subV(0.0, pieces.first().start) +
                    pieces.zipWithNext { c1, c2 ->
                        c.subV(c1.end, c2.start)
                    } + c.subV(pieces.last().end, 1.0)

    return pieces to broken
}

fun morphCurve(c: ShapeContour, inclCircles: List<Circle>, exclCircles: List<Circle>,
               gs: GeneralSettings, cds: ComputeDrawingSettings, debug: Debug): ShapeContour {
    if (exclCircles.isEmpty()) return c

    val (pieces, broken) = breakCurve(c, exclCircles, gs)

    val mends = broken.map { interC ->
        if (interC.segments.isEmpty()) ShapeContour.EMPTY else {
            // TODO: exclCircles should be filtered here! inclCircle maybe as well..
            val relevantExclCircles = exclCircles.filter {
                it.copy(radius = gs.expandRadius).contour.overlaps(interC)
            }
            val relevantInclCircles = inclCircles.filter {
                interC.distanceTo(it.center) <= 1.5 * gs.expandRadius
            }
            val inputIncl = relevantInclCircles.ifEmpty { listOf(inclCircles.minBy { interC.distanceTo(it.center) }) }
            val sepCurve = separatingCurve(interC, inputIncl, relevantExclCircles, gs, cds) { comp, s ->
                comp.draw {
                    fill = null
                    stroke = ColorRGBa.BLUE
                    contours(pieces)
                    stroke = ColorRGBa.RED
                    contours(broken)

                    stroke = ColorRGBa.BLACK
                    fill = ColorRGBa.GREEN
                    circles(inputIncl)

                    stroke = ColorRGBa.BLACK
                    fill = ColorRGBa.RED
                    circles(exclCircles)
                }
                debug(comp, s)
            }
            val comp = drawComposition {
                fill = null
                stroke = ColorRGBa.BLUE
                contours(pieces)
                stroke = ColorRGBa.RED
                contours(broken)

                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GREEN
                circles(inputIncl)

                stroke = ColorRGBa.BLACK
                fill = ColorRGBa.GREEN
                circles(exclCircles)
            }
//            debug(comp, "debug-morphCurve")
            sepCurve
        }
    }

    val comp = drawComposition {
        fill = null
        stroke = ColorRGBa.BLUE
        contours(pieces)
        stroke = ColorRGBa.RED
        contours(mends)
    }

    debug(comp, "pieces-mends")

    var result = ShapeContour.EMPTY
    val contourParts = (pieces + mends).filter { !it.empty }.sortedBy { c.nearest(it.start).contourT }

    for (p in contourParts) {
        result += p
    }

    val comp2 = drawComposition {
        fill = null
        stroke = ColorRGBa.BLUE
        contour(result)
    }

    debug(comp2, "result")

    return result
}