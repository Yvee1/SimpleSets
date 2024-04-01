import patterns.Island
import patterns.largestIslandAt
import patterns.p0
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LargestIslandKtTest {
    @ParameterizedTest
    @MethodSource("convexIslandInstances")
    fun testLargestConvexIslandAt(instance: PartitionInstance, expected: Island) {
        assertIslands(expected, instance.largestIslandAt(instance.points.maxBy { it.pos.x }, instance.points).original())
    }

    private fun assertIslands(expected: Island, actual: Island){
        assertEquals(expected.points.toSet(), actual.points.toSet())
        assertEquals(expected.weight, actual.weight)
    }

    private fun convexIslandInstances(): Stream<Arguments> {
        val pts0 = listOf(
            2 p0 0,
            0 p0 0,
            1 p0 1,
        )

        val pts1 = listOf(
            4 p0 0,
            0 p0 0,
            1 p0 2,
            2 p0 3,
            3 p0 2,
        )

        val pts2 = listOf(
            1 p0 0,
            0 p0 0,
            0 p0 1,
            1 p0 1,
        )

        val pts3 = listOf(
            0 p0 0,
            0 p0 1,
            0 p0 2,
            0 p0 3,
            0 p0 4,
            0 p0 5,
        )

        return Stream.of(
            Arguments.of(
                PartitionInstance(pts0),
                Island(pts0, pts0.size)
            ),
            Arguments.of(
                PartitionInstance(pts1),
                Island(pts1, pts1.size)
            ),
            Arguments.of(
                PartitionInstance(pts2),
                Island(pts2, pts2.size)
            ),
            Arguments.of(
                PartitionInstance(pts3),
                Island(pts3, pts3.size)
            ),
        )
    }
}