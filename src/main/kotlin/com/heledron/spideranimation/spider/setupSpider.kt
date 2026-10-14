package com.heledron.spideranimation.spider

import com.heledron.spideranimation.spider.components.body.setupSpiderBody
import com.heledron.spideranimation.spider.components.*
import com.heledron.spideranimation.spider.components.rendering.setupRenderer
import com.heledron.spideranimation.utilities.ecs.ECS

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
