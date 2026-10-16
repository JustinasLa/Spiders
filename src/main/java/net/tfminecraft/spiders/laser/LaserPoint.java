package net.tfminecraft.spiders.laser;

import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

public final class LaserPoint implements ECSComponent {
    private World world;
    private Vector position;
    private boolean isVisible;

    public LaserPoint(World world, Vector position, boolean isVisible) {
        this.world = world;
        this.position = position;
        this.isVisible = isVisible;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Vector getPosition() {
        return position;
    }

    public void setPosition(Vector position) {
        this.position = position;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    /** Spiders only follow a Brood Guide or Broodcall Whistle this close (blocks). */
    public static final double FOLLOW_RANGE = 64.0;

    public static void setupLaserPointer(ECS app) {
        app.onTick(ecs -> {
            List<LaserPoint> lasers = app.query(LaserPoint.class);

            // get spiders to follow the laser
            for (Triple<ECSEntity, SpiderBody, SpiderOptions> entry : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class)) {
                ECSEntity spiderEntity = entry.first();
                SpiderBody spider = entry.second();
                SpiderOptions options = entry.third();

                LaserPoint nearestLaser = null;
                double nearestDistance = 0.0;
                for (LaserPoint laser : lasers) {
                    if (!Objects.equals(laser.getWorld(), spider.getWorld())) continue;
                    double laserDistance = laser.getPosition().distanceSquared(spider.getPosition());
                    if (nearestLaser == null || Double.compare(nearestDistance, laserDistance) > 0) {
                        nearestLaser = laser;
                        nearestDistance = laserDistance;
                    }
                }
                if (nearestLaser == null || nearestDistance > FOLLOW_RANGE * FOLLOW_RANGE) continue;

                Locomotion locomotion = spiderEntity.query(Locomotion.class);
                if (locomotion == null) continue;

                Vector offset = nearestLaser.getPosition().clone().subtract(spider.getPosition());
                // already standing on the point: nothing to face or walk towards
                if (offset.lengthSquared() < 1e-8) continue;
                Vector direction = offset.normalize();
                locomotion.face(direction, Locomotion.LASER_PRIORITY);

                // only walk while far enough away to stop in time
                double distance = options.getWalkGait().getStationary().getBodyHeight() * 2;
                double currentSpeed = spider.getVelocity().length();
                double decelerateDistance = (currentSpeed * currentSpeed) / (2 * options.getGait().getMoveAcceleration());

                double currentDistance;
                if (options.getGait().getScanOrientation() == OrientationMode.YAxis) {
                    currentDistance = Geometry.horizontalDistance(spider.getPosition(), nearestLaser.getPosition());
                } else {
                    currentDistance = spider.getPosition().distance(nearestLaser.getPosition());
                }

                Vector walkVelocity;
                if (currentDistance > distance + decelerateDistance) {
                    walkVelocity = direction.clone().multiply(options.getGait().getMaxSpeed());
                } else {
                    walkVelocity = new Vector(0.0, 0.0, 0.0);
                }

                locomotion.walk(walkVelocity, Locomotion.LASER_PRIORITY);
            }
        });


        app.onRender(ecs -> {
            float size = .25f;
            for (LaserPoint laser : app.query(LaserPoint.class)) {
                if (!laser.isVisible()) continue;
                renderLaserPoint(laser.getWorld(), laser.getPosition(), size).submit(laser);
            }
        });
    }

    private static RenderItem renderLaserPoint(
        World world,
        Vector position,
        float size
    ) {
        return RenderUtilities.renderBlock(
            world,
            position,
            it -> {
                it.setBlock(Material.REDSTONE_BLOCK.createBlockData());
                it.setTeleportDuration(1);
                it.setBrightness(new Display.Brightness(15, 15));
                it.setTransformation(Geometry.centredTransform(size, size, size));
            }
        );
    }
}
