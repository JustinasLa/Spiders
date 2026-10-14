package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.StaffAccess
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.MimicPose
import com.heledron.spideranimation.spider.presets.MimicShape
import com.heledron.spideranimation.utilities.currentPlugin
import com.heledron.spideranimation.utilities.currentTick
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSComponent
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.events.addEventListener
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.moveTowards
import com.heledron.spideranimation.utilities.maths.toRadians
import com.heledron.spideranimation.utilities.namespacedID
import com.heledron.spideranimation.utilities.onPluginShutdown
import com.heledron.spideranimation.utilities.overloads.playSound
import com.heledron.spideranimation.utilities.raycastGround
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.InventoryHolder
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.bukkit.block.data.type.Chest as ChestData

enum class MimicState { WAKING, AWAKE, SETTLING, CROUCHING }

/** A chest that stands up when touched, chases whoever woke it, then settles back into a chest. */
class Mimic(val pose: MimicPose, val waker: UUID?) : ECSComponent {
    var state = MimicState.WAKING; private set
    var stateTicks = 0; private set
    /** 0 = sitting as a chest, 1 = standing. */
    var rise = 0.0; private set
    var lidAngle = 0f; private set

    private var chompTick = -1
    private var nextChomp = Random.nextInt(20, 40)
    var idleTicks = 0; private set
    private var chaseTicks = CHASE_TICKS

    var restCell: Block? = null; private set
    var restFacing = BlockFace.SOUTH; private set

    private fun enter(state: MimicState) {
        this.state = state
        stateTicks = 0
    }

    /** Pick a spot to sit down in; false if there's nowhere nearby to become a chest. */
    fun beginSettling(body: SpiderBody): Boolean {
        val cell = MimicChests.restCellNear(body.world, body.position) ?: return false
        restCell = cell
        restFacing = cardinalFacing(body.forwardDirection())
        enter(MimicState.SETTLING)
        return true
    }

    internal fun update(entity: ECSEntity, body: SpiderBody, options: SpiderOptions, locomotion: Locomotion?) {
        stateTicks++
        val rider = entity.query<Mountable>()?.getRider()
        var lidTarget: Float

        when (state) {
            MimicState.WAKING -> {
                rise = smoothstep(stateTicks.toDouble() / WAKE_TICKS)
                // the lid bursts open, then relaxes
                lidTarget = if (stateTicks < 5) 75f.toRadians() else 35f.toRadians()
                wakerNear(body)?.let { locomotion?.face(it.clone().setY(0), PRIORITY) }
                if (stateTicks >= WAKE_TICKS) enter(MimicState.AWAKE)
            }

            MimicState.AWAKE -> {
                rise = 1.0
                lidTarget = (22f + 4f * sin(currentTick * .15f)).toRadians()

                val toWaker = if (chaseTicks > 0 && rider == null) wakerNear(body) else null
                if (toWaker != null) {
                    chaseTicks--
                    val flat = toWaker.clone().setY(0)
                    locomotion?.face(flat, PRIORITY)
                    if (flat.length() > CHASE_STOP_DISTANCE) {
                        locomotion?.walk(flat.normalize().multiply(options.gait.maxSpeed), PRIORITY)
                    } else if (nextChomp > 12) {
                        nextChomp = Random.nextInt(4, 12)
                    }
                }

                idleTicks = if (body.isWalking || rider != null) 0 else idleTicks + 1
                if (idleTicks >= IDLE_SLEEP_TICKS && !beginSettling(body)) idleTicks = IDLE_SLEEP_TICKS / 2
            }

            MimicState.SETTLING -> {
                rise = 1.0
                lidTarget = 6f.toRadians()
                if (rider != null) {
                    enter(MimicState.AWAKE)
                } else {
                    val aligned = steerToRestCell(body, options, locomotion)
                    if (aligned || stateTicks > SETTLE_TIMEOUT_TICKS) enter(MimicState.CROUCHING)
                }
            }

            MimicState.CROUCHING -> {
                rise = 1.0 - smoothstep(stateTicks.toDouble() / CROUCH_TICKS)
                lidTarget = 0f
                steerToRestCell(body, options, locomotion)
                if (stateTicks >= CROUCH_TICKS + 3 && !MimicChests.rest(entity, body, this)) {
                    // the spot was taken while it sat down: stand back up
                    chaseTicks = 0
                    enter(MimicState.WAKING)
                }
            }
        }

        applyMimicRise(options, rise)
        lidTarget = updateChomp(body, lidTarget)

        val rate = if (lidTarget < lidAngle) .45f else .3f
        lidAngle = lidAngle.moveTowards(lidTarget, rate)
        val tongueOut = ((lidAngle - 12f.toRadians()) / 20f.toRadians()).coerceIn(0f, 1f)
        pose.apply(lidAngle, tongueOut, sin(currentTick * .3f) * .25f)
    }

    /** Snap shut and spring open again every so often while awake. */
    private fun updateChomp(body: SpiderBody, lidTarget: Float): Float {
        if (chompTick < 0) {
            if (state != MimicState.AWAKE || --nextChomp > 0) return lidTarget
            chompTick = 0
            nextChomp = Random.nextInt(40, 120)
        }
        chompTick++
        if (chompTick == 3) {
            body.world.playSound(body.position, Sound.BLOCK_CHEST_CLOSE, .6f, 1.3f)
            body.world.playSound(body.position, Sound.ENTITY_EVOKER_FANGS_ATTACK, .25f, 1.6f)
        }
        if (chompTick > 8) chompTick = -1
        return if (chompTick <= 3) 0f else 55f.toRadians()
    }

    private fun wakerNear(body: SpiderBody): Vector? {
        val player = waker?.let(Bukkit::getPlayer) ?: return null
        if (player.world != body.world || player.isDead) return null
        val offset = player.location.toVector().subtract(body.position)
        return offset.takeIf { it.lengthSquared() < CHASE_RANGE * CHASE_RANGE }
    }

    /** Walk onto the middle of the rest cell and turn square to it. True once lined up. */
    private fun steerToRestCell(body: SpiderBody, options: SpiderOptions, locomotion: Locomotion?): Boolean {
        val cell = restCell ?: return true
        val offset = Vector(cell.x + .5 - body.position.x, 0.0, cell.z + .5 - body.position.z)
        val distance = offset.length()
        val speed = (distance * .4).coerceAtMost(options.gait.maxSpeed * .6)
        locomotion?.walk(if (distance > 1e-3) offset.normalize().multiply(speed) else offset, SETTLE_PRIORITY)
        locomotion?.face(restFacing.direction, SETTLE_PRIORITY)

        val forward = body.forwardDirection().setY(0)
        val square = forward.lengthSquared() > 1e-6 && forward.normalize().dot(restFacing.direction) > cos(5f.toRadians())
        return distance < .1 && square
    }

    companion object {
        const val WAKE_TICKS = 14
        const val CROUCH_TICKS = 14
        const val CHASE_TICKS = 20 * 8
        const val CHASE_RANGE = 16.0
        const val CHASE_STOP_DISTANCE = 1.8
        const val IDLE_SLEEP_TICKS = 20 * 20
        const val SETTLE_TIMEOUT_TICKS = 20 * 4
        /** Above default steering, below staff tools, riders and pounces. */
        const val PRIORITY = 5
        const val SETTLE_PRIORITY = 15
    }
}

/** Body height follows [rise]: chest-on-the-floor at 0, standing at 1. */
internal fun applyMimicRise(options: SpiderOptions, rise: Double) {
    val resting = MimicShape.RESTING_HEIGHT
    options.walkGait.stationary.bodyHeight = resting.lerp(MimicShape.STANDING_HEIGHT, rise)
    options.walkGait.moving.bodyHeight = resting.lerp(MimicShape.STANDING_HEIGHT, rise)
    options.gallopGait.stationary.bodyHeight = resting.lerp(MimicShape.STANDING_HEIGHT, rise)
    options.gallopGait.moving.bodyHeight = resting.lerp(MimicShape.GALLOP_HEIGHT, rise)
}

internal fun smoothstep(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
}

internal fun cardinalFacing(forward: Vector): BlockFace =
    listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST).maxBy { it.direction.dot(forward) }

/** Dormant mimics are real chest blocks tagged in their block entity data, so they survive restarts. */
object MimicChests {
    private val key by lazy { namespacedID("mimic") }

    fun isMimic(block: Block): Boolean {
        if (block.type != Material.CHEST) return false
        return isMimic(block.getState(false) as? InventoryHolder)
    }

    fun isMimic(holder: InventoryHolder?): Boolean =
        (holder as? Chest)?.persistentDataContainer?.has(key, PersistentDataType.BYTE) == true

    fun canRestIn(block: Block): Boolean =
        (block.type.isAir || (block.isReplaceable && !block.isLiquid)) && block.getRelative(BlockFace.DOWN).type.isSolid

    /** Place a dormant mimic facing [facing]. */
    fun place(block: Block, facing: BlockFace): Boolean {
        if (!canRestIn(block)) return false
        val data = Material.CHEST.createBlockData { (it as ChestData).apply { this.facing = facing; type = ChestData.Type.SINGLE } }
        block.setBlockData(data, true)
        val state = block.state as? Chest ?: return false
        state.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
        state.update(true, false)
        return true
    }

    /** Free floor next to [position], nearest first: the cell it stands over, then its neighbours. */
    fun restCellNear(world: World, position: Vector): Block? {
        val ground = world.raycastGround(position, DOWN_VECTOR, 4.0)?.hitPosition ?: return null
        val y = ground.y.roundToInt()
        val x = floor(position.x).toInt()
        val z = floor(position.z).toInt()
        return (-1..1).flatMap { dx -> (-1..1).map { dz -> world.getBlockAt(x + dx, y, z + dz) } }
            .filter { abs(ground.y - y) < .02 || it.x != x || it.z != z }
            .sortedBy { Vector(it.x + .5, position.y, it.z + .5).distanceSquared(position) }
            .firstOrNull(::canRestIn)
    }

    fun wake(block: Block, waker: Player?): Boolean {
        val world = block.world
        val chest = block.getState(false) as? Chest ?: return false
        if (AppState.spiderCount() >= AppState.MAX_SPIDERS) {
            world.playSound(block.location.toCenterLocation().toVector(), Sound.BLOCK_CHEST_LOCKED, .6f, .7f)
            if (waker != null && StaffAccess.canUse(waker)) waker.sendActionBar(Component.text(
                "The mimic stirs, but ${AppState.MAX_SPIDERS} creatures are already awake.", NamedTextColor.GRAY))
            return false
        }

        val facing = (block.blockData as? Directional)?.facing ?: BlockFace.SOUTH
        val loot = chest.blockInventory.contents.filterNotNull().map { it.clone() }
        chest.blockInventory.clear()
        val blockData = block.blockData
        block.setType(Material.AIR, true)

        val floor = Vector(block.x + .5, block.y.toDouble(), block.z + .5)
        AppState.spawnMimic(world, floor, facing.direction, waker?.uniqueId) ?: return false

        val centre = floor.clone().add(Vector(0.0, MimicShape.RESTING_HEIGHT, 0.0))
        world.playSound(centre, Sound.BLOCK_CHEST_OPEN, 1f, .6f)
        world.playSound(centre, Sound.ENTITY_HOGLIN_ANGRY, .5f, 1.4f)
        world.spawnParticle(Particle.BLOCK, centre.toLocation(world), 24, .3, .3, .3, 0.0, blockData)

        // Anything stuffed inside is spat out of its mouth.
        val mouth = centre.clone().add(Vector(0.0, .4, 0.0))
        for (item in loot) world.dropItem(mouth.toLocation(world), item) {
            it.velocity = facing.direction.multiply(.25).add(Vector(Random.nextDouble(-.08, .08), .3, Random.nextDouble(-.08, .08)))
        }
        return true
    }

    /** Become a chest again at the chosen cell. */
    internal fun rest(entity: ECSEntity, body: SpiderBody, mimic: Mimic): Boolean {
        val cell = mimic.restCell?.takeIf(::canRestIn) ?: restCellNear(body.world, body.position) ?: return false
        if (!place(cell, mimic.restFacing)) return false
        body.world.playSound(cell.location.toCenterLocation().toVector(), Sound.BLOCK_CHEST_CLOSE, .8f, .7f)
        entity.remove()
        return true
    }

    /** Server stopping: drop straight back into chest form so the mimic isn't lost. */
    internal fun restNow(body: SpiderBody): Boolean {
        val cell = restCellNear(body.world, body.position) ?: return false
        return place(cell, cardinalFacing(body.forwardDirection()))
    }
}

fun setupMimic(app: ECS) {
    addEventListener(MimicChestListener())

    onPluginShutdown {
        for ((entity, body) in app.query<ECSEntity, SpiderBody>()) {
            if (entity.scheduledForRemoval || entity.query<Mimic>() == null) continue
            if (!MimicChests.restNow(body)) currentPlugin.logger.warning(
                "A waking mimic at ${body.position.blockX}, ${body.position.blockY}, ${body.position.blockZ} had no room to become a chest.")
        }
    }

    app.onTick {
        for ((entity, body, options, mimic) in app.query<ECSEntity, SpiderBody, SpiderOptions, Mimic>()) {
            if (entity.scheduledForRemoval) continue
            mimic.update(entity, body, options, entity.query<Locomotion>())
        }
    }
}

private class MimicChestListener : Listener {
    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.LEFT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (!MimicChests.isMimic(block)) return
        // staff in creative mode can break a dormant mimic to remove it
        if (event.action == Action.LEFT_CLICK_BLOCK && event.player.gameMode == GameMode.CREATIVE && StaffAccess.canUse(event.player)) return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        if (event.hand != EquipmentSlot.HAND) return
        MimicChests.wake(block, event.player)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onBreak(event: BlockBreakEvent) {
        if (!MimicChests.isMimic(event.block)) return
        if (!StaffAccess.canUse(event.player)) {
            event.isCancelled = true
            return
        }
        event.isDropItems = false
        event.player.sendActionBar(Component.text("Dormant mimic removed.", NamedTextColor.GRAY))
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf(MimicChests::isMimic)
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf(MimicChests::isMimic)
    }

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (MimicChests.isMimic(event.inventory.getHolder(false))) event.isCancelled = true
    }

    @EventHandler
    fun onHopper(event: InventoryMoveItemEvent) {
        if (MimicChests.isMimic(event.destination.getHolder(false)) || MimicChests.isMimic(event.source.getHolder(false))) {
            event.isCancelled = true
        }
    }
}
