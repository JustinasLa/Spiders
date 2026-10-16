package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.CloakOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.block_colors.BlockColorMatch;
import net.tfminecraft.spiders.utilities.block_colors.BlockColors;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.colors.Oklab;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.events.SeriesScheduler;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class Cloak implements ECSComponent {
    private boolean active = false;
    private WeakHashMap<Object, Oklab> cloakColor = new WeakHashMap<>();
    private WeakHashMap<Object, BlockData> cloakOverride = new WeakHashMap<>();
    private boolean cloakGlitching = false;
    /** Whoever rides the spider; the shroud imitates what others see, not the rider. */
    private Player rider = null;

    // what each piece last saw, and when: a ray per piece every tick is too costly
    private final WeakHashMap<Object, BlockData> seen = new WeakHashMap<>();
    private final WeakHashMap<Object, Integer> seenAt = new WeakHashMap<>();
    /** Each piece looks again this often (ticks), staggered across pieces. */
    static final int LOOK_TICKS = 4;

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setRider(Player rider) {
        this.rider = rider;
    }

    public void toggleCloak(ECS app, ECSEntity entity) {
        SpiderBody spider = entity.query(SpiderBody.class);
        if (spider == null) return;
        active = !active;
        app.emit(new CloakToggleEvent(entity, spider));
    }

    public Pair<BlockData, Display.Brightness> getPiece(Object id, World world, Vector position, BlockData originalBlock, Display.Brightness originalBrightness, CloakOptions options) {
        applyCloak(id, world, position, originalBlock, originalBrightness != null ? originalBrightness.getSkyLight() : 15, options);

        BlockData override = cloakOverride.get(id);
        if (override != null) return new Pair<>(override, new Display.Brightness(0, 15));

        Oklab cloakColor = this.cloakColor.get(id);
        if (cloakColor == null) return new Pair<>(originalBlock, originalBrightness);
        BlockColorMatch match = BlockColors.findBlockWithColor(cloakColor.toRGB(), options.getAllowCustomBrightness());
        // a full-colour match is lit by the world like the blocks it imitates
        if (!options.getAllowCustomBrightness()) return new Pair<>(match.getBlock(), null);
        return new Pair<>(match.getBlock(), new Display.Brightness(0, match.getBrightness()));
    }

    private void applyCloak(Object id, World world, Vector position, BlockData originalBlock, int originalBrightness, CloakOptions options) {
        if (cloakGlitching) return;

        Color originalRgb = BlockColors.getBlockColor(originalBlock, originalBrightness);
        if (originalRgb == null) return;
        Oklab originalColor = Oklab.toOklab(originalRgb);
        Oklab storedColor = cloakColor.get(id);
        Oklab currentColor = storedColor != null ? storedColor : originalColor;

        Oklab targetColor = getTargetColor(id, world, position, originalColor, currentColor);


        float lerpAmount = (float) options.getLerpSpeed() + ((float) Math.random() - 0.5f) * (float) options.getLerpRandomness();
        Oklab newColor = moveTowards(
            currentColor.lerp(targetColor, coerceIn(lerpAmount, 0f, 1f)),
            targetColor, (float) options.getMoveSpeed());

        if (Objects.equals(newColor, originalColor)) cloakColor.remove(id);
        else cloakColor.put(id, newColor);
    }

    private Oklab getTargetColor(Object id, World world, Vector position, Oklab originalColor, Oklab currentColor) {
        if (!active) return originalColor;

        BlockData block = lookBehind(id, world, position);
        if (block == null) return currentColor;
        int lightLevel = 15;
        Color color = BlockColors.getBlockColor(block, lightLevel);
        return color != null ? Oklab.toOklab(color) : currentColor;
    }


    private BlockData lookBehind(Object id, World world, Vector position) {
        int now = Core.getCurrentTick();
        Integer at = seenAt.get(id);
        if (at != null && now >= at && now - at < LOOK_TICKS) return seen.get(id);
        BlockData block = shroudSees(world, position, rider);
        seen.put(id, block);
        // the first look is backdated by up to LOOK_TICKS - 1 so the pieces don't all look on the same tick
        seenAt.put(id, at == null ? now - Math.floorMod(id.hashCode(), LOOK_TICKS) : now);
        return block;
    }

    public void breakCloak(CloakOptions options) {
        cloakGlitching = true;

        List<Oklab> originalColors = new ArrayList<>(cloakColor.values());

        List<Consumer<Object>> glitch = List.of(
            id -> cloakOverride.put(id, Material.LIGHT_BLUE_GLAZED_TERRACOTTA.createBlockData()),
            id -> cloakOverride.put(id, Material.CYAN_GLAZED_TERRACOTTA.createBlockData()),
            id -> cloakOverride.put(id, Material.WHITE_GLAZED_TERRACOTTA.createBlockData()),
            id -> cloakOverride.put(id, Material.GRAY_GLAZED_TERRACOTTA.createBlockData()),

            id -> cloakOverride.put(id, null),
            id -> cloakColor.put(id, random(originalColors))
        );

        int maxTime = 0;

        for (Object id : cloakColor.keySet()) {
            SeriesScheduler scheduler = new SeriesScheduler();

            randomSleep(scheduler, 0, 3);
            int glitches = (int) (Math.random() * 4);
            for (int i = 0; i < glitches; i++) {
                scheduler.run(() -> random(glitch).accept(id));
                scheduler.sleep(2L);
            }

            scheduler.run(() -> {
                cloakColor.put(id, null);
                cloakOverride.put(id, null);
            });

            if (Math.random() < 1.0 / 6) continue;

            randomSleep(scheduler, 0, 3);

            int flashes = (int) (Math.random() * 3);
            for (int i = 0; i < flashes; i++) {
                scheduler.run(() -> {
                    cloakOverride.put(id, BlockColors.findBlockWithColor(random(originalColors).toRGB(), options.getAllowCustomBrightness()).getBlock());
                });

                randomSleep(scheduler, 5, 15);

                scheduler.run(() -> {
                    cloakOverride.put(id, null);
                });
                scheduler.sleep(2L);
            }

            if (scheduler.getTime() > maxTime) maxTime = (int) scheduler.getTime();
        }

        Scheduler.runLater((long) maxTime, () -> {
            cloakGlitching = false;
        });
    }

    private static void randomSleep(SeriesScheduler scheduler, int min, int max) {
        scheduler.sleep((long) (min + Math.random() * (max - min)));
    }

    private static <T> T random(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // Kotlin's coerceIn (keeps -0.0 and NaN as they are, unlike Math.clamp)
    private static float coerceIn(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Players further than this from a piece do not steer the shroud; it copies the ground instead. */
    private static final double VIEWER_RANGE = 48.0;

    private static Oklab moveTowards(Oklab from, Oklab target, float maxDelta) {
        float delta = Oklab.distanceTo(from, target);
        if (delta <= maxDelta) return target;
        float t = maxDelta / delta;
        return from.lerp(target, t);
    }

    public static void setupCloak(ECS app) {
        app.onEvent(SpiderHitEvent.class, event -> {
            if (!event.getForceful()) return;
            Cloak cloak = event.getEntity().query(Cloak.class);
            if (cloak == null) return;
            if (cloak.getActive()) {
                SpiderOptions spiderOptions = event.getEntity().query(SpiderOptions.class);
                CloakOptions options = spiderOptions != null ? spiderOptions.getCloak() : null;
                if (options == null) return;
                app.emit(new CloakDamageEvent(event.getEntity(), event.getSpider(), cloak));
                cloak.breakCloak(options);
            }
            cloak.setActive(false);
        });
    }

    public static BlockData shroudSees(World world, Vector position) {
        return shroudSees(world, position, null);
    }

    /**
     * The block a shroud piece at {@code position} imitates: what lies behind it as seen by the nearest
     * player in this world, or the ground beneath when nobody is close. Water and lava surfaces count.
     * Spectators, vanished staff and the spider's {@code rider} don't count as viewers.
     */
    public static BlockData shroudSees(World world, Vector position, Player rider) {
        Player viewer = null;
        double viewerDistance = 0.0;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.equals(rider) || isVanished(player)) continue;
            double distance = Overloads.getEyePosition(player).distanceSquared(position);
            if (viewer == null || Double.compare(viewerDistance, distance) > 0) {
                viewer = player;
                viewerDistance = distance;
            }
        }
        if (viewer != null && !(Overloads.getEyePosition(viewer).distanceSquared(position) < VIEWER_RANGE * VIEWER_RANGE)) viewer = null;
        Vector direction = viewer != null ? position.clone().subtract(Overloads.getEyePosition(viewer)) : null;

        RayTraceResult hit = direction == null || direction.lengthSquared() < 1e-8
            ? surfaceCast(world, position, Maths.downVector(), GROUND_RANGE)
            : surfaceCast(world, position, direction, BEHIND_RANGE);
        if (hit == null) return null;
        Block hitBlock = hit.getHitBlock();
        return hitBlock != null ? hitBlock.getBlockData() : null;
    }

    /** Vanish plugins (SuperVanish, PremiumVanish, Essentials) mark vanished players with this metadata. */
    static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }

    private static RayTraceResult surfaceCast(World world, Vector position, Vector direction, double distance) {
        return world.rayTraceBlocks(position.toLocation(world), direction, distance, FluidCollisionMode.ALWAYS, true);
    }

    private static final double GROUND_RANGE = 5.0;
    private static final double BEHIND_RANGE = 30.0;
}
