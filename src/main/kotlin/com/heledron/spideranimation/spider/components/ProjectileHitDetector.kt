package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.StaffAccess
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.events.addEventListener
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector

/**
 * An arrow, spectral arrow or trident struck the spider's drawn shape at [point].
 * [forceful] hits come from staff and push the spider (and break its shroud); others only bounce off.
 */
class SpiderProjectileHitEvent(
    val entity: ECSEntity,
    val spider: SpiderBody,
    val projectile: AbstractArrow,
    val point: Vector,
    val forceful: Boolean,
)

class ProjectileHitDetector : ECSComponent {
    var stunned = false
}

fun setupProjectileHitDetector(app: ECS) {
    addEventListener(SeatProjectileListener(app))
    setupProjectileCollisions(app)
}

internal fun setupProjectileCollisions(app: ECS) {
    // Runs before entities move this tick, so each projectile's coming step is tested against the shape.
    app.onTick {
        for ((entity, spider, options, _) in app.query<ECSEntity, SpiderBody, SpiderOptions, ProjectileHitDetector>()) {
            val range = options.bodyPlan.reach() + MAX_PROJECTILE_STEP
            val projectiles = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), range, range, range)
                .filterIsInstance<AbstractArrow>()
                .filter { it.isValid && !it.isInBlock && it.velocity.lengthSquared() > MIN_SPEED * MIN_SPEED }
                // a loyalty trident flying home after its hit passes through
                .filter { it !is Trident || !it.hasDealtDamage() }
            if (projectiles.isEmpty()) continue

            val rider = entity.query<Mountable>()?.getRider()
            val pieces = spider.shapePieces(options.bodyPlan)

            for (projectile in projectiles) {
                if (rider != null && projectile.shooter == rider) continue

                val from = projectile.location.toVector()
                val velocity = projectile.velocity
                val (_, point) = firstHit(pieces, from, from.clone().add(velocity)) ?: continue

                val shooter = projectile.shooter as? Player
                val forceful = shooter != null && StaffAccess.canUse(shooter) && velocity.length() >= MIN_FORCEFUL_SPEED
                val direction = velocity.clone().normalize()
                if (forceful) knockBack(spider, options, direction, point, if (projectile is Trident) 1.0 else ARROW_WEIGHT)

                // bounce off where it struck, then fall
                val location = projectile.location
                location.set(point.x - direction.x * BACK_OFF, point.y - direction.y * BACK_OFF, point.z - direction.z * BACK_OFF)
                projectile.teleport(location)
                projectile.velocity = direction.clone().multiply(-velocity.length() * BOUNCE)
                if (projectile is Trident) projectile.setHasDealtDamage(true)

                app.emit(SpiderProjectileHitEvent(entity, spider, projectile, point, forceful))
            }
        }
    }
}

private fun knockBack(spider: SpiderBody, options: SpiderOptions, direction: Vector, point: Vector, weight: Double) {
    val gait = options.gait
    spider.velocity.add(direction.clone().multiply(gait.tridentKnockBack * weight))

    // spin about the vertical, more for hits far from straight up
    val hitDirection = spider.position.clone().subtract(point).normalize()
    val axis = UP_VECTOR.crossProduct(direction)
    val angle = hitDirection.angle(UP_VECTOR)
    spider.accelerateRotation(axis, (angle * gait.tridentRotationalKnockBack * weight).toFloat())
}

/** The hidden saddle pig and seat marker would otherwise stop projectiles in mid-air. */
internal class SeatProjectileListener(private val app: ECS) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHit(event: ProjectileHitEvent) {
        val hit = event.hitEntity ?: return
        if (app.query<Mountable>().any { hit == it.currentPig || hit == it.currentMarker }) event.isCancelled = true
    }
}

/** Fastest a projectile moves in one tick (blocks); widens the search around the spider. */
private const val MAX_PROJECTILE_STEP = 4.0
private const val MIN_SPEED = .05
/** Slower projectiles (a weak bow draw, a dropped arrow) bounce off without pushing. */
private const val MIN_FORCEFUL_SPEED = 1.0
/** An arrow pushes this much compared with a trident. */
private const val ARROW_WEIGHT = .3
private const val BOUNCE = .1
private const val BACK_OFF = .05
