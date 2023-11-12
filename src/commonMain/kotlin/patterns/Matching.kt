package patterns

import GeneralSettings
import org.openrndr.math.Vector2
import org.openrndr.math.asRadians
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contains
import kotlin.math.max

data class Matching(val point1: Point, val point2: Point) : Pattern() {
    override val weight = 2
    override val contour = ShapeContour.fromPoints(listOf(point1.pos, point2.pos), true)
    override val type = point1.type
    override val points = listOf(point1, point2)
    override val vecs = listOf(point1.pos, point2.pos)
    override val boundaryPoints = points
    override val segments: List<LineSegment>
        get() = listOf(LineSegment(point1.pos, point2.pos))
    override val coverRadius = point1.pos.distanceTo(point2.pos) / 2
    override fun original() = Matching(point1.originalPoint ?: point1, point2.originalPoint ?: point2)
    override fun isEmpty() = false
    override operator fun contains(v: Vector2) = v in contour
    override fun isValid(gs: GeneralSettings) = true

    fun extensionStart(p: Point, gs: GeneralSettings): Pair<Double, Bank>? {
        val angle = angleBetween(point1.pos - point2.pos, p.pos - point1.pos)
        if (angle > gs.maxTurningAngle.asRadians) return null
        if (angle > gs.maxBendAngle.asRadians) return null
//        println("Strange things incoming!!")
//        println("Any moment now")
//        val ptList = listOf(p, point1, point2)
//        println("Normal point list: $ptList")
//        val wat = Bank(ptList)
//        println("Good now: ${wat}")
        val huh = point1.pos.distanceTo(p.pos) / 2 to Bank(listOf(p, point1, point2))
        return huh

    }

    fun extensionEnd(p: Point, gs: GeneralSettings): Pair<Double, Bank>? {
        val angle = angleBetween(point2.pos - point1.pos, p.pos - point2.pos)
        if (angle > gs.maxTurningAngle.asRadians) return null
        if (angle > gs.maxBendAngle.asRadians) return null
        return point2.pos.distanceTo(p.pos) / 2 to Bank(listOf(point1, point2, p))
    }

    fun extension(p: Point, gs: GeneralSettings): Pair<Double, Bank>? {
        return listOfNotNull(extensionStart(p, gs), extensionEnd(p, gs)).minByOrNull { it.first }
    }

    fun toBank(): Bank {
        return Bank(points)
    }
}