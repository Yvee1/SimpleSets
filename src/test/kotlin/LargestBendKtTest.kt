import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.openrndr.math.Vector2
import java.util.stream.Stream
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LargestBendKtTest {
    @ParameterizedTest
    @MethodSource("monotoneBendInstances")
    fun testLargestMonotoneBend(instance: ProblemInstance, dir: Orientation, expected: Bend) {
        assertEquals(expected, instance.largestMonotoneBendFrom(instance.points[0], instance.points[1], dir).original())
    }

    private fun monotoneBendInstances(): Stream<Arguments> {
        data class Input(val points: List<Point>, val expected: List<Point>)
        fun List<Point>.flip() = map { it.copy(pos=Vector2(-it.pos.x, it.pos.y)) }

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

        val inputs = listOf(
            Input(pts1, pts1),
            Input(pts2, pts2),
            Input(pts3, pts3),
            Input(pts4, pts4.subList(0, pts4.size-4)),
        )

        return listOf(Orientation.LEFT, Orientation.RIGHT).flatMap { dir ->
            inputs.map { input ->
                Arguments.of(
                    ProblemInstance(if (dir == Orientation.RIGHT) input.points else input.points.flip()),
                    dir,
                    Bend(if (dir == Orientation.RIGHT) input.expected else input.expected.flip(), input.expected.size)
                )
            }
        }.stream()
    }

    @ParameterizedTest
    @MethodSource("inflectionBendInstances")
    fun testLargestInflectionBend(instance: ProblemInstance, expected: Bend) {
        assertEquals(expected, instance.largestInflectionBend(Orientation.RIGHT).original())
    }

    private fun inflectionBendInstances(): Stream<Arguments> {
        val pts1 = listOf(
            0 p 0,
            1 p 2,
            1.5 p 2.5,
            3 p 3,
            4.5 p 3.5,
            5 p 4,
            6 p 6,
        )

        return Stream.of(
            Arguments.of(
                ProblemInstance(pts1),
                Bend(pts1, pts1.size)
            ),
        )
    }

    @Test
    fun firstClockwiseHitEmpty() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(null, firstHit(emptyList(), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstClockwiseHitStraight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 2).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(0, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstClockwiseHitRight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 1).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(0, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstClockwiseHitLeft() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(null, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstClockwiseHitMultiple() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 3 p 1, 2 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(1, firstHit(pts.subList(2, pts.size).asReversed(), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstClockwiseHitAnother() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 0, 2 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(1, firstHit(pts.subList(2, pts.size).asReversed(), pts[0], pts[1], pts[1], Orientation.RIGHT))
        }
    }

    @Test
    fun firstCClockwiseHitEmpty() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(null, firstHit(emptyList(), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun firstCClockwiseHitStraight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 2).map { Point(it.pos.rotate(i.toDouble() / 360.0), it.type) }
            assertEquals(0, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun firstCClockwiseHitRight() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 1).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(null, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun firstCClockwiseHitLeft() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 1 p 2).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(0, firstHit(listOf(pts[2]), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun firstCClockwiseHitMultiple() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 1, 1 p 2, 1 p 3).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(1, firstHit(pts.subList(2, pts.size), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun firstCClockwiseHitAnother() {
        repeat(360) { i ->
            val pts = listOf(0 p 0, 1 p 1, 2 p 1, 1 p 2, 1 p 3).map { Point(it.pos.rotate(i.toDouble()), it.type) }
            assertEquals(1, firstHit(pts.subList(2, pts.size), pts[0], pts[1], pts[1], Orientation.LEFT))
        }
    }

    @Test
    fun constrainedBendRightAngleAllowed() {
        val pts = listOf(0 p 0, 0 p 1, 1 p 1)
        val instance = ProblemInstance(pts, maxTurningAngle = 100.0)
        assertEquals(Bend(instance.points, 3), instance.largestMonotoneBendFrom(instance.points[0], instance.points[1], Orientation.RIGHT))
    }

    @Test
    fun constrainedBendRightAngleRestricted() {
        val pts = listOf(0 p 0, 0 p 1, 1 p 1)
        val instance = ProblemInstance(pts, maxTurningAngle = 50.0)
        assertEquals(Bend(instance.points.subList(0, 2), 2), instance.largestMonotoneBendFrom(instance.points[0], instance.points[1], Orientation.RIGHT))
    }

    @Test
    fun constrainedBendInflection() {
        val pts = listOf(291.2 p 67.3, 305.6 p 74.1, 322.6 p 73.2, 335.3 p 67.2,
            348.5 p 63.1, 363.4 p 60.2, 378.2 p 61.7, 393.2 p 65.9)
        val instance = ProblemInstance(pts, maxTurningAngle = 150.0)
        assertEquals(Bend(instance.points, instance.points.size), instance.largestInflectionBend(Orientation.RIGHT))
    }
}