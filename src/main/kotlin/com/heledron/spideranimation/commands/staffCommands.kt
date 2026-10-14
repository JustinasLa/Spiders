package com.heledron.spideranimation.commands

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.spider.components.Mimic
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.presets.CinderweaverSize
import com.heledron.spideranimation.spider.presets.cinderweaver
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.maths.FORWARD_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.rotate
import com.heledron.spideranimation.utilities.overloads.direction
import com.heledron.spideranimation.utilities.overloads.eyePosition
import com.heledron.spideranimation.utilities.overloads.yawRadians
import com.heledron.spideranimation.utilities.raycastGround
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Quaternionf

private val SIZE_SUGGESTIONS = CinderweaverSize.entries.map { it.id } + listOf("mimic", "0.25", "0.5", "1.0", "2.0", "2.5")

internal fun spawnCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("spawn").then(
    Commands.argument("size", StringArgumentType.word())
        .suggests { _, builder ->
            SIZE_SUGGESTIONS.filter { it.startsWith(builder.remaining.lowercase()) }.forEach { builder.suggest(it) }
            builder.buildFuture()
        }
        .executes { ctx ->
            if (StringArgumentType.getString(ctx, "size").equals(MIMIC, ignoreCase = true)) return@executes placeAimed(ctx)
            val player = ctx.source.sender as? Player ?: throw error("Console: /spider spawn <size> at <world> <x> <y> <z>")
            val hit = player.world.raycastGround(player.eyePosition, player.direction, 32.0)
                ?: throw error("Aim at a nearby solid surface to spawn a Cinderweaver.")
            // Floors, walls and ceilings: the spider stands on the face it was aimed at.
            val up = hit.hitBlockFace?.direction ?: UP_VECTOR
            val yawDirection = FORWARD_VECTOR.rotate(Quaternionf().rotationY(player.yawRadians()))
            val orientation = SpiderBody.orientationFrom(up, player.direction, yawDirection)
            spawn(ctx, hit.hitPosition.toLocation(player.world), orientation, onSurface = up.y < SURFACE_FLOOR_MIN_UP)
        }
        .then(Commands.literal("at").then(
            Commands.argument("world", StringArgumentType.word())
                .suggests { _, builder ->
                    Bukkit.getWorlds().filter { it.name.startsWith(builder.remaining) }.forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                }
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0)).then(
                    Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                        Commands.argument("z", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0)).executes { ctx ->
                            val world = Bukkit.getWorld(StringArgumentType.getString(ctx, "world"))
                                ?: throw error("World not found.")
                            spawn(ctx, Location(world,
                                DoubleArgumentType.getDouble(ctx, "x"),
                                DoubleArgumentType.getDouble(ctx, "y"),
                                DoubleArgumentType.getDouble(ctx, "z")), Quaternionf(), onSurface = false)
                        }
                    )
                ))
        ))
)

private const val MIMIC = "mimic"

/** Faces whose normal points less upward than this are walls or ceilings. */
private const val SURFACE_FLOOR_MIN_UP = .7

private fun spawn(ctx: CommandContext<CommandSourceStack>, location: Location, orientation: Quaternionf, onSurface: Boolean): Int {
    val sizeId = StringArgumentType.getString(ctx, "size")
    if (sizeId.equals(MIMIC, ignoreCase = true)) throw error("Console: /spider mimic place at <world> <x> <y> <z>")
    val scale = CinderweaverSize.parseScale(sizeId)
        ?: throw error("Size must be hatchling, hunter, broodmother or a scale from ${CinderweaverSize.MIN_SCALE} to ${CinderweaverSize.MAX_SCALE}.")
    val world = location.world
    if (!location.isFinite || location.y < world.minHeight || location.y >= world.maxHeight) {
        throw error("Choose a position within the world's build height.")
    }
    if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
        throw error("Visit that area first; spiders only spawn in loaded chunks.")
    }
    val options = cinderweaver(scale)
    // A spider placed on a wall or ceiling has to cling to it.
    if (onSurface) options.climbing = true
    AppState.createSpider(ctx.source.sender, world, location.toVector(), options, orientation) ?: return 0
    val name = CinderweaverSize.fromId(sizeId)?.id ?: "at scale $scale"
    val climbing = if (onSurface) " Clinging to the surface (wall climbing on)." else ""
    ctx.source.sender.sendPlainMessage("Spawned Cinderweaver $name.$climbing ${AppState.spiderCount()}/${AppState.MAX_SPIDERS} active. /spider tools opens its controls.")
    return 1
}

internal fun listCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("list").executes { ctx ->
    val spiders = AppState.ecs.query<ECSEntity, SpiderBody>().filter { !it.first.scheduledForRemoval }
    ctx.source.sender.sendPlainMessage("Cinderweavers: ${spiders.size}/${AppState.MAX_SPIDERS}")
    for ((entity, body) in spiders) {
        val kind = entity.query<Mimic>()?.let { " • mimic (${it.state.name.lowercase()})" } ?: ""
        ctx.source.sender.sendPlainMessage("${body.uuid.toString().take(8)} • ${body.world.name} • ${body.position.blockX}, ${body.position.blockY}, ${body.position.blockZ}$kind")
    }
    1
}

internal fun removeCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("remove")
    .executes { ctx ->
        val player = ctx.source.sender as? Player ?: throw error("Console: /spider remove all")
        val entity = AppState.findNearestSpider(player) ?: throw error("No spider found in this world.")
        entity.remove()
        player.sendPlainMessage("Removed the nearest Cinderweaver.")
        1
    }
    .then(Commands.literal("all").executes { ctx ->
        val spiders = AppState.ecs.query<ECSEntity, SpiderBody>().filter { !it.first.scheduledForRemoval }
        spiders.forEach { it.first.remove() }
        ctx.source.sender.sendPlainMessage("Removed ${spiders.size} Cinderweavers.")
        1
    })
    .then(Commands.argument("id", StringArgumentType.word()).executes { ctx ->
        val id = StringArgumentType.getString(ctx, "id")
        if (id.length < 8) throw error("Use at least the eight-character ID from /spider list.")
        val matches = AppState.ecs.query<ECSEntity, SpiderBody>()
            .filter { !it.first.scheduledForRemoval && it.second.uuid.toString().startsWith(id, ignoreCase = true) }
        if (matches.size != 1) throw error("Use an unambiguous ID from /spider list.")
        matches.single().first.remove()
        ctx.source.sender.sendPlainMessage("Removed Cinderweaver $id.")
        1
    })

private fun error(message: String) = SimpleCommandExceptionType { message }.create()
