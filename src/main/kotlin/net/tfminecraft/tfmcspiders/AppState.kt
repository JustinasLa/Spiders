package net.tfminecraft.tfmcspiders

import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.spider.components.body.SpiderBody
import net.tfminecraft.tfmcspiders.spider.components.Cloak
import net.tfminecraft.tfmcspiders.spider.components.Locomotion
import net.tfminecraft.tfmcspiders.spider.components.Mimic
import net.tfminecraft.tfmcspiders.spider.components.applyMimicRise
import net.tfminecraft.tfmcspiders.spider.components.Mountable
import net.tfminecraft.tfmcspiders.spider.components.PointDetector
import net.tfminecraft.tfmcspiders.spider.components.SoundsAndParticles
import net.tfminecraft.tfmcspiders.spider.components.HitDetector
import net.tfminecraft.tfmcspiders.spider.components.rendering.SpiderRenderer
import net.tfminecraft.tfmcspiders.spider.presets.CinderweaverSize
import net.tfminecraft.tfmcspiders.spider.presets.MimicPose
import net.tfminecraft.tfmcspiders.spider.presets.mimic
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSComponent
import net.tfminecraft.tfmcspiders.utilities.ecs.ECS
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSEntity
import net.tfminecraft.tfmcspiders.utilities.maths.UP_VECTOR
import net.tfminecraft.tfmcspiders.utilities.maths.rotate
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Quaternionf
import java.util.UUID
import kotlin.math.atan2

object AppState {
    const val MAX_SPIDERS = 6
    var miscOptions = MiscellaneousOptions()
    var renderDebugVisuals = false

    val ecs = ECS()

    fun createSpider(
        actor: CommandSender,
        world: World,
        position: Vector,
        options: SpiderOptions,
        orientation: Quaternionf = Quaternionf(),
    ): ECSEntity? {
        if (!StaffAccess.require(actor)) return null
        if (!options.bodyPlan.scale.isFinite() || options.bodyPlan.scale !in CinderweaverSize.MIN_SCALE..CinderweaverSize.MAX_SCALE) {
            actor.sendPlainMessage("Cinderweaver scale must be between ${CinderweaverSize.MIN_SCALE} and ${CinderweaverSize.MAX_SCALE}.")
            return null
        }
        if (spiderCount() >= MAX_SPIDERS) {
            actor.sendPlainMessage("The test limit is $MAX_SPIDERS Cinderweavers. Remove one first.")
            return null
        }
        return spawn(world, position, options, orientation)
    }

    /** A dormant mimic chest woken by anyone; the chest was placed by staff. [floor] is the chest's bottom centre. */
    fun spawnMimic(world: World, floor: Vector, facing: Vector, waker: UUID?): ECSEntity? {
        if (spiderCount() >= MAX_SPIDERS) return null
        val options = mimic()
        applyMimicRise(options, 0.0)
        val orientation = Quaternionf().rotationY(atan2(facing.x, facing.z).toFloat())
        return spawn(world, floor, options, orientation, Mimic(MimicPose(options.bodyPlan.bodyModel), waker))
    }

    private fun spawn(
        world: World,
        position: Vector,
        options: SpiderOptions,
        orientation: Quaternionf,
        vararg extra: ECSComponent,
    ): ECSEntity {
        val up = UP_VECTOR.clone().rotate(orientation)
        val bodyPosition = position.clone().add(up.multiply(options.walkGait.stationary.bodyHeight))
        return ecs.spawn(
            SpiderBody.create(world, bodyPosition, orientation),
            options,
            Locomotion(),
            HitDetector(),
            Cloak(),
            SoundsAndParticles(),
            Mountable(),
            PointDetector(),
            SpiderRenderer(),
            *extra,
        )
    }

    fun spiderCount(): Int = ecs.query<ECSEntity, SpiderBody>().count { !it.first.scheduledForRemoval }

    fun findSpiderByUUID(uuid: java.util.UUID): ECSEntity? {
        return ecs.query<ECSEntity, SpiderBody>().find { !it.first.scheduledForRemoval && it.second.uuid == uuid }?.first
    }

    fun findNearestSpider(player: Player): ECSEntity? {
        return findNearestSpider(player.location)
    }

    fun findNearestSpider(location: Location): ECSEntity? {
        return ecs.query<ECSEntity, SpiderBody>()
            .filter { !it.first.scheduledForRemoval && it.second.world == location.world }
            .minByOrNull { it.second.position.distanceSquared(location.toVector()) }
            ?.first
    }
}

class MiscellaneousOptions {
    /** Draw a marker where the Brood Guide points. Off: spiders follow it unseen. */
    var showLaser = false
}
