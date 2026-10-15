package net.tfminecraft.spiders.utilities.colors;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Color;

import java.util.List;

/** {@code ColorGradient} (a Kotlin typealias) is {@code List<Pair<Float, Color>>}. */
public final class Colors {
    private Colors() {
    }

    @FunctionalInterface
    public interface ColorLerpFunction {
        Color apply(Color start, Color end, float fraction);
    }


    public static Color blendAlpha(Color bottom, Color top) {
        double alpha = top.getAlpha() / 255.0;
        double topAlpha = bottom.getAlpha() / 255.0;
        double blendedAlpha = alpha + topAlpha * (1 - alpha);
        double r = (top.getRed() * alpha + bottom.getRed() * topAlpha * (1 - alpha)) / blendedAlpha;
        double g = (top.getGreen() * alpha + bottom.getGreen() * topAlpha * (1 - alpha)) / blendedAlpha;
        double b = (top.getBlue() * alpha + bottom.getBlue() * topAlpha * (1 - alpha)) / blendedAlpha;
        return Color.fromARGB((int) (blendedAlpha * 255), (int) r, (int) g, (int) b);
    }

    public static Color lerpRGB(Color color, Color other, float t) {
        return Color.fromARGB(
            Maths.lerpSafely(color.getAlpha(), other.getAlpha(), t),
            Maths.lerpSafely(color.getRed(), other.getRed(), t),
            Maths.lerpSafely(color.getGreen(), other.getGreen(), t),
            Maths.lerpSafely(color.getBlue(), other.getBlue(), t)
        );
    }

    public static float value(Color color) {
        return ((color.getRed() + color.getGreen() + color.getBlue()) / 3f) / 255f;
    }

    public static Color scaleRGB(Color color, float value) {
        int r = Math.clamp((int) (color.getRed() * value), 0, 255);
        int g = Math.clamp((int) (color.getGreen() * value), 0, 255);
        int b = Math.clamp((int) (color.getBlue() * value), 0, 255);
        return Color.fromARGB(color.getAlpha(), r, g, b);
    }

    public static Color scaleAlpha(Color color, float value) {
        int a = Math.clamp((int) (color.getAlpha() * value), 0, 255);
        return Color.fromARGB(a, color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Triple<Float, Float, Float> toHSV(Color color) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));

        float delta = max - min;

        float h;
        if (delta == 0f) h = 0f;
        else if (max == r) h = 60 * (((g - b) / delta) % 6);
        else if (max == g) h = 60 * ((b - r) / delta + 2);
        else if (max == b) h = 60 * ((r - g) / delta + 4);
        else throw new IllegalStateException("Unreachable");

        float s = max == 0f ? 0f : delta / max;
        float v = max;

        return new Triple<>(h, s, v);
    }

    public static Color interpolate(List<Pair<Float, Color>> gradient, float t, ColorLerpFunction lerpFunction) {
        int index = -1;
        for (int i = gradient.size() - 1; i >= 0; i--) {
            if (gradient.get(i).first() <= t) {
                index = i;
                break;
            }
        }
        if (index == gradient.size() - 1) return gradient.getLast().second();
        Pair<Float, Color> start = gradient.get(index);
        Pair<Float, Color> end = gradient.get(index + 1);
//    return start.second.lerp(end.second, (t - start.first) / (end.first - start.first))
        return lerpFunction.apply(start.second(), end.second(), (t - start.first()) / (end.first() - start.first()));
    }

    public static Color interpolateRGB(List<Pair<Float, Color>> gradient, float t) {
        return interpolate(gradient, t, (start, end, fraction) -> lerpRGB(start, end, fraction));
    }

    public static Color interpolateOkLab(List<Pair<Float, Color>> gradient, float t) {
        return interpolate(gradient, t, (start, end, fraction) -> lerpOkLab(start, end, fraction));
    }

    public static Color lerpOkLab(Color color, Color other, float t) {
        Oklab start = Oklab.toOklab(color);
        Oklab end = Oklab.toOklab(other);
        Color result = start.lerp(end, t).toRGB();
        return result;
    }

    public static float distanceTo(Color color, Color other) {
        return (float) Math.sqrt(
            (float) Math.pow((float) (color.getRed() - other.getRed()), 2)
                + (float) Math.pow((float) (color.getGreen() - other.getGreen()), 2)
                + (float) Math.pow((float) (color.getBlue() - other.getBlue()), 2)
        );
    }

//fun Color.hsvLerp(other: Color, t: Double): Color {
//    val (h1, s1, v1) = this.toHSV()
//    val (h2, s2, v2) = other.toHSV()
//
//    val h = h1.lerp(h2, t)
//    val s = s1.lerp(s2, t)
//    val v = v1.lerp(v2, t)
//    val a = this.alpha.toDouble().lerp(other.alpha.toDouble(), t).toInt()
//
//    return hsv(h, s, v).setAlpha(a)
//}

    /**
     * Converts an HSV color to RGB.
     * @param h The hue in degrees. (0 - 360)
     * @param s The saturation as a percentage. (0 - 1)
     * @param v The value as a percentage. (0 - 1)
     * @return The RGB color.
     */
    public static Color hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60) { r = c; g = x; b = 0f; }
        else if (h < 120) { r = x; g = c; b = 0f; }
        else if (h < 180) { r = 0f; g = c; b = x; }
        else if (h < 240) { r = 0f; g = x; b = c; }
        else if (h < 300) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return Color.fromRGB((int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
    }
}
