package com.heledron.spideranimation.spider.components.rendering

import com.heledron.spideranimation.utilities.rendering.interpolateTransform
import com.heledron.spideranimation.utilities.rendering.renderBlock
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.components.Cloak
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.rendering.RenderGroup
import com.heledron.spideranimation.utilities.rendering.RenderItem
import com.heledron.spideranimation.utilities.rendering.renderItem
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.WeakHashMap
import org.joml.Matrix4f
import org.joml.Vector4f


fun renderSpider(spider: SpiderBody, cloak: Cloak, options: SpiderOptions): RenderGroup {
    val group = RenderGroup()

    val transform = Matrix4f().rotate(spider.orientation)
    group[spider] = renderModel(spider, cloak, spider.position, options.bodyPlan.bodyModel, transform, options)


    for ((legIndex, leg) in spider.legs.withIndex()) {
        val positions = leg.ik.nodes
        val rotations = leg.ik.segmentModelRotations

        for (segmentIndex in rotations.indices) {
            val segmentPlan = options.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue

            val parent = positions[segmentIndex].position

            val segmentTransform = Matrix4f().rotate(rotations[segmentIndex])
            group[legIndex to segmentIndex] = renderModel(spider, cloak, parent, segmentPlan.model, segmentTransform, options)
        }
    }

    return group
}

private fun renderModel(
    spider: SpiderBody,
    cloak: Cloak,
    position: Vector,
    model: DisplayModel,
    transformation: Matrix4f,
    options: SpiderOptions,
): RenderGroup {
    val group = RenderGroup()

    for ((index, piece) in model.pieces.withIndex()) {
        group[index] = renderModelPiece(spider, cloak, position, piece, transformation, options)
    }

    return group
}


private fun renderModelPiece(
    spider: SpiderBody,
    cloak: Cloak,
    position: Vector,
    piece: BlockDisplayModelPiece,
    transformation: Matrix4f,
    options: SpiderOptions,
): RenderItem {
    val itemModel = piece.itemModel
    if (itemModel != null) return renderItemPiece(spider, position, piece, itemModel, transformation)
    return renderBlock(
        location = position.toLocation(spider.world),
        init = {
            it.teleportDuration = 1
            it.interpolationDuration = 1
        },
        update = {
            val transform = Matrix4f(transformation).mul(piece.transform)
            it.interpolateTransform(transform)

            val cloak = if (piece.tags.contains("cloak")) {
                val relative = transform.transform(Vector4f(.5f, .5f, .5f, 1f))
                val piecePosition = position.clone()
                piecePosition.x += relative.x
                piecePosition.y += relative.y
                piecePosition.z += relative.z

                cloak.getPiece(piece, spider.world, piecePosition, piece.block, piece.brightness, options.cloak)
            } else null

            if (cloak != null) {
                it.block = cloak.first
                it.brightness = cloak.second
            } else {
                it.block = piece.block
                it.brightness = piece.brightness
            }
        }
    )
}

/** Item models are drawn centred on the display, so shift them into the piece's unit cube. */
private fun renderItemPiece(
    spider: SpiderBody,
    position: Vector,
    piece: BlockDisplayModelPiece,
    itemModel: NamespacedKey,
    transformation: Matrix4f,
) = renderItem(
    location = position.toLocation(spider.world),
    init = {
        it.teleportDuration = 1
        it.interpolationDuration = 1
    },
    update = {
        it.interpolateTransform(Matrix4f(transformation).mul(piece.transform).translate(.5f, .5f, .5f))
        it.brightness = piece.brightness
        val shown = itemModel to piece.itemModelData
        if (shownItems[it] != shown) {
            shownItems[it] = shown
            it.setItemStack(modelItem(itemModel, piece.itemModelData))
        }
    }
)

private val shownItems = WeakHashMap<ItemDisplay, Pair<NamespacedKey, Float>>()

private fun modelItem(model: NamespacedKey, data: Float) = ItemStack(Material.PAPER).apply {
    editMeta { meta ->
        meta.setItemModel(model)
        meta.setCustomModelDataComponent(meta.customModelDataComponent.apply { floats = listOf(data) })
    }
}
