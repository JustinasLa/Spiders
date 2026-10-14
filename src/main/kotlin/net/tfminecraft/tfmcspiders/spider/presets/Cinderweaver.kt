package net.tfminecraft.tfmcspiders.spider.presets

import net.tfminecraft.tfmcspiders.spider.configuration.BodyPlan
import net.tfminecraft.tfmcspiders.spider.configuration.PaletteEntry
import net.tfminecraft.tfmcspiders.spider.configuration.SoundPlayer
import net.tfminecraft.tfmcspiders.spider.configuration.SpiderOptions
import net.tfminecraft.tfmcspiders.utilities.BlockDisplayModelPiece
import net.tfminecraft.tfmcspiders.utilities.DisplayModel
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.sqrt

enum class CinderweaverSize(val scale: Double) {
    HATCHLING(0.35), HUNTER(0.8), BROODMOTHER(1.5);

    val id: String get() = name.lowercase()

    companion object {
        const val MIN_SCALE = 0.25
        const val MAX_SCALE = 2.5

        fun fromId(id: String): CinderweaverSize? = entries.find { it.id == id.lowercase() }

        /** A named size or any scale from [MIN_SCALE] to [MAX_SCALE]. */
        fun parseScale(id: String): Double? =
            fromId(id)?.scale ?: id.toDoubleOrNull()?.takeIf { it.isFinite() && it in MIN_SCALE..MAX_SCALE }
    }
}

/** Organic, real-spider-inspired dark fantasy art direction; no resource pack required. */
fun cinderweaver(size: CinderweaverSize = CinderweaverSize.HUNTER): SpiderOptions = cinderweaver(size.scale)

fun cinderweaver(scale: Double): SpiderOptions {
    val options = SpiderOptions()
    options.bodyPlan = cinderweaverBodyPlan(scale)
    options.useOrganicSounds()
    options.walkGait.maxSpeed = 0.12
    options.gallopGait.maxSpeed = 0.24
    options.walkGait.scale(scale)
    options.gallopGait.scale(scale)
    return options
}

internal fun SpiderOptions.useOrganicSounds() {
    sound.step = SoundPlayer(Sound.ENTITY_SPIDER_STEP, 0.14f, 0.75f)
    sound.stepBlockOverlay = SoundPlayer(Sound.ENTITY_SPIDER_STEP, 0.06f, 0.85f)
}

/** Seals store full body plans. Upgrade the old firebox model without changing its saved rig. */
internal fun upgradeClockworkAppearance(plan: BodyPlan): Boolean {
    if (plan.bodyModel.pieces.none { it.block.material == Material.BLAST_FURNACE }) return false
    if (plan.legs.size != 8 || plan.legs.any { it.segments.size != 3 }) return false
    val organic = cinderweaverBodyPlan(CinderweaverSize.HUNTER)
    organic.scale(plan.scale / organic.scale)
    plan.bodyModel = organic.bodyModel
    plan.eyePalette = organic.eyePalette
    plan.blinkingPalette = organic.blinkingPalette
    for ((legIndex, leg) in plan.legs.withIndex()) {
        for ((segmentIndex, segment) in leg.segments.withIndex()) {
            val replacement = organic.legs[legIndex].segments[segmentIndex]
            segment.model = replacement.model.scale((segment.length / replacement.length).toFloat())
        }
    }
    return true
}

internal fun cinderweaverBodyPlan(size: CinderweaverSize): BodyPlan = cinderweaverBodyPlan(size.scale)

internal fun cinderweaverBodyPlan(scale: Double): BodyPlan {
    val plan = octopodBodyPlan(3, 0.7)
    plan.bodyModel = cinderweaverTorso()

    for (leg in plan.legs) {
        for ((index, segment) in leg.segments.withIndex()) {
            val thickness = listOf(.18f, .13f, .085f)[index]
            val length = segment.length.toFloat()
            val parts = mutableListOf<BlockDisplayModelPiece>()
            // Three narrowing sections give each articulated leg an organic taper.
            for (section in 0..2) {
                val width = thickness * (1f - section * .26f)
                parts += cuboid(
                    if (index == 0) Material.GRAY_TERRACOTTA else Material.BLACK_TERRACOTTA,
                    -width / 2, -width / 2, length * section / 3,
                    width, width, length / 3 + .012f,
                )
            }
            if (index == 1) {
                parts += link(Material.BLACK_CONCRETE, Vector3f(0f, thickness / 2, length * .35f),
                    Vector3f(0f, thickness / 2 + .13f, length * .25f), .018f, .008f)
            }
            segment.model = DisplayModel(parts)
        }
    }

    // Subdued eyeshine rather than flashing lights. Real spiders do not blink.
    val eye = PaletteEntry(Material.RED_TERRACOTTA.createBlockData(), Display.Brightness(5, 0))
    plan.eyePalette = listOf(eye)
    plan.blinkingPalette = listOf(eye)
    plan.scale(scale)
    return plan
}

internal fun cinderweaverTorso(): DisplayModel {
    val parts = mutableListOf<BlockDisplayModelPiece>()
    // A swollen abdomen and smaller, flatter cephalothorax joined by a narrow pedicel.
    parts += roundedBody(Vector3f(0f, .02f, -1.03f), Vector3f(.68f, .49f, .87f), 5, 7, Material.BLACK_TERRACOTTA)
    parts += cuboid(Material.BLACK_TERRACOTTA, -.16f, -.15f, -.47f, .32f, .26f, .39f)
    parts += roundedBody(Vector3f(0f, -.015f, .20f), Vector3f(.46f, .27f, .58f), 3, 5, Material.GRAY_TERRACOTTA)

    // Muted abdominal chevrons are natural markings, laid against the dorsal surface.
    for (z in listOf(-1.27f, -.95f)) {
        for (side in listOf(-1f, 1f)) {
            parts += link(Material.GRAY_TERRACOTTA, Vector3f(side * .29f, .514f, z - .12f),
                Vector3f(0f, .514f, z + .04f), .045f, .025f)
        }
    }

    // Eight unequal eyes in a curved cluster: large central pair, three smaller pairs.
    for (side in listOf(-1f, 1f)) {
        for ((x, y, z, radius) in listOf(
            listOf(.085f, .025f, .782f, .066f),
            listOf(.225f, .060f, .741f, .043f),
            listOf(.315f, .115f, .550f, .034f),
            listOf(.145f, .187f, .550f, .036f),
        )) {
            parts += cuboid(Material.BLACK_CONCRETE, side * x - radius * 1.3f, y - radius * 1.3f,
                z - .025f, radius * 2.6f, radius * 2.6f, radius * 1.3f)
            parts += cuboid(Material.RED_TERRACOTTA, side * x - radius, y - radius,
                z + radius * .3f, radius * 2, radius * 2, radius * .75f, listOf("eye")).apply {
                brightness = Display.Brightness(5, 0)
            }
        }

        // Paired chelicerae bend down and inward into sharp fangs; short palps flank them.
        parts += link(Material.BLACK_TERRACOTTA, Vector3f(side * .18f, -.12f, .67f),
            Vector3f(side * .21f, -.20f, .91f), .16f, .13f)
        parts += link(Material.BLACK_CONCRETE, Vector3f(side * .21f, -.20f, .91f),
            Vector3f(side * .15f, -.34f, 1.04f), .11f, .065f)
        parts += link(Material.BLACK_CONCRETE, Vector3f(side * .15f, -.34f, 1.04f),
            Vector3f(side * .06f, -.40f, .99f), .055f, .018f)
        parts += link(Material.GRAY_TERRACOTTA, Vector3f(side * .35f, -.11f, .54f),
            Vector3f(side * .47f, -.23f, .80f), .09f, .07f)
        parts += link(Material.BLACK_TERRACOTTA, Vector3f(side * .47f, -.23f, .80f),
            Vector3f(side * .39f, -.28f, .96f), .065f, .035f)

        // Sparse, short setae break up the silhouette without a particle cloud.
        for (z in listOf(-1.45f, -1.11f, -.77f)) {
            val x = .68f * sqrt(1f - ((z + 1.03f) / .87f).let { it * it }) * .93f
            parts += link(Material.BLACK_CONCRETE, Vector3f(side * x, .09f, z),
                Vector3f(side * (x + .14f), .16f, z - .07f), .022f, .008f)
        }
        parts += link(Material.BLACK_TERRACOTTA, Vector3f(side * .11f, -.19f, -1.77f),
            Vector3f(side * .12f, -.22f, -1.99f), .07f, .03f)
    }
    return DisplayModel(parts)
}

/** Compact horizontal slices make a rounded Minecraft silhouette with a bounded display count. */
private fun roundedBody(center: Vector3f, radius: Vector3f, rows: Int, slices: Int, material: Material): List<BlockDisplayModelPiece> {
    val parts = mutableListOf<BlockDisplayModelPiece>()
    for (row in 0 until rows) {
        val y = -1f + (row + .5f) * 2f / rows
        for (slice in 0 until slices) {
            val z = -1f + (slice + .5f) * 2f / slices
            val widthSquared = 1f - y * y - z * z
            if (widthSquared <= 0f) continue
            val halfWidth = radius.x * sqrt(widthSquared)
            parts += cuboid(material, center.x - halfWidth, center.y + radius.y * (y - 1f / rows),
                center.z + radius.z * (z - 1f / slices),
                halfWidth * 2, radius.y * 2 / rows, radius.z * 2 / slices)
        }
    }
    return parts
}

private fun link(material: Material, from: Vector3f, to: Vector3f, width: Float, thickness: Float): BlockDisplayModelPiece {
    val direction = Vector3f(to).sub(from)
    val length = direction.length()
    return BlockDisplayModelPiece(material.createBlockData(), Matrix4f().translation(from)
        .rotate(Quaternionf().rotationTo(Vector3f(0f, 0f, 1f), direction.normalize()))
        .translate(-width / 2, -thickness / 2, 0f).scale(width, thickness, length), tags = listOf("cloak"))
}

private fun cuboid(
    material: Material, x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float,
    tags: List<String> = listOf("cloak"),
) = BlockDisplayModelPiece(
    block = material.createBlockData(),
    transform = Matrix4f().translation(x, y, z).scale(width, height, depth),
    tags = tags,
)
