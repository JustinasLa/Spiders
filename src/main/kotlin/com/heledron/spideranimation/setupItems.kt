package com.heledron.spideranimation

import com.heledron.spideranimation.AppState.ecs
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.components.PointDetector
import com.heledron.spideranimation.spider.components.rendering.SpiderRenderer
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.laser.LaserPoint
import com.heledron.spideranimation.spider.presets.defaultPreset
import com.heledron.spideranimation.spider.presets.upgradeClockworkAppearance
import com.heledron.spideranimation.spider.presets.useOrganicSounds
import com.heledron.spideranimation.utilities.custom_items.CustomItemComponent
import com.heledron.spideranimation.utilities.custom_items.StaffTool
import com.heledron.spideranimation.utilities.custom_items.staffTools
import com.heledron.spideranimation.utilities.currentPlugin
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.namespacedID
import com.heledron.spideranimation.utilities.raycastGround
import com.heledron.spideranimation.utilities.Serializer
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.maths.toRadians
import com.heledron.spideranimation.utilities.overloads.direction
import com.heledron.spideranimation.utilities.overloads.eyePosition
import com.heledron.spideranimation.utilities.overloads.playSound
import com.heledron.spideranimation.utilities.overloads.position
import com.heledron.spideranimation.utilities.overloads.yawRadians
import com.heledron.spideranimation.utilities.persistence.UUIDDataType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.joml.Quaternionf
import java.util.UUID


fun setupItems() {
    val spiderComponent = CustomItemComponent("spider", Material.FERMENTED_SPIDER_EYE)
    staffTools += StaffTool(spiderComponent, "Cinderweaver Summoning Seal", "item/fermented_spider_eye",
        "Summons a Cinderweaver, then recalls it again.",
        "Right-click a floor, wall or ceiling to summon one there. Right-click again to recall it; the seal keeps its settings. Commands act on the spider linked to the seal in your hand.")
    spiderComponent.onGestureUse { player, item ->
        val storedUuid = item.spiderUUID
		val existing = if (storedUuid == null) null else AppState.findSpiderByUUID(storedUuid)
		if (existing != null) {
			player.world.playSound(player.position, Sound.BLOCK_WOOL_BREAK, .4f, .7f)

			item.spiderOptions = existing.query<SpiderOptions>()

			existing.remove()
			item.spiderUUID = null
			player.sendActionBar(Component.text("Spider removed"))
		} else {
			val result = player.world.raycastGround(player.eyePosition, player.direction, 100.0) ?: return@onGestureUse
			val hitPosition = result.hitPosition
			val up = result.hitBlockFace?.direction ?: UP_VECTOR

			player.world.playSound(hitPosition, Sound.ENTITY_SPIDER_AMBIENT, .4f, .7f)

			val options = item.spiderOptions ?: defaultPreset()
			// placed on a wall or ceiling: it has to cling on
			if (up.y < .7) options.climbing = true
			val yawDirection = FORWARD_VECTOR.rotate(Quaternionf().rotationY(player.yawRadians()))
			val orientation = SpiderBody.snapRotationAbout(
				SpiderBody.orientationFrom(up, player.direction, yawDirection),
				up,
				45f.toRadians(),
			)

			val entity = AppState.createSpider(player, player.world, hitPosition, options, orientation) ?: return@onGestureUse
			val spider = entity.query<SpiderBody>() ?: return@onGestureUse
			item.spiderUUID = spider.uuid

			player.sendActionBar(Component.text("Spider created"))
		}

    }


    val disableLegComponent = CustomItemComponent("disableLeg", Material.RABBIT_HIDE)
    staffTools += StaffTool(disableLegComponent, "Limb Binding Charm", "item/rabbit_hide",
        "Lames or frees a single leg.",
        "Hold it and look at a spider's foot to select that leg, then right-click to bind or free it.")
    onTick {
        val players = disableLegComponent.getPlayersHoldingItem().toSet()
        for (pointDetector in AppState.ecs.query<PointDetector>()) {
            pointDetector.checkPlayers = players
        }
    }
    disableLegComponent.onGestureUse { player, _ ->
        for (pointDetector in AppState.ecs.query<PointDetector>()) {
            val selectedLeg = pointDetector.selectedLeg[player]
            if (selectedLeg == null) {
                player.world.playSound(player.position, Sound.BLOCK_SCULK_HIT, 1.0f, 2.0f)
                return@onGestureUse
            }

            selectedLeg.isDisabled = !selectedLeg.isDisabled
            player.world.playSound(player.position, Sound.BLOCK_WOOL_HIT, 1.0f, 1.0f)
        }
    }

    val toggleDebugComponent = CustomItemComponent("toggleDebug", Material.BONE)
    staffTools += StaffTool(toggleDebugComponent, "Broodkeeper's Sight", "item/bone",
        "Shows the animation debug view for every spider.",
        "Right-click to show or hide leg targets, step zones and balance.")
    toggleDebugComponent.onGestureUse { player, _ ->
        AppState.renderDebugVisuals = !AppState.renderDebugVisuals

        val pitch = if (AppState.renderDebugVisuals) 2.0f else 1.5f
        player.world.playSound(player.position, Sound.BLOCK_SCULK_HIT, 1.0f, pitch)
    }


    val switchRendererComponent = CustomItemComponent("switchRenderer", Material.LIGHT_BLUE_DYE)
    staffTools += StaffTool(switchRendererComponent, "Spectral Form", "item/light_blue_dye",
        "Draws the nearest spider as a particle sketch instead of blocks.",
        "Right-click to switch the nearest spider between its solid body and particles.")
    switchRendererComponent.onGestureUse { player, _ ->
        val renderer = AppState.findNearestSpider(player)?.query<SpiderRenderer>() ?: return@onGestureUse
        renderer.useParticles = !renderer.useParticles

        if (renderer.useParticles) {
            player.world.playSound(player.position, Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f)
        } else {
            player.world.playSound(player.position, Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 1.0f)
        }
    }

    val toggleCloakComponent = CustomItemComponent("toggleCloak", Material.GREEN_DYE)
    staffTools += StaffTool(toggleCloakComponent, "Shroud Charm", "item/green_dye",
        "Shrouds the nearest spider so it copies whatever lies behind it.",
        "Right-click to shroud or unshroud the nearest spider.")
    toggleCloakComponent.onGestureUse { player, _ ->
        val entity = AppState.findNearestSpider(player) ?: return@onGestureUse
        val cloak = entity.query<Cloak>() ?: return@onGestureUse
        cloak.toggleCloak(AppState.ecs, entity)
    }

    val switchGaitComponent = CustomItemComponent("switchGait", Material.BREEZE_ROD)
    staffTools += StaffTool(switchGaitComponent, "Hunting Instinct", "item/breeze_rod",
        "Switches the nearest spider between walking and galloping.",
        "Right-click to switch gait. Riders can also hold sprint to gallop.")
    switchGaitComponent.onGestureUse { player, _ ->
        val entity = AppState.findNearestSpider(player) ?: return@onGestureUse
        val options = entity.query<SpiderOptions>() ?: return@onGestureUse
        options.gallop = !options.gallop
        player.world.playSound(player.position, Sound.BLOCK_SCULK_HIT, 1.0f, 2.0f)
        player.sendActionBar(Component.text(if (!options.gallop) "Walk mode" else "Gallop mode"))
    }

    val toggleGripComponent = CustomItemComponent("toggleGrip", Material.SLIME_BALL)
    staffTools += StaffTool(toggleGripComponent, "Clinging Charm", "item/slime_ball",
        "Lets the nearest spider climb walls and ceilings, or drop back to the floor.",
        "Right-click to switch wall climbing on or off.")
    toggleGripComponent.onGestureUse { player, _ ->
        val entity = AppState.findNearestSpider(player) ?: return@onGestureUse
        val options = entity.query<SpiderOptions>() ?: return@onGestureUse
        options.climbing = !options.climbing
        player.world.playSound(player.position, Sound.BLOCK_HONEY_BLOCK_HIT, 1.0f, 2.0f)
        player.sendActionBar(Component.text(if (!options.climbing) "Wall climbing off" else "Wall climbing on"))
    }

    val laserPointerComponent = CustomItemComponent("laserPointer", Material.REDSTONE_TORCH)
    staffTools += StaffTool(laserPointerComponent, "Brood Guide — aim to guide", "block/redstone_torch",
        "Spiders walk to wherever you point it.",
        "Hold it and aim at the ground. Nothing is drawn where it points.")

    val comeHereComponent = CustomItemComponent("comeHere", Material.CARROT_ON_A_STICK)
    staffTools += StaffTool(comeHereComponent, "Broodcall Whistle — hold to recall", "item/carrot_on_a_stick",
        "Calls the spiders to you.",
        "Hold it and they walk towards you; put it away to release them.")

    class LaserPointExpire(val owner: Player) : ECSComponent {
        var expired = false
    }

    ecs.onTick {
        // mark laser for removal
        ecs.query<LaserPointExpire>().forEach { expire ->
            expire.expired = true
        }
    }

    ecs.onTick {
        val lasers = ecs.query<ECSEntity, LaserPoint, LaserPointExpire>()

        fun spawnLaser(player: Player, position: Vector, isVisible: Boolean) {
            val existing = lasers.find { it.third.owner == player }

            if (existing != null) {
                // update existing laser
                existing.second.world = player.world
                existing.second.position = position
                existing.second.isVisible = isVisible
                existing.third.expired = false
            } else {
                // create new laser
                ecs.spawn(
                    LaserPointExpire(player),
                    LaserPoint(
                        world = player.world,
                        position = position,
                        isVisible = isVisible,
                    )
                )
            }
        }

        // handle laser pointer
        for (player in laserPointerComponent.getPlayersHoldingItem()) {
            val direction = player.direction
            val result = player.world.raycastGround(player.eyePosition, direction, 100.0)

            val hitPosition = result?.hitPosition ?:
                player.eyePosition.add(direction.multiply(200))


            val isUsingFallback = result == null
            val isVisible = !isUsingFallback && AppState.miscOptions.showLaser

            spawnLaser(player, hitPosition, isVisible)
        }

        // handle carrot on a stick
        for (player in comeHereComponent.getPlayersHoldingItem()) {
            spawnLaser(player, player.eyePosition, isVisible = false)
        }
    }

    ecs.onTick {
        ecs.query<ECSEntity,LaserPointExpire>().forEach { (entity, expire) ->
            if (expire.expired) entity.remove()
        }
    }
}

private val SPIDER_UUID_KEY = namespacedID("spider_uuid")
private val SPIDER_OPTIONS_KEY = namespacedID("spider_options")

internal fun Player.heldSpiderUUID(): UUID? =
    this.inventory.itemInMainHand.spiderUUID
        ?: this.inventory.itemInOffHand.spiderUUID

private var ItemStack.spiderUUID
    get(): UUID? {
        return itemMeta?.persistentDataContainer?.get(SPIDER_UUID_KEY, UUIDDataType)
    }
    set(value) {
        val meta = itemMeta
        if (value == null) {
            meta.persistentDataContainer.remove(SPIDER_UUID_KEY)
        } else {
            meta.persistentDataContainer.set(SPIDER_UUID_KEY, UUIDDataType, value)
        }
        itemMeta = meta
    }

private var ItemStack.spiderOptions
    get(): SpiderOptions? {
        val json = itemMeta?.persistentDataContainer?.get(SPIDER_OPTIONS_KEY, PersistentDataType.STRING) ?: return null

        return try {
            Serializer.deserialize<SpiderOptions>(json).also { options ->
                if (upgradeClockworkAppearance(options.bodyPlan)) options.useOrganicSounds()
            }
        } catch (e: Exception) {
            currentPlugin.logger.warning("Ignoring unreadable stored spider options: $e")
            null
        }
    }
    set(value) {
        val meta = itemMeta
        if (value == null) {
            meta.persistentDataContainer.remove(SPIDER_OPTIONS_KEY)
            itemMeta = meta
            return
        }

        val json = try {
            Serializer.serialize(value)
        } catch (e: Exception) {
            currentPlugin.logger.severe("Could not serialize spider options: $e")
            return
        }

        meta.persistentDataContainer.set(SPIDER_OPTIONS_KEY, PersistentDataType.STRING, json)
        itemMeta = meta
    }
