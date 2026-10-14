package net.tfminecraft.tfmcspiders.spider.components

import net.tfminecraft.tfmcspiders.AppState
import net.tfminecraft.tfmcspiders.StaffAccess
import net.tfminecraft.tfmcspiders.spider.components.body.SpiderBody
import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.spider.presets.MimicPose
import net.tfminecraft.tfmcspiders.spider.presets.MimicShape
import net.tfminecraft.tfmcspiders.utilities.currentPlugin
import net.tfminecraft.tfmcspiders.utilities.currentTick
import net.tfminecraft.tfmcspiders.utilities.ecs.ECS
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSComponent
import net.tfminecraft.tfmcspiders.utilities.ecs.ECSEntity
import net.tfminecraft.tfmcspiders.utilities.events.addEventListener
import net.tfminecraft.tfmcspiders.utilities.maths.DOWN_VECTOR
import net.tfminecraft.tfmcspiders.utilities.maths.lerp
import net.tfminecraft.tfmcspiders.utilities.maths.moveTowards
import net.tfminecraft.tfmcspiders.utilities.maths.toRadians
import net.tfminecraft.tfmcspiders.utilities.namespacedID
import net.tfminecraft.tfmcspiders.utilities.onPluginShutdown
import net.tfminecraft.tfmcspiders.utilities.overloads.playSound
import net.tfminecraft.tfmcspiders.utilities.raycastGround
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.bukkit.block.data.type.Chest as ChestData

enum class MimicState { WAKING, FLEEING, AWAKE, SETTLING, CROUCHING }

/**
 * A chest that stands up when touched, screeches, gallops [Mimic.FLEE_DISTANCE] blocks away from
 * whoever woke it, then settles back into a chest.
 *
 * @param threat where the mimic is fleeing from, in its own world; defaults to the waker's position.
 */
class Mimic(
    val pose: MimicPose,
    val waker: UUID?,
    private val threat: ((SpiderBody) -> Vector?)? = null,
) : ECSComponent {
    var state = MimicState.WAKING; private set
    var stateTicks = 0; private set
    /** 0 = sitting as a chest, 1 = standing. */
    var rise = 0.0; private set
    var lidAngle = 0f; private set

    private var chompTick = -1
    private var nextChomp = Random.nextInt(20, 40)
    var idleTicks = 0; private set

    /** Last place the threat was seen; the mimic keeps running from it if the player leaves. */
    private var lastThreat: Vector? = null
    /** Turn off the straight escape line, used to get round whatever it's stuck on. */
    private var detour = 0.0
    private var fleeDirection: Vector? = null
    private var progressCheck: Vector? = null
    private var stuckChecks = 0

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
                // already turning its back on whoever woke it
                threatPosition(body)?.let { locomotion?.face(escapeDirection(body, it), PRIORITY) }
                if (stateTicks >= WAKE_TICKS) {
                    if (threatPosition(body) != null && rider == null) {
                        options.gallop = true
                        enter(MimicState.FLEEING)
                    } else {
                        enter(MimicState.AWAKE)
                    }
                }
            }

            MimicState.FLEEING -> {
                rise = 1.0
                // the lid flaps as it runs
                lidTarget = (30f + 18f * sin(currentTick * .9f)).toRadians()
                val from = threatPosition(body)
                val direction = from?.let { escapeDirection(body, it) }
                fleeDirection = direction
                val escaped = from == null || horizontalDistance(body.position, from) >= FLEE_DISTANCE
                if (rider != null || escaped || direction == null || !groundAheadExists(body, direction)) {
                    fleeDirection = null
                    options.gallop = false
                    if (rider != null || !beginSettling(body)) enter(MimicState.AWAKE)
                } else {
                    locomotion?.face(direction, PRIORITY)
                    locomotion?.walk(direction.clone().multiply(options.gait.maxSpeed), PRIORITY)
                    checkProgress(body)
                }
            }

            MimicState.AWAKE -> {
                rise = 1.0
                lidTarget = (22f + 4f * sin(currentTick * .15f)).toRadians()

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
                    lastThreat = null
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

    private fun threatPosition(body: SpiderBody): Vector? {
        val seen = if (threat != null) threat.invoke(body) else waker?.let(Bukkit::getPlayer)
            ?.takeIf { it.world == body.world && !it.isDead }?.location?.toVector()
        if (seen != null) lastThreat = seen.clone()
        return lastThreat
    }

    /** Straight away from the threat across the ground, turned by [detour] while working round an obstacle. */
    private fun escapeDirection(body: SpiderBody, from: Vector): Vector {
        val away = body.position.clone().subtract(from).setY(0)
        if (away.lengthSquared() < 1e-6) away.copy(body.forwardDirection().setY(0))
        if (away.lengthSquared() < 1e-6) away.copy(Vector(0, 0, 1))
        return away.normalize().rotateAroundY(detour)
    }

    /** Every two seconds: if it has barely moved, try a wider detour, alternating sides. */
    private fun checkProgress(body: SpiderBody) {
        if (stateTicks % PROGRESS_CHECK_TICKS != 0) return
        val previous = progressCheck
        progressCheck = body.position.clone()
        if (previous == null) return
        if (horizontalDistance(previous, body.position) < MIN_PROGRESS) {
            stuckChecks++
            val side = if (stuckChecks % 2 == 1) 1 else -1
            detour = side * DETOUR_STEP * ((stuckChecks + 1) / 2).coerceAtMost(3)
        } else if (stuckChecks > 0) {
            stuckChecks = 0
            detour = 0.0
        }
    }

    private fun pointAhead(body: SpiderBody, direction: Vector) =
        body.position.clone().add(direction.clone().multiply(LOOK_AHEAD))

    /** It runs on through chunks the plugin keeps loaded for it, but never into terrain that hasn't been generated. */
    private fun groundAheadExists(body: SpiderBody, direction: Vector): Boolean {
        val ahead = pointAhead(body, direction)
        return body.world.isChunkGenerated(floor(ahead.x).toInt() shr 4, floor(ahead.z).toInt() shr 4)
    }

    /** Chunks to keep loaded: the one it stands in and, while fleeing, the one it's running into. */
    internal fun wantedChunks(body: SpiderBody): List<Long> {
        val chunks = mutableListOf(chunkKey(body.position))
        val direction = fleeDirection
        if (state == MimicState.FLEEING && direction != null && groundAheadExists(body, direction)) {
            chunks += chunkKey(pointAhead(body, direction))
        }
        return chunks
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
        const val FLEE_DISTANCE = 250.0
        const val PROGRESS_CHECK_TICKS = 40
        const val MIN_PROGRESS = 1.5
        const val DETOUR_STEP = PI / 4
        const val LOOK_AHEAD = 12.0
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

private fun chunkKey(position: Vector): Long {
    val x = floor(position.x).toInt() shr 4
    val z = floor(position.z).toInt() shr 4
    return (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
}

/** Plugin chunk tickets held for running mimics, so they aren't stranded where no player is. */
private object MimicChunkTickets {
    private val held = mutableMapOf<World, MutableSet<Long>>()

    fun sync(app: ECS) {
        val wanted = mutableMapOf<World, MutableSet<Long>>()
        for ((entity, body, mimic) in app.query<ECSEntity, SpiderBody, Mimic>()) {
            if (entity.scheduledForRemoval) continue
            wanted.getOrPut(body.world) { mutableSetOf() } += mimic.wantedChunks(body)
        }
        for (world in held.keys + wanted.keys) {
            val have = held.getOrPut(world) { mutableSetOf() }
            val want = wanted[world].orEmpty()
            for (key in want - have) world.addPluginChunkTicket((key shr 32).toInt(), key.toInt(), currentPlugin)
            for (key in have - want) world.removePluginChunkTicket((key shr 32).toInt(), key.toInt(), currentPlugin)
            have.retainAll(want)
            have += want
        }
    }
}

private fun horizontalDistance(a: Vector, b: Vector) = Vector(a.x - b.x, 0.0, a.z - b.z).length()

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

    private const val SCREECH_RANGE = 80.0
    private const val SECOND_SHRIEK_TICKS = 6L
    private val SCREECH = listOf(
        Sound.ENTITY_FOX_SCREECH to .7f,
        Sound.ENTITY_GHAST_SCREAM to 1.6f,
        Sound.ENTITY_ENDERMAN_SCREAM to .8f,
        Sound.ENTITY_PHANTOM_DEATH to .6f,
    )

    /**
     * A sound can't play louder than volume 1 (higher only widens its range), so the screech is
     * layered, and each player within range also hears it at full volume from just beside them,
     * towards the mimic, so it stays loud far off and still comes from the right direction.
     */
    private fun screech(world: World, centre: Vector, pitch: Float = 1f) {
        val location = centre.toLocation(world)
        for ((sound, soundPitch) in SCREECH) world.playSound(location, sound, SoundCategory.HOSTILE, 5f, soundPitch * pitch)
        for (player in world.players) {
            val ear = player.eyeLocation.toVector()
            val toMimic = centre.clone().subtract(ear)
            val distance = toMimic.length()
            if (distance > SCREECH_RANGE) continue
            val source = if (distance < 2) centre else ear.add(toMimic.multiply(2 / distance))
            repeat(2) {
                for ((sound, soundPitch) in SCREECH) {
                    player.playSound(source.toLocation(world), sound, SoundCategory.HOSTILE, 1f, soundPitch * pitch)
                }
            }
        }
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
        world.playSound(centre.toLocation(world), Sound.BLOCK_CHEST_OPEN, SoundCategory.HOSTILE, 1.5f, .6f)
        screech(world, centre)
        // a second, higher shriek on its heels
        Bukkit.getScheduler().runTaskLater(currentPlugin, Runnable { screech(world, centre, pitch = 1.15f) }, SECOND_SHRIEK_TICKS)
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
        MimicChunkTickets.sync(app)
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
