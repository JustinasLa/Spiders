package net.tfminecraft.tfmcspiders.spider.components

import net.tfminecraft.tfmcspiders.spider.components.body.SpiderBody
import net.tfminecraft.tfmcspiders.spider.configuration.Gait
import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.utilities.ecs.ECS
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSComponent
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSEntity
import net.tfminecraft.tfmcspiders.utilities.maths.UP_VECTOR
import net.tfminecraft.tfmcspiders.utilities.maths.rotate
import org.bukkit.util.Vector
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Gallop at a target, then leap at it. [target] returns null once the target is gone;
 * [onEnd] hears how it ended and, after a landing, how far from the target it came down.
 */
class Pounce(
    val target: () -> Vector?,
    val onEnd: (outcome: Outcome, landedDistance: Double?) -> Unit = { _, _ -> },
) : ECSComponent {
    enum class Phase { CHASE, AIRBORNE }
    enum class Outcome { LANDED, TARGET_LOST, GAVE_UP, STOPPED }

    var phase = Phase.CHASE; private set
    var ticks = 0; private set
    /** Gallop was off before the pounce and is switched off again afterwards. */
    var restoreWalk = false; private set
    private var lastTarget: Vector? = null

    /** Advances one tick; returns how the pounce ended, or null while it goes on. */
    internal fun update(body: SpiderBody, options: SpiderOptions, locomotion: Locomotion): Outcome? {
        ticks++
        return when (phase) {
            Phase.CHASE -> chase(body, options, locomotion)
            Phase.AIRBORNE -> if (body.leapTicks > 0 || !hasLanded(body) && ticks < MAX_LANDING_TICKS) null else Outcome.LANDED
        }
    }

    private fun chase(body: SpiderBody, options: SpiderOptions, locomotion: Locomotion): Outcome? {
        val target = target() ?: return Outcome.TARGET_LOST
        lastTarget = target
        if (ticks > options.pounce.giveUpTicks) return Outcome.GAVE_UP

        if (!options.gallop) {
            options.gallop = true
            restoreWalk = true
        }

        // ground plane: the floor, or the wall or ceiling a climbing spider is on
        val up = if (options.climbing) UP_VECTOR.rotate(body.preferredOrientation) else UP_VECTOR
        val offset = target.clone().subtract(body.position)
        val along = offset.subtract(up.clone().multiply(offset.dot(up)))
        val distance = along.length()
        val direction = if (distance > 1e-6) along.multiply(1 / distance) else body.forwardDirection()

        val gait = options.gallopGait
        val scale = options.bodyPlan.scale
        val lift = options.pounce.leapLift * sqrt(scale)
        val flight = Flight.of(lift, gait)
        val leapSpeed = gait.maxSpeed * options.pounce.leapBoost
        val range = min(options.pounce.leapRange * scale, leapSpeed * flight.reachPerSpeed)

        val footing = body.legs.count { it.isGrounded() } * 2 >= body.legs.size
        if (footing && distance <= range) {
            // land on the target rather than overshoot it
            val speed = distance / flight.reachPerSpeed
            body.leap(direction.clone().multiply(speed).add(up.clone().multiply(lift)), flight.ticks)
            phase = Phase.AIRBORNE
            ticks = 0
            return null
        }

        locomotion.face(direction, Locomotion.POUNCE_PRIORITY)
        locomotion.walk(direction.clone().multiply(gait.maxSpeed), Locomotion.POUNCE_PRIORITY)
        return null
    }

    private fun hasLanded(body: SpiderBody) = body.onGround || body.legs.count { it.isGrounded() } * 2 >= body.legs.size

    /** Put the gait back and report how it ended. */
    internal fun finish(options: SpiderOptions?, body: SpiderBody?, outcome: Outcome) {
        if (restoreWalk) options?.gallop = false
        restoreWalk = false
        val landedDistance = if (outcome != Outcome.LANDED || body == null) null else (target() ?: lastTarget)?.let {
            Vector(it.x - body.position.x, 0.0, it.z - body.position.z).length()
        }
        onEnd(outcome, landedDistance)
    }

    /** A leap launched with [lift], stepped like [SpiderBody] steps it (gravity, then air drag). */
    private class Flight(val ticks: Int, val reachPerSpeed: Double) {
        companion object {
            fun of(lift: Double, gait: Gait): Flight {
                val keep = 1 - gait.airDragCoefficient
                var rise = lift
                var height = 0.0
                var speed = 1.0
                var reach = 0.0
                var ticks = 0
                // until it falls back to the height it left from
                while (ticks < MAX_FLIGHT_TICKS) {
                    rise = (rise - gait.gravityAcceleration) * keep
                    speed *= keep
                    height += rise
                    reach += speed
                    ticks++
                    if (height <= 0) break
                }
                return Flight(ticks, reach)
            }
        }
    }

    companion object {
        const val MAX_LANDING_TICKS = 100
        const val MAX_FLIGHT_TICKS = 200
    }
}

fun ECSEntity.stopPounce(outcome: Pounce.Outcome = Pounce.Outcome.STOPPED) {
    val pounce = query<Pounce>() ?: return
    removeComponent<Pounce>()
    pounce.finish(query<SpiderOptions>(), query<SpiderBody>(), outcome)
}

fun setupPounce(app: ECS) {
    app.onTick {
        for ((entity, body, options, locomotion, pounce) in app.query<ECSEntity, SpiderBody, SpiderOptions, Locomotion, Pounce>()) {
            pounce.update(body, options, locomotion)?.let { entity.stopPounce(it) }
        }
    }
}
