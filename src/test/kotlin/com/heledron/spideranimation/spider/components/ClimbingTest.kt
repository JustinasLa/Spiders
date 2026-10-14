package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.body.setupSpiderBody
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.OrientationMode
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.cinderweaverBodyPlan
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.rotate
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real body, leg and locomotion code against a small voxel room. */
class ClimbingTest {
    companion object {
        private lateinit var bukkit: MockedStatic<Bukkit>

        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() {
            bukkit = mockStatic(Bukkit::class.java)
            bukkit.`when`<BlockData> { Bukkit.createBlockData(any(Material::class.java)) }.thenAnswer { blockData(it.getArgument(0)) }
            bukkit.`when`<BlockData> { Bukkit.createBlockData(anyString()) }.thenAnswer {
                blockData(Material.matchMaterial(it.getArgument<String>(0).substringBefore('['))!!)
            }
        }

        @JvmStatic
        @AfterAll
        fun restoreBukkit() { if (::bukkit.isInitialized) bukkit.close() }

        private fun blockData(material: Material): BlockData {
            val data = mock(BlockData::class.java)
            `when`(data.material).thenReturn(material)
            `when`(data.clone()).thenAnswer { blockData(material) }
            return data
        }
    }

    /** Floor at y < 0, a wall at z >= 10 and a ceiling at y >= 8, open towards -z. */
    private fun wallAndCeiling(x: Int, y: Int, z: Int): Boolean =
        x in -12..12 && z in -30..13 && y in -2..10 && (y < 0 || z >= 10 || y >= 8)

    private class Room(val isSolid: (x: Int, y: Int, z: Int) -> Boolean) {
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

    private class Run(val app: ECS, val body: SpiderBody, val locomotion: Locomotion, val gait: Gait)

    private fun spawn(
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
        val options = mock(SpiderOptions::class.java)
        `when`(options.gait).thenReturn(gait)
        `when`(options.bodyPlan).thenReturn(plan)

        val app = ECS()
        setupSpiderBody(app)
        setupLocomotion(app)
        val body = SpiderBody.create(world, start.clone().add(Vector(0.0, gait.stationary.bodyHeight, 0.0)), orientation)
        val locomotion = Locomotion()
        app.spawn(body, options, locomotion)
        return Run(app, body, locomotion, gait)
    }

    /** Walks forward until [onTick] returns true or the ticks run out. */
    private fun Run.walkForward(ticks: Int, onTick: (Int) -> Boolean = { false }) {
        for (tick in 0 until ticks) {
            locomotion.walk(body.forwardDirection().multiply(gait.maxSpeed))
            app.update()
            assertTrue(listOf(body.position.x, body.position.y, body.position.z).all { it.isFinite() }, "Body left the finite world")
            if (onTick(tick)) return
        }
    }

    private fun Run.up() = UP_VECTOR.rotate(body.orientation)

    private fun Run.describe(tick: Int) =
        "t=$tick pos=${body.position} up=${up()} grounded=${body.legs.count { it.isGrounded() }}\n"

    /** The wall-and-ceiling room turned so that the wall lies [degrees] clockwise from +z. */
    private fun climbRoom(scale: Double, gallop: Boolean, degrees: Int = 0) {
        val angle = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        fun local(x: Double, z: Double) = Pair(x * cos - z * sin, x * sin + z * cos)
        val world = Room { x, y, z ->
            val (lx, lz) = local(x + .5, z + .5)
            wallAndCeiling(floor(lx).toInt(), y, floor(lz).toInt())
        }.world()
        val facing = Vector(sin, 0.0, cos)
        val start = Vector(.5 * cos + 2.5 * sin, 0.0, -.5 * sin + 2.5 * cos)
        val run = spawn(world, scale, gallop, start, orientation = Quaternionf().rotationY(angle.toFloat()))

        var reachedWall = false
        var ceilingStart: Vector? = null
        var fell = false
        val trace = StringBuilder()
        fun progress(from: Vector) = from.clone().subtract(run.body.position).dot(facing)
        run.walkForward(2400) { tick ->
            val up = run.up()
            if (tick % 40 == 0) trace.append(run.describe(tick))
            if (up.dot(facing) < -.9) reachedWall = true
            if (reachedWall && up.y < -.9 && ceilingStart == null) ceilingStart = run.body.position.clone()
            val from = ceilingStart
            if (from != null && up.y > -.5) fell = true
            // done once it has hung from the ceiling for five blocks
            from != null && progress(from) > 5
        }
        val label = "scale=$scale gallop=$gallop degrees=$degrees\n$trace${run.describe(-1)}"
        assertTrue(reachedWall, "Never stood on the wall: $label")
        assertTrue(ceilingStart != null, "Never reached the ceiling: $label")
        assertTrue(!fell, "Lost the ceiling: $label")
        assertTrue(progress(ceilingStart!!) > 5, "Did not walk along the ceiling: $label")
        assertTrue(run.body.position.y > 8 - 2.5 * scale, "Hung too far below the ceiling: $label")
        assertTrue(run.body.legs.count { it.isGrounded() } >= 3, "Legs lost the ceiling: $label")
    }

    @Test
    fun `hunter walks up a wall and along the ceiling`() = climbRoom(0.8, gallop = false)

    @Test
    fun `hatchling walks up a wall and along the ceiling`() = climbRoom(0.35, gallop = false)

    @Test
    fun `broodmother walks up a wall and along the ceiling`() = climbRoom(1.5, gallop = false)

    @Test
    fun `smallest spider climbs`() = climbRoom(0.25, gallop = false)

    @Test
    fun `largest spider climbs`() = climbRoom(2.5, gallop = false)

    @Test
    fun `galloping spiders climb too`() {
        for (scale in listOf(0.35, 0.8, 1.5)) climbRoom(scale, gallop = true)
    }

    @Test
    fun `walls facing every direction are climbable`() {
        for (degrees in listOf(90, 180, 270)) climbRoom(0.8, gallop = false, degrees = degrees)
    }

    @Test
    fun `without climbing the spider stays upright at the wall`() {
        val run = spawn(Room(::wallAndCeiling).world(), 0.8, gallop = false, Vector(.5, 0.0, 2.5), climbing = false)
        run.walkForward(600) { false }
        assertTrue(run.up().y > .9, "Tipped over: ${run.up()}")
    }

    @Test
    fun `climbing spider walks over a ledge and down the far face`() {
        // a raised platform (top at y = 4) ending at z = 0, above the floor at y = -4
        val world = Room { x, y, z -> x in -12..12 && z in -30..30 && y in -6..10 && (y < -4 || (z < 0 && y < 4)) }.world()
        val run = spawn(world, 0.8, gallop = false, Vector(.5, 4.0, -6.5))
        var wentOverEdge = false
        var lowest = Double.MAX_VALUE
        val trace = StringBuilder()
        run.walkForward(1600) { tick ->
            if (tick % 40 == 0) trace.append(run.describe(tick))
            if (run.up().z > .9) wentOverEdge = true
            lowest = minOf(lowest, run.body.position.y)
            wentOverEdge && run.body.position.y < 1.0
        }
        assertTrue(wentOverEdge, "Never wrapped onto the vertical face:\n$trace")
        assertTrue(run.body.position.y < 1.0, "Did not descend the face:\n$trace")
        assertTrue(lowest > -4.0, "Fell to the floor:\n$trace")
        assertTrue(run.up().z > .9, "Not clinging to the face:\n$trace${run.describe(-1)}")
        assertTrue(run.body.position.z in 0.0..2.0, "Drifted away from the face:\n$trace${run.describe(-1)}")
    }
}
