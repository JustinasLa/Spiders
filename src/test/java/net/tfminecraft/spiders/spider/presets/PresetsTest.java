package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SoundPlayer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKHingeJoint3D;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class PresetsTest {
    private AppTestSupport app;

    @BeforeEach
    void setup() {
        app = new AppTestSupport();
    }

    @AfterEach
    void restore() {
        app.close();
    }

    @Test
    @DisplayName("knees bend one way only: forwards by default, backwards when inverted")
    void kneesBendOneWay() {
        IKHingeJoint3D knee = (IKHingeJoint3D) Presets.kneeJoint(1.0);
        assertEquals(0.0, knee.getMinAngle());
        assertEquals(1.0, knee.getMaxAngle());
        IKHingeJoint3D inverted = (IKHingeJoint3D) Presets.kneeJoint(1.0, true);
        assertEquals(-1.0, inverted.getMinAngle());
        assertEquals(0.0, inverted.getMaxAngle());
        assertEquals(.65 * Math.PI, ((IKHingeJoint3D) Presets.kneeJoint()).getMaxAngle(), 1e-12);
    }

    @Test
    @DisplayName("the default spider is a hunter-sized Cinderweaver with spider footsteps")
    void theDefaultSpiderIsAHunterCinderweaver() {
        SpiderOptions options = Presets.defaultPreset();
        double scale = CinderweaverSize.HUNTER.getScale();
        assertEquals(scale, options.getBodyPlan().getScale(), 1e-12);
        assertEquals(.12 * scale, options.getWalkGait().getMaxSpeed(), 1e-12);
        assertEquals(.24 * scale, options.getGallopGait().getMaxSpeed(), 1e-12);
        SoundPlayer step = options.getSound().getStep();
        assertSame(Sound.ENTITY_SPIDER_STEP, step.getSound());
        assertEquals(.14f, step.getVolume());
        assertSame(Sound.ENTITY_SPIDER_STEP, options.getSound().getStepBlockOverlay().getSound());
        assertEquals(.06f, options.getSound().getStepBlockOverlay().getVolume());
    }

    @Test
    @DisplayName("each named size scales the body and its speeds")
    void eachNamedSizeScalesTheSpider() {
        for (CinderweaverSize size : CinderweaverSize.values()) {
            SpiderOptions options = Cinderweaver.cinderweaver(size);
            assertEquals(size.getScale(), options.getBodyPlan().getScale(), 1e-12, size.getId());
            assertEquals(.12 * size.getScale(), options.getWalkGait().getMaxSpeed(), 1e-12, size.getId());
            assertEquals(size, CinderweaverSize.fromId(size.getId().toUpperCase()));
        }
        assertEquals(null, CinderweaverSize.fromId("giant"));
    }

    private static BodyPlan clockwork() {
        BodyPlan plan = Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.HUNTER);
        plan.getBodyModel().getPieces().getFirst().setBlock(AppTestSupport.blockData(Material.BLAST_FURNACE));
        return plan;
    }

    private static boolean hasFirebox(BodyPlan plan) {
        return plan.getBodyModel().getPieces().stream().anyMatch(piece -> piece.getBlock().getMaterial() == Material.BLAST_FURNACE);
    }

    @Test
    @DisplayName("a clockwork body with an edited leg layout is left as it is")
    void anEditedClockworkRigIsLeftAlone() {
        BodyPlan sixLegs = clockwork();
        sixLegs.setLegs(new ArrayList<>(sixLegs.getLegs().subList(0, 6)));
        assertFalse(Cinderweaver.upgradeClockworkAppearance(sixLegs));
        assertEquals(true, hasFirebox(sixLegs));

        BodyPlan longLegs = clockwork();
        List<LegPlan> legs = longLegs.getLegs();
        legs.getLast().setSegments(Presets.octopodBodyPlan(4, .7).getLegs().getFirst().getSegments());
        assertFalse(Cinderweaver.upgradeClockworkAppearance(longLegs));
        assertEquals(true, hasFirebox(longLegs));
    }
}
