import org.openrndr.shape.LineSegment

fun <K> Map<K, Boolean>.getF(key: K) = getOrDefault(key, false)

class CapsuleData(points: List<Point>, radius: Double) {
    val capsule: Map<Pair<Point, Point>, Boolean>

    init {
        capsule = buildMap {
            for (p in points) {
                for (q in points) {
                    if (p.type != q.type) continue
                    val segContour = LineSegment(p.pos, q.pos)
                    var found = false
                    for (r in points) {
                        if (r != p && r != q && r.type != p.type &&
                            r.pos.squaredDistanceTo(segContour.nearest(r.pos)) <= radius * radius) {
                            found = true
                            break
                        }
                    }
                    put(p to q, found)
                }
            }
        }
    }
}