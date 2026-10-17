package net.tfminecraft.spiders.utilities.custom_items;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Isolates a test from the staff tools other tests made, and keeps the tools it makes from leaking out. */
public final class StaffToolsReset {
    private StaffToolsReset() {
    }

    /**
     * Empties the {@link CustomItems} tool list and the {@link StaffToolMenu} tools. Returns what puts both back
     * exactly as they were.
     */
    public static Runnable isolate() {
        List<CustomItemComponent> components = new ArrayList<>(toolComponents());
        List<StaffTool> tools = new ArrayList<>(StaffToolMenu.getStaffTools());
        clear();
        return () -> {
            clear();
            toolComponents().addAll(components);
            StaffToolMenu.getStaffTools().addAll(tools);
        };
    }

    /** Forgets every tool, for a test that checks what happens without them. */
    public static void clear() {
        toolComponents().clear();
        StaffToolMenu.getStaffTools().clear();
    }

    @SuppressWarnings("unchecked")
    private static List<CustomItemComponent> toolComponents() {
        try {
            Field field = CustomItems.class.getDeclaredField("toolComponents");
            field.setAccessible(true);
            return (List<CustomItemComponent>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
