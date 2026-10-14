package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.body.setupSpiderBody
import com.heledron.spideranimation.spider.configuration.PounceOptions
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.spider.presets.MimicPose
import com.heledron.spideranimation.spider.presets.MimicShape
import com.heledron.spideranimation.spider.presets.mimicBodyPlan
import com.heledron.spideranimation.spider.presets.mimicGallopGait
import com.heledron.spideranimation.spider.presets.mimicWalkGait
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.maths.toRadians
import org.bukkit.Material
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MimicTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }

    private val px = MimicShape.PX

    private fun corners(piece: BlockDisplayModelPiece): List<Vector3f> =
        (0..7).map { piece.transform.transformPosition(Vector3f((it and 1).toFloat(), (it shr 1 and 1).toFloat(), (it shr 2 and 1).toFloat())) }

    @Test
    fun `shut mimic fits a vanilla chest and hides its mouth`() {
        val plan = mimicBodyPlan()
        MimicPose(plan.bodyModel).apply(0f, 0f, 0f)

        for (piece in plan.bodyModel.pieces) {
            val points = corners(piece)
            if (MimicShape.TONGUE in piece.tags) {
                // collapsed into the seam between lid and base
                for (point in points) assertTrue(point.distance(MimicShape.TONGUE_ROOT) < .01f, "tongue shows at $point")
                continue
            }
            for (point in points) {
                // the latch stands 1 px proud of the front, like vanilla's
                assertTrue(point.x in -MimicShape.HALF - px..MimicShape.HALF + px, "${piece.block.material} x=${point.x}")
                assertTrue(point.z in -MimicShape.HALF - px..MimicShape.HALF + px, "${piece.block.material} z=${point.z}")
                assertTrue(point.y in -MimicShape.HALF - .1f * px..MimicShape.HALF + .1f * px, "${piece.block.material} y=${point.y}")
            }
            // teeth and gullet sit wholly inside the closed box
            if (piece.block.material == Material.BONE_BLOCK || piece.block.material == Material.BLACK_CONCRETE) {
                for (point in points) assertTrue(kotlin.math.abs(point.x) < MimicShape.HALF && kotlin.math.abs(point.z) < MimicShape.HALF,
                    "${piece.block.material} pokes out at $point")
            }
        }
    }

    @Test
    fun `opening raises the latch and pushes the tongue over the lip`() {
        val plan = mimicBodyPlan()
        val latch = plan.bodyModel.pieces.single { it.block.material == Material.IRON_BLOCK }
        val tongue = plan.bodyModel.pieces.filter { MimicShape.TONGUE in it.tags }
        val pose = MimicPose(plan.bodyModel)

        pose.apply(0f, 0f, 0f)
        val shutLatch = corners(latch).minOf { it.y }
        pose.apply(60f.toRadians(), 1f, 0f)
        assertTrue(corners(latch).minOf { it.y } > shutLatch + .5f, "lid did not swing up")
        assertTrue(tongue.flatMap(::corners).maxOf { it.z } > MimicShape.HALF + px, "tongue stays in the mouth")

        // shutting again restores the exact chest pose
        pose.apply(0f, 0f, 0f)
        assertEquals(shutLatch, corners(latch).minOf { it.y }, 1e-5f)
    }

    @Test
    fun `four legs with the front pair ahead`() {
        val plan = mimicBodyPlan()
        assertEquals(4, plan.legs.size)
        assertTrue(plan.legs.take(2).all { it.restPosition.z > 0 } && plan.legs.drop(2).all { it.restPosition.z < 0 })
        assertTrue(plan.legs.all { it.segments.size == 3 })
    }

    @Test
    fun `stands up from chest height, walks, and sits back down`() {
        val world = Room { x, y, z -> x in -20..20 && z in -20..40 && y in -3..-1 }.world()
        val walk = mimicWalkGait()
        val gallop = mimicGallopGait()
        val plan = mimicBodyPlan()
        val options = mock(SpiderOptions::class.java)
        `when`(options.gait).thenReturn(walk)
        `when`(options.walkGait).thenReturn(walk)
        `when`(options.gallopGait).thenReturn(gallop)
        `when`(options.pounce).thenReturn(PounceOptions())
        `when`(options.bodyPlan).thenReturn(plan)
        applyMimicRise(options, 0.0)

        val app = ECS()
        setupSpiderBody(app)
        setupLocomotion(app)
        setupPounce(app)
        val body = SpiderBody.create(world, Vector(.5, MimicShape.RESTING_HEIGHT, .5))
        val locomotion = Locomotion()
        app.spawn(body, options, locomotion)

        fun up() = UP_VECTOR.rotate(body.orientation)
        fun tick(times: Int = 1, each: (Int) -> Unit = {}) = repeat(times) {
            each(it)
            app.update()
            assertTrue(listOf(body.position.x, body.position.y, body.position.z).all(Double::isFinite))
        }

        tick(10)
        assertEquals(MimicShape.RESTING_HEIGHT, body.position.y, .1, "sank or jumped as a chest")
        assertTrue(kotlin.math.abs(body.position.x - .5) < .05 && kotlin.math.abs(body.position.z - .5) < .05, "shuffled while asleep")

        tick(Mimic.WAKE_TICKS) { applyMimicRise(options, smoothstep((it + 1.0) / Mimic.WAKE_TICKS)) }
        tick(30)
        assertEquals(MimicShape.STANDING_HEIGHT, body.position.y, .12, "did not stand up")
        assertEquals(4, body.legs.count { it.isGrounded() })
        assertTrue(up().y > .97)

        tick(80) { locomotion.walk(body.forwardDirection().multiply(walk.maxSpeed)) }
        assertTrue(body.position.z > 3.5, "barely moved: ${body.position}")
        assertTrue(up().y > .95)
        assertEquals(MimicShape.STANDING_HEIGHT, body.position.y, .25)

        tick(20)
        tick(Mimic.CROUCH_TICKS) { applyMimicRise(options, 1 - smoothstep((it + 1.0) / Mimic.CROUCH_TICKS)) }
        tick(20)
        assertEquals(MimicShape.RESTING_HEIGHT, body.position.y, .1, "did not sit down")
        assertTrue(up().y > .97)
    }
}
