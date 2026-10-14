package net.tfminecraft.spiders.spider.components

import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PounceTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }

    /** A wide floor at y < 0. */
    private fun field() = Room { x, y, z -> x in -40..40 && z in -40..120 && y in -3..-1 }.world()

    private fun planar(a: Vector, b: Vector) = Vector(a.x - b.x, 0.0, a.z - b.z).length()

    private class Result(
        val maxHeight: Double, val leapt: Boolean, val ticks: Int, val trace: String,
        val outcome: Pounce.Outcome?, val landedDistance: Double?,
    )

    /** Runs until the pounce ends; returns what happened. */
    private fun Run.pounceAt(target: () -> Vector?, maxTicks: Int = 1200): Result {
        var outcome: Pounce.Outcome? = null
        var landedDistance: Double? = null
        val pounce = Pounce(target) { result, distance ->
            outcome = result
            landedDistance = distance
        }
        entity.addComponent(pounce)
        val restingHeight = body.position.y
        var maxHeight = 0.0
        var leapt = false
        val trace = StringBuilder()
        var tick = 0
        while (entity.query<Pounce>() != null && tick < maxTicks) {
            app.update()
            assertTrue(listOf(body.position.x, body.position.y, body.position.z).all { it.isFinite() })
            if (pounce.phase == Pounce.Phase.AIRBORNE) leapt = true
            maxHeight = maxOf(maxHeight, body.position.y - restingHeight)
            if (tick % 10 == 0) trace.append("${describe(tick).trim()} phase=${pounce.phase} vel=${body.velocity}\n")
            tick++
        }
        return Result(maxHeight, leapt, tick, trace.toString(), outcome, landedDistance)
    }

    @Test
    fun `hunter gallops at a target, leaps and lands on it`() {
        val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
        val target = Vector(.5, 0.0, 20.5)
        val result = run.pounceAt({ target })
        val label = result.trace + run.describe(-1)
        assertTrue(result.leapt, "Never leapt:\n$label")
        assertNull(run.entity.query<Pounce>(), "Pounce did not finish:\n$label")
        assertTrue(result.maxHeight > .4, "The leap barely left the ground (${result.maxHeight}):\n$label")
        assertTrue(planar(run.body.position, target) < 2.0, "Landed too far from the target:\n$label")
        assertEquals(Pounce.Outcome.LANDED, result.outcome, label)
        // the landing is reported where it happened; it may have settled a little since
        assertTrue(result.landedDistance!! < 2.0, "Reported landing distance ${result.landedDistance}")
        assertTrue(run.up().y > .9, "Landed tipped over:\n$label")
        assertTrue(run.body.legs.count { it.isGrounded() } >= 4, "Not standing after landing:\n$label")
    }

    @Test
    fun `every size leaps at a target`() {
        for (scale in listOf(0.25, 0.35, 1.5, 2.5)) {
            val run = spawn(field(), scale, gallop = true, Vector(.5, 0.0, .5), climbing = false)
            val target = Vector(.5, 0.0, 6.5 + 10 * scale)
            val result = run.pounceAt({ target })
            val label = "scale=$scale\n${result.trace}${run.describe(-1)}"
            assertTrue(result.leapt, "Never leapt: $label")
            assertNull(run.entity.query<Pounce>(), "Pounce did not finish: $label")
            assertTrue(planar(run.body.position, target) < 1.0 + 2 * scale, "Landed too far from the target: $label")
            assertTrue(run.up().y > .9, "Landed tipped over: $label")
        }
    }

    @Test
    fun `follows a moving target`() {
        val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
        var tick = 0
        // walks away sideways at 2 blocks per second
        val result = run.pounceAt({ Vector(.5 + .1 * tick++, 0.0, 15.5) })
        val target = Vector(.5 + .1 * tick, 0.0, 15.5)
        assertTrue(result.leapt, result.trace)
        assertTrue(planar(run.body.position, target) < 3.0, "Lost the moving target:\n${result.trace}${run.describe(-1)}")
    }

    @Test
    fun `gallop speed setting changes how fast it runs`() {
        val speeds = listOf(2.0, 4.0, 8.0, 12.0, 16.0).map { blocksPerSecond ->
            val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
            run.gait.setSpeed(blocksPerSecond / 20)
            run.entity.addComponent(Pounce({ Vector(.5, 0.0, 110.5) }))
            repeat(40) { run.app.update() } // get up to speed
            val from = run.body.position.clone()
            repeat(100) { run.app.update() }
            val achieved = planar(run.body.position, from) / 5.0
            assertTrue(achieved > .75 * blocksPerSecond, "Set $blocksPerSecond blocks/s, ran $achieved")
            assertTrue(achieved < 1.1 * blocksPerSecond, "Set $blocksPerSecond blocks/s, ran $achieved")
            achieved
        }
        assertEquals(speeds.sorted(), speeds, "Faster settings must run faster: $speeds")
    }

    @Test
    fun `leap boost and lift are adjustable`() {
        fun leapHeight(lift: Double): Double {
            val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
            run.options.pounce.leapLift = lift
            return run.pounceAt({ Vector(.5, 0.0, 20.5) }).maxHeight
        }
        val low = leapHeight(.3)
        val high = leapHeight(.7)
        assertTrue(high > low + .5, "Higher lift should leap higher: $low vs $high")
    }

    @Test
    fun `gives up when the target is gone`() {
        val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
        var present = true
        var outcome: Pounce.Outcome? = null
        run.entity.addComponent(Pounce({ if (present) Vector(.5, 0.0, 60.5) else null }, { result, _ -> outcome = result }))
        repeat(20) { run.app.update() }
        present = false
        run.app.update()
        assertNull(run.entity.query<Pounce>())
        assertEquals(Pounce.Outcome.TARGET_LOST, outcome)
    }

    @Test
    fun `gives up after the chase time limit`() {
        val run = spawn(field(), 0.8, gallop = true, Vector(.5, 0.0, .5), climbing = false)
        run.options.pounce.giveUpTicks = 30
        var outcome: Pounce.Outcome? = null
        run.entity.addComponent(Pounce({ Vector(.5, 0.0, 110.5) }, { result, _ -> outcome = result }))
        repeat(32) { run.app.update() }
        assertNull(run.entity.query<Pounce>())
        assertEquals(Pounce.Outcome.GAVE_UP, outcome)
    }

    @Test
    fun `a spider on the ceiling drops onto a target below`() {
        val world = Room(::wallAndCeiling).world()
        val run = spawn(world, 0.8, gallop = true, Vector(.5, 0.0, 2.5))
        // climb up the wall and onto the ceiling first
        run.walkForward(2400) { run.up().y < -.9 && run.body.position.z < 7 }
        assertTrue(run.up().y < -.9, "Setup: never reached the ceiling: ${run.describe(-1)}")
        val target = Vector(run.body.position.x, 0.0, run.body.position.z - 3)
        val result = run.pounceAt({ target })
        val label = result.trace + run.describe(-1)
        assertTrue(result.leapt, "Never leapt:\n$label")
        assertTrue(run.body.position.y < 3, "Did not drop to the floor:\n$label")
        assertTrue(planar(run.body.position, target) < 3.0, "Dropped too far from the target:\n$label")
    }
}
