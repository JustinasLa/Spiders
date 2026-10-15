package net.tfminecraft.spiders.spider.components.body;

public class SpiderBodyHitGroundEvent {
    private final SpiderBody spider;

    public SpiderBodyHitGroundEvent(SpiderBody spider) {
        this.spider = spider;
    }

    public SpiderBody getSpider() {
        return spider;
    }
}
