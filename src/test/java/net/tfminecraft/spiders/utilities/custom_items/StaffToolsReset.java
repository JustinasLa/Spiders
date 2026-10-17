package net.tfminecraft.spiders.utilities.custom_items;

/** Forgets every staff tool a test made, so the next test starts without them. */
public final class StaffToolsReset {
    private StaffToolsReset() {
    }

    public static void reset() {
        CustomItems.clearToolComponents();
        StaffToolMenu.getStaffTools().clear();
    }
}
