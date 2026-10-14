package net.tfminecraft.spiders.spider.components

import net.tfminecraft.spiders.StaffAccess
import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.configuration.SpiderOptions
import net.tfminecraft.spiders.utilities.ecs.ECSComponent
import net.tfminecraft.spiders.utilities.ecs.ECS
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.events.addEventListener
import net.tfminecraft.spiders.utilities.events.onInteractEntity
import net.tfminecraft.spiders.utilities.maths.UP_VECTOR
import net.tfminecraft.spiders.utilities.maths.rotate
import net.tfminecraft.spiders.utilities.overloads.direction
import net.tfminecraft.spiders.utilities.overloads.playSound
import net.tfminecraft.spiders.utilities.overloads.position
import net.tfminecraft.spiders.utilities.overloads.yawRadians
import net.tfminecraft.spiders.utilities.rendering.RenderEntity
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import org.joml.Quaternionf

class Mountable : ECSComponent {
    var currentMarker: ArmorStand? = null
    var currentPig: Pig? = null
    /** Gallop switched on by the rider's sprint key, to be switched off again when they let go. */
    var sprintGallop = false
    fun getRider() = currentMarker?.passengers?.firstOrNull() as? Player
}

fun setupMountable(app: ECS) {
    addEventListener(StaffMountListener(app))

    // Handle user input
    @Suppress("UnstableApiUsage")
    app.onTick {
        for ((mountable, spider, options, entity) in app.query<Mountable, SpiderBody, SpiderOptions, ECSEntity>()) {
            val player = mountable.getRider()
            if (player == null) {
                if (mountable.sprintGallop) setSprintGallop(mountable, options, false)
                continue
            }
            if (!StaffAccess.canUse(player)) {
                player.leaveVehicle()
                continue
            }
            val locomotion = entity.query<Locomotion>() ?: continue

            val input = Vector()
            if (player.currentInput.isLeft) input.x += 1.0
            if (player.currentInput.isRight) input.x -= 1.0
            if (player.currentInput.isForward) input.z += 1.0
            if (player.currentInput.isBackward) input.z -= 1.0

            // hold sprint to gallop
            val sprinting = player.currentInput.isSprint && !input.isZero
            if (sprinting && !options.gallop) setSprintGallop(mountable, options, true)
            if (!sprinting && mountable.sprintGallop) setSprintGallop(mountable, options, false)

            if (options.climbing) {
                // steer along the wall or ceiling: forward is where the rider looks, flattened onto it
                val up = UP_VECTOR.rotate(spider.preferredOrientation)
                val forward = alongSurface(player.direction, up)
                    .takeIf { it.lengthSquared() > MIN_LOOK_ALONG_SURFACE * MIN_LOOK_ALONG_SURFACE }
                    ?: alongSurface(spider.forwardDirection(), up)
                forward.normalize()
                val left = up.clone().crossProduct(forward)
                val direction = forward.clone().multiply(input.z).add(left.multiply(input.x))
                if (!direction.isZero) direction.normalize()

                locomotion.face(forward, Locomotion.RIDER_PRIORITY)
                locomotion.walk(direction.multiply(options.gait.maxSpeed), Locomotion.RIDER_PRIORITY)
                continue
            }

            val rotation = Quaternionf().rotationYXZ(player.yawRadians(), .0f, .0f)
            val direction = if (input.isZero) input else input.rotate(rotation).normalize()

            locomotion.face(player.direction, Locomotion.RIDER_PRIORITY)
            locomotion.walk(direction.multiply(options.gait.maxSpeed), Locomotion.RIDER_PRIORITY)
        }
    }

    // Render pig and marker
    app.onRender {
        for ((spider, mountable, options) in app.query<SpiderBody, Mountable, SpiderOptions>()) {
            val location = spider.location().add(spider.velocity)

            // seat on the spider's back, which faces sideways or down on walls and ceilings
            val up = if (options.climbing) UP_VECTOR.rotate(spider.orientation) else UP_VECTOR
            val pigLocation = location.clone().add(up.clone().multiply(-.6))
            val markerLocation = location.clone().add(up.clone().multiply(.3))
            // under a ceiling the rider hangs below the belly instead of standing in the blocks above
            if (up.y < 0) markerLocation.y += up.y * RIDER_HEIGHT

            RenderEntity(
                clazz = Pig::class.java,
                location = pigLocation,
                init = {
                    it.setGravity(false)
                    it.setAI(false)
                    it.isInvisible = true
                    it.isInvulnerable = true
                    it.isSilent = true
                    it.isCollidable = false
                },
                update = {
                    mountable.currentPig = it
                }
            ).submit(spider to "mountable.pig")

            RenderEntity(
                clazz = ArmorStand::class.java,
                location = markerLocation,
                init = {
                    it.setGravity(false)
                    it.isInvisible = true
                    it.isInvulnerable = true
                    it.isSilent = true
                    it.isCollidable = false
                    it.isMarker = true
                },
                update = update@{
                    mountable.currentMarker = it
                }
            ).submit(spider to "mountable.marker")
        }
    }
}

private const val MIN_LOOK_ALONG_SURFACE = .35
private const val RIDER_HEIGHT = 1.8

private fun alongSurface(direction: Vector, up: Vector): Vector =
    direction.clone().subtract(up.clone().multiply(direction.dot(up)))

private fun setSprintGallop(mountable: Mountable, options: SpiderOptions, gallop: Boolean) {
    options.gallop = gallop
    mountable.sprintGallop = gallop
}

/** Rechecks both interaction and actual mounting, including direct /ride attempts. */
internal class StaffMountListener(private val app: ECS) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        for (mountable in app.query<Mountable>()) {
            if (event.rightClicked != mountable.currentPig && event.rightClicked != mountable.currentMarker) continue
            if (!StaffAccess.require(event.player)) {
                event.isCancelled = true
                return
            }
            if (event.hand != EquipmentSlot.HAND) return
            val pig = mountable.currentPig ?: return
            if (event.player.isSneaking && event.player.inventory.itemInMainHand.type.isAir && mountable.getRider() == null) {
                pig.setSaddle(false)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMount(event: EntityMountEvent) {
        for (mountable in app.query<Mountable>()) {
            if (event.mount != mountable.currentPig && event.mount != mountable.currentMarker) continue
            val player = event.entity as? Player
            if (player == null || !StaffAccess.require(player)) {
                event.isCancelled = true
                return
            }
            if (event.mount == mountable.currentPig) {
                event.isCancelled = true
                val marker = mountable.currentMarker ?: return
                if (marker.passengers.isEmpty()) marker.addPassenger(player)
            }
        }
    }
}
