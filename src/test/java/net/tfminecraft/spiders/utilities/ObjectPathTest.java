package net.tfminecraft.spiders.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectPathTest {
    static class Leg {
        double length = 1.5;
        List<String> tags = new ArrayList<>(List.of("a"));
    }

    static class Box<T> {
        T value;

        Box() {
        }

        Box(T value) {
            this.value = value;
        }
    }

    /** Declares its own type parameter but fixes Box's, so Box's T can't be read from {@code Wrapper<X>}. */
    static class Wrapper<U> extends Box<String> {
    }

    static class Named<V> extends LinkedHashMap<String, V> {
    }

    static class Outer<T> {
        /** A generic type with no type arguments of its own: {@code Outer<String>.Inner}. */
        class Inner extends ArrayList<T> {
        }
    }

    static class Options {
        int speed = 3;
        String name = "spider";
        Leg leg = new Leg();
        List<Leg> legs = new ArrayList<>(List.of(new Leg(), new Leg()));
        Map<String, Integer> weights = new LinkedHashMap<>(Map.of("front", 2));
        Map<String, Integer> frozen = Map.of("x", 1);
        Leg missing = null;
        List<Leg> withNull = new ArrayList<>(Arrays.asList((Leg) null));
        Box<Leg> box = new Box<>(new Leg());
        Wrapper<Integer> wrapper = new Wrapper<>();
        Named<Integer> named = new Named<>();
        Outer<String>.Inner inner = new Outer<String>().new Inner();
    }

    static class Base {
        int inherited = 1;
    }

    static class Derived extends Base {
        int own = 2;
    }

    static class Small {
        int speed = 3;
        Leg leg = new Leg();
        List<Leg> legs = new ArrayList<>(List.of(new Leg(), new Leg()));
    }

    private final ObjectPath path = new ObjectPath();

    @Test
    @DisplayName("paths split on dots and brackets, ignoring blanks and spaces")
    void parseSplitsOnDotsAndBrackets() {
        assertEquals(List.of("a", "b", "1", "c"), ObjectPath.parse("a.b[1].c"));
        assertEquals(List.of("a", "b"), ObjectPath.parse(" a .. b "));
        assertEquals(List.of(), ObjectPath.parse(""));
    }

    @Test
    @DisplayName("get reads fields, list elements, map values and inherited fields")
    void getReadsThroughFieldsListsAndMaps() {
        Options options = new Options();
        assertEquals(3, path.get(options, "speed"));
        assertEquals(1.5, path.get(options, "legs[1].length"));
        assertEquals("a", path.get(options, "leg.tags[0]"));
        assertEquals(2, path.get(options, "weights.front"));
        assertSame(options, path.get(options, ""));
        assertEquals(1, path.get(new Derived(), "inherited"));
        assertEquals(2, path.get(new Derived(), "own"));
        assertEquals(1.0, path.get(Map.of("a", List.of(1.0)), "a[0]"));
    }

    @Test
    @DisplayName("get is null for anything that isn't there")
    void getIsNullForMissingThings() {
        Options options = new Options();
        assertNull(path.get(options, "nope"));
        assertNull(path.get(options, "missing.length"), "a null field");
        assertNull(path.get(options, "weights.back"), "a missing key");
        assertNull(path.get(options, "legs[x]"), "not an index");
        assertNull(path.get(options, "legs[-1]"));
        assertNull(path.get(options, "legs[2]"));
        assertNull(path.get(options, "withNull[0]"), "a null element");
        assertNull(path.get(options, "speed.value"), "a field of a JDK class that can't be opened");
        assertNull(path.get(new Object(), "anything"));
    }

    @Test
    @DisplayName("set converts the value to the field's declared type")
    void setConvertsToTheDeclaredType() {
        Options options = new Options();
        assertTrue(path.set(options, "speed", 7.0));
        assertEquals(7, options.speed);
        assertTrue(path.set(options, "leg", Map.of("length", 4.0, "tags", List.of("x", "y"))));
        assertEquals(4.0, options.leg.length);
        assertEquals(List.of("x", "y"), options.leg.tags);
        assertFalse(path.set(options, "inherited", 5.0), "Options has no such field");
        Derived derived = new Derived();
        assertTrue(path.set(derived, "inherited", 9));
        assertEquals(9, derived.inherited);
    }

    @Test
    @DisplayName("set fills type parameters from the field's declared type")
    void setResolvesTypeParameters() {
        Options options = new Options();
        assertTrue(path.set(options, "box.value", Map.of("length", 2.0)));
        Leg leg = assertInstanceOf(Leg.class, options.box.value);
        assertEquals(2.0, leg.length);

        // the root's own type argument is erased, and Wrapper<Integer> says nothing about Box's T
        Box<Leg> root = new Box<>();
        assertTrue(path.set(root, "value", Map.of("length", 3.0)));
        assertInstanceOf(Map.class, path.get(root, "value"));
        assertTrue(path.set(options, "wrapper.value", 7));
        assertEquals(7.0, path.get(options, "wrapper.value"));
    }

    @Test
    @DisplayName("set converts map values and list elements to their declared types")
    void setConvertsMapValuesAndElements() {
        Options options = new Options();
        assertTrue(path.set(options, "weights.back", 4.0));
        assertEquals(4, options.weights.get("back"));
        assertTrue(path.set(options, "legs[0]", Map.of("length", 6.0)));
        assertEquals(6.0, options.legs.get(0).length);

        // a map subclass that only names its value type, and a list with no type arguments of its own, fall back to plain JSON values
        assertTrue(path.set(options, "named.k", 4));
        assertEquals(4.0, path.get(options, "named.k"));
        assertTrue(path.set(options, "inner[0]", 5));
        assertEquals(List.of(5.0), new ArrayList<Object>(options.inner));
    }

    @Test
    @DisplayName("set appends at the end of a list but not past it")
    void setAppendsButNotPastTheEnd() {
        Options options = new Options();
        assertTrue(path.set(options, "legs[2]", Map.of("length", 8.0)));
        assertEquals(3, options.legs.size());
        assertEquals(8.0, options.legs.get(2).length);
        assertFalse(path.set(options, "legs[9]", Map.of()));
        assertFalse(path.set(options, "legs[-1]", Map.of()));
        assertFalse(path.set(options, "legs[x]", Map.of()));
        assertEquals(3, options.legs.size());
    }

    @Test
    @DisplayName("set fails without changing anything when it can't write")
    void setFailsCleanly() {
        Options options = new Options();
        assertFalse(path.set(options, "", 1), "no path");
        assertFalse(path.set(options, "missing.length", 1), "a null parent");
        assertFalse(path.set(options, "nope", 1), "no such field");
        assertFalse(path.set(options, "speed", "fast"), "not a number");
        assertEquals(3, options.speed);
        assertFalse(path.set(options, "frozen.y", 1), "an immutable map");
        assertFalse(path.set(options, "name.hash", 1), "a JDK field that can't be opened");
    }

    @Test
    @DisplayName("setAll writes every value it can and reports whether all were written")
    void setAllReportsPartialFailure() {
        Options options = new Options();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("speed", 5);
        values.put("nope", 1);
        assertFalse(path.setAll(options, values));
        assertEquals(5, options.speed);
        assertTrue(path.setAll(options, Map.of("leg.length", 2.5)));
        assertEquals(2.5, options.leg.length);
    }

    @Test
    @DisplayName("paths lists every key and index, down to an optional depth")
    void pathsListsEveryKeyAndIndex() {
        Small small = new Small();
        small.legs.remove(1);
        assertEquals(List.of("speed", "leg", "leg.length", "leg.tags", "leg.tags[0]", "legs", "legs[0]", "legs[0].length", "legs[0].tags", "legs[0].tags[0]"),
            path.paths(small));
        assertEquals(List.of("speed", "leg", "legs"), path.paths(small, 1));
        assertEquals(List.of(), path.paths(small, 0));
    }

    @Test
    @DisplayName("completions offer the next level, and a complete path's children")
    void completionsOfferTheNextLevel() {
        Small small = new Small();
        assertEquals(List.of("speed", "leg", "legs"), path.completions(small, ""));
        assertEquals(List.of("leg", "legs"), path.completions(small, "le"));
        assertEquals(List.of("leg", "leg.length", "leg.tags", "legs", "legs[0]", "legs[1]"), path.completions(small, "LEG"));
        assertEquals(List.of("leg.length", "leg.tags"), path.completions(small, "leg."));
        assertEquals(List.of("legs[0]", "legs[1]"), path.completions(small, "legs["));
        assertEquals(List.of(), path.completions(small, "x"));
    }
}
