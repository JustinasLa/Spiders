package net.tfminecraft.spiders.utilities.colors;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Color;

public class Oklab {
    private final float l;
    private final float a;
    private final float b;
    private final int alpha;

    public Oklab(float l, float a, float b, int alpha) {
        this.l = l;
        this.a = a;
        this.b = b;
        this.alpha = alpha;
    }

    public float getL() {
        return l;
    }

    public float getA() {
        return a;
    }

    public float getB() {
        return b;
    }

    public int getAlpha() {
        return alpha;
    }

    public Oklab lerp(Oklab other, float t) {
        return new Oklab(
            Maths.lerp(l, other.l, t),
            Maths.lerp(a, other.a, t),
            Maths.lerp(b, other.b, t),
            Maths.lerpSafely(alpha, other.alpha, t)
        );
    }

    public Color toRGB() {
        double L = Math.pow(l * 0.99999999845051981432 +
                0.39633779217376785678 * a +
                0.21580375806075880339 * b, 3);
        double M = Math.pow(l * 1.0000000088817607767 -
                0.1055613423236563494 * a -
                0.063854174771705903402 * b, 3);
        double S = Math.pow(l * 1.0000000546724109177 -
                0.089484182094965759684 * a -
                1.2914855378640917399 * b, 3);

        double r = +4.076741661347994 * L -
                3.307711590408193 * M +
                0.230969928729428 * S;
        double g = -1.2684380040921763 * L +
                2.6097574006633715 * M -
                0.3413193963102197 * S;
        double b = -0.004196086541837188 * L -
                0.7034186144594493 * M +
                1.7076147009309444 * S;

        return Color.fromARGB(
            alpha,
            (int) (coerceIn(r, 0.0, 1.0) * 255),
            (int) (coerceIn(g, 0.0, 1.0) * 255),
            (int) (coerceIn(b, 0.0, 1.0) * 255)
        );
    }

    public static float distanceTo(Oklab oklab, Oklab other) {
        return (float) Math.sqrt(
            (float) Math.pow(oklab.l - other.l, 2)
                + (float) Math.pow(oklab.a - other.a, 2)
                + (float) Math.pow(oklab.b - other.b, 2)
        );
    }

    public static Oklab toOklab(Color color) {
        double r = color.getRed() / 255.0;
        double g = color.getGreen() / 255.0;
        double b = color.getBlue() / 255.0;

        double L = Math.cbrt(
            0.41222147079999993 * r + 0.5363325363 * g + 0.0514459929 * b
        );
        double M = Math.cbrt(
            0.2119034981999999 * r + 0.6806995450999999 * g + 0.1073969566 * b
        );
        double S = Math.cbrt(
            0.08830246189999998 * r + 0.2817188376 * g + 0.6299787005000002 * b
        );

        return new Oklab(
            (float) (0.2104542553 * L + 0.793617785 * M - 0.0040720468 * S),
            (float) (1.9779984951 * L - 2.428592205 * M + 0.4505937099 * S),
            (float) (0.0259040371 * L + 0.7827717662 * M - 0.808675766 * S),
            color.getAlpha()
        );
    }

    // Kotlin's coerceIn (keeps -0.0 and NaN as they are, unlike Math.clamp)
    private static double coerceIn(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
