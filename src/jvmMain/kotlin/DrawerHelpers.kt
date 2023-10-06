import highlights.Highlight
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import patterns.Pattern
import patterns.Point
import patterns.SinglePoint

fun Drawer.highlightContour(h: Highlight, ds: DrawSettings) {
    fill = ds.colorSettings.lightColors[h.type].toColorRGBa().whiten(ds.whiten)
    stroke = ColorRGBa.BLACK
    strokeWeight = ds.contourStrokeWeight
    contour(h.contour)
}

fun Drawer.coloredPoints(points: List<Point>, ds: DrawSettings) {
    for (p in points) {
        fill = ds.colorSettings.lightColors[p.type].toColorRGBa()
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.pointStrokeWeight
        circle(p.pos, ds.pSize)
    }
}

fun Drawer.patternContour(p: Pattern, ds: DrawSettings) {
    if (p !is SinglePoint) {
        fill = ds.colorSettings.lightColors[p.type].toColorRGBa().whiten(ds.whiten)
        stroke = ColorRGBa.BLACK
        strokeWeight = ds.contourStrokeWeight
        contour(p.contour)
    }
}

fun Drawer.pattern(p: Pattern, ds: DrawSettings) {
    patternContour(p, ds)
    coloredPoints(p.points, ds)
}

fun Drawer.highlight(h: Highlight, ds: DrawSettings) {
    highlightContour(h, ds)
    coloredPoints(h.points, ds)
}

fun ColorRGBa.whiten(factor: Double) = mix(ColorRGBa.WHITE, factor)