package net.tfminecraft.spiders.spider.components

import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.configuration.SpiderOptions
import net.tfminecraft.spiders.utilities.ecs.ECS
import net.tfminecraft.spiders.utilities.ecs.ECSComponent
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.events.addEventListener
import net.tfminecraft.spiders.utilities.maths.UP_VECTOR
import io.papermc.paper.event.player.PlayerArmSwingEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.AbstractWindCharge
import org.bukkit.entity.Egg
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Snowball
import org.bukkit.entity.ThrowableProjectile
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector

/**
 * Something struck the spider's drawn shape at [point]: a projectile or a player's swing ([source]).
 * [forceful] hits push the spider and break its shroud; weak ones (a barely drawn bow) only glance off.
 */
class SpiderHitEvent(
    val entity: ECSEntity,
    val spider: SpiderBody,
    val source: Entity,
    val point: Vector,
    val forceful: Boolean,
)

class HitDetector : ECSComponent {
    var stunned = false
}

fun setupHitDetector(app: ECS) {
    addEventListener(SeatProjectileListener(app))
    addEventListener(MeleeListener(app))
    setupProjectileCollisions(app)
}

/** How hard each kind of projectile pushes, compared with a trident. */
internal enum class ProjectileKind(val weight: Double, val minForcefulSpeed: Double) {
    ARROW(.3, 1.0),
    TRIDENT(1.0, 1.0),
    THROWN(.15, .5),
    WIND_CHARGE(1.5, 0.0);

    companion object {
        fun of(projectile: Projectile): ProjectileKind? = when (projectile) {
            is Trident -> TRIDENT
            is AbstractArrow -> ARROW
            is AbstractWindCharge -> WIND_CHARGE
            is Snowball, is Egg -> THROWN
            else -> null
        }
    }
}

internal fun setupProjectileCollisions(app: ECS) {
    // Runs before entities move this tick, so each projectile's coming step is tested against the shape.
    app.onTick {
        for ((entity, spider, options, _) in app.query<ECSEntity, SpiderBody, SpiderOptions, HitDetector>()) {
            val range = options.bodyPlan.reach() + MAX_PROJECTILE_STEP
            val projectiles = spider.world.getNearbyEntities(spider.position.toLocation(spider.world), range, range, range)
                .filterIsInstance<Projectile>()
                .filter { ProjectileKind.of(it) != null && it.isValid && it.velocity.lengthSquared() > MIN_SPEED * MIN_SPEED }
                .filter { it !is AbstractArrow || !it.isInBlock }
                // a loyalty trident flying home after its hit passes through
                .filter { it !is Trident || !it.hasDealtDamage() }
            if (projectiles.isEmpty()) continue

            val rider = entity.query<Mountable>()?.getRider()
            val pieces = spider.shapePieces(options.bodyPlan)

            for (projectile in projectiles) {
                if (rider != null && projectile.shooter == rider) continue
                val kind = ProjectileKind.of(projectile) ?: continue

                val from = projectile.location.toVector()
                val velocity = projectile.velocity
                val (_, point) = firstHit(pieces, from, from.clone().add(velocity)) ?: continue

                val direction = velocity.clone().normalize()
                val forceful = velocity.length() >= kind.minForcefulSpeed
                if (forceful) {
                    // a wind burst shoves away from where it bursts; everything else along its flight
                    val push = if (kind == ProjectileKind.WIND_CHARGE) away(spider, point, direction) else direction
                    knockBack(spider, options, push, point, kind.weight)
                }

                when (kind) {
                    ProjectileKind.ARROW, ProjectileKind.TRIDENT -> {
                        // bounce off where it struck, then fall
                        projectile.teleport(projectile.location.apply { set(point.x, point.y, point.z) }.subtract(direction.clone().multiply(BACK_OFF)))
                        projectile.velocity = direction.clone().multiply(-velocity.length() * BOUNCE)
                        if (projectile is Trident) projectile.setHasDealtDamage(true)
                    }
                    ProjectileKind.THROWN -> {
                        val item = (projectile as? ThrowableProjectile)?.item
                        if (item != null) spider.world.spawnParticle(Particle.ITEM, point.toLocation(spider.world), 8, .1, .1, .1, .05, item)
                        projectile.remove()
                    }
                    ProjectileKind.WIND_CHARGE -> {
                        projectile.teleport(projectile.location.apply { set(point.x, point.y, point.z) })
                        (projectile as AbstractWindCharge).explode()
                    }
                }

                app.emit(SpiderHitEvent(entity, spider, projectile, point, forceful))
            }
        }
    }
}

private fun away(spider: SpiderBody, point: Vector, fallback: Vector): Vector {
    val out = spider.position.clone().subtract(point)
    return if (out.lengthSquared() < 1e-6) fallback.clone() else out.normalize()
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

/**
 * Players hit the spider by swinging at its drawn shape within their reach. Swings at the hidden
 * saddle pig (which sits inside the body) count as hits on the spider instead of attacks on the pig.
 */
internal class MeleeListener(
    private val app: ECS,
    private val reachOf: (Player) -> Double = ::attackReach,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwing(event: PlayerArmSwingEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        strike(event.player, null)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttackSeat(event: PrePlayerAttackEntityEvent) {
        val seated = app.query<ECSEntity, Mountable>()
            .find { (_, mountable) -> event.attacked == mountable.currentPig || event.attacked == mountable.currentMarker }
            ?: return
        event.isCancelled = true
        strike(event.player, seated.first to event.attacked.location.toVector())
    }

    /** [seat]: the spider whose seat was attacked, and where, used when the swing ray misses the drawn shape. */
    private fun strike(player: Player, seat: Pair<ECSEntity, Vector>?) {
        if (player.gameMode == GameMode.SPECTATOR) return
        val charge = player.attackCooldown.toDouble()
        if (charge < MIN_CHARGE) return

        val eyeLocation = player.eyeLocation
        val eye = eyeLocation.toVector()
        val look = eyeLocation.direction.normalize()
        val reach = reachOf(player)

        var best: Triple<ECSEntity, Double, Vector>? = null
        for ((entity, spider, options) in app.query<ECSEntity, SpiderBody, SpiderOptions>()) {
            if (spider.world != player.world || entity.query<HitDetector>() == null) continue
            if (entity.query<Mountable>()?.getRider() == player) continue
            val near = reach + options.bodyPlan.reach()
            if (spider.position.distanceSquared(eye) > near * near) continue
            val (t, point) = firstHit(spider.shapePieces(options.bodyPlan), eye, eye.clone().add(look.clone().multiply(reach))) ?: continue
            if (best == null || t < best.second) best = Triple(entity, t, point)
        }
        if (best == null && seat != null) best = Triple(seat.first, 0.0, seat.second)
        val (entity, t, point) = best ?: return

        // a block in the way takes the swing (e.g. mining next to a leg)
        if (seat == null && t > 0 && player.world.rayTraceBlocks(eyeLocation, look, t * reach, FluidCollisionMode.NEVER, true) != null) return

        val spider = entity.query<SpiderBody>() ?: return
        val options = entity.query<SpiderOptions>() ?: return
        knockBack(spider, options, look, point, MELEE_WEIGHT * charge)
        player.resetCooldown()
        app.emit(SpiderHitEvent(entity, spider, player, point, forceful = true))
    }
}

private fun attackReach(player: Player): Double =
    player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.value
        ?: if (player.gameMode == GameMode.CREATIVE) 5.0 else 3.0

/** Fastest a projectile moves in one tick (blocks); widens the search around the spider. */
private const val MAX_PROJECTILE_STEP = 4.0
private const val MIN_SPEED = .05
private const val BOUNCE = .1
private const val BACK_OFF = .05
/** A fully charged swing pushes this much compared with a trident. */
private const val MELEE_WEIGHT = .5
/** Swings while mining or spamming the button are too weak to count. */
private const val MIN_CHARGE = .3
