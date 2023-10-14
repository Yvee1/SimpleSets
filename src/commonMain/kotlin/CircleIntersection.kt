import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.shape.intersection

fun main() = application {
    configure {
        width = 800
        height = 800
    }

    program {
        val c = Circle(width/2.0, height/2.0, 150.0)
        val r = Rectangle.fromCenter(drawer.bounds.center, 200.0, 400.0)
        val inter = c.shape.intersection(r.shape)
        println(inter.area)

        extend {
            drawer.apply {
                clear(ColorRGBa.WHITE)
                stroke = ColorRGBa.BLACK

                circle(c)
                rectangle(r)

                fill = ColorRGBa.RED
                shape(inter)
            }
        }
    }
}