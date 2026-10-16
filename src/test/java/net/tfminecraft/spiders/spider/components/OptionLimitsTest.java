package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptionsLimits;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OptionLimitsTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    @Test
    @DisplayName("every Cinderweaver size is within the limits")
    void everyCinderweaverSizeIsWithinTheLimits() {
        assertNull(SpiderOptionsLimits.problem(Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.MIN_SCALE)));
        assertNull(SpiderOptionsLimits.problem(Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.MAX_SCALE)));
        for (CinderweaverSize size : CinderweaverSize.values()) {
            assertNull(SpiderOptionsLimits.problem(Cinderweaver.cinderweaverBodyPlan(size.getScale())), size.getId());
        }
        assertNull(SpiderOptionsLimits.nonFinite(Gait.defaultWalk()));
        assertNull(SpiderOptionsLimits.nonFinite(Cinderweaver.cinderweaverBodyPlan(1.0)));
    }

    @Test
    @DisplayName("bodies and gaits outside the limits are refused")
    void outsideTheLimitsIsRefused() {
        BodyPlan huge = Cinderweaver.cinderweaverBodyPlan(1.0);
        huge.setScale(10.0);
        assertNotNull(SpiderOptionsLimits.problem(huge), "scale");

        Gait nan = Gait.defaultWalk();
        nan.setMaxSpeed(Double.NaN);
        assertNotNull(SpiderOptionsLimits.nonFinite(nan), "NaN speed");

        BodyPlan legless = Cinderweaver.cinderweaverBodyPlan(1.0);
        legless.setLegs(new ArrayList<>());
        assertNotNull(SpiderOptionsLimits.problem(legless), "no legs");

        BodyPlan manySegments = Cinderweaver.cinderweaverBodyPlan(1.0);
        LegPlan leg = manySegments.getLegs().get(0);
        List<SegmentPlan> segments = new ArrayList<>();
        for (int i = 0; i < 100; i++) segments.add(leg.getSegments().get(0).clone());
        leg.setSegments(segments);
        assertNotNull(SpiderOptionsLimits.problem(manySegments), "100 segments");

        BodyPlan farReach = Cinderweaver.cinderweaverBodyPlan(1.0);
        farReach.getLegs().get(0).getSegments().get(0).setLength(1e9);
        assertNotNull(SpiderOptionsLimits.problem(farReach), "huge segment");
    }
}
