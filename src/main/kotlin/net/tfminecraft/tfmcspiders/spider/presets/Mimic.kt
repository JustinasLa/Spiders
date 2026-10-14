package net.tfminecraft.tfmcspiders.spider.presets

import net.tfminecraft.tfmcspiders.spider.configuration.BodyPlan
import net.tfminecraft.tfmcspiders.spider.configuration.Gait
import net.tfminecraft.tfmcspiders.spider.configuration.LegPlan
import net.tfminecraft.tfmcspiders.spider.configuration.SegmentPlan
import net.tfminecraft.tfmcspiders.spider.configuration.SoundPlayer
import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.utilities.BlockDisplayModelPiece
import net.tfminecraft.tfmcspiders.utilities.DisplayModel
import net.tfminecraft.tfmcspiders.utilities.maths.toRadians
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * A chest on four long legs. Asleep it is a real chest block; awake it is the vanilla chest model
 * itself, drawn from the `tfmc_spiders` resource pack (the vanilla `minecraft:chest` special model at
 * a chosen lid openness), so the two look the same.
 */
object MimicShape {
    const val PX = 1f / 16
    /** Half the chest's width, depth and height. */
    const val HALF = 7 * PX
    /** Body centre above the floor while it is still a chest. */
    const val RESTING_HEIGHT = HALF.toDouble()
    const val STANDING_HEIGHT = 1.15
    const val GALLOP_HEIGHT = 1.35

    /** Underside of the vanilla lid (9 px up); it overlaps the base's top by 1 px. */
    const val SEAM = 2 * PX
    /** Top of the vanilla chest's base (10 px up): the floor of the mouth. */
    const val MOUTH = 3 * PX
    /** The lid swings up about its back bottom edge, as the vanilla lid does. */
    val HINGE = Vector3f(0f, SEAM, -HALF)
    val TONGUE_ROOT = Vector3f(0f, MOUTH, -3 * PX)

    /** Resource-pack item models: the vanilla chest by openness, and a cube of its plank texture. */
    val CHEST_MODEL = NamespacedKey("tfmc_spiders", "mimic_chest")
    val WOOD_MODEL = NamespacedKey("tfmc_spiders", "mimic_wood")
    /** The pack's chest model comes in this many openness steps (0 to 90°). */
    const val OPENNESS_STEPS = 40

    const val LID = "lid"
    const val CHEST = "chest"
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

/** The flight gait: 7 blocks/s, faster than a sprinting player. */
internal fun mimicGallopGait() = Gait.defaultGallop().apply {
    setSpeed(.35)
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

/** Wooden limbs cut from the chest's own planks, with black iron-bound knuckles like its frame. */
private fun mimicLegModel(index: Int, length: Float): DisplayModel {
    val thickness = listOf(.15f, .11f, .08f)[index]
    val parts = mutableListOf<BlockDisplayModelPiece>()
    for (section in 0..1) {
        val width = thickness * (1f - section * .18f)
        parts += wood(-width / 2, -width / 2, length * section / 2, width, width, length / 2 + .012f)
    }
    // Knuckles bound in black where the segments meet, pinned like the latch.
    parts += box(Material.BLACK_TERRACOTTA, -thickness * .62f, -thickness * .62f, -thickness * .3f,
        thickness * 1.24f, thickness * 1.24f, thickness * .9f)
    parts += box(Material.SMOOTH_STONE, -thickness * .7f, -thickness * .2f, -thickness * .05f,
        thickness * 1.4f, thickness * .4f, thickness * .4f)
    // A black-capped wooden foot.
    if (index == 2) parts += box(Material.BLACK_TERRACOTTA, -thickness * .55f, -thickness * .55f, length - .02f,
        thickness * 1.1f, thickness * 1.1f, .05f)
    return DisplayModel(parts)
}

internal fun mimicChestModel(): DisplayModel {
    val px = MimicShape.PX
    val half = MimicShape.HALF
    val seam = MimicShape.SEAM
    val mouth = MimicShape.MOUTH
    val parts = mutableListOf<BlockDisplayModelPiece>()

    // The vanilla chest. Item models fill the unit cube, and the chest sits on its floor, 1 px below ours.
    parts += BlockDisplayModelPiece(
        block = Material.CHEST.createBlockData(),
        transform = Matrix4f().translation(-.5f, -half, -.5f),
        tags = listOf(MimicShape.CHEST),
        itemModel = MimicShape.CHEST_MODEL,
    )

    // A black gullet on the floor of the mouth, and lower teeth that hide inside the shut lid.
    parts += box(Material.BLACK_CONCRETE, -half + px, mouth, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px)
    for (tooth in teeth()) parts += box(Material.BONE_BLOCK, tooth.x - .6f * px, mouth, tooth.z - .6f * px,
        1.2f * px, 1.7f * px, 1.2f * px)

    // The lid's black palate and upper teeth swing with the chest's own lid.
    val lid = mutableListOf<BlockDisplayModelPiece>()
    lid += box(Material.BLACK_CONCRETE, -half + px, seam - .05f * px, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px)
    for (tooth in teeth(offset = .5f)) lid += box(Material.BONE_BLOCK, tooth.x - .6f * px, seam - 1.7f * px, tooth.z - .6f * px,
        1.2f * px, 1.7f * px, 1.2f * px)
    lid.forEach { it.tags = listOf(MimicShape.LID) }
    parts += lid

    // Tongue: lies in the gullet and slides out over the front lip as the lid opens.
    parts += box(Material.NETHER_WART_BLOCK, -2 * px, mouth + .05f * px, -3 * px, 4 * px, 1.2f * px, 12 * px,
        listOf(MimicShape.TONGUE))
    parts += link(Material.NETHER_WART_BLOCK, Vector3f(0f, mouth + .6f * px, 9 * px),
        Vector3f(0f, mouth - 3.5f * px, 10.5f * px), 3.4f * px, 1.1f * px, listOf(MimicShape.TONGUE))

    return DisplayModel(parts)
}

/** Points along the front and side rims where fangs grow; [offset] staggers the upper row. */
private fun teeth(offset: Float = 0f): List<Vector3f> {
    val px = MimicShape.PX
    // just inside the chest's rim
    val rim = MimicShape.HALF - 1.8f * px
    val points = mutableListOf<Vector3f>()
    for (i in 0..4) points += Vector3f((-4f + 2f * (i + offset)) * px, 0f, rim)
    for (side in listOf(-1f, 1f)) for (i in 0..2) points += Vector3f(side * rim, 0f, (4f - 3f * (i + offset)) * px)
    return points
}

/** Swing the lid and slide the tongue. Base transforms are captured once in [MimicPose]. */
class MimicPose(model: DisplayModel) {
    private val chest = model.pieces.filter { MimicShape.CHEST in it.tags }
    private val lid = model.pieces.filter { MimicShape.LID in it.tags }.map { it to Matrix4f(it.transform) }
    private val tongue = model.pieces.filter { MimicShape.TONGUE in it.tags }.map { it to Matrix4f(it.transform) }

    /**
     * @param lidAngle radians the lid is raised (0 = shut).
     * @param tongueOut 0 hides the tongue inside the gullet, 1 hangs it over the lip.
     * @param sway side-to-side tongue swing in radians.
     */
    fun apply(lidAngle: Float, tongueOut: Float, sway: Float) {
        // the chest model only comes in fixed steps, so the teeth snap to the same angle
        val openness = (lidAngle / (PI.toFloat() / 2)).coerceIn(0f, 1f)
        val step = (openness * MimicShape.OPENNESS_STEPS).roundToInt()
        for (piece in chest) piece.itemModelData = step.toFloat() / MimicShape.OPENNESS_STEPS
        val angle = step * (PI.toFloat() / 2) / MimicShape.OPENNESS_STEPS

        val hinge = MimicShape.HINGE
        val lidTransform = Matrix4f().translate(hinge).rotateX(-angle).translate(Vector3f(hinge).negate())
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

/** A box of the chest's plank texture. */
private fun wood(x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float) =
    box(Material.STRIPPED_OAK_WOOD, x, y, z, width, height, depth).apply { itemModel = MimicShape.WOOD_MODEL }

private fun box(
    material: Material, x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float,
    tags: List<String> = emptyList(),
) = BlockDisplayModelPiece(
    block = material.createBlockData(),
    transform = Matrix4f().translation(x, y, z).scale(width, height, depth),
    tags = tags,
)
