package com.heledron.spideranimation

import com.heledron.spideranimation.AppState.ecs
import com.heledron.spideranimation.commands.setupCommands
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.spider.setupSpider
import com.heledron.spideranimation.laser.setupLaserPointer
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.custom_items.setupStaffToolInventory
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.setupCoreUtils
import com.heledron.spideranimation.utilities.shutdownCoreUtils
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
        setupStaffToolInventory()
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
