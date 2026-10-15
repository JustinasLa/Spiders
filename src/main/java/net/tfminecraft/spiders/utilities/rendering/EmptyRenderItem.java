package net.tfminecraft.spiders.utilities.rendering;

public final class EmptyRenderItem implements RenderItem {
    public static final EmptyRenderItem INSTANCE = new EmptyRenderItem();

    private EmptyRenderItem() {
    }

    @Override
    public void submit(Object handle) {
        // No operation
    }
}
