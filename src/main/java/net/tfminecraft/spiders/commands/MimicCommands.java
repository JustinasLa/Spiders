package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.MimicChests;
import net.tfminecraft.spiders.spider.components.MimicState;
import net.tfminecraft.spiders.spider.components.MimicTerror;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MimicCommands {
    private MimicCommands() {}

    private static final double REACH = 32.0;

    public static LiteralArgumentBuilder<CommandSourceStack> mimicCommand() {
        return Commands.literal("mimic")
            .executes(ctx -> {
                ctx.getSource().getSender().sendPlainMessage("Mimic: /spider mimic place, wake, sleep, scare. Anyone who right-clicks or hits a dormant mimic wakes it.");
                return 1;
            })
            .then(Commands.literal("scare").executes(ctx -> {
                // preview the jump scare a waker sees, to check the resource pack
                if (!(ctx.getSource().getSender() instanceof Player player)) throw error("Only players can see the jump scare.");
                MimicTerror.jumpScare(player);
                return 1;
            }))
            .then(Commands.literal("place")
                .executes(ctx -> placeAimed(ctx))
                .then(atBlock((ctx, block) -> place(ctx, block, BlockFace.SOUTH))))
            .then(Commands.literal("wake")
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) throw error("Console: /spider mimic wake at <world> <x> <y> <z>");
                    Block block = player.getTargetBlockExact((int) REACH);
                    if (block == null) throw error("Look at a dormant mimic.");
                    return wake(ctx, block);
                })
                .then(atBlock((ctx, block) -> wake(ctx, block))))
            .then(Commands.literal("sleep").executes(ctx -> {
                Pair<ECSEntity, SpiderBody> nearest = nearestAwakeMimic(ctx.getSource());
                if (nearest == null) throw error("No awake mimic found.");
                ECSEntity entity = nearest.first();
                SpiderBody body = nearest.second();
                Mimic mimic = Objects.requireNonNull(entity.query(Mimic.class));
                if (mimic.getState() == MimicState.SETTLING || mimic.getState() == MimicState.CROUCHING) throw error("That mimic is already settling down.");
                if (!mimic.beginSettling(body)) throw error("There is no free floor next to the mimic for it to become a chest.");
                ctx.getSource().getSender().sendPlainMessage("The mimic is settling back into a chest.");
                return 1;
            }));
    }

    /** Aimed at a floor: a chest on top of it, turned to face the player. */
    public static int placeAimed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getSender() instanceof Player player)) throw error("Console: /spider mimic place at <world> <x> <y> <z>");
        RayTraceResult hit = Geometry.raycastGround(player.getWorld(), Overloads.getEyePosition(player), Overloads.getDirection(player), REACH);
        Block block = hit == null ? null : hit.getHitBlock();
        if (block == null) throw error("Aim at a nearby floor to place a mimic.");
        if (hit.getHitBlockFace() != BlockFace.UP) throw error("Mimics sit on floors. Aim at the top of a block.");
        return place(ctx, block.getRelative(BlockFace.UP), Mimic.cardinalFacing(Overloads.getDirection(player).multiply(-1)));
    }

    private static int place(CommandContext<CommandSourceStack> ctx, Block block, BlockFace facing) throws CommandSyntaxException {
        if (!MimicChests.place(block, facing)) throw error("A mimic needs an empty space with solid floor beneath it.");
        ctx.getSource().getSender().sendPlainMessage("Placed a dormant mimic at " + block.getX() + ", " + block.getY() + ", " + block.getZ() + ". It wakes when anyone right-clicks or hits it.");
        return 1;
    }

    private static int wake(CommandContext<CommandSourceStack> ctx, Block block) throws CommandSyntaxException {
        if (!MimicChests.isMimic(block)) throw error("That block isn't a dormant mimic.");
        Player waker = ctx.getSource().getSender() instanceof Player player ? player : null;
        if (!MimicChests.wake(block, waker)) throw error("The test limit is " + AppState.MAX_SPIDERS + " awake creatures. Remove one first.");
        ctx.getSource().getSender().sendPlainMessage("The mimic wakes. " + AppState.spiderCount() + "/" + AppState.MAX_SPIDERS + " active.");
        return 1;
    }

    private static Pair<ECSEntity, SpiderBody> nearestAwakeMimic(CommandSourceStack source) {
        List<Pair<ECSEntity, SpiderBody>> mimics = new ArrayList<>();
        for (Pair<ECSEntity, SpiderBody> pair : AppState.getEcs().query(ECSEntity.class, SpiderBody.class)) {
            if (!pair.first().getScheduledForRemoval() && pair.first().query(Mimic.class) != null) mimics.add(pair);
        }
        if (!(source.getSender() instanceof Player player)) return mimics.isEmpty() ? null : mimics.get(0);
        Pair<ECSEntity, SpiderBody> nearest = null;
        double nearestDistance = 0.0;
        for (Pair<ECSEntity, SpiderBody> pair : mimics) {
            if (!Objects.equals(pair.second().getWorld(), player.getWorld())) continue;
            double distance = pair.second().getPosition().distanceSquared(player.getLocation().toVector());
            if (nearest == null || Double.compare(nearestDistance, distance) > 0) {
                nearest = pair;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @FunctionalInterface
    private interface BlockCommand {
        int run(CommandContext<CommandSourceStack> ctx, Block block) throws CommandSyntaxException;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> atBlock(BlockCommand run) {
        return Commands.literal("at").then(
            Commands.argument("world", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (World world : Bukkit.getWorlds()) {
                        if (world.getName().startsWith(builder.getRemaining())) builder.suggest(world.getName());
                    }
                    return builder.buildFuture();
                })
                .then(Commands.argument("x", IntegerArgumentType.integer(-29999984, 29999984)).then(
                    Commands.argument("y", IntegerArgumentType.integer()).then(
                        Commands.argument("z", IntegerArgumentType.integer(-29999984, 29999984)).executes(ctx -> {
                            World world = Bukkit.getWorld(StringArgumentType.getString(ctx, "world"));
                            if (world == null) throw error("World not found.");
                            int x = IntegerArgumentType.getInteger(ctx, "x");
                            int y = IntegerArgumentType.getInteger(ctx, "y");
                            int z = IntegerArgumentType.getInteger(ctx, "z");
                            if (y < world.getMinHeight() || y >= world.getMaxHeight()) throw error("Choose a position within the world's build height.");
                            if (!world.isChunkLoaded(x >> 4, z >> 4)) throw error("Visit that area first; mimics only wake in loaded chunks.");
                            return run.run(ctx, world.getBlockAt(x, y, z));
                        })
                    )
                ))
        );
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }
}
