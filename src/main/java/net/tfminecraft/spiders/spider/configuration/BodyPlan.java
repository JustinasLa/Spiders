package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.spider.presets.AnimatedPalettes;
import net.tfminecraft.spiders.utilities.DisplayModel;

import java.util.ArrayList;
import java.util.List;

public class BodyPlan {
    private double scale = 1.0;
    private List<LegPlan> legs = new ArrayList<>();

    private DisplayModel bodyModel = DisplayModel.empty();

    private List<PaletteEntry> eyePalette = AnimatedPalettes.CYAN_EYES.getPalette();
    private List<PaletteEntry> blinkingPalette = AnimatedPalettes.CYAN_BLINKING_LIGHTS.getPalette();

    public BodyPlan() {
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public List<LegPlan> getLegs() {
        return legs;
    }

    public void setLegs(List<LegPlan> legs) {
        this.legs = legs;
    }

    public DisplayModel getBodyModel() {
        return bodyModel;
    }

    public void setBodyModel(DisplayModel bodyModel) {
        this.bodyModel = bodyModel;
    }

    public List<PaletteEntry> getEyePalette() {
        return eyePalette;
    }

    public void setEyePalette(List<PaletteEntry> eyePalette) {
        this.eyePalette = eyePalette;
    }

    public List<PaletteEntry> getBlinkingPalette() {
        return blinkingPalette;
    }

    public void setBlinkingPalette(List<PaletteEntry> blinkingPalette) {
        this.blinkingPalette = blinkingPalette;
    }

    public void scale(double scale) {
        this.scale *= scale;
        bodyModel.scale((float) scale);
        for (LegPlan it : legs) {
            it.getAttachmentPosition().multiply(scale);
            it.getRestPosition().multiply(scale);
            for (SegmentPlan segment : it.getSegments()) {
                segment.setLength(segment.getLength() * scale);
                segment.getModel().scale((float) scale);
            }
        }
    }
}
