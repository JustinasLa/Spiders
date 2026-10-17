package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderGroupTest {
    @Test
    @DisplayName("children are kept by key, replaced in place, and submitted in order under (parent handle, key)")
    void submitsChildrenUnderNestedHandles() {
        List<Object> submitted = new ArrayList<>();
        RenderItem first = submitted::add;
        RenderItem second = submitted::add;
        RenderItem replacement = handle -> submitted.add("replaced " + handle);

        RenderGroup group = new RenderGroup();
        group.set("a", first);
        group.set("b", second);
        group.set("a", replacement);
        assertSame(replacement, group.get("a"));
        assertSame(second, group.get("b"));
        assertNull(group.get("c"));

        RenderGroup outer = new RenderGroup();
        outer.set("inner", group);
        outer.submit("root");

        Pair<Object, Object> inner = new Pair<>("root", "inner");
        assertEquals(List.of("replaced " + new Pair<>(inner, "a"), new Pair<>(inner, "b")), submitted);
    }
}
