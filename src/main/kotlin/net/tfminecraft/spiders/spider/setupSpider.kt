package net.tfminecraft.spiders.spider

import net.tfminecraft.spiders.spider.components.body.setupSpiderBody
import net.tfminecraft.spiders.spider.components.*
import net.tfminecraft.spiders.spider.components.rendering.setupRenderer
import net.tfminecraft.spiders.utilities.ecs.ECS

fun setupSpider(app: ECS) {
    // before the body, so a mimic's height and steering apply on the same tick
    setupMimic(app)
    setupSpiderBody(app)
    setupLocomotion(app)
    setupPounce(app)
    setupSpiderRepulsion(app)

    setupCloak(app)
    setupMountable(app)
    setupPointDetector(app)
    setupSoundAndParticles(app)
    setupHitDetector(app)
    setupRenderer(app)
}
