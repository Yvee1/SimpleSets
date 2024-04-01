import geometric.PRECISION
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.openrndr.math.Vector2
import patterns.coverRadiusVoronoi
import patterns.v
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IslandTest {
    @ParameterizedTest
    @MethodSource("triangles")
    fun coverRadiusTriangle(p: Vector2, q: Vector2, r: Vector2) {
        assertTrue {
            println("Expected: ${coverRadiusVoronoi(listOf(p, q, r))}")
            println("Actual: ${patterns.coverRadiusTriangle(p, q, r)}")
            abs(patterns.coverRadiusTriangle(p, q, r) - coverRadiusVoronoi(listOf(p, q, r))) < PRECISION
        }
    }

    private fun triangles(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                0 v 0,
                1 v 0,
                0.5 v 0.866,
            ),
            Arguments.of(
                0 v 0,
                1 v 0,
                1 v 1,
            ),
            Arguments.of(
                0 v 0,
                10 v 0,
                3 v 1,
            )
        )
    }
}