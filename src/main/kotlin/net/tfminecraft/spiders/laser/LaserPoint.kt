package net.tfminecraft.spiders.laser

import net.tfminecraft.spiders.utilities.rendering.renderBlock
import net.tfminecraft.spiders.spider.components.Locomotion
import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.configuration.OrientationMode
import net.tfminecraft.spiders.spider.configuration.SpiderOptions
import net.tfminecraft.spiders.utilities.ecs.ECSComponent
import net.tfminecraft.spiders.utilities.ecs.ECS
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.centredTransform
import net.tfminecraft.spiders.utilities.horizontalDistance
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.util.Vector

class LaserPoint(
    var world: World,
    var position: Vector,
    var isVisible: Boolean,
) : ECSComponent

fun setupLaserPointer(app: ECS) {
    app.onTick {
        val lasers = app.query<LaserPoint>()

        // get spiders to follow the laser
        for ((spiderEntity, spider, options) in app.query<ECSEntity, SpiderBody, SpiderOptions>()) {
            val nearestLaser = lasers
                .filter { it.world == spider.world }
                .minByOrNull { it.position.distanceSquared(spider.position) }
                ?: continue

            val locomotion = spiderEntity.query<Locomotion>() ?: continue

            val direction = nearestLaser.position.clone().subtract(spider.position).normalize()
            locomotion.face(direction, Locomotion.LASER_PRIORITY)

            // only walk while far enough away to stop in time
            val distance = options.walkGait.stationary.bodyHeight * 2
            val currentSpeed = spider.velocity.length()
            val decelerateDistance = (currentSpeed * currentSpeed) / (2 * options.gait.moveAcceleration)

            val currentDistance = if (options.gait.scanOrientation == OrientationMode.YAxis) {
                spider.position.horizontalDistance(nearestLaser.position)
            } else {
                spider.position.distance(nearestLaser.position)
            }

            val walkVelocity = if (currentDistance > distance + decelerateDistance) {
                direction.clone().multiply(options.gait.maxSpeed)
            } else {
                Vector(0.0, 0.0, 0.0)
            }

            locomotion.walk(walkVelocity, Locomotion.LASER_PRIORITY)
        }
    }


    app.onRender {
        val size = .25f
        for (laser in app.query<LaserPoint>()) {
            if (!laser.isVisible) continue
            renderLaserPoint(laser.world, laser.position, size).submit(laser)
        }
    }
}

private fun renderLaserPoint(
    world: World,
    position: Vector,
    size: Float,
) = renderBlock(
    world = world,
    position = position,
    init = {
        it.block = Material.REDSTONE_BLOCK.createBlockData()
        it.teleportDuration = 1
        it.brightness = Display.Brightness(15, 15)
        it.transformation = centredTransform(size, size, size)
    }
)