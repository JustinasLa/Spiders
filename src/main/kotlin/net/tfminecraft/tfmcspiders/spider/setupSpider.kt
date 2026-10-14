package net.tfminecraft.tfmcspiders.spider

import net.tfminecraft.tfmcspiders.spider.components.body.setupSpiderBody
import net.tfminecraft.tfmcspiders.spider.components.*
import net.tfminecraft.tfmcspiders.spider.components.rendering.setupRenderer
import net.tfminecraft.tfmcspiders.utilities.ecs.ECS

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
