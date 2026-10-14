package net.tfminecraft.spiders

import net.tfminecraft.spiders.AppState.ecs
import net.tfminecraft.spiders.commands.setupCommands
import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer
import net.tfminecraft.spiders.spider.setupSpider
import net.tfminecraft.spiders.laser.setupLaserPointer
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.custom_items.setupStaffTools
import net.tfminecraft.spiders.utilities.events.onTick
import net.tfminecraft.spiders.utilities.setupCoreUtils
import net.tfminecraft.spiders.utilities.shutdownCoreUtils
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class SpiderAnimationPlugin : JavaPlugin() {
    fun writeAndSaveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
    }

    override fun onDisable() {
        logger.info("Disabling Spider Animation plugin")
        shutdownCoreUtils()
        ecs.entities.clear()
    }

    override fun onEnable() {
        logger.info("Enabling Spider Animation plugin")

        setupCoreUtils()

        setupCommands(this)
        setupItems()
        setupStaffTools()
        setupSpider(ecs)
        setupLaserPointer(ecs)

        ecs.start()
        onTick {
            // sync AppState properties
            ecs.query<ECSEntity, SpiderBody>().forEach { (entity, spider) ->
                entity.query<SpiderRenderer>()?.renderDebugVisuals = AppState.renderDebugVisuals
            }

            ecs.update()
            ecs.render()
        }

        logger.info("Cinderweavers ready. Staff permission: ${StaffAccess.PERMISSION}; limit: ${AppState.MAX_SPIDERS}.")
    }
}
