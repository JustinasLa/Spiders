package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Something struck the spider's drawn shape at {@code point}: a projectile or a player's swing ({@code source}).
 * {@code forceful} hits push the spider and break its shroud; weak ones (a barely drawn bow) only glance off.
 */
public class SpiderHitEvent {
    private final ECSEntity entity;
    private final SpiderBody spider;
    private final Entity source;
    private final Vector point;
    private final boolean forceful;

    public SpiderHitEvent(ECSEntity entity, SpiderBody spider, Entity source, Vector point, boolean forceful) {
        this.entity = entity;
        this.spider = spider;
        this.source = source;
        this.point = point;
        this.forceful = forceful;
    }

    public ECSEntity getEntity() {
        return entity;
    }

    public SpiderBody getSpider() {
        return spider;
    }

    public Entity getSource() {
        return source;
    }

    public Vector getPoint() {
        return point;
    }

    public boolean getForceful() {
        return forceful;
    }
}
