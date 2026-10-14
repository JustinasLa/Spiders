package net.tfminecraft.spiders.spider.components

import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.components.body.setupSpiderBody
import net.tfminecraft.spiders.spider.configuration.Gait
import net.tfminecraft.spiders.spider.configuration.OrientationMode
import net.tfminecraft.spiders.spider.configuration.PounceOptions
import net.tfminecraft.spiders.spider.configuration.SpiderOptions
import net.tfminecraft.spiders.spider.presets.cinderweaverBodyPlan
import net.tfminecraft.spiders.utilities.ecs.ECS
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.maths.UP_VECTOR
import net.tfminecraft.spiders.utilities.maths.rotate
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.assertTrue

// Voxel-world harness that runs the real body, leg and locomotion code without a server.

/** Block data without a server registry. Install before building body plans. */
internal object BlockDataMocks {
    private var bukkit: MockedStatic<Bukkit>? = null

    fun install() {
        bukkit = mockStatic(Bukkit::class.java).also {
            it.`when`<BlockData> { Bukkit.createBlockData(any(Material::class.java)) }.thenAnswer { call -> blockData(call.getArgument(0)) }
            it.`when`<BlockData> { Bukkit.createBlockData(anyString()) }.thenAnswer { call ->
                blockData(Material.matchMaterial(call.getArgument<String>(0).substringBefore('['))!!)
            }
        }
    }

    fun restore() {
        bukkit?.close()
        bukkit = null
    }

    private fun blockData(material: Material): BlockData {
        val data = mock(BlockData::class.java)
        `when`(data.material).thenReturn(material)
        `when`(data.clone()).thenAnswer { blockData(material) }
        return data
    }
}

/** Floor at y < 0, a wall at z >= 10 and a ceiling at y >= 8, open towards -z. */
internal fun wallAndCeiling(x: Int, y: Int, z: Int): Boolean =
    x in -12..12 && z in -30..13 && y in -2..10 && (y < 0 || z >= 10 || y >= 8)

internal class Room(val isSolid: (x: Int, y: Int, z: Int) -> Boolean) {
    fun world(): World {
        val world = mock(World::class.java)
        `when`(world.getBlockAt(any(Location::class.java))).thenAnswer { block(it.getArgument(0)) }
        `when`(world.rayTraceBlocks(any(Location::class.java), any(Vector::class.java), anyDouble(),
            eq(FluidCollisionMode.NEVER), eq(true))).thenAnswer { call ->
            rayTrace(call.getArgument<Location>(0).toVector(), call.getArgument(1), call.getArgument(2))
        }
        return world
    }

    private fun block(location: Location): Block {
        val block = mock(Block::class.java)
        val solid = isSolid(floor(location.x).toInt(), floor(location.y).toInt(), floor(location.z).toInt())
        `when`(block.isPassable).thenReturn(!solid)
        return block
    }

    /** Mirrors vanilla clipping, including a hit at the start when it begins inside a block. */
    fun rayTrace(start: Vector, direction: Vector, maxDistance: Double): RayTraceResult? {
        assertTrue(listOf(start.x, start.y, start.z, direction.x, direction.y, direction.z).all { it.isFinite() })
        val dir = direction.clone().normalize()
        if (isSolid(floor(start.x).toInt(), floor(start.y).toInt(), floor(start.z).toInt())) {
            return RayTraceResult(start.clone(), faceAgainst(dir))
        }
        val step = 0.01
        var travelled = 0.0
        var previous = start.clone()
        while (travelled < maxDistance) {
            travelled = (travelled + step).coerceAtMost(maxDistance)
            val point = start.clone().add(dir.clone().multiply(travelled))
            val bx = floor(point.x).toInt(); val by = floor(point.y).toInt(); val bz = floor(point.z).toInt()
            if (isSolid(bx, by, bz)) return entry(previous, dir, bx, by, bz)
            previous = point
        }
        return null
    }

    private fun entry(from: Vector, dir: Vector, bx: Int, by: Int, bz: Int): RayTraceResult {
        var best = Double.MAX_VALUE
        var face = BlockFace.UP
        fun test(axis: Int, plane: Double, hitFace: BlockFace) {
            val d = when (axis) { 0 -> dir.x; 1 -> dir.y; else -> dir.z }
            if (abs(d) < 1e-12) return
            val o = when (axis) { 0 -> from.x; 1 -> from.y; else -> from.z }
            val t = (plane - o) / d
            if (t < -1e-9 || t > best) return
            val p = from.clone().add(dir.clone().multiply(t))
            val inside = (axis == 0 || p.x in bx - 1e-6..bx + 1 + 1e-6) &&
                (axis == 1 || p.y in by - 1e-6..by + 1 + 1e-6) &&
                (axis == 2 || p.z in bz - 1e-6..bz + 1 + 1e-6)
            if (inside) { best = t; face = hitFace }
        }
        if (dir.x > 0) test(0, bx.toDouble(), BlockFace.WEST) else test(0, bx + 1.0, BlockFace.EAST)
        if (dir.y > 0) test(1, by.toDouble(), BlockFace.DOWN) else test(1, by + 1.0, BlockFace.UP)
        if (dir.z > 0) test(2, bz.toDouble(), BlockFace.NORTH) else test(2, bz + 1.0, BlockFace.SOUTH)
        if (best == Double.MAX_VALUE) best = 0.0
        return RayTraceResult(from.clone().add(dir.clone().multiply(best)), face)
    }

    private fun faceAgainst(dir: Vector): BlockFace {
        val ax = abs(dir.x); val ay = abs(dir.y); val az = abs(dir.z)
        return when {
            ay >= ax && ay >= az -> if (dir.y > 0) BlockFace.DOWN else BlockFace.UP
            ax >= az -> if (dir.x > 0) BlockFace.WEST else BlockFace.EAST
            else -> if (dir.z > 0) BlockFace.NORTH else BlockFace.SOUTH
        }
    }
}

internal class Run(
    val app: ECS, val entity: ECSEntity, val body: SpiderBody, val locomotion: Locomotion,
    val gait: Gait, val options: SpiderOptions,
)

/** A Cinderweaver body on [start] whose options always use one gait (gallop or walk). */
internal fun spawn(
    world: World, scale: Double, gallop: Boolean, start: Vector,
    climbing: Boolean = true, orientation: Quaternionf = Quaternionf(),
): Run {
    val gait = (if (gallop) Gait.defaultGallop().apply { maxSpeed = .24 } else Gait.defaultWalk().apply { maxSpeed = .12 })
    gait.scale(scale)
    if (climbing) {
        gait.grip = true
        gait.scanOrientation = OrientationMode.GroundOrientation
    }
    val plan = cinderweaverBodyPlan(scale)
    val pounce = PounceOptions()
    val options = mock(SpiderOptions::class.java)
    `when`(options.gait).thenReturn(gait)
    `when`(options.walkGait).thenReturn(gait)
    `when`(options.gallopGait).thenReturn(gait)
    `when`(options.gallop).thenReturn(gallop)
    `when`(options.climbing).thenReturn(climbing)
    `when`(options.pounce).thenReturn(pounce)
    `when`(options.bodyPlan).thenReturn(plan)

    val app = ECS()
    setupSpiderBody(app)
    setupLocomotion(app)
    setupPounce(app)
    val body = SpiderBody.create(world, start.clone().add(Vector(0.0, gait.stationary.bodyHeight, 0.0)), orientation)
    val locomotion = Locomotion()
    val entity = app.spawn(body, options, locomotion)
    return Run(app, entity, body, locomotion, gait, options)
}

/** Walks forward until [onTick] returns true or the ticks run out. */
internal fun Run.walkForward(ticks: Int, onTick: (Int) -> Boolean = { false }) {
    for (tick in 0 until ticks) {
        locomotion.walk(body.forwardDirection().multiply(gait.maxSpeed))
        app.update()
        assertTrue(listOf(body.position.x, body.position.y, body.position.z).all { it.isFinite() }, "Body left the finite world")
        if (onTick(tick)) return
    }
}

internal fun Run.up() = UP_VECTOR.rotate(body.orientation)

internal fun Run.describe(tick: Int) =
    "t=$tick pos=${body.position} up=${up()} grounded=${body.legs.count { it.isGrounded() }}\n"
