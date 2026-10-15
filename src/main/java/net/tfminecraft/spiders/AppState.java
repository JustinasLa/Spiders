package net.tfminecraft.spiders;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.HitDetector;
import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.Mountable;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.SoundsAndParticles;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import net.tfminecraft.spiders.spider.presets.MimicPose;
import net.tfminecraft.spiders.spider.presets.MimicPreset;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Objects;
import java.util.UUID;

public final class AppState {
    private AppState() {}

    public static final int MAX_SPIDERS = 6;
    private static MiscellaneousOptions miscOptions = new MiscellaneousOptions();
    private static boolean renderDebugVisuals = false;

    private static final ECS ecs = new ECS();

    public static MiscellaneousOptions getMiscOptions() {
        return miscOptions;
    }

    public static void setMiscOptions(MiscellaneousOptions miscOptions) {
        AppState.miscOptions = miscOptions;
    }

    public static boolean getRenderDebugVisuals() {
        return renderDebugVisuals;
    }

    public static void setRenderDebugVisuals(boolean renderDebugVisuals) {
        AppState.renderDebugVisuals = renderDebugVisuals;
    }

    public static ECS getEcs() {
        return ecs;
    }

    public static ECSEntity createSpider(
        CommandSender actor,
        World world,
        Vector position,
        SpiderOptions options,
        Quaternionf orientation
    ) {
        if (!StaffAccess.require(actor)) return null;
        double scale = options.getBodyPlan().getScale();
        if (!Double.isFinite(scale) || !(scale >= CinderweaverSize.MIN_SCALE && scale <= CinderweaverSize.MAX_SCALE)) {
            actor.sendPlainMessage("Cinderweaver scale must be between " + CinderweaverSize.MIN_SCALE + " and " + CinderweaverSize.MAX_SCALE + ".");
            return null;
        }
        if (spiderCount() >= MAX_SPIDERS) {
            actor.sendPlainMessage("The test limit is " + MAX_SPIDERS + " Cinderweavers. Remove one first.");
            return null;
        }
        return spawn(world, position, options, orientation);
    }

    public static ECSEntity createSpider(
        CommandSender actor,
        World world,
        Vector position,
        SpiderOptions options
    ) {
        return createSpider(actor, world, position, options, new Quaternionf());
    }

    /** A dormant mimic chest woken by anyone; the chest was placed by staff. {@code floor} is the chest's bottom centre. */
    public static ECSEntity spawnMimic(World world, Vector floor, Vector facing, UUID waker) {
        if (spiderCount() >= MAX_SPIDERS) return null;
        SpiderOptions options = MimicPreset.mimic();
        Mimic.applyMimicRise(options, 0.0);
        Quaternionf orientation = new Quaternionf().rotationY((float) Math.atan2(facing.getX(), facing.getZ()));
        return spawn(world, floor, options, orientation, new Mimic(new MimicPose(options.getBodyPlan().getBodyModel()), waker));
    }

    private static ECSEntity spawn(
        World world,
        Vector position,
        SpiderOptions options,
        Quaternionf orientation,
        ECSComponent... extra
    ) {
        Vector up = Maths.rotate(Maths.upVector().clone(), orientation);
        Vector bodyPosition = position.clone().add(up.multiply(options.getWalkGait().getStationary().getBodyHeight()));
        ECSComponent[] base = {
            SpiderBody.create(world, bodyPosition, orientation),
            options,
            new Locomotion(),
            new HitDetector(),
            new Cloak(),
            new SoundsAndParticles(),
            new Mountable(),
            new PointDetector(),
            new SpiderRenderer(),
        };
        ECSComponent[] components = new ECSComponent[base.length + extra.length];
        System.arraycopy(base, 0, components, 0, base.length);
        System.arraycopy(extra, 0, components, base.length, extra.length);
        return ecs.spawn(components);
    }

    public static int spiderCount() {
        int count = 0;
        for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
            if (!pair.first().getScheduledForRemoval()) count++;
        }
        return count;
    }

    public static ECSEntity findSpiderByUUID(UUID uuid) {
        for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
            if (!pair.first().getScheduledForRemoval() && Objects.equals(pair.second().getUuid(), uuid)) return pair.first();
        }
        return null;
    }

    public static ECSEntity findNearestSpider(Player player) {
        return findNearestSpider(player.getLocation());
    }

    public static ECSEntity findNearestSpider(Location location) {
        ECSEntity nearest = null;
        double nearestDistance = 0.0;
        for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
            if (pair.first().getScheduledForRemoval() || !Objects.equals(pair.second().getWorld(), location.getWorld())) continue;
            double distance = pair.second().getPosition().distanceSquared(location.toVector());
            if (nearest == null || Double.compare(nearestDistance, distance) > 0) {
                nearest = pair.first();
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
