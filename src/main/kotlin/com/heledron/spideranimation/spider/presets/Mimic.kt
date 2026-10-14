package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.configuration.BodyPlan
import com.heledron.spideranimation.spider.configuration.Gait
import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.spider.configuration.SegmentPlan
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.DisplayModel
import com.heledron.spideranimation.utilities.maths.toRadians
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A chest on four long legs. Asleep it is a real chest block; awake it is this display model,
 * built to the vanilla chest's 14×14×14 px footprint so the swap lines up.
 */
object MimicShape {
    const val PX = 1f / 16
    /** Half the chest's width, depth and height. */
    const val HALF = 7 * PX
    /** Body centre above the floor while it is still a chest. */
    const val RESTING_HEIGHT = HALF.toDouble()
    const val STANDING_HEIGHT = 1.15
    const val GALLOP_HEIGHT = 1.35

    /** Top of the base, where the lid closes: 9 px up, as on the vanilla chest. */
    const val SEAM = 2 * PX
    /** The lid swings up about its back top edge. */
    val HINGE = Vector3f(0f, SEAM, -HALF)
    val TONGUE_ROOT = Vector3f(0f, SEAM, -3 * PX)

    const val LID = "lid"
    const val TONGUE = "tongue"

    val MAX_LID_ANGLE = 80f.toRadians()
}

fun mimic(): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan = mimicBodyPlan()
    options.walkGait = mimicWalkGait()
    options.gallopGait = mimicGallopGait()
    options.sound.step = SoundPlayer(Sound.ENTITY_ZOMBIE_STEP, 0.12f, 1.3f)
    options.sound.stepBlockOverlay = SoundPlayer(Sound.ENTITY_ZOMBIE_STEP, 0.05f, 1.5f)
    return options
}

internal fun mimicWalkGait() = Gait.defaultWalk().apply {
    maxSpeed = 0.12
    legMoveSpeed = 0.12 * 2.5
    stationary.bodyHeight = MimicShape.STANDING_HEIGHT
    moving.bodyHeight = MimicShape.STANDING_HEIGHT
    moving.triggerZoneRadius = .6
}

internal fun mimicGallopGait() = Gait.defaultGallop().apply {
    maxSpeed = 0.28
    legMoveSpeed = .45
    stationary.bodyHeight = MimicShape.STANDING_HEIGHT
    moving.bodyHeight = MimicShape.GALLOP_HEIGHT
}

internal fun mimicBodyPlan(): BodyPlan = BodyPlan().apply {
    bodyModel = mimicChestModel()

    // Hips under the chest's corners; long legs splay out wide and knuckle up above the lid.
    val hip = Vector(5 * MimicShape.PX, -6 * MimicShape.PX, 5 * MimicShape.PX)
    for ((hipZ, restZ) in listOf(1.0 to .95, -1.0 to -.95)) {
        for (side in listOf(1.0, -1.0)) {
            legs += LegPlan(
                attachmentPosition = Vector(side * hip.x, hip.y, hipZ * hip.z),
                restPosition = Vector(side * 1.0, .0, restZ),
                segments = mimicLegSegments(),
                orientation = Quaternionf().rotateX(-(20f.toRadians())),
            )
        }
    }
}

private fun mimicLegSegments(): List<SegmentPlan> {
    val lengths = listOf(.55, .65, .6)
    return lengths.mapIndexed { index, length ->
        val joint = if (index == 0) hipJoint() else kneeJoint()
        SegmentPlan(length, joint, mimicLegModel(index, length.toFloat()))
    }
}

/** Pale, sinewy limbs that taper to a black claw. */
private fun mimicLegModel(index: Int, length: Float): DisplayModel {
    val thickness = listOf(.15f, .11f, .075f)[index]
    val parts = mutableListOf<BlockDisplayModelPiece>()
    for (section in 0..2) {
        val width = thickness * (1f - section * .22f)
        parts += box(Material.WHITE_TERRACOTTA, -width / 2, -width / 2, length * section / 3,
            width, width, length / 3 + .012f)
    }
    // Knuckles darken where the segments meet.
    parts += box(Material.BROWN_TERRACOTTA, -thickness * .6f, -thickness * .6f, -thickness * .3f,
        thickness * 1.2f, thickness * 1.2f, thickness * .9f)
    if (index == 2) parts += link(Material.BLACK_CONCRETE, Vector3f(0f, 0f, length * .82f),
        Vector3f(0f, -.03f, length + .06f), .05f, .04f)
    return DisplayModel(parts)
}

internal fun mimicChestModel(): DisplayModel {
    val px = MimicShape.PX
    val half = MimicShape.HALF
    val seam = MimicShape.SEAM
    // Closest block to the chest texture's orange-brown wood; planks are too pale.
    val wood = Material.YELLOW_TERRACOTTA
    val parts = mutableListOf<BlockDisplayModelPiece>()

    // Base: wooden box framed in black like the vanilla chest, with a black gullet under the lid.
    parts += box(wood, -half, -half, -half, half * 2, half + seam, half * 2)
    parts += outline(-half, seam)
    parts += box(Material.BLACK_CONCRETE, -half + px, seam, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px)

    // Lower teeth point up from the rim; when the lid is shut they sit inside it.
    for (tooth in teeth()) parts += box(Material.BONE_BLOCK, tooth.x - .6f * px, seam, tooth.z - .6f * px,
        1.2f * px, 1.7f * px, 1.2f * px)

    // Lid: the chest top, its frame, latch and the upper teeth all swing together.
    // Its bottom edge and the base's top edge make the dark band across the front.
    val lid = mutableListOf<BlockDisplayModelPiece>()
    lid += box(wood, -half, seam, -half, half * 2, half - seam, half * 2)
    lid += outline(seam, half)
    lid += box(Material.BLACK_CONCRETE, -half + px, seam - .05f * px, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px)
    lid += box(Material.SMOOTH_STONE, -px, seam - 2 * px, half, 2 * px, 4 * px, px)
    for (tooth in teeth(offset = .5f)) lid += box(Material.BONE_BLOCK, tooth.x - .6f * px, seam - 1.7f * px, tooth.z - .6f * px,
        1.2f * px, 1.7f * px, 1.2f * px)
    lid.forEach { it.tags = listOf(MimicShape.LID) }
    parts += lid

    // Tongue: lies in the gullet and slides out over the front lip as the lid opens.
    parts += box(Material.NETHER_WART_BLOCK, -2 * px, seam + .05f * px, -3 * px, 4 * px, 1.2f * px, 12 * px,
        listOf(MimicShape.TONGUE))
    parts += link(Material.NETHER_WART_BLOCK, Vector3f(0f, seam + .6f * px, 9 * px),
        Vector3f(0f, seam - 3.5f * px, 10.5f * px), 3.4f * px, 1.1f * px, listOf(MimicShape.TONGUE))

    return DisplayModel(parts)
}

/** The vanilla chest's 1 px dark border along all twelve edges of a box from [minY] to [maxY], standing just proud of its faces. */
private fun outline(minY: Float, maxY: Float): List<BlockDisplayModelPiece> {
    val px = MimicShape.PX
    val half = MimicShape.HALF
    val proud = .03f * px
    val edge = px + proud
    val near = -half - proud
    val far = half - px
    val parts = mutableListOf<BlockDisplayModelPiece>()
    for (a in listOf(near, far)) for (b in listOf(near, far)) {
        // along x, along z (at the top and bottom of the box), and the upright corners
        parts += box(Material.BLACK_TERRACOTTA, near, if (a == near) minY - proud else maxY - px, b, half * 2 + proud * 2, edge, edge)
        parts += box(Material.BLACK_TERRACOTTA, b, if (a == near) minY - proud else maxY - px, near, edge, edge, half * 2 + proud * 2)
        parts += box(Material.BLACK_TERRACOTTA, a, minY - proud, b, edge, maxY - minY + proud * 2, edge)
    }
    return parts
}

/** Points along the front and side rims where fangs grow; [offset] staggers the upper row. */
private fun teeth(offset: Float = 0f): List<Vector3f> {
    val px = MimicShape.PX
    // inside the black rim
    val rim = MimicShape.HALF - 1.8f * px
    val points = mutableListOf<Vector3f>()
    for (i in 0..4) points += Vector3f((-4f + 2f * (i + offset)) * px, 0f, rim)
    for (side in listOf(-1f, 1f)) for (i in 0..2) points += Vector3f(side * rim, 0f, (4f - 3f * (i + offset)) * px)
    return points
}

/** Swing the lid and slide the tongue. Base transforms are captured once in [MimicPose]. */
class MimicPose(model: DisplayModel) {
    private val lid = model.pieces.filter { MimicShape.LID in it.tags }.map { it to Matrix4f(it.transform) }
    private val tongue = model.pieces.filter { MimicShape.TONGUE in it.tags }.map { it to Matrix4f(it.transform) }

    /**
     * @param lidAngle radians the lid is raised (0 = shut).
     * @param tongueOut 0 hides the tongue inside the gullet, 1 hangs it over the lip.
     * @param sway side-to-side tongue swing in radians.
     */
    fun apply(lidAngle: Float, tongueOut: Float, sway: Float) {
        val hinge = MimicShape.HINGE
        val lidTransform = Matrix4f().translate(hinge).rotateX(-lidAngle).translate(Vector3f(hinge).negate())
        for ((piece, base) in lid) piece.transform.set(lidTransform).mul(base)

        val root = MimicShape.TONGUE_ROOT
        val extent = tongueOut.coerceIn(0.001f, 1f)
        val tongueTransform = Matrix4f().translate(root).rotateY(sway).scale(extent).translate(Vector3f(root).negate())
        for ((piece, base) in tongue) piece.transform.set(tongueTransform).mul(base)
    }
}

private fun link(
    material: Material, from: Vector3f, to: Vector3f, width: Float, thickness: Float,
    tags: List<String> = emptyList(),
): BlockDisplayModelPiece {
    val direction = Vector3f(to).sub(from)
    val length = direction.length()
    return BlockDisplayModelPiece(material.createBlockData(), Matrix4f().translation(from)
        .rotate(Quaternionf().rotationTo(Vector3f(0f, 0f, 1f), direction.normalize()))
        .translate(-width / 2, -thickness / 2, 0f).scale(width, thickness, length), tags = tags)
}

private fun box(
    material: Material, x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float,
    tags: List<String> = emptyList(),
) = BlockDisplayModelPiece(
    block = material.createBlockData(),
    transform = Matrix4f().translation(x, y, z).scale(width, height, depth),
    tags = tags,
)
