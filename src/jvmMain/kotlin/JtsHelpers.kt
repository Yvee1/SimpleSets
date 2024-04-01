import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.shape.CubicBezierCurve
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.*

fun Vector2.toCoordinate() = Coordinate(x, y)

fun Coordinate.toVector2() = Vector2(x, y)

fun ShapeContour.toJtsGeometry(): Geometry {
    val gf = GeometryFactory()
    val pts = segments.map { it.start } + segments.last().end
    val coords = pts.map { it.toCoordinate() }.toTypedArray()
//    return if (closed) {
//        gf.createPolygon(gf.createLinearRing(coords + coords.first()), emptyList<LinearRing>().toTypedArray())
//    } else {
        val controls = segments.flatMap { it.cubic.control.toList() }.map { it.toCoordinate() }.toTypedArray()
        return CubicBezierCurve.bezierCurve(gf.createLineString(coords), gf.createLineString(controls))
//    }
}

fun ShapeContour.toJtsPolygon(): Polygon {
    val gf = GeometryFactory()
    val pts = segments.map { it.start } + segments.last().end
    val coords = pts.map { it.toCoordinate() }.toTypedArray()
    return gf.createPolygon(gf.createLinearRing(coords + coords.first()), emptyList<LinearRing>().toTypedArray())
}

fun LinearRing.toShapeContour() = ShapeContour.fromPoints(coordinates.map { it.toVector2() }, closed = true)

fun Geometry.toShape(): Shape =
    when(this) {
        is Polygon -> exteriorRing.toShapeContour().shape
        is MultiPolygon -> Shape(List(numGeometries) { getGeometryN(it).toShape().contours }.flatten())
        else -> error("Unknown geometry")
    }

fun ShapeContour.buffer(r: Double) = toJtsGeometry().buffer(r).toShape().contours.first()

fun ShapeContour.polyNegativeBuffer(r: Double) = toJtsPolygon().buffer(r).toShape().contours.first()

fun ShapeContour.smooth(r: Double) = toJtsGeometry().buffer(r).buffer(-r).toShape().contours.first()
//
//fun main() = application {
//    program {
//        val pts = listOf(Vector2(100.0, 200.0), Vector2(250.0, 350.0), Vector2(350.0, 350.0))
//        val c = ShapeContour.fromPoints(pts, closed=false)
//        val l = LineSegment(Vector2(250.0, 350.0), Vector2(250.0, 250.0)).contour
//        val curve = contour {
//            moveTo(250.0, 50.0)
//            curveTo(270.0, 90.0, 310.0, 40.0, 400.0, 175.0)
//        }
//        val square = Rectangle(Vector2(300.0, 25.0), 50.0, 150.0).contour
//        val g = c.toJtsGeometry().union(l.toJtsGeometry()).union(curve.toJtsGeometry()).union(square.toJtsGeometry())
//        val b = g.buffer(30.0).buffer(-10.0)
//        println(b)
//
//        extend {
//            drawer.apply {
//                clear(ColorRGBa.WHITE)
//                stroke = ColorRGBa.BLACK
//                fill = ColorRGBa.BLUE.opacify(0.3)
//                contour(curve)
//                contour(c)
//                contour(l)
//                contour(square)
//                contours(b.toShape().contours)
//            }
//        }
//    }
//}
