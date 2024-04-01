import highlights.Highlight
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import patterns.Pattern
import patterns.Point
import patterns.SinglePoint

fun Drawer.highlightContour(h: Highlight, gs: GeneralSettings, ds: DrawSettings) {
    fill = ds.colors[h.type].toColorRGBa().whiten(ds.whiten)
    stroke = ColorRGBa.BLACK
    strokeWeight = ds.contourStrokeWeight(gs)
    contour(h.contour)
}

fun Drawer.coloredPoints(points: List<Point>, gs: GeneralSettings, ds: DrawSettings) {
    for (p in points) {
        fill = ds.colors[p.type].toColorRGBa()
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.pointStrokeWeight(gs)
        circle(p.pos, gs.pSize)
    }
}

fun Drawer.patternContour(p: Pattern, gs: GeneralSettings, ds: DrawSettings) {
    if (p !is SinglePoint) {
        fill = ds.colors[p.type].toColorRGBa().whiten(ds.whiten)
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.contourStrokeWeight(gs)
        contour(p.contour)
    }
}

fun Drawer.pattern(p: Pattern, gs: GeneralSettings, ds: DrawSettings) {
    patternContour(p, gs, ds)
    coloredPoints(p.points, gs, ds)
}

fun Drawer.highlight(h: Highlight, gs: GeneralSettings, ds: DrawSettings) {
    highlightContour(h, gs, ds)
    coloredPoints(h.points, gs, ds)
}

fun CompositionDrawer.highlightContour(h: Highlight, gs: GeneralSettings, ds: DrawSettings) {
//    fill = ds.colorSettings.lightColors[h.type].toColorRGBa().whiten(ds.whiten)
    fill = (ds.colors.getOrNull(h.type)?.toColorRGBa() ?: ColorRGBa.WHITE).whiten(ds.whiten)
    stroke = ColorRGBa.BLACK
    strokeWeight = ds.contourStrokeWeight(gs)
    contour(h.contour)
}

fun CompositionDrawer.coloredPoints(points: List<Point>, gs: GeneralSettings, ds: DrawSettings) {
    for (p in points) {
        fill = ds.colors.getOrNull(p.type)?.toColorRGBa() ?: ColorRGBa.WHITE
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.pointStrokeWeight(gs)
        circle(p.pos, gs.pSize)
    }
}

fun CompositionDrawer.patternContour(p: Pattern, gs: GeneralSettings, ds: DrawSettings) {
    if (p !is SinglePoint) {
        fill = ds.colors.getOrNull(p.type)?.toColorRGBa() ?: ColorRGBa.WHITE
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.contourStrokeWeight(gs)
        contour(p.contour)
    }
}

fun CompositionDrawer.pattern(p: Pattern, gs: GeneralSettings, ds: DrawSettings) {
    patternContour(p, gs, ds)
    coloredPoints(p.points, gs, ds)
}

fun CompositionDrawer.highlight(h: Highlight, gs: GeneralSettings, ds: DrawSettings) {
    highlightContour(h, gs, ds)
    coloredPoints(h.points, gs, ds)
}

fun ColorRGBa.whiten(factor: Double) = mix(ColorRGBa.WHITE, factor)