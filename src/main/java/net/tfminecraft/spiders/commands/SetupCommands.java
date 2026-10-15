package net.tfminecraft.spiders.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.MiscellaneousOptions;
import net.tfminecraft.spiders.SetupItems;
import net.tfminecraft.spiders.SpiderAnimationPlugin;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.Pounce;
import net.tfminecraft.spiders.spider.components.Splay;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Presets;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Serializer;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class SetupCommands {
    private SetupCommands() {}

    private static final int SUCCESS = 1;

    public static void setupCommands(SpiderAnimationPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(spiderCommandTree(() -> plugin.writeAndSaveConfig()).build(), "Spider animation");
        });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> spiderCommandTree(Runnable afterChange) {
        return Commands.literal("spider")
            .requires(source -> StaffAccess.canUse(source.getSender()))
            .executes(ctx -> {
                ctx.getSource().getSender().sendPlainMessage("Cinderweaver: /spider spawn <hatchling|hunter|broodmother|mimic|0.25–2.5>, mimic <place|wake|sleep>, gait <walk|gallop>, speed <walk|gallop> [blocks/s], pounce <player|at x y z> [blocks/s], climb <on|off>, shroud <on|off>, tools, list, remove [all], scale <0.25–2.5>, splay, options");
                return SUCCESS;
            })
            .then(optionsCommand(afterChange))
            .then(scaleCommand(afterChange))
            .then(gaitCommand())
            .then(climbCommand())
            .then(shroudCommand())
            .then(speedCommand())
            .then(pounceCommand())
            .then(splayCommand())
            .then(itemsCommand())
            .then(Commands.literal("tools").executes(ctx -> {
                if (!(ctx.getSource().getSender() instanceof Player player)) throw commandError("Players only");
                StaffToolMenu.openStaffToolMenu(player);
                return SUCCESS;
            }))
            .then(StaffCommands.spawnCommand())
            .then(MimicCommands.mimicCommand())
            .then(StaffCommands.listCommand())
            .then(StaffCommands.removeCommand())
            // temp debug
            .then(Commands.literal("fall").then(Commands.argument("height", DoubleArgumentType.doubleArg()).executes(ctx -> {
                double height = DoubleArgumentType.getDouble(ctx, "height");
                ECSEntity spider = findTargetSpider(ctx.getSource());
                if (spider == null) return SUCCESS;
                SpiderBody body = spider.query(SpiderBody.class);
                if (body == null) return SUCCESS;
                Vector newPosition = body.getPosition().clone();
                newPosition.setY(newPosition.getY() + height);
                body.teleport(spider, newPosition);

                return SUCCESS;
            })))
            .then(Commands.literal("walk").then(Commands.argument("ticks", IntegerArgumentType.integer(1)).executes(ctx -> {
                int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                ECSEntity spider = requireTargetSpider(ctx.getSource());
                SpiderOptions options = spider.query(SpiderOptions.class);
                if (options == null) throw commandError("No spider found");

                int[] remaining = {ticks};
                Scheduler.interval(0, 1, self -> {
                    SpiderBody body = spider.query(SpiderBody.class);
                    Locomotion locomotion = spider.query(Locomotion.class);

                    CommandSender actor = ctx.getSource().getSender();
                    if (!StaffAccess.canUse(actor) || (actor instanceof Player player && !player.isOnline()) || remaining[0]-- <= 0 || body == null || locomotion == null || spider.getScheduledForRemoval()) {
                        self.close();
                        return;
                    }

                    locomotion.walk(body.forwardDirection().multiply(options.getGait().getMaxSpeed()));
                });

                return SUCCESS;
            })));
    }

    // KNOWN LIMITATION: A path containing `[` must be quoted.
    // Paper only accepts CustomArgumentType if its native type is one of the six Brigadier primitives.
    // A greedy string would make the client treat the rest of the line as part of the path and
    // prevent auto-completion for subsequent arguments.
    // Replace with NBTPathArgument once Paper supports it, or if porting to a mod.
    private static RequiredArgumentBuilder<CommandSourceStack, String> pathArgument() {
        return Commands.argument("path", StringArgumentType.string())
            .suggests((ctx, builder) -> suggestOptionPath(ctx.getSource(), builder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> optionsCommand(Runnable afterChange) {
        return Commands.literal("options").then(
            pathArgument()
                .executes(SetupCommands::getOption)
                .then(
                    Commands.literal("set").then(
                        Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(SetupCommands::suggestOptionValue)
                            .executes(ctx -> setOption(ctx, afterChange))
                    )
                )
                .then(
                    Commands.literal("reset").executes(ctx -> resetOption(ctx, afterChange))
                )
        );
    }

    private static Pair<String, String> parsePath(String option) {
        int separator = option.indexOf('.');
        return separator < 0 ? new Pair<>(option, "")
            : new Pair<>(option.substring(0, separator), option.substring(separator + 1));
    }

    private static int getOption(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String rawPath = StringArgumentType.getString(ctx, "path");
        Pair<String, String> parsed = parsePath(rawPath);
        String groupName = parsed.first();
        String subPath = parsed.second();
        OptionGroup group = requireOptionGroup(groupName);
        Object live = requireLive(group, ctx.getSource());

        Object value = Serializer.getPath().get(live, subPath);
        if (value == null) throw commandError("\"" + subPath + "\" does not exist in " + groupName);

        ctx.getSource().getSender().sendPlainMessage(rawPath + " is " + Serializer.serialize(value));
        return SUCCESS;
    }

    private static int setOption(CommandContext<CommandSourceStack> ctx, Runnable afterChange) throws CommandSyntaxException {
        String rawPath = StringArgumentType.getString(ctx, "path");
        Pair<String, String> parsed = parsePath(rawPath);
        String groupName = parsed.first();
        String subPath = parsed.second();
        OptionGroup group = requireOptionGroup(groupName);
        Object live = requireLive(group, ctx.getSource());
        String raw = StringArgumentType.getString(ctx, "value");

        Object value = Symbols.resolveSymbol(raw);
        if (value == null) {
            try {
                value = Serializer.getGson().fromJson(raw, Object.class);
            } catch (JsonSyntaxException e) {
                // treat unparseable values as raw strings (enums, block ids)
                value = raw;
            }
        }

        Object finalValue = value;
        if (!group.edit(live, it -> writeOption(it, subPath, finalValue))) {
            throw commandError("Could not set " + rawPath);
        }

        afterChange.run();

        ctx.getSource().getSender().sendPlainMessage("Set " + rawPath + " to " + raw);
        return SUCCESS;
    }

    private static int resetOption(CommandContext<CommandSourceStack> ctx, Runnable afterChange) throws CommandSyntaxException {
        String rawPath = StringArgumentType.getString(ctx, "path");
        Pair<String, String> parsed = parsePath(rawPath);
        String groupName = parsed.first();
        String subPath = parsed.second();
        OptionGroup group = requireOptionGroup(groupName);
        Object live = requireLive(group, ctx.getSource());

        Object value = Serializer.getPath().get(group.defaultValue.get(), subPath);
        if (value == null) throw commandError("\"" + rawPath + "\" does not exist");

        if (!group.edit(live, it -> writeOption(it, subPath, value))) {
            throw commandError("Could not reset " + rawPath);
        }

        afterChange.run();

        if (subPath.isEmpty()) ctx.getSource().getSender().sendPlainMessage("Reset all of " + groupName);
        else ctx.getSource().getSender().sendPlainMessage("Reset " + rawPath + " to " + Serializer.serialize(value));
        return SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scaleCommand(Runnable afterChange) {
        return Commands.literal("scale").then(
            Commands.argument("scale", DoubleArgumentType.doubleArg(0.25, 2.5))
                .executes(ctx -> {
                    double scale = DoubleArgumentType.getDouble(ctx, "scale");

                    ECSEntity entity = requireTargetSpider(ctx.getSource());
                    if (entity.query(Mimic.class) != null) throw commandError("Mimics stay chest-sized.");
                    SpiderBody spider = entity.query(SpiderBody.class);
                    if (spider == null) throw commandError("No spider found");
                    SpiderOptions options = entity.query(SpiderOptions.class);
                    if (options == null) throw commandError("No spider found");

                    double oldScale = options.getBodyPlan().getScale();
                    options.getWalkGait().scale(scale / oldScale);
                    options.getGallopGait().scale(scale / oldScale);
                    options.getBodyPlan().scale(scale / oldScale);
                    spider.updateBodyPlan();

                    afterChange.run();
                    ctx.getSource().getSender().sendPlainMessage("Set scale to " + scale);
                    return SUCCESS;
                })
        );
    }

    private static int setGait(CommandContext<CommandSourceStack> ctx, boolean gallop) throws CommandSyntaxException {
        SpiderOptions options = requireTargetSpider(ctx.getSource()).query(SpiderOptions.class);
        if (options == null) throw commandError("No spider found");
        options.setGallop(gallop);
        ctx.getSource().getSender().sendPlainMessage(gallop ? "Gallop mode" : "Walk mode");
        return SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gaitCommand() {
        return Commands.literal("gait")
            .then(Commands.literal("walk").executes(ctx -> setGait(ctx, false)))
            .then(Commands.literal("gallop").executes(ctx -> setGait(ctx, true)));
    }

    private static final double MIN_SPEED = 0.5;
    private static final double MAX_SPEED = 20.0;
    private static final double TICKS_PER_SECOND = 20.0;

    /** Blocks per second, as typed by staff. */
    private static RequiredArgumentBuilder<CommandSourceStack, Double> speedArgument() {
        return Commands.argument("blocksPerSecond", DoubleArgumentType.doubleArg(MIN_SPEED, MAX_SPEED));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gaitArgument(String name, Function<SpiderOptions, Gait> gait) {
        return Commands.literal(name)
            .executes(ctx -> {
                SpiderOptions options = requireTargetSpider(ctx.getSource()).query(SpiderOptions.class);
                if (options == null) throw commandError("No spider found");
                ctx.getSource().getSender().sendPlainMessage(name + " speed: " + "%.1f".formatted(gait.apply(options).getMaxSpeed() * TICKS_PER_SECOND) + " blocks/s");
                return SUCCESS;
            })
            .then(speedArgument().executes(ctx -> {
                SpiderOptions options = requireTargetSpider(ctx.getSource()).query(SpiderOptions.class);
                if (options == null) throw commandError("No spider found");
                double speed = DoubleArgumentType.getDouble(ctx, "blocksPerSecond");
                gait.apply(options).setSpeed(speed / TICKS_PER_SECOND);
                ctx.getSource().getSender().sendPlainMessage("Set " + name + " speed to " + speed + " blocks/s");
                return SUCCESS;
            }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> speedCommand() {
        return Commands.literal("speed")
            .then(gaitArgument("walk", it -> it.getWalkGait()))
            .then(gaitArgument("gallop", it -> it.getGallopGait()));
    }

    private static boolean actorPresent(CommandSender actor) {
        return !(actor instanceof Player player) || player.isOnline();
    }

    /** Starts a pounce by the targeted spider; {@code name} describes the target in messages to the staff member. */
    private static int startPounce(
        CommandContext<CommandSourceStack> ctx, Double speed, String name,
        Function<SpiderBody, Vector> target
    ) throws CommandSyntaxException {
        CommandSender actor = ctx.getSource().getSender();
        ECSEntity entity = requireTargetSpider(ctx.getSource());
        SpiderBody body = entity.query(SpiderBody.class);
        if (body == null) throw commandError("No spider found");
        SpiderOptions options = entity.query(SpiderOptions.class);
        if (options == null) throw commandError("No spider found");
        if (speed != null) options.getGallopGait().setSpeed(speed / TICKS_PER_SECOND);

        Pounce.stopPounce(entity);
        entity.addComponent(new Pounce(
            // the chase also ends if the staff member loses access or leaves
            () -> StaffAccess.canUse(actor) && actorPresent(actor) ? target.apply(body) : null,
            (outcome, landedDistance) -> {
                if (actorPresent(actor)) actor.sendPlainMessage(switch (outcome) {
                    case LANDED -> "Pounced at " + name + ": landed " + "%.1f".formatted(landedDistance != null ? landedDistance : 0.0) + " blocks away";
                    case TARGET_LOST -> "Pounce ended: lost " + name;
                    case GAVE_UP -> "Pounce ended: could not reach " + name + " in time";
                    case STOPPED -> "Pounce at " + name + " stopped";
                });
            }
        ));
        String gallop = "%.1f".formatted(options.getGallopGait().getMaxSpeed() * TICKS_PER_SECOND);
        actor.sendPlainMessage("Pouncing at " + name + " (gallop " + gallop + " blocks/s). /spider pounce stop cancels.");
        return SUCCESS;
    }

    private static int pounce(CommandContext<CommandSourceStack> ctx, Double speed) throws CommandSyntaxException {
        Player player = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (player == null) throw commandError("Player not found");
        SpiderBody spiderBody = requireTargetSpider(ctx.getSource()).query(SpiderBody.class);
        World spiderWorld = spiderBody == null ? null : spiderBody.getWorld();
        if (!Objects.equals(player.getWorld(), spiderWorld)) throw commandError(player.getName() + " is not in the spider's world");
        return startPounce(ctx, speed, player.getName(), body -> {
            boolean reachable = player.isOnline() && Objects.equals(player.getWorld(), body.getWorld()) && player.getGameMode() != GameMode.SPECTATOR &&
                player.getLocation().toVector().distanceSquared(body.getPosition()) < MAX_POUNCE_DISTANCE * MAX_POUNCE_DISTANCE;
            return reachable ? player.getLocation().toVector() : null;
        });
    }

    // a fixed spot in the spider's world, e.g. for staged scenes
    private static int pounceAt(CommandContext<CommandSourceStack> ctx, Double speed) throws CommandSyntaxException {
        Vector spot = new Vector(DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"));
        return startPounce(ctx, speed, spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ(), body -> spot);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pounceCommand() {
        return Commands.literal("pounce")
            .then(Commands.literal("at").then(
                Commands.argument("x", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0)).then(
                    Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                        Commands.argument("z", DoubleArgumentType.doubleArg(-29999984.0, 29999984.0))
                            .executes(ctx -> pounceAt(ctx, null))
                            .then(speedArgument().executes(ctx -> pounceAt(ctx, DoubleArgumentType.getDouble(ctx, "blocksPerSecond"))))
                    )
                )
            ))
            .then(Commands.literal("stop").executes(ctx -> {
                ECSEntity entity = requireTargetSpider(ctx.getSource());
                // whoever started the pounce is told it stopped
                if (entity.query(Pounce.class) == null) ctx.getSource().getSender().sendPlainMessage("That spider is not pouncing");
                Pounce.stopPounce(entity);
                return SUCCESS;
            }))
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                    for (String name : names) {
                        if (name.regionMatches(true, 0, builder.getRemaining(), 0, builder.getRemaining().length())) builder.suggest(name);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> pounce(ctx, null))
                .then(speedArgument().executes(ctx -> pounce(ctx, DoubleArgumentType.getDouble(ctx, "blocksPerSecond")))));
    }

    private static final double MAX_POUNCE_DISTANCE = 96.0;

    private static int setShroud(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ECSEntity entity = requireTargetSpider(ctx.getSource());
        Cloak cloak = entity.query(Cloak.class);
        if (cloak == null) throw commandError("No spider found");
        if (cloak.getActive() != on) cloak.toggleCloak(AppState.getEcs(), entity);
        ctx.getSource().getSender().sendPlainMessage(on ? "Shroud on" : "Shroud off");
        return SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shroudCommand() {
        return Commands.literal("shroud")
            .then(Commands.literal("on").executes(ctx -> setShroud(ctx, true)))
            .then(Commands.literal("off").executes(ctx -> setShroud(ctx, false)));
    }

    private static int setClimbing(CommandContext<CommandSourceStack> ctx, boolean climbing) throws CommandSyntaxException {
        ECSEntity entity = requireTargetSpider(ctx.getSource());
        if (entity.query(Mimic.class) != null) throw commandError("Mimics keep to the floor so they can sit back down as chests.");
        SpiderOptions options = entity.query(SpiderOptions.class);
        if (options == null) throw commandError("No spider found");
        options.setClimbing(climbing);
        ctx.getSource().getSender().sendPlainMessage(climbing ? "Wall climbing on: walls and ceilings" : "Wall climbing off");
        return SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> climbCommand() {
        return Commands.literal("climb")
            .then(Commands.literal("on").executes(ctx -> setClimbing(ctx, true)))
            .then(Commands.literal("off").executes(ctx -> setClimbing(ctx, false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> splayCommand() {
        return Commands.literal("splay")
            .executes(ctx -> splaySpider(ctx, 0L))
            .then(
                Commands.argument("delay", IntegerArgumentType.integer(0))
                    .executes(ctx -> splaySpider(ctx, (long) IntegerArgumentType.getInteger(ctx, "delay")))
            );
    }

    private static int splaySpider(CommandContext<CommandSourceStack> ctx, long delay) throws CommandSyntaxException {
        ECSEntity spider = requireTargetSpider(ctx.getSource());
        Scheduler.runLater(delay, () -> {
            if (StaffAccess.canUse(ctx.getSource().getSender()) && !spider.getScheduledForRemoval()) Splay.splay(spider);
        });
        return SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> itemsCommand() {
        return Commands.literal("items").executes(ctx -> {
            if (!(ctx.getSource().getSender() instanceof Player player)) {
                throw commandError("This command can only be used by players");
            }
            StaffToolMenu.openStaffToolMenu(player);
            return SUCCESS;
        });
    }

    private static boolean writeOption(Object target, String path, Object value) {
        if (!path.isEmpty()) return Serializer.getPath().set(target, path, value);

        if (value == null) return false;
        Object root = value;
        if (!(Serializer.toMap(root) instanceof Map<?, ?> fields)) return false;
        return Serializer.getPath().setAll(target, fields);
    }

    private static CommandSyntaxException commandError(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<String> options) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(option);
        return builder.buildFuture();
    }

    private static ECSEntity findTargetSpider(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            List<Pair<ECSEntity, SpiderBody>> spiders = AppState.getEcs().query(ECSEntity.class, SpiderBody.class);
            return spiders.isEmpty() ? null : spiders.get(0).first();
        }

        UUID held = SetupItems.heldSpiderUUID(player);
        ECSEntity named = held == null ? null : AppState.findSpiderByUUID(held);
        return named != null ? named : AppState.findNearestSpider(player);
    }

    private static ECSEntity requireTargetSpider(CommandSourceStack source) throws CommandSyntaxException {
        ECSEntity spider = findTargetSpider(source);
        if (spider == null) throw commandError("No spider found");
        return spider;
    }

    private static Object suggestionSource(CommandSourceStack source, OptionGroup group) {
        Object live = group.live.apply(source);
        return live != null ? live : group.defaultValue.get();
    }

    private static final class OptionGroup {
        final Function<CommandSourceStack, Object> live;
        final Supplier<Object> defaultValue;
        final BiPredicate<Object, Object> swap;

        OptionGroup(Function<CommandSourceStack, Object> live, Supplier<Object> defaultValue, BiPredicate<Object, Object> swap) {
            this.live = live;
            this.defaultValue = defaultValue;
            this.swap = swap;
        }

        OptionGroup(Function<CommandSourceStack, Object> live, Supplier<Object> defaultValue) {
            this(live, defaultValue, null);
        }

        boolean edit(Object live, Predicate<Object> edit) {
            // edit in place
            if (swap == null) return edit.test(live);

            // edit draft then swap
            Object draft = Serializer.copyOf(live);
            return edit.test(draft) && swap.test(live, draft);
        }
    }

    private static Object requireLive(OptionGroup group, CommandSourceStack source) throws CommandSyntaxException {
        Object live = group.live.apply(source);
        if (live == null) throw commandError("No spider found");
        return live;
    }

    private static final Map<String, OptionGroup> OPTION_GROUPS = new LinkedHashMap<>();

    static {
        OPTION_GROUPS.put("spider_options", new OptionGroup(
            source -> {
                ECSEntity spider = findTargetSpider(source);
                return spider == null ? null : spider.query(SpiderOptions.class);
            },
            () -> Presets.defaultPreset(),
            (live, draft) -> {
                ((SpiderOptions) live).copyFrom((SpiderOptions) draft);
                return true;
            }
        ));
        OPTION_GROUPS.put("global", new OptionGroup(
            source -> AppState.getMiscOptions(),
            () -> new MiscellaneousOptions()
        ));
    }

    private static OptionGroup requireOptionGroup(String groupName) throws CommandSyntaxException {
        OptionGroup group = OPTION_GROUPS.get(groupName);
        if (group == null) {
            throw commandError("Unknown option group \"" + groupName + "\", expected one of " + String.join(", ", OPTION_GROUPS.keySet()));
        }
        return group;
    }

    private static CompletableFuture<Suggestions> suggestOptionPath(CommandSourceStack source, SuggestionsBuilder builder) {
        // Quoted string if needed. See NBT path comment.
        String remaining = builder.getRemaining();
        String typed = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
        Pair<String, String> parsed = parsePath(typed);
        String groupName = parsed.first();
        String path = parsed.second();

        // Suggest groups
        if (path.isEmpty()) suggest(builder, OPTION_GROUPS.keySet());

        // An exact group name falls through
        OptionGroup group = OPTION_GROUPS.get(groupName);
        if (group == null) return builder.buildFuture();

        for (String candidate : Serializer.getPath().completions(suggestionSource(source, group), path)) {
            String argument = groupName + "." + candidate;
            boolean unquotable = true;
            for (int i = 0; i < argument.length(); i++) {
                if (!StringReader.isAllowedInUnquotedString(argument.charAt(i))) {
                    unquotable = false;
                    break;
                }
            }
            builder.suggest(unquotable ? argument : "\"" + argument + "\"");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOptionValue(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Pair<String, String> parsed = parsePath(StringArgumentType.getString(ctx, "path"));
        String groupName = parsed.first();
        String path = parsed.second();
        OptionGroup group = OPTION_GROUPS.get(groupName);
        if (group == null) return builder.buildFuture();

        Object current = Serializer.getPath().get(suggestionSource(ctx.getSource(), group), path);
        if (current == null) return builder.buildFuture();

        for (Pair<String, String> suggestion : valueSuggestions(current)) {
            String text = suggestion.first();
            String tooltip = suggestion.second();
            if (tooltip == null) builder.suggest(text);
            else {
                Message message = () -> tooltip;
                builder.suggest(text, message);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Returns {@code Pair<suggestion, tooltip>[]}
     */
    private static List<Pair<String, String>> valueSuggestions(Object current) {
        List<Pair<String, String>> symbols = Symbols.suggestSymbolsFor(current);

        // don't suggest json if symbols are available
        if (!symbols.isEmpty()) return symbols;

        if (current instanceof Boolean) {
            return List.of(new Pair<>("true", "boolean"), new Pair<>("false", "boolean"));
        }

        if (current instanceof Enum<?>) {
            List<Pair<String, String>> values = new ArrayList<>();
            for (Object constant : current.getClass().getEnumConstants()) {
                values.add(new Pair<>(((Enum<?>) constant).name(), current.getClass().getSimpleName() + " value"));
            }
            return values;
        }

        if (current instanceof BlockData) {
            List<Pair<String, String>> values = new ArrayList<>();
            for (String id : getBlockIds()) values.add(new Pair<>(id, "BlockData"));
            return values;
        }

        if (current instanceof Number || current instanceof Character) {
            return List.of(new Pair<>(current.toString(), current.getClass().getSimpleName()));
        }

        String type = describeType(current);

        JsonElement tree = Serializer.getGson().toJsonTree(current);
        String text = tree instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
        if (text == null) {
            String json = Serializer.serialize(current);
            if (json.length() <= MAX_VALUE_SUGGESTION_LENGTH) return List.of(new Pair<>(json, type));
            else return List.of(new Pair<>(type, type));
        } else {
            String suggestion = needsQuoting(text) ? Serializer.serialize(text) : text;
            return List.of(new Pair<>(suggestion, type));
        }
    }

    private static final int MAX_VALUE_SUGGESTION_LENGTH = 200;

    private static boolean needsQuoting(String text) {
        try {
            return !(Serializer.getGson().fromJson(text, Object.class) instanceof String);
        } catch (Exception e) {
            return false;
        }
    }

    public static String describeType(Object value) {
        if (value instanceof Collection<?> collection) {
            Object first = null;
            if (collection instanceof List<?> list) {
                if (!list.isEmpty()) first = list.get(0);
            } else {
                Iterator<?> iterator = collection.iterator();
                if (iterator.hasNext()) first = iterator.next();
            }
            return first != null ? "List<" + first.getClass().getSimpleName() + ">" : "List<unknown>";
        }
        if (value instanceof Map<?, ?>) return "map";
        return value.getClass().getSimpleName();
    }

    private static final class BlockIdsHolder {
        static final List<String> BLOCK_IDS = createBlockIds();

        private static List<String> createBlockIds() {
            List<String> ids = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isBlock()) ids.add(material.getKey().toString());
            }
            return ids;
        }
    }

    private static List<String> getBlockIds() {
        return BlockIdsHolder.BLOCK_IDS;
    }
}
