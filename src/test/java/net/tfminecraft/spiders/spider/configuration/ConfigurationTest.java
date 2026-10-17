package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.spider.components.body.GaitType;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.presets.Presets;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigurationTest {
    private AppTestSupport app;

    @BeforeEach
    void setup() {
        app = new AppTestSupport();
    }

    @AfterEach
    void restore() {
        app.close();
    }

    /** A value of {@code type} different from {@code current}, or null if the type isn't a simple setting. */
    private static Object differentValue(Class<?> type, Object current, int seed) {
        if (type == double.class) return (Double) current + 1.25 + seed;
        if (type == float.class) return (Float) current + 1.25f + seed;
        if (type == int.class) return (Integer) current + 3 + seed;
        if (type == boolean.class) return !(Boolean) current;
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) if (constant != current) return constant;
        }
        if (type == Posture.class) return new Posture(seed, seed);
        return null;
    }

    private static Method getter(Class<?> type, String property) {
        for (String prefix : List.of("get", "is")) {
            try {
                return type.getMethod(prefix + property);
            } catch (NoSuchMethodException e) {
                // try the next prefix
            }
        }
        return null;
    }

    /** Every simple {@code setX} is read back by its {@code getX}/{@code isX}, and changes nothing else. */
    private static int assertSettingsStick(Object options) throws Exception {
        int checked = 0;
        int seed = 0;
        for (Method setter : options.getClass().getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
            String property = setter.getName().substring(3);
            Method getter = getter(options.getClass(), property);
            if (getter == null) continue;
            Object value = differentValue(setter.getParameterTypes()[0], getter.invoke(options), seed++);
            if (value == null) continue;
            setter.invoke(options, value);
            assertEquals(value, getter.invoke(options), property);
            checked++;
        }
        return checked;
    }

    @Test
    @DisplayName("every gait, shroud, pounce and debug setting reads back what was set")
    void everySettingReadsBack() throws Exception {
        assertEquals(31, assertSettingsStick(Gait.defaultWalk()));
        assertEquals(4, assertSettingsStick(new CloakOptions()));
        assertEquals(4, assertSettingsStick(new PounceOptions()));
        assertEquals(10, assertSettingsStick(new SpiderDebugOptions()));
    }

    @Test
    @DisplayName("defaults: a shroud matches slowly without dimmed blocks, a pounce leaps 5 blocks, debug shows everything")
    void defaults() throws Exception {
        CloakOptions cloak = new CloakOptions();
        assertEquals(1.0 / 255, cloak.getMoveSpeed());
        assertFalse(cloak.getAllowCustomBrightness());
        PounceOptions pounce = new PounceOptions();
        assertEquals(5.0, pounce.getLeapRange());
        assertEquals(2.0, pounce.getLeapBoost());
        assertEquals(.5, pounce.getLeapLift());
        assertEquals(400, pounce.getGiveUpTicks());
        SpiderDebugOptions debug = new SpiderDebugOptions();
        for (Method getter : SpiderDebugOptions.class.getDeclaredMethods()) {
            if (getter.getName().startsWith("get")) assertEquals(true, getter.invoke(debug), getter.getName());
        }
    }

    @Test
    @DisplayName("galloping is faster, taller and steadier between leg pairs than walking")
    void gallopingDiffersFromWalking() {
        Gait walk = Gait.defaultWalk();
        Gait gallop = Gait.defaultGallop();
        assertEquals(GaitType.WALK, walk.getType());
        assertEquals(GaitType.GALLOP, gallop.getType());
        assertEquals(.15, walk.getMaxSpeed());
        assertEquals(.15 * 2.5, walk.getLegMoveSpeed(), 1e-12);
        assertEquals(.4, gallop.getMaxSpeed());
        assertEquals(1.6, gallop.getMoving().getBodyHeight());
        assertEquals(.5, gallop.getLegMoveSpeed());
        assertEquals(2, gallop.getSamePairCooldown());
        assertEquals(4, gallop.getCrossPairCooldown());
        assertEquals(.6, gallop.getUncomfortableSpeedMultiplier());
    }

    @Test
    @DisplayName("scaling a gait scales its distances and speeds, and eases the trident's spin")
    void scalingAGait() {
        Gait gait = Gait.defaultWalk();
        Gait base = Gait.defaultWalk();
        gait.scale(2.0);
        assertEquals(base.getMaxSpeed() * 2, gait.getMaxSpeed(), 1e-12);
        assertEquals(base.getLegLiftHeight() * 2, gait.getLegLiftHeight(), 1e-12);
        assertEquals(base.getComfortZoneRadius() * 2, gait.getComfortZoneRadius(), 1e-12);
        assertEquals(base.getStationary().getBodyHeight() * 2, gait.getStationary().getBodyHeight(), 1e-12);
        assertEquals(base.getTridentRotationalKnockBack() / 2, gait.getTridentRotationalKnockBack(), 1e-12);
        assertEquals(base.getGravityAcceleration(), gait.getGravityAcceleration());
    }

    @Test
    @DisplayName("a new top speed keeps the stride rhythm, widening the comfort zone when strides outgrow it")
    void aNewTopSpeedKeepsTheStrideRhythm() {
        Gait gait = Gait.defaultWalk();
        double legSpeed = gait.getLegMoveSpeed();
        double acceleration = gait.getMoveAcceleration();
        double stride = gait.getMoving().getTriggerZoneRadius();
        double comfort = gait.getComfortZoneRadius();

        gait.setSpeed(gait.getMaxSpeed() / 2);
        assertEquals(.075, gait.getMaxSpeed(), 1e-12);
        assertEquals(legSpeed / 2, gait.getLegMoveSpeed(), 1e-12);
        assertEquals(acceleration / 2, gait.getMoveAcceleration(), 1e-12);
        assertEquals(stride / 2, gait.getMoving().getTriggerZoneRadius(), 1e-12);
        assertEquals(comfort, gait.getComfortZoneRadius(), 1e-12, "shorter strides keep the comfort zone");

        gait.setSpeed(gait.getMaxSpeed() * 4);
        assertEquals(stride * 2, gait.getMoving().getTriggerZoneRadius(), 1e-12);
        assertEquals(stride * 2 * 1.5, gait.getComfortZoneRadius(), 1e-12, "longer strides widen it");
    }

    @Test
    @DisplayName("the active gait follows the gallop switch, and climbing applies to both gaits")
    void theActiveGaitAndClimbing() {
        SpiderOptions options = new SpiderOptions();
        assertFalse(options.getGallop());
        assertSame(options.getWalkGait(), options.getGait());
        options.setGallop(true);
        assertSame(options.getGallopGait(), options.getGait());

        assertFalse(options.getClimbing());
        options.setClimbing(true);
        assertTrue(options.getClimbing());
        for (Gait gait : List.of(options.getWalkGait(), options.getGallopGait())) {
            assertTrue(gait.getGrip());
            assertEquals(OrientationMode.GroundOrientation, gait.getScanOrientation());
        }
        options.setClimbing(false);
        for (Gait gait : List.of(options.getWalkGait(), options.getGallopGait())) {
            assertFalse(gait.getGrip());
            assertEquals(OrientationMode.YAxis, gait.getScanOrientation());
        }
    }

    @Test
    @DisplayName("options copy every part from other options")
    void optionsCopyEveryPart() {
        SpiderOptions source = new SpiderOptions();
        source.setWalkGait(Gait.defaultWalk());
        source.setGallopGait(Gait.defaultGallop());
        source.setGallop(true);
        source.setCloak(new CloakOptions());
        source.setPounce(new PounceOptions());
        source.setBodyPlan(new BodyPlan());
        source.setDebug(new SpiderDebugOptions());
        source.setSound(new SoundOptions());

        SpiderOptions copy = new SpiderOptions();
        copy.copyFrom(source);

        assertSame(source.getWalkGait(), copy.getWalkGait());
        assertSame(source.getGallopGait(), copy.getGallopGait());
        assertTrue(copy.getGallop());
        assertSame(source.getCloak(), copy.getCloak());
        assertSame(source.getPounce(), copy.getPounce());
        assertSame(source.getBodyPlan(), copy.getBodyPlan());
        assertSame(source.getDebug(), copy.getDebug());
        assertSame(source.getSound(), copy.getSound());
    }

    @Test
    @DisplayName("a sound plays at the spot with a little random volume and pitch on top")
    void aSoundPlaysWithVariation() {
        SoundPlayer player = new SoundPlayer(Sound.ENTITY_SPIDER_STEP, .5f, 1.0f, .2f, .3f);
        World world = mock(World.class);
        ArgumentCaptor<Float> volume = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> pitch = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Location> at = ArgumentCaptor.forClass(Location.class);

        player.play(world, new Vector(1, 2, 3));

        verify(world).playSound(at.capture(), eq(Sound.ENTITY_SPIDER_STEP), volume.capture(), pitch.capture());
        assertEquals(new Vector(1, 2, 3), at.getValue().toVector());
        assertSame(world, at.getValue().getWorld());
        assertTrue(volume.getValue() >= .5f && volume.getValue() <= .7f, String.valueOf(volume.getValue()));
        assertTrue(pitch.getValue() >= 1.0f && pitch.getValue() <= 1.3f, String.valueOf(pitch.getValue()));
    }

    @Test
    @DisplayName("sounds vary by 0.1 unless told otherwise, and scale or swap without changing the original")
    void soundsScaleAndSwap() {
        SoundPlayer plain = new SoundPlayer(Sound.ENTITY_SPIDER_STEP, .5f, 1.0f);
        assertEquals(.1f, plain.getVolumeVary());
        assertEquals(.1f, plain.getPitchVary());
        SoundPlayer louder = new SoundPlayer(Sound.ENTITY_SPIDER_STEP, .5f, 1.0f, .4f);
        assertEquals(.4f, louder.getVolumeVary());
        assertEquals(.1f, louder.getPitchVary());

        SoundPlayer scaled = plain.scale(2f, .5f);
        assertSame(Sound.ENTITY_SPIDER_STEP, scaled.getSound());
        assertEquals(1.0f, scaled.getVolume());
        assertEquals(.5f, scaled.getPitch());
        assertEquals(.2f, scaled.getVolumeVary(), 1e-6);
        assertEquals(.05f, scaled.getPitchVary(), 1e-6);
        SoundPlayer quieter = plain.scale(.5f);
        assertEquals(.25f, quieter.getVolume());
        assertEquals(1.0f, quieter.getPitch());
        SoundPlayer same = plain.scale();
        assertNotSame(plain, same);
        assertEquals(plain.getVolume(), same.getVolume());
        assertEquals(plain.getPitch(), same.getPitch());

        SoundPlayer swapped = plain.withSound(Sound.BLOCK_WOOL_HIT);
        assertSame(Sound.BLOCK_WOOL_HIT, swapped.getSound());
        assertEquals(.5f, swapped.getVolume());
        assertSame(Sound.ENTITY_SPIDER_STEP, plain.getSound());
    }

    @Test
    @DisplayName("default steps are metal footfalls with a quieter overlay of the block stepped on")
    void defaultSteps() {
        SoundOptions sounds = new SoundOptions();
        assertSame(Sound.BLOCK_NETHERITE_BLOCK_STEP, sounds.getStep().getSound());
        assertEquals(.2f, sounds.getStep().getVolume());
        assertEquals(.15f, sounds.getStepBlockOverlay().getVolume(), 1e-6);
        SoundPlayer step = new SoundPlayer(Sound.ENTITY_SPIDER_STEP, 1f, 1f);
        sounds.setStep(step);
        sounds.setStepBlockOverlay(step);
        assertSame(step, sounds.getStep());
        assertSame(step, sounds.getStepBlockOverlay());
    }

    @Test
    @DisplayName("a palette entry keeps its block and brightness")
    void aPaletteEntryKeepsItsBlockAndBrightness() {
        BlockData block = AppTestSupport.blockData(Material.RED_TERRACOTTA);
        PaletteEntry entry = new PaletteEntry(block, new Display.Brightness(5, 0));
        assertSame(block, entry.getBlock());
        assertEquals(new Display.Brightness(5, 0), entry.getBrightness());
    }

    @Test
    @DisplayName("a leg segment starts without a model and clones into an independent copy")
    void aLegSegmentClones() {
        IKJoint3D knee = Presets.kneeJoint();
        SegmentPlan segment = new SegmentPlan(1.5, knee);
        assertTrue(segment.getModel().getPieces().isEmpty());

        IKJoint3D hip = Presets.hipJoint();
        segment.setJoint(hip);
        assertSame(hip, segment.getJoint());
        DisplayModel model = new DisplayModel(new ArrayList<>());
        segment.setModel(model);

        SegmentPlan copy = segment.clone();
        assertEquals(1.5, copy.getLength());
        assertSame(hip, copy.getJoint());
        assertNotSame(model, copy.getModel());
    }

    @Test
    @DisplayName("a leg rests aimed from its root to its rest position, or just at the hip angle when they meet")
    void aLegRestsAimedAtItsRestPosition() {
        Quaternionf hip = new Quaternionf().rotateX(.3f);
        LegPlan pointing = new LegPlan(new Vector(), new Vector(1, 0, 0), new ArrayList<>(), new Quaternionf());
        Vector aimed = Maths.rotate(new Vector(0, 0, 1), pointing.getRestOrientation());
        assertEquals(1.0, aimed.getX(), 1e-6);
        assertEquals(0.0, aimed.getZ(), 1e-6);

        LegPlan folded = new LegPlan(new Vector(1, 0, 0), new Vector(1, 0, 0), new ArrayList<>(), hip);
        assertTrue(folded.getRestOrientation().equals(hip, 1e-6f));

        folded.setAttachmentPosition(new Vector(2, 0, 0));
        folded.setRestPosition(new Vector(3, 0, 0));
        List<SegmentPlan> segments = List.of(new SegmentPlan(1.0, Presets.kneeJoint()));
        folded.setSegments(segments);
        assertEquals(new Vector(2, 0, 0), folded.getAttachmentPosition());
        assertEquals(new Vector(3, 0, 0), folded.getRestPosition());
        assertSame(segments, folded.getSegments());
    }

    @Test
    @DisplayName("scans orient by the spider's yaw, its full orientation, or the ground beneath it")
    void scanOrientations() {
        SpiderBody body = SpiderBody.create(mock(World.class), new Vector(), new Quaternionf().rotationYXZ(1f, .5f, 0f));
        body.setPreferredOrientation(new Quaternionf().rotationX(.2f));

        assertTrue(OrientationMode.SpiderOrientation.get(body).equals(body.getOrientation(), 1e-6f));
        assertTrue(OrientationMode.GroundOrientation.get(body).equals(new Quaternionf().rotationX(.2f), 1e-6f));
        Vector forward = Maths.rotate(new Vector(0, 0, 1), OrientationMode.YAxis.get(body));
        assertEquals(0.0, forward.getY(), 1e-6);
        assertEquals(Math.sin(1.0), forward.getX(), 1e-6);
    }

    @Test
    @DisplayName("options staff can't use are refused with the reason")
    void unusableOptionsAreRefused() {
        SpiderOptions options = new SpiderOptions();
        options.setBodyPlan(Presets.octopodBodyPlan(3, .7));
        assertNull(SpiderOptionsLimits.problem(options));

        options.getWalkGait().setMaxSpeed(Double.NaN);
        assertEquals("every number must be finite", SpiderOptionsLimits.problem(options));
        assertEquals("every number must be finite", SpiderOptionsLimits.nonFinite(options.getWalkGait()));

        for (double scale : new double[]{Double.NaN, .1, 3.0}) {
            BodyPlan plan = Presets.octopodBodyPlan(3, .7);
            plan.setScale(scale);
            assertTrue(SpiderOptionsLimits.problem(plan).startsWith("scale must be between"), String.valueOf(scale));
        }

        BodyPlan legless = Presets.octopodBodyPlan(3, .7);
        legless.setLegs(new ArrayList<>());
        assertEquals("a spider needs 1 to 12 legs", SpiderOptionsLimits.problem(legless));
        BodyPlan many = Presets.octopodBodyPlan(3, .7);
        List<LegPlan> legs = new ArrayList<>(many.getLegs());
        legs.addAll(many.getLegs());
        many.setLegs(legs);
        assertEquals("a spider needs 1 to 12 legs", SpiderOptionsLimits.problem(many));

        assertEquals("each leg needs 1 to 8 segments", SpiderOptionsLimits.problem(Presets.octopodBodyPlan(0, .7)));
        assertEquals("each leg needs 1 to 8 segments", SpiderOptionsLimits.problem(Presets.octopodBodyPlan(9, .7)));

        for (double length : new double[]{0.0, SpiderOptionsLimits.MAX_SEGMENT_LENGTH + .1}) {
            BodyPlan plan = Presets.octopodBodyPlan(3, .7);
            plan.getLegs().getLast().getSegments().getLast().setLength(length);
            assertTrue(SpiderOptionsLimits.problem(plan).startsWith("leg segments must be longer than 0"), String.valueOf(length));
        }
    }

    @Test
    @DisplayName("scaling a body plan scales its model, leg positions and segment lengths")
    void scalingABodyPlan() {
        BodyPlan plan = Presets.octopodBodyPlan(3, .7);
        LegPlan leg = plan.getLegs().getFirst();
        Vector rest = leg.getRestPosition().clone();
        double length = leg.getSegments().getFirst().getLength();

        plan.scale(2.0);

        assertEquals(2.0, plan.getScale());
        assertEquals(rest.multiply(2.0), leg.getRestPosition());
        assertEquals(length * 2, leg.getSegments().getFirst().getLength(), 1e-12);
        List<PaletteEntry> palette = List.of();
        plan.setEyePalette(palette);
        plan.setBlinkingPalette(palette);
        assertSame(palette, plan.getEyePalette());
        assertSame(palette, plan.getBlinkingPalette());
    }
}
