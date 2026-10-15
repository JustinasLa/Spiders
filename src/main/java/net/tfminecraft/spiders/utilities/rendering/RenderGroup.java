package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.Pair;

import java.util.LinkedHashMap;
import java.util.Map;

public class RenderGroup implements RenderItem {
    private final Map<Object, RenderItem> children = new LinkedHashMap<>();

//    fun add(handle: Any, item: RenderItem) {
//        children[handle] = item
//    }

    public void set(Object handle, RenderItem item) {
        children.put(handle, item);
    }

    public RenderItem get(Object handle) {
        return children.get(handle);
    }

    @Override
    public void submit(Object handle) {
        for (Map.Entry<Object, RenderItem> entry : children.entrySet()) {
            entry.getValue().submit(new Pair<>(handle, entry.getKey()));
        }
    }
}
