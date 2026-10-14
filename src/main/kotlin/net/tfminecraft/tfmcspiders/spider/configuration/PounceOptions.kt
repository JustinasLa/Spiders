package net.tfminecraft.tfmcspiders.spider.configuration

/** A pounce gallops at a target and leaps the last stretch. The run uses the gallop gait's speed. */
class PounceOptions {
    /** Longest leap in blocks for a scale 1 spider; scales with the spider. */
    var leapRange = 5.0

    /** Fastest leap along the ground, as a multiple of the gallop speed. */
    var leapBoost = 2.0

    /** Upward launch speed in blocks per tick for a scale 1 spider; scales with the square root of its size. */
    var leapLift = .5

    /** Give up the chase after this many ticks. */
    var giveUpTicks = 400
}
