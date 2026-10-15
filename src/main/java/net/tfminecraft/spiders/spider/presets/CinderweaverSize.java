package net.tfminecraft.spiders.spider.presets;

import java.util.Locale;

public enum CinderweaverSize {
    HATCHLING(0.35), HUNTER(0.8), BROODMOTHER(1.5);

    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 2.5;

    private final double scale;

    CinderweaverSize(double scale) {
        this.scale = scale;
    }

    public double getScale() {
        return scale;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CinderweaverSize fromId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (CinderweaverSize size : values()) {
            if (size.getId().equals(lower)) return size;
        }
        return null;
    }

    /** A named size or any scale from {@link #MIN_SCALE} to {@link #MAX_SCALE}. */
    public static Double parseScale(String id) {
        CinderweaverSize size = fromId(id);
        if (size != null) return size.getScale();
        double value;
        try {
            value = Double.parseDouble(id);
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isFinite(value) && value >= MIN_SCALE && value <= MAX_SCALE) return value;
        return null;
    }
}
