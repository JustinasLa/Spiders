package net.tfminecraft.spiders.commands

import net.tfminecraft.spiders.AppState
import net.tfminecraft.spiders.spider.components.Mimic
import net.tfminecraft.spiders.spider.components.MimicChests
import net.tfminecraft.spiders.spider.components.MimicState
import net.tfminecraft.spiders.spider.components.body.SpiderBody
import net.tfminecraft.spiders.spider.components.cardinalFacing
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import net.tfminecraft.spiders.utilities.overloads.direction
import net.tfminecraft.spiders.utilities.overloads.eyePosition
import net.tfminecraft.spiders.utilities.raycastGround
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player

private const val REACH = 32.0

internal fun mimicCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("mimic")
    .executes { ctx ->
        ctx.source.sender.sendPlainMessage("Mimic: /spider mimic place, wake, sleep. Anyone who right-clicks or hits a dormant mimic wakes it.")
        1
    }
    .then(Commands.literal("place")
        .executes { ctx -> placeAimed(ctx) }
        .then(atBlock { ctx, block -> place(ctx, block, BlockFace.SOUTH) }))
    .then(Commands.literal("wake")
        .executes { ctx ->
            val player = ctx.source.sender as? Player ?: throw error("Console: /spider mimic wake at <world> <x> <y> <z>")
            val block = player.getTargetBlockExact(REACH.toInt()) ?: throw error("Look at a dormant mimic.")
            wake(ctx, block)
        }
        .then(atBlock { ctx, block -> wake(ctx, block) }))
    .then(Commands.literal("sleep").executes { ctx ->
        val (entity, body) = nearestAwakeMimic(ctx.source) ?: throw error("No awake mimic found.")
        val mimic = entity.query<Mimic>()!!
        if (mimic.state == MimicState.SETTLING || mimic.state == MimicState.CROUCHING) throw error("That mimic is already settling down.")
        if (!mimic.beginSettling(body)) throw error("There is no free floor next to the mimic for it to become a chest.")
        ctx.source.sender.sendPlainMessage("The mimic is settling back into a chest.")
        1
    })

/** Aimed at a floor: a chest on top of it, turned to face the player. */
internal fun placeAimed(ctx: CommandContext<CommandSourceStack>): Int {
    val player = ctx.source.sender as? Player ?: throw error("Console: /spider mimic place at <world> <x> <y> <z>")
    val hit = player.world.raycastGround(player.eyePosition, player.direction, REACH)
    val block = hit?.hitBlock ?: throw error("Aim at a nearby floor to place a mimic.")
    if (hit.hitBlockFace != BlockFace.UP) throw error("Mimics sit on floors. Aim at the top of a block.")
    return place(ctx, block.getRelative(BlockFace.UP), cardinalFacing(player.direction.multiply(-1)))
}

private fun place(ctx: CommandContext<CommandSourceStack>, block: Block, facing: BlockFace): Int {
    if (!MimicChests.place(block, facing)) throw error("A mimic needs an empty space with solid floor beneath it.")
    ctx.source.sender.sendPlainMessage("Placed a dormant mimic at ${block.x}, ${block.y}, ${block.z}. It wakes when anyone right-clicks or hits it.")
    return 1
}

private fun wake(ctx: CommandContext<CommandSourceStack>, block: Block): Int {
    if (!MimicChests.isMimic(block)) throw error("That block isn't a dormant mimic.")
    if (!MimicChests.wake(block, ctx.source.sender as? Player)) throw error("The test limit is ${AppState.MAX_SPIDERS} awake creatures. Remove one first.")
    ctx.source.sender.sendPlainMessage("The mimic wakes. ${AppState.spiderCount()}/${AppState.MAX_SPIDERS} active.")
    return 1
}

private fun nearestAwakeMimic(source: CommandSourceStack): Pair<ECSEntity, SpiderBody>? {
    val mimics = AppState.ecs.query<ECSEntity, SpiderBody>()
        .filter { !it.first.scheduledForRemoval && it.first.query<Mimic>() != null }
    val player = source.sender as? Player ?: return mimics.firstOrNull()
    return mimics.filter { it.second.world == player.world }
        .minByOrNull { it.second.position.distanceSquared(player.location.toVector()) }
}

private fun atBlock(run: (CommandContext<CommandSourceStack>, Block) -> Int): ArgumentBuilder<CommandSourceStack, *> =
    Commands.literal("at").then(
        Commands.argument("world", StringArgumentType.word())
            .suggests { _, builder ->
                Bukkit.getWorlds().filter { it.name.startsWith(builder.remaining) }.forEach { builder.suggest(it.name) }
                builder.buildFuture()
            }
            .then(Commands.argument("x", IntegerArgumentType.integer(-29999984, 29999984)).then(
                Commands.argument("y", IntegerArgumentType.integer()).then(
                    Commands.argument("z", IntegerArgumentType.integer(-29999984, 29999984)).executes { ctx ->
                        val world = Bukkit.getWorld(StringArgumentType.getString(ctx, "world")) ?: throw error("World not found.")
                        val x = IntegerArgumentType.getInteger(ctx, "x")
                        val y = IntegerArgumentType.getInteger(ctx, "y")
                        val z = IntegerArgumentType.getInteger(ctx, "z")
                        if (y < world.minHeight || y >= world.maxHeight) throw error("Choose a position within the world's build height.")
                        if (!world.isChunkLoaded(x shr 4, z shr 4)) throw error("Visit that area first; mimics only wake in loaded chunks.")
                        run(ctx, world.getBlockAt(x, y, z))
                    }
                )
            ))
    )

private fun error(message: String) = SimpleCommandExceptionType { message }.create()
