package com.heledron.spideranimation.commands

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.spider.presets.CinderweaverSize
import com.heledron.spideranimation.spider.presets.cinderweaver
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.overloads.direction
import com.heledron.spideranimation.utilities.overloads.eyePosition
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
import org.bukkit.util.Vector

internal fun spawnCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("spawn").then(
    Commands.argument("size", StringArgumentType.word())
        .suggests { _, builder ->
            CinderweaverSize.entries.filter { it.id.startsWith(builder.remaining.lowercase()) }.forEach { builder.suggest(it.id) }
            builder.buildFuture()
        }
        .executes { ctx ->
            val player = ctx.source.sender as? Player ?: throw error("Console: /spider spawn <size> at <world> <x> <y> <z>")
            val hit = player.world.raycastGround(player.eyePosition, player.direction, 32.0)
                ?: throw error("Aim at a nearby solid surface to spawn a Cinderweaver.")
            spawn(ctx, hit.hitPosition.toLocation(player.world).apply { yaw = player.location.yaw })
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
                                DoubleArgumentType.getDouble(ctx, "z")))
                        }
                    )
                ))
        ))
)

private fun spawn(ctx: CommandContext<CommandSourceStack>, location: Location): Int {
    val size = CinderweaverSize.fromId(StringArgumentType.getString(ctx, "size"))
        ?: throw error("Size must be hatchling, hunter or broodmother.")
    val world = location.world
    if (!location.isFinite || location.y < world.minHeight || location.y >= world.maxHeight) {
        throw error("Choose a position within the world's build height.")
    }
    if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
        throw error("Visit that area first; spiders only spawn in loaded chunks.")
    }
    val entity = AppState.createSpider(ctx.source.sender, world, location.toVector(), cinderweaver(size)) ?: return 0
    entity.query<SpiderBody>()?.preferredOrientation?.rotationY(Math.toRadians(-location.yaw.toDouble()).toFloat())
    ctx.source.sender.sendPlainMessage("Spawned Cinderweaver ${size.id}. ${AppState.spiderCount()}/${AppState.MAX_SPIDERS} active. /spider tools opens its controls.")
    return 1
}

internal fun listCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("list").executes { ctx ->
    val spiders = AppState.ecs.query<ECSEntity, SpiderBody>().filter { !it.first.scheduledForRemoval }
    ctx.source.sender.sendPlainMessage("Cinderweavers: ${spiders.size}/${AppState.MAX_SPIDERS}")
    for ((_, body) in spiders) {
        ctx.source.sender.sendPlainMessage("${body.uuid.toString().take(8)} • ${body.world.name} • ${body.position.blockX}, ${body.position.blockY}, ${body.position.blockZ}")
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
