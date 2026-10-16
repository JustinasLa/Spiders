package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.utilities.ecs.ECSComponent;

import java.util.Arrays;

public class SpiderOptions implements ECSComponent {
    private Gait walkGait = Gait.defaultWalk();
    private Gait gallopGait = Gait.defaultGallop();

    private boolean gallop = false;

    private CloakOptions cloak = new CloakOptions();

    private PounceOptions pounce = new PounceOptions();

    private BodyPlan bodyPlan = new BodyPlan();
    private SpiderDebugOptions debug = new SpiderDebugOptions();

    private SoundOptions sound = new SoundOptions();

    public Gait getWalkGait() {
        return walkGait;
    }

    public void setWalkGait(Gait walkGait) {
        this.walkGait = walkGait;
    }

    public Gait getGallopGait() {
        return gallopGait;
    }

    public void setGallopGait(Gait gallopGait) {
        this.gallopGait = gallopGait;
    }

    public boolean getGallop() {
        return gallop;
    }

    public void setGallop(boolean gallop) {
        this.gallop = gallop;
    }

    /** Currently active gait. */
    public Gait getGait() {
        return gallop ? gallopGait : walkGait;
    }

    /** Walls and ceilings: both gaits grip and follow the surface beneath the legs. */
    public boolean getClimbing() {
        return getGait().getGrip();
    }

    /** Walls and ceilings: both gaits grip and follow the surface beneath the legs. */
    public void setClimbing(boolean value) {
        for (Gait gait : Arrays.asList(walkGait, gallopGait)) {
            gait.setGrip(value);
            gait.setScanOrientation(value ? OrientationMode.GroundOrientation : OrientationMode.YAxis);
        }
    }

    public CloakOptions getCloak() {
        return cloak;
    }

    public void setCloak(CloakOptions cloak) {
        this.cloak = cloak;
    }

    public PounceOptions getPounce() {
        return pounce;
    }

    public void setPounce(PounceOptions pounce) {
        this.pounce = pounce;
    }

    public BodyPlan getBodyPlan() {
        return bodyPlan;
    }

    public void setBodyPlan(BodyPlan bodyPlan) {
        this.bodyPlan = bodyPlan;
    }

    public SpiderDebugOptions getDebug() {
        return debug;
    }

    public void setDebug(SpiderDebugOptions debug) {
        this.debug = debug;
    }

    public SoundOptions getSound() {
        return sound;
    }

    public void setSound(SoundOptions sound) {
        this.sound = sound;
    }

    public void copyFrom(SpiderOptions other) {
        walkGait = other.getWalkGait();
        gallopGait = other.getGallopGait();
        gallop = other.getGallop();
        cloak = other.getCloak();
        pounce = other.getPounce();
        bodyPlan = other.getBodyPlan();
        debug = other.getDebug();
        sound = other.getSound();
    }
}
