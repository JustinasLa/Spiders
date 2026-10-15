package net.tfminecraft.spiders.spider.configuration;

import org.bukkit.Sound;

public class SoundOptions {
    private SoundPlayer step = new SoundPlayer(
        Sound.BLOCK_NETHERITE_BLOCK_STEP,
        .2f,
        1.0f
    );

    private SoundPlayer stepBlockOverlay = step.scale(.75f);

    public SoundPlayer getStep() {
        return step;
    }

    public void setStep(SoundPlayer step) {
        this.step = step;
    }

    public SoundPlayer getStepBlockOverlay() {
        return stepBlockOverlay;
    }

    public void setStepBlockOverlay(SoundPlayer stepBlockOverlay) {
        this.stepBlockOverlay = stepBlockOverlay;
    }
}
