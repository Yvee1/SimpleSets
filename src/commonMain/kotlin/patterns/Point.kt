package patterns

import org.openrndr.math.Vector2
import kotlin.math.roundToInt

data class Point(val pos: Vector2, val type: Int, val originalPoint: Point? = null) {
    override fun toString(): String {
        return "(${pos.x.roundToDecimals(1)}, ${pos.y.roundToDecimals(1)})"
    }
}

// https://stackoverflow.com/questions/61225315/is-there-a-way-in-kotlin-multiplatform-to-format-a-float-to-a-number-of-decimal
fun Double.roundToDecimals(decimals: Int): Double {
    var dotAt = 1
    repeat(decimals) { dotAt *= 10 }
    val roundedValue = (this * dotAt).roundToInt()
    return (roundedValue / dotAt) + (roundedValue % dotAt).toDouble() / dotAt
}

infix fun Double.v(y: Double) = Vector2(this, y)
infix fun Int.v(y: Int) = Vector2(this.toDouble(), y.toDouble())
infix fun Double.p(y: Double) = Point(Vector2(this, y), 0)
infix fun Int.p(y: Int) = Point(Vector2(this.toDouble(), y.toDouble()), 0)