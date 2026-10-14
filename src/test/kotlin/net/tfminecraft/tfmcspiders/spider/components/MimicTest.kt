package net.tfminecraft.tfmcspiders.spider.components

import net.tfminecraft.tfmcspiders.spider.components.body.SpiderBody
import net.tfminecraft.tfmcspiders.spider.components.body.setupSpiderBody
import net.tfminecraft.tfmcspiders.spider.configuration.PounceOptions
import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.spider.presets.MimicPose
import net.tfminecraft.tfmcspiders.spider.presets.MimicShape
import net.tfminecraft.tfmcspiders.spider.presets.mimicBodyPlan
import net.tfminecraft.tfmcspiders.spider.presets.mimicGallopGait
import net.tfminecraft.tfmcspiders.spider.presets.mimicWalkGait
import net.tfminecraft.tfmcspiders.utilities.BlockDisplayModelPiece
import net.tfminecraft.tfmcspiders.utilities.ecs.ECS
import net.tfminecraft.tfmcspiders.utilities.maths.UP_VECTOR
import net.tfminecraft.tfmcspiders.utilities.maths.rotate
import net.tfminecraft.tfmcspiders.utilities.maths.toRadians
import org.bukkit.Material
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
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
    fun `shut mimic is the vanilla chest with its mouth hidden inside`() {
        val plan = mimicBodyPlan()
        MimicPose(plan.bodyModel).apply(0f, 0f, 0f)

        val chest = plan.bodyModel.pieces.single { MimicShape.CHEST in it.tags }
        assertEquals(MimicShape.CHEST_MODEL, chest.itemModel)
        assertEquals(0f, chest.itemModelData)
        // the item model's cube stands on the chest's floor
        assertEquals(-MimicShape.HALF, corners(chest).minOf { it.y }, 1e-6f)
        assertEquals(0f, corners(chest).map { it.x }.average().toFloat(), 1e-6f)

        for (piece in plan.bodyModel.pieces - chest) {
            val points = corners(piece)
            if (MimicShape.TONGUE in piece.tags) {
                // collapsed into the floor of the mouth
                for (point in points) assertTrue(point.distance(MimicShape.TONGUE_ROOT) < .01f, "tongue shows at $point")
                continue
            }
            // teeth, gullet and palate all sit inside the solid shut chest
            for (point in points) {
                assertTrue(kotlin.math.abs(point.x) < MimicShape.HALF && kotlin.math.abs(point.z) < MimicShape.HALF,
                    "${piece.block.material} pokes out at $point")
                assertTrue(point.y in -MimicShape.HALF..MimicShape.HALF, "${piece.block.material} y=${point.y}")
            }
        }
    }

    @Test
    fun `opening lifts the chest lid with its teeth and pushes the tongue over the lip`() {
        val plan = mimicBodyPlan()
        val chest = plan.bodyModel.pieces.single { MimicShape.CHEST in it.tags }
        // the front row, furthest from the hinge
        val upperTeeth = plan.bodyModel.pieces.filter {
            MimicShape.LID in it.tags && it.block.material == Material.BONE_BLOCK && corners(it).minOf { c -> c.z } > 4 * px
        }
        val tongue = plan.bodyModel.pieces.filter { MimicShape.TONGUE in it.tags }
        val pose = MimicPose(plan.bodyModel)

        pose.apply(0f, 0f, 0f)
        val shut = upperTeeth.flatMap(::corners).minOf { it.y }
        assertEquals(5, upperTeeth.size)
        pose.apply(60f.toRadians(), 1f, 0f)
        // 60° of the vanilla lid's 90°, to the nearest of the pack's steps
        assertEquals(60f / 90f, chest.itemModelData, .5f / MimicShape.OPENNESS_STEPS)
        assertTrue(upperTeeth.flatMap(::corners).minOf { it.y } > shut + .3f, "teeth did not lift with the lid")
        assertTrue(tongue.flatMap(::corners).maxOf { it.z } > MimicShape.HALF + px, "tongue stays in the mouth")

        // shutting again restores the exact chest pose
        pose.apply(0f, 0f, 0f)
        assertEquals(0f, chest.itemModelData)
        assertEquals(shut, upperTeeth.flatMap(::corners).minOf { it.y }, 1e-5f)
    }

    @Test
    fun `legs are cut from the chest's wood`() {
        val wood = mimicBodyPlan().legs.flatMap { leg -> leg.segments.flatMap { it.model.pieces } }
            .filter { it.itemModel != null }
        assertTrue(wood.isNotEmpty() && wood.all { it.itemModel == MimicShape.WOOD_MODEL })
    }

    @Test
    fun `four legs with the front pair ahead`() {
        val plan = mimicBodyPlan()
        assertEquals(4, plan.legs.size)
        assertTrue(plan.legs.take(2).all { it.restPosition.z > 0 } && plan.legs.drop(2).all { it.restPosition.z < 0 })
        assertTrue(plan.legs.all { it.segments.size == 3 })
    }

    @Test
    fun `woken mimic gallops straight away from the player`() {
        val world = Room { _, y, _ -> y in -3..-1 }.world()
        `when`(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true)
        val walk = mimicWalkGait()
        val gallop = mimicGallopGait()
        val plan = mimicBodyPlan()
        val options = mock(SpiderOptions::class.java)
        // the mimic asks for its gallop; the mock always answers with it
        `when`(options.gait).thenReturn(gallop)
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
        // the player stands just behind the chest, to the north-west
        val player = Vector(-1.5, 0.0, -2.5)
        val mimic = Mimic(MimicPose(plan.bodyModel), waker = null, threat = { player })
        val entity = app.spawn(body, options, locomotion, mimic)

        var maxTilt = 0.0
        repeat(600) { tick ->
            mimic.update(entity, body, options, locomotion)
            app.update()
            assertTrue(listOf(body.position.x, body.position.y, body.position.z).all(Double::isFinite), "t=$tick")
            if (tick > Mimic.WAKE_TICKS + 20) maxTilt = maxOf(maxTilt, 1 - UP_VECTOR.rotate(body.orientation).y)
        }

        assertEquals(MimicState.FLEEING, mimic.state)
        verify(options).gallop = true
        val away = body.position.clone().subtract(player).setY(0)
        assertTrue(away.length() > 150, "only ${away.length()} blocks away after 30 s")
        // it ran directly away: the escape line from the player through the chest
        val line = Vector(.5, 0.0, .5).subtract(player).setY(0).normalize()
        assertTrue(away.normalize().dot(line) > .97, "veered off: $away")
        assertTrue(maxTilt < .1, "wobbled: $maxTilt")
        assertEquals(MimicShape.GALLOP_HEIGHT, body.position.y, .35)
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
