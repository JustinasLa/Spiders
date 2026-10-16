package net.tfminecraft.spiders.spider;

import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.logging.Logger;

/**
 * Spiders nobody can see stop simulating. A spider sleeps while the chunk under it is unloaded
 * (its raycasts would load chunks and its displays would respawn every tick), or while no player
 * is within {@link #ACTIVE_RANGE}. It wakes, where it was, as soon as that changes.
 */
public final class SpiderActivity {
    private SpiderActivity() {
    }

    /** A spider simulates while any player is this close. */
    public static final double ACTIVE_RANGE = 96.0;

    /** Puts spiders to sleep or wakes them. Runs before each tick, outside the systems, so it sees every spider. */
    public static void updateSleep(ECS app) {
        for (Pair<ECSEntity, SpiderBody> row : app.query(ECSEntity.class, SpiderBody.class)) {
            row.first().setAsleep(!isActive(row.first(), row.second()));
        }
    }

    static boolean isActive(ECSEntity entity, SpiderBody body) {
        Vector position = body.getPosition();
        // a broken spider is left awake for removeBroken to clear
        if (!isFinite(position)) return true;
        World world = body.getWorld();
        if (!world.isChunkLoaded(position.getBlockX() >> 4, position.getBlockZ() >> 4)) return false;
        // an awake mimic keeps its own chunks loaded and must finish hiding even when nobody watches
        if (entity.query(Mimic.class) != null) return true;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().toVector().distanceSquared(position) < ACTIVE_RANGE * ACTIVE_RANGE) return true;
        }
        return false;
    }

    /** Removes spiders whose simulation went non-finite; they can never recover and would log errors every tick. */
    public static void setupRemoveBroken(ECS app, Logger logger) {
        app.onPostTick(ecs -> {
            for (Pair<ECSEntity, SpiderBody> row : app.query(ECSEntity.class, SpiderBody.class)) {
                SpiderBody body = row.second();
                if (isFinite(body.getPosition()) && isFinite(body.getVelocity()) && isFinite(body.getOrientation())) continue;
                row.first().remove();
                logger.warning("Removed a Cinderweaver in " + body.getWorld().getName() + " whose simulation broke (non-finite position or motion).");
            }
        });
    }

    static boolean isFinite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
    }

    static boolean isFinite(Quaternionf rotation) {
        return rotation.isFinite();
    }
}
