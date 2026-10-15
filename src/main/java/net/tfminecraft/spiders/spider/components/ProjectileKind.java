package net.tfminecraft.spiders.spider.components;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;

/** How hard each kind of projectile pushes, compared with a trident. */
public enum ProjectileKind {
    ARROW(.3, 1.0),
    TRIDENT(1.0, 1.0),
    THROWN(.15, .5),
    WIND_CHARGE(1.5, 0.0);

    private final double weight;
    private final double minForcefulSpeed;

    ProjectileKind(double weight, double minForcefulSpeed) {
        this.weight = weight;
        this.minForcefulSpeed = minForcefulSpeed;
    }

    public double getWeight() {
        return weight;
    }

    public double getMinForcefulSpeed() {
        return minForcefulSpeed;
    }

    public static ProjectileKind of(Projectile projectile) {
        if (projectile instanceof Trident) return TRIDENT;
        if (projectile instanceof AbstractArrow) return ARROW;
        if (projectile instanceof AbstractWindCharge) return WIND_CHARGE;
        if (projectile instanceof Snowball || projectile instanceof Egg) return THROWN;
        return null;
    }
}
