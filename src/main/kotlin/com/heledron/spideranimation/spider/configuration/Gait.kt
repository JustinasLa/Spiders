package com.heledron.spideranimation.spider.configuration

import com.heledron.spideranimation.spider.components.body.GaitType
import com.heledron.spideranimation.spider.components.body.SpiderBody
import com.heledron.spideranimation.utilities.maths.horizontal
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.toRadians
import org.joml.Quaternionf
import kotlin.math.max


class Posture(
    var bodyHeight: Double,
    var triggerZoneRadius: Double,
) {
    fun scale(scale: Double): Posture {
        bodyHeight *= scale
        triggerZoneRadius *= scale
        return this
    }

    fun clone() = Posture(
        bodyHeight = bodyHeight,
        triggerZoneRadius = triggerZoneRadius,
    )

    fun lerp(target: Posture, factor: Double): Posture {
        this.bodyHeight = bodyHeight.lerp(target.bodyHeight, factor)
        this.triggerZoneRadius = triggerZoneRadius.lerp(target.triggerZoneRadius, factor)
        return this
    }
}


/** Comfort zone kept around a lengthened stride. */
private const val STRIDE_COMFORT_RATIO = 1.5

class Gait(
    walkSpeed: Double,
    val type: GaitType,
) {
    companion object {
        fun defaultWalk() = Gait(.15, GaitType.WALK)

        fun defaultGallop() = Gait(.4, GaitType.GALLOP).apply {
            moving.bodyHeight = 1.6
            legMoveSpeed = .5
            rotateAcceleration = .25f / 4
            uncomfortableSpeedMultiplier = .6
            samePairCooldown = 2
            crossPairCooldown = 4
        }
    }

    fun scale(scale: Double) {
        stationary.scale(scale)
        moving.scale(scale)
        maxBodyDistanceFromGround *= scale
        maxSpeed *= scale
        moveAcceleration *= scale
        legMoveSpeed *= scale
        legLiftHeight *= scale
        comfortZoneRadius *= scale
        legScanHeightBias *= scale
        tridentRotationalKnockBack /= scale
    }

    /**
     * Change the top speed. Stride length, stride speed and acceleration follow in proportion,
     * so steps keep their rhythm instead of falling behind (which would stall the spider).
     */
    fun setSpeed(speed: Double) {
        val factor = speed / maxSpeed
        maxSpeed = speed
        legMoveSpeed *= factor
        moveAcceleration *= factor
        moving.triggerZoneRadius *= factor
        comfortZoneRadius = max(comfortZoneRadius, moving.triggerZoneRadius * STRIDE_COMFORT_RATIO)
    }

    var stationary = Posture(
        bodyHeight = 1.1,
        triggerZoneRadius = .25,
    )

    var moving = Posture(
        bodyHeight = 1.1,
        triggerZoneRadius = .8,
    )

    var maxBodyDistanceFromGround = .25

    var maxSpeed = walkSpeed
    var moveAcceleration = .15 / 4

    var rotateAcceleration = .15f / 4
    var rotationalDragCoefficient = .2f

    var legMoveSpeed = walkSpeed * 2.5

    var legLiftHeight = .35

    var comfortZoneRadius = 1.2

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

    var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    var bodyHeightCorrectionFactor = .25

    var legScanAlternativeGround = true
    var legScanHeightBias = .5

    var tridentKnockBack = .3
    var tridentRotationalKnockBack = tridentKnockBack / 4
    var legLookAheadFraction = .6
    var groundDragCoefficient = .2

    var samePairCooldown = 1
    var crossPairCooldown = 1

    var gripAcceleration = gravityAcceleration * 4
    var grip = false

    var topplingAcceleration = gravityAcceleration / 4

    var uncomfortableSpeedMultiplier = 0.0

    var preferredPitchLeeway = 10f.toRadians()

    var scanOrientation = OrientationMode.YAxis

    var preferredRotationLerpFraction = .3f

    var rotationLerp = .3f
}


enum class OrientationMode(val get: (spider: SpiderBody) -> Quaternionf) {
    YAxis({ spider -> spider.orientation.horizontal() }),
    SpiderOrientation({ spider -> spider.orientation }),
    GroundOrientation({ spider -> spider.preferredOrientation })
}
