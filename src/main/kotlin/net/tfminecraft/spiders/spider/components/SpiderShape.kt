package net.tfminecraft.spiders.spider.components

import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.configuration.BodyPlan
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Vector3f

/** A drawn block: the unit cube mapped by [transform], placed relative to [origin]. */
class ShapePiece(val origin: Vector, val transform: Matrix4f)

/** Every block the spider is drawn with, placed exactly as renderSpider places them. */
fun SpiderBody.shapePieces(plan: BodyPlan): List<ShapePiece> {
    val pieces = mutableListOf<ShapePiece>()

    val bodyTransform = Matrix4f().rotate(orientation)
    for (piece in plan.bodyModel.pieces) pieces += ShapePiece(position, Matrix4f(bodyTransform).mul(piece.transform))

    for ((legIndex, leg) in legs.withIndex()) {
        val rotations = leg.ik.segmentModelRotations
        for (segmentIndex in rotations.indices) {
            val segment = plan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue
            val segmentTransform = Matrix4f().rotate(rotations[segmentIndex])
            val parent = leg.ik.nodes[segmentIndex].position
            for (piece in segment.model.pieces) pieces += ShapePiece(parent, Matrix4f(segmentTransform).mul(piece.transform))
        }
    }
    return pieces
}

/** How far the spider's drawn shape can reach from its body position: body plus outstretched legs. */
fun BodyPlan.reach(): Double {
    val legReach = legs.maxOfOrNull { leg -> leg.attachmentPosition.length() + leg.segments.sumOf { it.length } } ?: 0.0
    return maxOf(legReach, BODY_REACH * scale)
}

private const val BODY_REACH = 2.2

/** Where along [from]→[to] (0..1) the segment first enters any piece, and that point; null if it misses. */
fun firstHit(pieces: List<ShapePiece>, from: Vector, to: Vector): Pair<Double, Vector>? {
    var best = Double.MAX_VALUE
    for (piece in pieces) {
        // into the piece's unit cube, where the slab test is simple
        val inverse = Matrix4f(piece.transform)
        if (inverse.determinant() == 0f) continue
        inverse.invert()
        val start = inverse.transformPosition(Vector3f(
            (from.x - piece.origin.x).toFloat(), (from.y - piece.origin.y).toFloat(), (from.z - piece.origin.z).toFloat()))
        val end = inverse.transformPosition(Vector3f(
            (to.x - piece.origin.x).toFloat(), (to.y - piece.origin.y).toFloat(), (to.z - piece.origin.z).toFloat()))
        val t = segmentEntersUnitCube(start, end) ?: continue
        if (t < best) best = t
    }
    if (best == Double.MAX_VALUE) return null
    return best to from.clone().add(to.clone().subtract(from).multiply(best))
}

/** Slab test against [0,1]³; returns the entry parameter in 0..1, or 0 when it starts inside. */
private fun segmentEntersUnitCube(start: Vector3f, end: Vector3f): Double? {
    var enter = 0.0
    var exit = 1.0
    for (axis in 0..2) {
        val s = start[axis].toDouble()
        val d = end[axis].toDouble() - s
        if (kotlin.math.abs(d) < 1e-12) {
            if (s < 0.0 || s > 1.0) return null
            continue
        }
        var t0 = (0.0 - s) / d
        var t1 = (1.0 - s) / d
        if (t0 > t1) t0 = t1.also { t1 = t0 }
        enter = maxOf(enter, t0)
        exit = minOf(exit, t1)
        if (enter > exit) return null
    }
    return enter
}
