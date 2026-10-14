package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.configuration.OrientationMode
import org.bukkit.World
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.math.floor
import kotlin.test.assertTrue

/** Runs the real body, leg and locomotion code against a small voxel room. */
class ClimbingTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }


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

    /** Same placement as /spider spawn aimed at a face: oriented to the face, raised along its normal. */
    private fun spawnOnFace(world: World, hit: Vector, normal: Vector, look: Vector): Run {
        val orientation = SpiderBody.orientationFrom(normal, look, Vector(0.0, 0.0, 1.0))
        val run = spawn(world, 0.8, gallop = false, Vector(), orientation = orientation)
        run.body.teleport(com.heledron.spideranimation.utilities.ecs.ECSEntity(),
            hit.clone().add(normal.clone().multiply(run.gait.stationary.bodyHeight)))
        return run
    }

    @Test
    fun `a spider spawned on a wall clings and climbs to the ceiling`() {
        // aimed horizontally at the wall's face (z = 10, facing -z)
        val run = spawnOnFace(Room(::wallAndCeiling).world(), Vector(.5, 3.0, 10.0), Vector(0.0, 0.0, -1.0), Vector(0.0, 0.0, 1.0))
        assertTrue(run.up().z < -.99, "Not facing out of the wall: ${run.up()}")
        val trace = StringBuilder()
        var onCeiling = false
        run.walkForward(1200) { tick ->
            if (tick % 40 == 0) trace.append(run.describe(tick))
            if (run.up().y < -.9) onCeiling = true
            onCeiling
        }
        assertTrue(onCeiling, "Never reached the ceiling from the wall:\n$trace${run.describe(-1)}")
    }

    @Test
    fun `a spider spawned on the ceiling hangs and walks along it`() {
        val run = spawnOnFace(Room(::wallAndCeiling).world(), Vector(.5, 8.0, 0.5), Vector(0.0, -1.0, 0.0), Vector(0.0, .3, -1.0))
        val trace = StringBuilder()
        run.walkForward(300) { tick -> if (tick % 40 == 0) trace.append(run.describe(tick)); false }
        assertTrue(run.up().y < -.9, "Fell off the ceiling:\n$trace${run.describe(-1)}")
        assertTrue(run.body.position.y > 6.5, "Hung too low:\n$trace${run.describe(-1)}")
        assertTrue(run.body.position.z < -5, "Did not walk along the ceiling:\n$trace${run.describe(-1)}")
    }

    @Test
    fun `turning climbing off on the ceiling drops the spider back onto its feet`() {
        val run = spawn(Room(::wallAndCeiling).world(), 0.8, gallop = false, Vector(.5, 0.0, 2.5))
        var ceilingStart: Vector? = null
        run.walkForward(2400) {
            if (ceilingStart == null && run.up().y < -.9 && run.body.position.y > 6) ceilingStart = run.body.position.clone()
            ceilingStart.let { it != null && it.z - run.body.position.z > 3 }
        }
        assertTrue(ceilingStart != null, "Never reached the ceiling: ${run.describe(-1)}")

        run.gait.grip = false
        run.gait.scanOrientation = OrientationMode.YAxis
        val trace = StringBuilder()
        repeat(400) { tick ->
            run.app.update()
            if (tick % 20 == 0) trace.append(run.describe(tick))
        }
        assertTrue(run.body.position.y < 2.0, "Still on the ceiling:\n$trace${run.describe(-1)}")
        assertTrue(run.up().y > .9, "Did not land upright:\n$trace${run.describe(-1)}")
        assertTrue(run.body.legs.count { it.isGrounded() } >= 4, "Not standing on the floor:\n$trace${run.describe(-1)}")
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
