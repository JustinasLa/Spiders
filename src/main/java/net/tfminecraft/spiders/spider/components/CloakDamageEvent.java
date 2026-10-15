package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;

public class CloakDamageEvent {
    private final ECSEntity entity;
    private final SpiderBody spider;
    private final Cloak cloak;

    public CloakDamageEvent(ECSEntity entity, SpiderBody spider, Cloak cloak) {
        this.entity = entity;
        this.spider = spider;
        this.cloak = cloak;
    }

    public ECSEntity getEntity() {
        return entity;
    }

    public SpiderBody getSpider() {
        return spider;
    }

    public Cloak getCloak() {
        return cloak;
    }
}
