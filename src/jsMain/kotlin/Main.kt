import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.difference
import web.dom.document
import react.create
import react.dom.client.createRoot

fun main() {
    val container = document.getElementById("root") ?: error("Couldn't find root container!")
    createRoot(container).render(App.create())
}

fun fakeMain() {
    val c = Circle(Vector2.ZERO, 100.0)
    val pts = c.contour.equidistantPositions(100000)
    val c2 = ShapeContour.fromPoints(pts, closed = true)
    println(c.shape.difference(c2.shape))
}