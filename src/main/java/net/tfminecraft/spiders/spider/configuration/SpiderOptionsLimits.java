package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import net.tfminecraft.spiders.utilities.Serializer;

/** Bounds on options staff can set with {@code /spider options}, so one typo can't lag or break the server. */
public final class SpiderOptionsLimits {
    private SpiderOptionsLimits() {
    }

    public static final int MAX_LEGS = 12;
    public static final int MAX_SEGMENTS = 8;
    /** Longest leg segment, in blocks. */
    public static final double MAX_SEGMENT_LENGTH = 8.0;

    /** Why these options can't be used, or null if they can. */
    public static String problem(SpiderOptions options) {
        String nonFinite = nonFinite(options);
        return nonFinite != null ? nonFinite : problem(options.getBodyPlan());
    }

    /** A complaint if any number in {@code part} is NaN or infinite (the serializer refuses those). */
    public static String nonFinite(Object part) {
        try {
            Serializer.serialize(part);
            return null;
        } catch (IllegalArgumentException e) {
            return "every number must be finite";
        }
    }

    /** Why this body can't be used, or null if it can. */
    public static String problem(BodyPlan plan) {
        double scale = plan.getScale();
        if (!(scale >= CinderweaverSize.MIN_SCALE && scale <= CinderweaverSize.MAX_SCALE)) {
            return "scale must be between " + CinderweaverSize.MIN_SCALE + " and " + CinderweaverSize.MAX_SCALE;
        }
        if (plan.getLegs().isEmpty() || plan.getLegs().size() > MAX_LEGS) return "a spider needs 1 to " + MAX_LEGS + " legs";
        for (LegPlan leg : plan.getLegs()) {
            if (leg.getSegments().isEmpty() || leg.getSegments().size() > MAX_SEGMENTS) return "each leg needs 1 to " + MAX_SEGMENTS + " segments";
            for (SegmentPlan segment : leg.getSegments()) {
                if (!(segment.getLength() > 0.0 && segment.getLength() <= MAX_SEGMENT_LENGTH)) {
                    return "leg segments must be longer than 0 and at most " + MAX_SEGMENT_LENGTH + " blocks";
                }
            }
        }
        return null;
    }
}
