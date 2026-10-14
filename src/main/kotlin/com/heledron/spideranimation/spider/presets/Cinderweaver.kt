package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.configuration.PaletteEntry
import com.heledron.spideranimation.spider.configuration.SoundPlayer
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import com.heledron.spideranimation.utilities.DisplayModel
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.joml.Matrix4f

enum class CinderweaverSize(val scale: Double) {
    HATCHLING(0.35), HUNTER(0.8), BROODMOTHER(1.5);

    val id: String get() = name.lowercase()

    companion object {
        fun fromId(id: String): CinderweaverSize? = entries.find { it.id == id.lowercase() }
    }
}

/** New TFMC art direction, not a claim about an existing canonical creature. No pack required. */
fun cinderweaver(size: CinderweaverSize = CinderweaverSize.HUNTER): SpiderOptions {
    val options = octopod(3, 0.7)
    val plan = options.bodyPlan
    plan.bodyModel = cinderweaverTorso()

    for (leg in plan.legs) {
        for ((index, segment) in leg.segments.withIndex()) {
            val thickness = 0.17f - index * 0.035f
            val length = segment.length.toFloat()
            segment.model = DisplayModel(listOf(
                cuboid(Material.NETHERITE_BLOCK, -thickness / 2, -thickness / 2, 0f,
                    thickness, thickness, length, listOf("cloak")),
                cuboid(Material.WAXED_EXPOSED_COPPER, -thickness * 0.7f, -thickness * 0.7f, 0.025f,
                    thickness * 1.4f, thickness * 1.4f, 0.12f),
                cuboid(Material.WAXED_WEATHERED_COPPER, -thickness * 0.45f, thickness / 2, length * 0.2f,
                    thickness * 0.9f, 0.045f, length * 0.55f),
            ))
        }
    }

    val ember = PaletteEntry(Material.SHROOMLIGHT.createBlockData(), Display.Brightness(12, 0))
    val smoulder = PaletteEntry(Material.MAGMA_BLOCK.createBlockData(), Display.Brightness(9, 0))
    plan.eyePalette = listOf(ember, ember, smoulder)
    plan.blinkingPalette = listOf(ember, smoulder)

    options.sound.step = SoundPlayer(Sound.BLOCK_CHAIN_STEP, 0.16f, 0.65f)
    options.sound.stepBlockOverlay = SoundPlayer(Sound.BLOCK_NETHERITE_BLOCK_STEP, 0.10f, 0.7f)
    options.walkGait.maxSpeed = 0.12
    options.gallopGait.maxSpeed = 0.24
    plan.scale(size.scale)
    options.walkGait.scale(size.scale)
    options.gallopGait.scale(size.scale)
    return options
}

internal fun cinderweaverTorso(): DisplayModel {
    val parts = mutableListOf<BlockDisplayModelPiece>()
    // Low thorax and overlapping black carapace plates around a copper pressure vessel.
    parts += cuboid(Material.NETHERITE_BLOCK, -.40f, -.23f, -.38f, .80f, .42f, 1.0f)
    parts += cuboid(Material.POLISHED_BLACKSTONE, -.48f, -.14f, -1.12f, .96f, .43f, .9f, listOf("cloak"))
    parts += cuboid(Material.POLISHED_BLACKSTONE, -.36f, .18f, -1.02f, .72f, .18f, .72f, listOf("cloak"))
    parts += cuboid(Material.WAXED_EXPOSED_COPPER, -.29f, .10f, -.45f, .58f, .45f, .60f)
    parts += cuboid(Material.WAXED_WEATHERED_COPPER, -.34f, .25f, -.35f, .68f, .16f, .38f)
    parts += cuboid(Material.NETHERITE_BLOCK, -.40f, -.13f, .43f, .80f, .36f, .39f)

    // Copper ribs, a visible firebox, paired exhaust stacks and their collars.
    for (z in listOf(-.96f, -.64f)) {
        parts += cuboid(Material.WAXED_EXPOSED_COPPER, -.50f, -.14f, z, 1.0f, .46f, .065f)
    }
    parts += cuboid(Material.BLAST_FURNACE, -.25f, -.10f, -.73f, .50f, .44f, .40f).apply {
        block = Bukkit.createBlockData("minecraft:blast_furnace[facing=north,lit=true]")
    }
    for (x in listOf(-.25f, .16f)) {
        parts += cuboid(Material.POLISHED_BLACKSTONE, x, .30f, -.86f, .09f, .43f, .09f)
        parts += cuboid(Material.WAXED_EXPOSED_COPPER, x - .02f, .64f, -.88f, .13f, .07f, .13f)
    }

    // Six ember lenses behind a copper brow; hooked iron mandibles below them.
    parts += cuboid(Material.WAXED_EXPOSED_COPPER, -.43f, .17f, .65f, .86f, .07f, .18f)
    for (x in listOf(-.29f, -.10f, .09f)) {
        for (y in listOf(-.02f, .09f)) {
            parts += cuboid(Material.SHROOMLIGHT, x, y, .825f, .105f, .065f, .045f,
                listOf("eye")).apply { brightness = Display.Brightness(12, 0) }
        }
    }
    for (side in listOf(-1f, 1f)) {
        parts += cuboid(Material.NETHERITE_BLOCK, side * .27f - .045f, -.25f, .65f, .09f, .12f, .47f)
        parts += cuboid(Material.WAXED_WEATHERED_COPPER, side * .25f - .055f, -.20f, .61f, .11f, .17f, .12f)
        parts += cuboid(Material.NETHERITE_BLOCK, side * .20f - .10f, -.25f, 1.04f, .20f, .12f, .08f)
        for (z in listOf(-.92f, -.58f, .18f)) {
            parts += cuboid(Material.WAXED_EXPOSED_COPPER, side * .44f - .035f, .20f, z, .07f, .07f, .07f)
        }
    }
    return DisplayModel(parts)
}

private fun cuboid(
    material: Material, x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float,
    tags: List<String> = emptyList(),
) = BlockDisplayModelPiece(
    block = material.createBlockData(),
    transform = Matrix4f().translation(x, y, z).scale(width, height, depth),
    tags = tags,
)
