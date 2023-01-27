import org.openrndr.math.Vector2

data class Point(val pos: Vector2, val type: Int, val originalPoint: Point? = null){
    override fun toString(): String {
        return "(${pos.x.toInt()}, ${pos.y.toInt()})"
    }
}