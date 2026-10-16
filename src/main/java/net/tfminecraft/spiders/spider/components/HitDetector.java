package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.ecs.Quadruple;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HitDetector implements ECSComponent {
    private boolean stunned = false;

    public boolean getStunned() {
        return stunned;
    }

    public void setStunned(boolean stunned) {
        this.stunned = stunned;
    }

    public static void setupHitDetector(ECS app) {
        Events.addEventListener(new SeatProjectileListener(app));
        Events.addEventListener(new MeleeListener(app));
        setupProjectileCollisions(app);
    }

    public static void setupProjectileCollisions(ECS app) {
        // Runs before entities move this tick, so each projectile's coming step is tested against the shape.
        app.onTick(ecs -> {
            for (Quadruple<ECSEntity, SpiderBody, SpiderOptions, HitDetector> row : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class, HitDetector.class)) {
                ECSEntity entity = row.first();
                SpiderBody spider = row.second();
                SpiderOptions options = row.third();

                double range = SpiderShape.reach(options.getBodyPlan()) + MAX_PROJECTILE_STEP;
                List<Projectile> projectiles = new ArrayList<>();
                for (Entity nearby : spider.getWorld().getNearbyEntities(spider.getPosition().toLocation(spider.getWorld()), range, range, range)) {
                    if (!(nearby instanceof Projectile it)) continue;
                    if (!(ProjectileKind.of(it) != null && it.isValid() && it.getVelocity().lengthSquared() > MIN_SPEED * MIN_SPEED)) continue;
                    if (it instanceof AbstractArrow arrow && arrow.isInBlock()) continue;
                    // a loyalty trident flying home after its hit passes through
                    if (it instanceof Trident trident && trident.hasDealtDamage()) continue;
                    projectiles.add(it);
                }
                if (projectiles.isEmpty()) continue;

                Mountable mountable = entity.query(Mountable.class);
                Player rider = mountable != null ? mountable.getRider() : null;
                List<ShapePiece> pieces = SpiderShape.shapePieces(spider, options.getBodyPlan());

                for (Projectile projectile : projectiles) {
                    if (rider != null && Objects.equals(projectile.getShooter(), rider)) continue;
                    // not null: only projectiles of a known kind were collected above
                    ProjectileKind kind = ProjectileKind.of(projectile);

                    Vector from = projectile.getLocation().toVector();
                    Vector velocity = projectile.getVelocity();
                    Pair<Double, Vector> hit = SpiderShape.firstHit(pieces, from, from.clone().add(velocity));
                    if (hit == null) continue;
                    Vector point = hit.second();

                    Vector direction = velocity.clone().normalize();
                    boolean forceful = velocity.length() >= kind.getMinForcefulSpeed();
                    if (forceful) {
                        // a wind burst shoves away from where it bursts; everything else along its flight
                        Vector push = kind == ProjectileKind.WIND_CHARGE ? away(spider, point, direction) : direction;
                        knockBack(spider, options, push, point, kind.getWeight());
                    }

                    switch (kind) {
                        case ARROW, TRIDENT -> {
                            // bounce off where it struck, then fall
                            Location location = projectile.getLocation();
                            location.set(point.getX(), point.getY(), point.getZ());
                            projectile.teleport(location.subtract(direction.clone().multiply(BACK_OFF)));
                            projectile.setVelocity(direction.clone().multiply(-velocity.length() * BOUNCE));
                            if (projectile instanceof Trident trident) trident.setHasDealtDamage(true);
                        }
                        case THROWN -> {
                            // THROWN is only ever a snowball or an egg
                            ItemStack item = ((ThrowableProjectile) projectile).getItem();
                            if (item != null) spider.getWorld().spawnParticle(Particle.ITEM, point.toLocation(spider.getWorld()), 8, .1, .1, .1, .05, item);
                            projectile.remove();
                        }
                        default -> { // WIND_CHARGE, the only kind left
                            Location location = projectile.getLocation();
                            location.set(point.getX(), point.getY(), point.getZ());
                            projectile.teleport(location);
                            ((AbstractWindCharge) projectile).explode();
                        }
                    }

                    app.emit(new SpiderHitEvent(entity, spider, projectile, point, forceful));
                }
            }
        });
    }

    private static Vector away(SpiderBody spider, Vector point, Vector fallback) {
        Vector out = spider.getPosition().clone().subtract(point);
        return out.lengthSquared() < 1e-6 ? fallback.clone() : out.normalize();
    }

    static void knockBack(SpiderBody spider, SpiderOptions options, Vector direction, Vector point, double weight) {
        Gait gait = options.getGait();
        spider.shove(direction.clone().multiply(gait.getTridentKnockBack() * weight));

        // spin about the vertical, more for hits far from straight up
        Vector hitDirection = spider.getPosition().clone().subtract(point).normalize();
        Vector axis = Maths.upVector().crossProduct(direction);
        float angle = hitDirection.angle(Maths.upVector());
        spider.accelerateRotation(axis, (float) (angle * gait.getTridentRotationalKnockBack() * weight));
    }

    /** Fastest a projectile moves in one tick (blocks); widens the search around the spider. */
    private static final double MAX_PROJECTILE_STEP = 4.0;
    private static final double MIN_SPEED = .05;
    private static final double BOUNCE = .1;
    private static final double BACK_OFF = .05;
    /** A fully charged swing pushes this much compared with a trident. */
    static final double MELEE_WEIGHT = .5;
    /** Swings while mining or spamming the button are too weak to count. */
    static final double MIN_CHARGE = .3;
}
