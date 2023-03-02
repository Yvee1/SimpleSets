import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LargestConvexIslandKtTest {
    @ParameterizedTest
    @MethodSource("convexIslandInstances")
    fun testLargestConvexIslandAt(instance: ProblemInstance, expected: ConvexIsland) {
        assertIslands(expected, instance.largestConvexIslandAt(instance.points.maxBy { it.pos.x }, instance.points).original())
    }

    private fun assertIslands(expected: ConvexIsland, actual: ConvexIsland){
        assertEquals(expected.points.toSet(), actual.points.toSet())
        assertEquals(expected.weight, actual.weight)
    }

    private fun convexIslandInstances(): Stream<Arguments> {
        val pts0 = listOf(
            2 p 0,
            0 p 0,
            1 p 1,
        )

        val pts1 = listOf(
            4 p 0,
            0 p 0,
            1 p 2,
            2 p 3,
            3 p 2,
        )

        val pts2 = listOf(
            1 p 0,
            0 p 0,
            0 p 1,
            1 p 1,
        )

        val pts3 = listOf(
            0 p 0,
            0 p 1,
            0 p 2,
            0 p 3,
            0 p 4,
            0 p 5,
        )

        return Stream.of(
            Arguments.of(
                ProblemInstance(pts0),
                ConvexIsland(pts0, pts0.size)
            ),
            Arguments.of(
                ProblemInstance(pts1),
                ConvexIsland(pts1, pts1.size)
            ),
            Arguments.of(
                ProblemInstance(pts2),
                ConvexIsland(pts2, pts2.size)
            ),
            Arguments.of(
                ProblemInstance(pts3),
                ConvexIsland(pts3, pts3.size)
            ),
        )
    }
}