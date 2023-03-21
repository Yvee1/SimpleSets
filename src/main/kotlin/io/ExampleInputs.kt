package io

import ProblemInstance
import patterns.Pattern
import patterns.Point
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import java.io.File
import java.io.IOException
import kotlin.math.pow

enum class ExampleInput {
    LowerBound, NYC, FiveColors
}

fun getExampleInput(e: ExampleInput): List<Point> =
    when (e){
        ExampleInput.LowerBound -> {
            val scale = 50.0
            val center = Vector2(100.0, 100.0)
            val pts = mutableListOf<Point>()
            val l = 3
            val k = 2.toDouble().pow(l).toInt()
            for (i in 0 until k){
                pts.add(Point(Vector2(center.x - scale/4, center.y + i * scale), 0))
                pts.add(Point(Vector2(center.x + scale/4, center.y + i * scale), 0))
            }
            fun clusterAt(v: Vector2, n: Int) = Circle(v, scale/10.0).contour.equidistantPositions(n).dropLast(1)
            // 0001    1    0
            // 0010    2    1
            // 0011    3    0
            // 0100    4    2
            // 0101    5    0
            // 0110    6    1
            // 0111    7    0
            // 1000    8    3
            // 1001    9    0
            // 1010    10   1
            // 1011    11   0
            // 1100    12   2
            // 1101    13   0
            // 1110    14   1
            // 1111    15   0
            for (i in 1 until k){
                val layer = i.toString(2).reversed().toList().takeWhile { it == '0' }.size
                val n = if (layer == 0) 3 else 4.toDouble().pow(layer-1).toInt() * 7

                (clusterAt(Vector2(center.x - scale/2, 100.0 - scale/2 + i * scale), n)
                        + clusterAt(Vector2(center.y + scale/2, 100.0 - scale/2 + i * scale), n)).forEach { p ->
                    pts.add(Point(p, 1))
                }
            }

            pts
        }
        ExampleInput.NYC -> {
            val f = File("nyc.ipe")
            ipeToPoints(f)
        }
        ExampleInput.FiveColors -> {
            val f = File("5-colors.ipe")
            ipeToPoints(f)
        }
    }


fun writeToIpe(instance: ProblemInstance, solution: List<Pattern>, fileName: String) {
    val colors = listOf("CB light blue", "CB light red", "CB light green")

    val file = File(fileName)

    try {
        val s = ipeDraw(colors) {
            for (pat in solution){
                pattern(pat, instance.expandRadius)
            }
            for (p in instance.points){
                point(p)
            }
        }
        file.writeText(s)
    } catch (e: IOException) {
        println("Could not write to output file!")
        e.printStackTrace()
    }
}

fun writeToIpe(points: List<Point>, fileName: String) {
    val colors = listOf("CB light blue", "CB light red", "CB light green", "CB light orange", "CB light purple")

    val file = File(fileName)

    try {
        val s = ipeDraw(colors) {
            for (p in points){
                point(p)
            }
        }
        file.writeText(s)
    } catch (e: IOException) {
        println("Could not write to output file!")
        e.printStackTrace()
    }
}
