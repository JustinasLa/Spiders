package com.heledron.spideranimation

import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.components.Locomotion
import com.heledron.spideranimation.spider.components.Mountable
import com.heledron.spideranimation.spider.components.PointDetector
import com.heledron.spideranimation.spider.components.SoundsAndParticles
import com.heledron.spideranimation.spider.components.ProjectileHitDetector
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.spider.presets.CinderweaverSize
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Quaternionf

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
        val up = UP_VECTOR.clone().rotate(orientation)
        val bodyPosition = position.clone().add(up.multiply(options.walkGait.stationary.bodyHeight))
        return ecs.spawn(
            SpiderBody.create(world, bodyPosition, orientation),
            options,
            Locomotion(),
            ProjectileHitDetector(),
            Cloak(),
            SoundsAndParticles(),
            Mountable(),
            PointDetector(),
            SpiderRenderer(),
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
    var showLaser = true
}
