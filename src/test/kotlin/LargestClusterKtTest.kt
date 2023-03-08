import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LargestClusterKtTest {
    @ParameterizedTest
    @MethodSource("convexIslandInstances")
    fun testLargestConvexIslandAt(instance: ProblemInstance, expected: Cluster) {
        assertIslands(expected, instance.largestClusterAt(instance.points.maxBy { it.pos.x }, instance.points).original())
    }

    private fun assertIslands(expected: Cluster, actual: Cluster){
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
                Cluster(pts0, pts0.size)
            ),
            Arguments.of(
                ProblemInstance(pts1),
                Cluster(pts1, pts1.size)
            ),
            Arguments.of(
                ProblemInstance(pts2),
                Cluster(pts2, pts2.size)
            ),
            Arguments.of(
                ProblemInstance(pts3),
                Cluster(pts3, pts3.size)
            ),
        )
    }
}