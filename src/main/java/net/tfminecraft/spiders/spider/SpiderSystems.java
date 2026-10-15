package net.tfminecraft.spiders.spider;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.HitDetector;
import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.Mountable;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.Pounce;
import net.tfminecraft.spiders.spider.components.SoundsAndParticles;
import net.tfminecraft.spiders.spider.components.SpiderRepulsion;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.utilities.ecs.ECS;

public final class SpiderSystems {
    private SpiderSystems() {
    }

    public static void setupSpider(ECS app) {
        // before the body, so a mimic's height and steering apply on the same tick
        Mimic.setupMimic(app);
        SpiderBody.setupSpiderBody(app);
        Locomotion.setupLocomotion(app);
        Pounce.setupPounce(app);
        SpiderRepulsion.setupSpiderRepulsion(app);

        Cloak.setupCloak(app);
        Mountable.setupMountable(app);
        PointDetector.setupPointDetector(app);
        SoundsAndParticles.setupSoundAndParticles(app);
        HitDetector.setupHitDetector(app);
        SpiderRenderer.setupRenderer(app);
    }
}
