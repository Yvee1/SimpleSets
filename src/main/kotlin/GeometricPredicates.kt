import org.openrndr.math.Matrix33
import org.openrndr.math.Vector2
import kotlin.math.abs
import kotlin.math.sign

const val PRECISION = 1e-9

enum class Orientation {
    LEFT, STRAIGHT, RIGHT;

    fun opposite(): Orientation = when(this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        STRAIGHT -> STRAIGHT
    }
}

fun orientation(p: Vector2, q: Vector2, r: Vector2): Orientation {
    val d = Matrix33(1.0, p.x, p.y,
                     1.0, q.x, q.y,
                     1.0, r.x, r.y).determinant

    return if (abs(d) <= PRECISION) {
        Orientation.STRAIGHT
    } else {
        if (d.sign > 0){
            Orientation.LEFT
        } else {
            Orientation.RIGHT
        }
    }
}

fun compare(x1: Double, x2: Double): Orientation {
    val d = x1 - x2
    return if (abs(d) <= PRECISION){
        Orientation.STRAIGHT
    } else {
        if (d.sign < 0){
            Orientation.LEFT
        } else {
            Orientation.RIGHT
        }
    }
}