package net.tfminecraft.spiders.spider.components.body;

import net.tfminecraft.spiders.utilities.ecs.ECSEntity;

public class LegStepEvent {
    private final ECSEntity entity;
    private final SpiderBody spider;
    private final Leg leg;

    public LegStepEvent(ECSEntity entity, SpiderBody spider, Leg leg) {
        this.entity = entity;
        this.spider = spider;
        this.leg = leg;
    }

    public ECSEntity getEntity() {
        return entity;
    }

    public SpiderBody getSpider() {
        return spider;
    }

    public Leg getLeg() {
        return leg;
    }
}
