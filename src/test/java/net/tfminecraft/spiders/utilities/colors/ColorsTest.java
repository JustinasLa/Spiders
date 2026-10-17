package net.tfminecraft.spiders.utilities.colors;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;
import org.bukkit.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColorsTest {
    private static void assertColor(int r, int g, int b, Color actual) {
        assertEquals(r, actual.getRed(), 1, "red of " + actual);
        assertEquals(g, actual.getGreen(), 1, "green of " + actual);
        assertEquals(b, actual.getBlue(), 1, "blue of " + actual);
    }

    @Test
    @DisplayName("RGB lerp blends every channel, alpha included")
    void lerpRgbBlendsEveryChannel() {
        Color blended = Colors.lerpRGB(Color.fromARGB(0, 0, 0, 0), Color.fromARGB(200, 100, 50, 255), 0.5f);
        assertEquals(Color.fromARGB(100, 50, 25, 127), blended);
        assertEquals(Color.BLACK, Colors.lerpRGB(Color.BLACK, Color.WHITE, 0f));
    }

    @Test
    @DisplayName("value is the mean channel as a fraction")
    void valueIsTheMeanChannel() {
        assertEquals(60f / 255f, Colors.value(Color.fromRGB(30, 60, 90)), 1e-6);
    }

    @Test
    @DisplayName("HSV conversion finds the hue from the strongest channel")
    void toHsvFindsTheHue() {
        assertEquals(new Triple<>(0f, 1f, 1f), Colors.toHSV(Color.RED));
        assertEquals(new Triple<>(120f, 1f, 1f), Colors.toHSV(Color.LIME));
        assertEquals(new Triple<>(240f, 1f, 1f), Colors.toHSV(Color.BLUE));
        assertEquals(new Triple<>(0f, 0f, 128f / 255f), Colors.toHSV(Color.fromRGB(128, 128, 128)), "grey has no hue");
        assertEquals(new Triple<>(0f, 0f, 0f), Colors.toHSV(Color.BLACK));
    }

    @Test
    @DisplayName("HSV to RGB covers each sixth of the hue circle")
    void hsvCoversEverySector() {
        assertColor(255, 0, 0, Colors.hsv(0f, 1f, 1f));
        assertColor(255, 255, 0, Colors.hsv(60f, 1f, 1f));
        assertColor(0, 255, 0, Colors.hsv(120f, 1f, 1f));
        assertColor(0, 255, 255, Colors.hsv(180f, 1f, 1f));
        assertColor(0, 0, 255, Colors.hsv(240f, 1f, 1f));
        assertColor(255, 0, 255, Colors.hsv(300f, 1f, 1f));
        assertColor(127, 127, 127, Colors.hsv(200f, 0f, 0.5f));
    }

    @Test
    @DisplayName("a gradient blends between the stops around t, and holds the last stop past it")
    void gradientInterpolation() {
        List<Pair<Float, Color>> gradient = List.of(
            new Pair<>(0f, Color.BLACK),
            new Pair<>(0.5f, Color.WHITE),
            new Pair<>(1f, Color.RED)
        );
        assertColor(127, 127, 127, Colors.interpolate(gradient, 0.25f, Colors::lerpRGB));
        assertColor(255, 127, 127, Colors.interpolate(gradient, 0.75f, Colors::lerpRGB));
        assertEquals(Color.RED, Colors.interpolate(gradient, 1f, Colors::lerpRGB));
        assertEquals(Color.RED, Colors.interpolate(gradient, 2f, Colors::lerpRGB));
        assertThrows(IndexOutOfBoundsException.class, () -> Colors.interpolate(gradient, -1f, Colors::lerpRGB), "before the first stop");
    }

    @Test
    @DisplayName("Oklab lerp goes from one colour to the other")
    void lerpOklabEnds() {
        assertColor(0, 0, 0, Colors.lerpOkLab(Color.BLACK, Color.WHITE, 0f));
        assertColor(255, 255, 255, Colors.lerpOkLab(Color.BLACK, Color.WHITE, 1f));
        Color middle = Colors.lerpOkLab(Color.BLACK, Color.WHITE, 0.5f);
        assertEquals(middle.getRed(), middle.getBlue(), "a grey");
    }

    @Test
    @DisplayName("RGB distance is Euclidean")
    void rgbDistanceIsEuclidean() {
        assertEquals(5f, Colors.distanceTo(Color.fromRGB(0, 0, 0), Color.fromRGB(3, 4, 0)), 1e-6);
    }

    @Test
    @DisplayName("Oklab round-trips RGB, keeps alpha and clamps out-of-gamut values")
    void oklabRoundTrips() {
        Color color = Color.fromARGB(77, 200, 100, 50);
        Oklab oklab = Oklab.toOklab(color);
        assertEquals(77, oklab.getAlpha());
        assertColor(200, 100, 50, oklab.toRGB());
        assertEquals(77, oklab.toRGB().getAlpha());

        Oklab white = Oklab.toOklab(Color.WHITE);
        assertEquals(1f, white.getL(), 1e-3);
        assertEquals(0f, white.getA(), 1e-3);
        assertEquals(0f, white.getB(), 1e-3);
        assertColor(255, 255, 255, new Oklab(2f, 0f, 0f, 255).toRGB());
        assertColor(0, 0, 0, new Oklab(-1f, 0f, 0f, 255).toRGB());
    }

    @Test
    @DisplayName("Oklab lerp and distance work per component")
    void oklabLerpAndDistance() {
        Oklab start = new Oklab(0f, 0f, 0f, 0);
        Oklab end = new Oklab(1f, 0.4f, -0.2f, 200);
        Oklab middle = start.lerp(end, 0.5f);
        assertEquals(0.5f, middle.getL(), 1e-6);
        assertEquals(0.2f, middle.getA(), 1e-6);
        assertEquals(-0.1f, middle.getB(), 1e-6);
        assertEquals(100, middle.getAlpha());
        assertEquals(0.5f, Oklab.distanceTo(new Oklab(0f, 0f, 0f, 255), new Oklab(0f, 0.3f, 0.4f, 255)), 1e-6);
    }
}
