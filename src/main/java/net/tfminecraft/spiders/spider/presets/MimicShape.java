package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.NamespacedKey;
import org.joml.Vector3f;

/**
 * A chest on four long legs. Asleep it is a real chest block; awake it is the vanilla chest model
 * itself, drawn from the `tfmc_spiders` resource pack (the vanilla `minecraft:chest` special model at
 * a chosen lid openness), so the two look the same.
 */
public final class MimicShape {
    private MimicShape() {}

    public static final float PX = 1f / 16;
    /** Half the chest's width, depth and height. */
    public static final float HALF = 7 * PX;
    /** Body centre above the floor while it is still a chest. */
    public static final double RESTING_HEIGHT = (double) HALF;
    public static final double STANDING_HEIGHT = 1.15;
    public static final double GALLOP_HEIGHT = 1.35;

    /** Underside of the vanilla lid (9 px up); it overlaps the base's top by 1 px. */
    public static final float SEAM = 2 * PX;
    /** Top of the vanilla chest's base (10 px up): the floor of the mouth. */
    public static final float MOUTH = 3 * PX;
    /** The lid swings up about its back bottom edge, as the vanilla lid does. */
    private static final Vector3f HINGE = new Vector3f(0f, SEAM, -HALF);
    private static final Vector3f TONGUE_ROOT = new Vector3f(0f, MOUTH, -3 * PX);

    /** Resource-pack item models: the vanilla chest by openness, and a cube of its plank texture. */
    private static final NamespacedKey CHEST_MODEL = new NamespacedKey("tfmc_spiders", "mimic_chest");
    private static final NamespacedKey WOOD_MODEL = new NamespacedKey("tfmc_spiders", "mimic_wood");
    /** The pack's chest model comes in this many openness steps (0 to 90°). */
    public static final int OPENNESS_STEPS = 40;

    public static final String LID = "lid";
    public static final String CHEST = "chest";
    public static final String TONGUE = "tongue";

    private static final float MAX_LID_ANGLE = Maths.toRadians(80f);

    /** The lid swings up about its back bottom edge, as the vanilla lid does. */
    public static Vector3f getHINGE() {
        return HINGE;
    }

    public static Vector3f getTONGUE_ROOT() {
        return TONGUE_ROOT;
    }

    /** Resource-pack item model of the vanilla chest by openness. */
    public static NamespacedKey getCHEST_MODEL() {
        return CHEST_MODEL;
    }

    /** Resource-pack item model of a cube of the chest's plank texture. */
    public static NamespacedKey getWOOD_MODEL() {
        return WOOD_MODEL;
    }

    public static float getMAX_LID_ANGLE() {
        return MAX_LID_ANGLE;
    }
}
