package geometric

import patterns.Point

fun convexHull(points: List<Point>): List<Point> {
    if (points.size <= 1) return points
    val hull = mutableListOf<Point>()
    val sorted = points.sortedWith(compareBy({ it.pos.x }, { it.pos.y }))

    fun rightTurn(p: Point, q: Point, r: Point) =
        orientation(p.pos, q.pos, r.pos) == Orientation.RIGHT

    for (p in sorted) {
        while (hull.size >= 2 && !rightTurn(hull[hull.size-2], hull.last(), p)) {
            hull.removeLast()
        }
        hull.add(p)
    }

    val t = hull.size + 1
    for (i in sorted.size - 2 downTo 0) {
        val p = sorted[i]
        while (hull.size >= t && !rightTurn(hull[hull.size-2], hull.last(), p)) {
            hull.removeLast()
        }
        hull.add(p)
    }

    hull.removeLast()
    return hull
}
