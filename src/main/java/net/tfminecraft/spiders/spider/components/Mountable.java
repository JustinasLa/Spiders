package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.ecs.Quadruple;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import net.tfminecraft.spiders.utilities.rendering.RenderEntity;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.List;

public class Mountable implements ECSComponent {
    private ArmorStand currentMarker = null;
    private Pig currentPig = null;
    /** Gallop switched on by the rider's sprint key, to be switched off again when they let go. */
    private boolean sprintGallop = false;

    public ArmorStand getCurrentMarker() {
        return currentMarker;
    }

    public void setCurrentMarker(ArmorStand currentMarker) {
        this.currentMarker = currentMarker;
    }

    public Pig getCurrentPig() {
        return currentPig;
    }

    public void setCurrentPig(Pig currentPig) {
        this.currentPig = currentPig;
    }

    public boolean getSprintGallop() {
        return sprintGallop;
    }

    public void setSprintGallop(boolean sprintGallop) {
        this.sprintGallop = sprintGallop;
    }

    public Player getRider() {
        if (currentMarker == null) return null;
        List<Entity> passengers = currentMarker.getPassengers();
        if (passengers == null || passengers.isEmpty()) return null;
        return passengers.get(0) instanceof Player player ? player : null;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void setupMountable(ECS app) {
        Events.addEventListener(new StaffMountListener(app));

        // Handle user input
        app.onTick(ecs -> {
            for (Quadruple<Mountable, SpiderBody, SpiderOptions, ECSEntity> row : app.query(Mountable.class, SpiderBody.class, SpiderOptions.class, ECSEntity.class)) {
                Mountable mountable = row.first();
                SpiderBody spider = row.second();
                SpiderOptions options = row.third();
                ECSEntity entity = row.fourth();

                Player player = mountable.getRider();
                Cloak cloak = entity.query(Cloak.class);
                if (cloak != null) cloak.setRider(player);
                if (player == null) {
                    if (mountable.getSprintGallop()) setSprintGallop(mountable, options, false);
                    continue;
                }
                if (!StaffAccess.canUse(player)) {
                    player.leaveVehicle();
                    continue;
                }
                Locomotion locomotion = entity.query(Locomotion.class);
                if (locomotion == null) continue;

                Vector input = new Vector();
                if (player.getCurrentInput().isLeft()) input.setX(input.getX() + 1.0);
                if (player.getCurrentInput().isRight()) input.setX(input.getX() - 1.0);
                if (player.getCurrentInput().isForward()) input.setZ(input.getZ() + 1.0);
                if (player.getCurrentInput().isBackward()) input.setZ(input.getZ() - 1.0);

                // hold sprint to gallop
                boolean sprinting = player.getCurrentInput().isSprint() && !input.isZero();
                if (sprinting && !options.getGallop()) setSprintGallop(mountable, options, true);
                if (!sprinting && mountable.getSprintGallop()) setSprintGallop(mountable, options, false);

                if (options.getClimbing()) {
                    // steer along the wall or ceiling: forward is where the rider looks, flattened onto it
                    Vector up = Maths.rotate(Maths.upVector(), spider.getPreferredOrientation());
                    Vector forward = alongSurface(Overloads.getDirection(player), up);
                    if (!(forward.lengthSquared() > MIN_LOOK_ALONG_SURFACE * MIN_LOOK_ALONG_SURFACE)) {
                        forward = alongSurface(spider.forwardDirection(), up);
                    }
                    forward.normalize();
                    Vector left = up.clone().crossProduct(forward);
                    Vector direction = forward.clone().multiply(input.getZ()).add(left.multiply(input.getX()));
                    if (!direction.isZero()) direction.normalize();

                    locomotion.face(forward, Locomotion.RIDER_PRIORITY);
                    locomotion.walk(direction.multiply(options.getGait().getMaxSpeed()), Locomotion.RIDER_PRIORITY);
                    continue;
                }

                Quaternionf rotation = new Quaternionf().rotationYXZ(Overloads.yawRadians(player), .0f, .0f);
                Vector direction = input.isZero() ? input : Maths.rotate(input, rotation).normalize();

                locomotion.face(Overloads.getDirection(player), Locomotion.RIDER_PRIORITY);
                locomotion.walk(direction.multiply(options.getGait().getMaxSpeed()), Locomotion.RIDER_PRIORITY);
            }
        });

        // Render pig and marker
        app.onRender(ecs -> {
            for (Triple<SpiderBody, Mountable, SpiderOptions> row : app.query(SpiderBody.class, Mountable.class, SpiderOptions.class)) {
                SpiderBody spider = row.first();
                Mountable mountable = row.second();
                SpiderOptions options = row.third();

                Location location = spider.location().add(spider.getVelocity());

                // seat on the spider's back, which faces sideways or down on walls and ceilings
                Vector up = options.getClimbing() ? Maths.rotate(Maths.upVector(), spider.getOrientation()) : Maths.upVector();
                Location pigLocation = location.clone().add(up.clone().multiply(-.6));
                Location markerLocation = location.clone().add(up.clone().multiply(.3));
                // under a ceiling the rider hangs below the belly instead of standing in the blocks above
                if (up.getY() < 0) markerLocation.setY(markerLocation.getY() + up.getY() * RIDER_HEIGHT);

                new RenderEntity<>(
                    Pig.class,
                    pigLocation,
                    it -> {
                        it.setGravity(false);
                        it.setAI(false);
                        it.setInvisible(true);
                        it.setInvulnerable(true);
                        it.setSilent(true);
                        it.setCollidable(false);
                    },
                    it -> {
                        // a saddle would draw on the invisible pig and give the rider away
                        if (it.hasSaddle()) it.setSaddle(false);
                        mountable.setCurrentPig(it);
                    }
                ).submit(new Pair<>(spider, "mountable.pig"));

                new RenderEntity<>(
                    ArmorStand.class,
                    markerLocation,
                    it -> {
                        it.setGravity(false);
                        it.setInvisible(true);
                        it.setInvulnerable(true);
                        it.setSilent(true);
                        it.setCollidable(false);
                        it.setMarker(true);
                    },
                    it -> {
                        mountable.setCurrentMarker(it);
                    }
                ).submit(new Pair<>(spider, "mountable.marker"));
            }
        });
    }

    private static final double MIN_LOOK_ALONG_SURFACE = .35;
    private static final double RIDER_HEIGHT = 1.8;

    private static Vector alongSurface(Vector direction, Vector up) {
        return direction.clone().subtract(up.clone().multiply(direction.dot(up)));
    }

    private static void setSprintGallop(Mountable mountable, SpiderOptions options, boolean gallop) {
        options.setGallop(gallop);
        mountable.setSprintGallop(gallop);
    }
}
