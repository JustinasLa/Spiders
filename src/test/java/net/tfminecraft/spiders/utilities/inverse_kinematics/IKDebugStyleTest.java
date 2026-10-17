package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IKDebugStyleTest {
    private static final Material HINGE = Material.LIGHT_BLUE_CONCRETE;
    private static final Material SWING = Material.CYAN_CONCRETE;
    private static final Material TWIST = Material.PINK_CONCRETE;
    private static final Material INDICATOR = Material.MAGENTA_CONCRETE;

    private static IKDebugStyle full(double hinge, double cone, double twistDistance, double twistRadius, double indicator, double axis,
                                     double targetAxis, int hingeSteps, int twistSteps, int ringSteps, int spokes, boolean axes,
                                     boolean constraints, Material hingeColor, Material swingColor, Material twistColor, Material indicatorColor) {
        return new IKDebugStyle(hinge, cone, twistDistance, twistRadius, indicator, axis, targetAxis, hingeSteps, twistSteps, ringSteps, spokes,
            axes, constraints, hingeColor, swingColor, twistColor, indicatorColor);
    }

    private static IKDebugStyle defaults() {
        return full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR);
    }

    @Test
    @DisplayName("every shorter constructor fills the remaining fields with the defaults")
    void telescopingDefaults() {
        IKDebugStyle expected = defaults();
        assertEquals(expected, new IKDebugStyle());
        assertEquals(expected, new IKDebugStyle(0.6));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING));
        assertEquals(expected, new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST));
    }

    @Test
    @DisplayName("getters return the configured values and the size helpers scale by segment length")
    void getters() {
        IKDebugStyle style = full(0.5, 2.0, 0.25, 0.1, 0.2, 0.3, 0.7, 4, 5, 6, 2, false, true,
            Material.STONE, Material.DIRT, Material.SAND, Material.GLASS);
        assertEquals(0.5, style.getHingeArcRadiusFraction(), 0.0);
        assertEquals(2.0, style.getSwingConeLengthFraction(), 0.0);
        assertEquals(0.25, style.getTwistArcDistanceFraction(), 0.0);
        assertEquals(0.1, style.getTwistArcRadius(), 0.0);
        assertEquals(0.2, style.getTwistIndicatorLength(), 0.0);
        assertEquals(0.3, style.getAxisLength(), 0.0);
        assertEquals(0.7, style.getTargetAxisLength(), 0.0);
        assertEquals(4, style.getHingeArcSteps());
        assertEquals(5, style.getTwistArcSteps());
        assertEquals(6, style.getConeRingSteps());
        assertEquals(2, style.getConeSpokes());
        assertEquals(false, style.getDrawNodeAxes());
        assertEquals(true, style.getDrawConstraints());
        assertEquals(Material.STONE, style.getHingeColor());
        assertEquals(Material.DIRT, style.getSwingColor());
        assertEquals(Material.SAND, style.getTwistArcColor());
        assertEquals(Material.GLASS, style.getTwistIndicatorColor());
        assertEquals(1.5, style.hingeArcRadius(3.0), 0.0);
        assertEquals(6.0, style.swingConeLength(3.0), 0.0);
    }

    @Test
    @DisplayName("styles differing in any one field are not equal")
    void equalityChecksEveryField() {
        List<IKDebugStyle> variants = new ArrayList<>();
        variants.add(full(0.7, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.1, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.9, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.4, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.5, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.7, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.1, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 19, 10, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 11, 12, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 13, 3, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 4, true, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, false, true, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, false, HINGE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, Material.STONE, SWING, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, Material.STONE, TWIST, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, Material.STONE, INDICATOR));
        variants.add(full(0.6, 1.0, 0.8, 0.3, 0.4, 0.6, 1.0, 18, 10, 12, 3, true, true, HINGE, SWING, TWIST, Material.STONE));

        IKDebugStyle base = defaults();
        for (IKDebugStyle variant : variants) assertNotEquals(base, variant, variant.toString());
        assertEquals(base, base);
        assertNotEquals(base, (Object) "IKDebugStyle");
        assertNotEquals(base, null);
    }

    @Test
    @DisplayName("equal styles hash alike and print every field")
    void hashAndText() {
        assertEquals(defaults().hashCode(), new IKDebugStyle().hashCode());
        assertNotEquals(defaults().hashCode(), new IKDebugStyle(0.7).hashCode());

        String text = new IKDebugStyle().toString();
        assertTrue(text.startsWith("IKDebugStyle(hingeArcRadiusFraction=0.6, swingConeLengthFraction=1.0"), text);
        assertTrue(text.contains("coneSpokes=3, drawNodeAxes=true, drawConstraints=true"), text);
        assertTrue(text.endsWith("twistIndicatorColor=MAGENTA_CONCRETE)"), text);
    }
}
