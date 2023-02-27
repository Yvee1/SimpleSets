import org.openrndr.math.Vector2

data class Point(val pos: Vector2, val type: Int, val originalPoint: Point? = null){
    override fun toString(): String {
        return "(${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)})"
    }
}