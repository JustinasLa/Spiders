package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StaffCommands {
    private StaffCommands() {}

    private static final List<String> SIZE_SUGGESTIONS = createSizeSuggestions();

    private static List<String> createSizeSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (CinderweaverSize size : CinderweaverSize.values()) suggestions.add(size.getId());
        suggestions.addAll(List.of("mimic", "0.25", "0.5", "1.0", "2.0", "2.5"));
        return suggestions;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("spawn").then(
            Commands.argument("size", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (String suggestion : SIZE_SUGGESTIONS) {
                        if (suggestion.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) builder.suggest(suggestion);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    if (StringArgumentType.getString(ctx, "size").equalsIgnoreCase(MIMIC)) return MimicCommands.placeAimed(ctx);
                    if (!(ctx.getSource().getSender() instanceof Player player)) throw error("Console: /spider spawn <size> at <world> <x> <y> <z>");
                    RayTraceResult hit = Geometry.raycastGround(player.getWorld(), Overloads.getEyePosition(player), Overloads.getDirection(player), 32.0);
                    if (hit == null) throw error("Aim at a nearby solid surface to spawn a Cinderweaver.");
                    // Floors, walls and ceilings: the spider stands on the face it was aimed at.
                    Vector up = hit.getHitBlockFace() != null ? hit.getHitBlockFace().getDirection() : Maths.upVector();
                    Vector yawDirection = Maths.rotate(Maths.forwardVector(), new Quaternionf().rotationY(Overloads.yawRadians(player)));
                    Quaternionf orientation = SpiderBody.orientationFrom(up, Overloads.getDirection(player), yawDirection);
                    return spawn(ctx, hit.getHitPosition().toLocation(player.getWorld()), orientation, up.getY() < SURFACE_FLOOR_MIN_UP);
                })
                .then(Commands.literal("at").then(
                    Commands.argument("world", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (World world : Bukkit.getWorlds()) {
                                if (world.getName().startsWith(builder.getRemaining())) builder.suggest(world.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0)).then(
                            Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                                Commands.argument("z", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0)).executes(ctx -> {
                                    World world = Bukkit.getWorld(StringArgumentType.getString(ctx, "world"));
                                    if (world == null) throw error("World not found.");
                                    return spawn(ctx, new Location(world,
                                        DoubleArgumentType.getDouble(ctx, "x"),
                                        DoubleArgumentType.getDouble(ctx, "y"),
                                        DoubleArgumentType.getDouble(ctx, "z")), new Quaternionf(), false);
                                })
                            )
                        ))
                ))
        );
    }

    private static final String MIMIC = "mimic";

    /** Faces whose normal points less upward than this are walls or ceilings. */
    private static final double SURFACE_FLOOR_MIN_UP = .7;

    private static int spawn(CommandContext<CommandSourceStack> ctx, Location location, Quaternionf orientation, boolean onSurface) throws CommandSyntaxException {
        String sizeId = StringArgumentType.getString(ctx, "size");
        if (sizeId.equalsIgnoreCase(MIMIC)) throw error("Console: /spider mimic place at <world> <x> <y> <z>");
        Double parsedScale = CinderweaverSize.parseScale(sizeId);
        if (parsedScale == null) {
            throw error("Size must be hatchling, hunter, broodmother or a scale from " + CinderweaverSize.MIN_SCALE + " to " + CinderweaverSize.MAX_SCALE + ".");
        }
        double scale = parsedScale;
        World world = location.getWorld();
        if (!location.isFinite() || location.getY() < world.getMinHeight() || location.getY() >= world.getMaxHeight()) {
            throw error("Choose a position within the world's build height.");
        }
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            throw error("Visit that area first; spiders only spawn in loaded chunks.");
        }
        SpiderOptions options = Cinderweaver.cinderweaver(scale);
        // A spider placed on a wall or ceiling has to cling to it.
        if (onSurface) options.setClimbing(true);
        if (AppState.createSpider(ctx.getSource().getSender(), world, location.toVector(), options, orientation) == null) return 0;
        CinderweaverSize size = CinderweaverSize.fromId(sizeId);
        String name = size != null ? size.getId() : "at scale " + scale;
        String climbing = onSurface ? " Clinging to the surface (wall climbing on)." : "";
        ctx.getSource().getSender().sendPlainMessage("Spawned Cinderweaver " + name + "." + climbing + " " + AppState.spiderCount() + "/" + AppState.MAX_SPIDERS + " active. /spider tools opens its controls.");
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list").executes(ctx -> {
            List<Pair<ECSEntity, SpiderBody>> spiders = new ArrayList<>();
            for (Pair<ECSEntity, SpiderBody> pair : AppState.getEcs().query(ECSEntity.class, SpiderBody.class)) {
                if (!pair.first().getScheduledForRemoval()) spiders.add(pair);
            }
            ctx.getSource().getSender().sendPlainMessage("Cinderweavers: " + spiders.size() + "/" + AppState.MAX_SPIDERS);
            for (Pair<ECSEntity, SpiderBody> pair : spiders) {
                ECSEntity entity = pair.first();
                SpiderBody body = pair.second();
                Mimic mimic = entity.query(Mimic.class);
                String kind = mimic != null ? " • mimic (" + mimic.getState().name().toLowerCase(Locale.ROOT) + ")" : "";
                String id = body.getUuid().toString();
                ctx.getSource().getSender().sendPlainMessage(id.substring(0, Math.min(8, id.length())) + " • " + body.getWorld().getName() + " • " + body.getPosition().getBlockX() + ", " + body.getPosition().getBlockY() + ", " + body.getPosition().getBlockZ() + kind);
            }
            return 1;
        });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> removeCommand() {
        return Commands.literal("remove")
            // the seal's spider, else the nearest, like the other commands
            .executes(ctx -> {
                SetupCommands.requireTargetSpider(ctx.getSource()).remove();
                ctx.getSource().getSender().sendPlainMessage("Removed the Cinderweaver.");
                return 1;
            })
            .then(Commands.literal("all").executes(ctx -> {
                List<Pair<ECSEntity, SpiderBody>> spiders = new ArrayList<>();
                for (Pair<ECSEntity, SpiderBody> pair : AppState.getEcs().query(ECSEntity.class, SpiderBody.class)) {
                    if (!pair.first().getScheduledForRemoval()) spiders.add(pair);
                }
                for (Pair<ECSEntity, SpiderBody> pair : spiders) pair.first().remove();
                ctx.getSource().getSender().sendPlainMessage("Removed " + spiders.size() + " Cinderweavers.");
                return 1;
            }))
            .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                String id = StringArgumentType.getString(ctx, "id");
                if (id.length() < 8) throw error("Use at least the eight-character ID from /spider list.");
                List<Pair<ECSEntity, SpiderBody>> matches = new ArrayList<>();
                for (Pair<ECSEntity, SpiderBody> pair : AppState.getEcs().query(ECSEntity.class, SpiderBody.class)) {
                    String uuid = pair.second().getUuid().toString();
                    if (!pair.first().getScheduledForRemoval() && uuid.regionMatches(true, 0, id, 0, id.length())) matches.add(pair);
                }
                if (matches.size() != 1) throw error("Use an unambiguous ID from /spider list.");
                matches.get(0).first().remove();
                ctx.getSource().getSender().sendPlainMessage("Removed Cinderweaver " + id + ".");
                return 1;
            }));
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }
}
