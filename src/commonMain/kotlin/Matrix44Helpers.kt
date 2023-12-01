import org.openrndr.math.IntVector2
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import patterns.Point

operator fun Matrix44.times(v: Vector2) = (this * v.xy01).xy
operator fun Matrix44.times(v: IntVector2) = this * v.vector2

operator fun Matrix44.times(p: Point) = p.copy(pos = this * p.pos)