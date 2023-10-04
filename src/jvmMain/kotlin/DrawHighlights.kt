import highlights.Highlight
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer

fun Drawer.highlightContour(h: Highlight, ds: DrawSettings) {
    fill = ds.colorSettings.lightColors[h.type].toColorRGBa().whiten(ds.whiten)
    stroke = ColorRGBa.BLACK
    strokeWeight = ds.contourStrokeWeight
    contour(h.contour)
}

fun Drawer.highlightPoints(h: Highlight, ds: DrawSettings) {
    fill = ds.colorSettings.lightColors[h.type].toColorRGBa()
    stroke = ColorRGBa.BLACK
    strokeWeight = ds.pointStrokeWeight
    circles(h.allPoints.map { it.pos }, ds.pSize)
}

fun Drawer.highlight(h: Highlight, ds: DrawSettings) {
    highlightContour(h, ds)
    highlightPoints(h, ds)
}

fun ColorRGBa.whiten(factor: Double) = mix(ColorRGBa.WHITE, factor)