package patterns

import org.openrndr.math.Vector2

data class Point(val pos: Vector2, val type: Int, val originalPoint: Point? = null) {
    override fun toString(): String {
        return "(${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)})"
    }
}

infix fun Double.v(y: Double) = Vector2(this, y)
infix fun Int.v(y: Int) = Vector2(this.toDouble(), y.toDouble())
infix fun Double.p(y: Double) = Point(Vector2(this, y), 0)
infix fun Int.p(y: Int) = Point(Vector2(this.toDouble(), y.toDouble()), 0)