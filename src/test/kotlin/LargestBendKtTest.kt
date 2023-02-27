import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LargestBendKtTest {
    @ParameterizedTest
    @MethodSource("bendInstances")
    fun testLargestBend(instance: ProblemInstance, expected: Bend) {
        assertEquals(expected, instance.computeLargestCwBendFrom(instance.points[0], instance.points[1]).original())
    }

    private fun bendInstances(): Stream<Arguments> {
        val pts1 = listOf(
            0 p 0,
            1 p 2,
            2 p 3,
            3 p 2,
            4 p 0,
        )

        val pts2 = listOf(
            0 p 1,
            1 p 1,
            1 p 0,
            0 p 0,
        )

        val pts3 = listOf(
            0 p 0,
            0 p 1,
            0 p 2,
            0 p 3,
            0 p 4,
            0 p 5,
        )

        val pts4 = listOf(
            0 p 0,
            0 p 1,
            0 p 2,
            1 p 2,
            2 p 2,
            2 p 1,
            2 p 0,

            1 p 0,
            1 p 1,
            -1 p 2,
            3 p 2,
        )

        return Stream.of(
            Arguments.of(
                ProblemInstance(pts1),
                Bend(pts1, pts1.size)
            ),
            Arguments.of(
                ProblemInstance(pts2),
                Bend(pts2, pts2.size)
            ),
            Arguments.of(
                ProblemInstance(pts3),
                Bend(pts3, pts3.size)
            ),
            Arguments.of(
                ProblemInstance(pts4),
                Bend(pts4.subList(0, pts4.size-4), pts4.size-4)
            )
        )
    }

    @Test
    fun firstClockwiseHitEmpty() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(null, firstClockwiseHit(emptyList(), pts[0], pts[1], pts[1]))
        }
    }

    @Test
    fun firstClockwiseHitStraight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 2).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(0, firstClockwiseHit(listOf(pts[2]), pts[0], pts[1], pts[1]))
        }
    }

    @Test
    fun firstClockwiseHitRight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 1).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(0, firstClockwiseHit(listOf(pts[2]), pts[0], pts[1], pts[1]))
        }
    }

    @Test
    fun firstClockwiseHitLeft() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(null, firstClockwiseHit(listOf(pts[2]), pts[0], pts[1], pts[1]))
        }
    }

    @Test
    fun firstClockwiseHitMultiple() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 3 p 1, 2 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(1, firstClockwiseHit(pts.subList(2, pts.size), pts[0], pts[1], pts[1]))
        }
    }
}