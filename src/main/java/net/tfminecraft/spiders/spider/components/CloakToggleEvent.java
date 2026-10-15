package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;

public class CloakToggleEvent {
    private final ECSEntity entity;
    private final SpiderBody spider;

    public CloakToggleEvent(ECSEntity entity, SpiderBody spider) {
        this.entity = entity;
        this.spider = spider;
    }

    public ECSEntity getEntity() {
        return entity;
    }

    public SpiderBody getSpider() {
        return spider;
    }
}
