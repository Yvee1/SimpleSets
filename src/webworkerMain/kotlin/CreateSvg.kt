import highlights.toHighlight
import org.openrndr.shape.*

fun createSvg(gs: GeneralSettings, cds: ComputeDrawingSettings, ds: DrawSettings,
              filtration: List<Pair<Double, Partition>>, cover: Double): String =
    drawComposition(CompositionDimensions(0.0.pixels, 0.0.pixels, 800.0.pixels, 800.0.pixels)) {

        val partition = filtration.takeWhile { it.first < cover * gs.expandRadius }.lastOrNull()?.second
            ?: return@drawComposition
        val highlights = partition.patterns.map { it.toHighlight(gs.expandRadius) }
        val xGraph = XGraph(highlights, gs, cds)
        xGraph.draw(this, ds)
        coloredPoints(partition.points, gs, ds)
    }.toSVG()