import geometric.Orientation
import patterns.Point
import patterns.compareAround
import patterns.p
import org.junit.jupiter.api.Assertions.*
import org.openrndr.math.Vector2
import kotlin.test.Test

internal class StripeDataTest {
    @Test
    fun clockwiseAroundTest() {
        repeat(360) { i ->
            val pts = listOf(3 p 0, 4 p 1, 5 p 2, 6 p 3, 5 p 4, 4 p 5, 3 p 6).map { Point(it.pos.rotate(i.toDouble(), origin=Vector2(3.0, 3.0)), it.type) }
            assertEquals(pts, pts.sortedWith(compareAround(3 p 3, 180.0 + i, Orientation.RIGHT)))
        }
    }
}