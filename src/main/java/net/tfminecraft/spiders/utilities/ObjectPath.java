package net.tfminecraft.spiders.utilities;

import com.google.gson.Gson;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectPath {
    private final Gson gson;

    public ObjectPath(Gson gson) {
        this.gson = gson;
    }

    public ObjectPath() {
        this(Serializer.getGson());
    }

    public Object get(Object root, String path) {
        Node node = walk(root, parse(path));
        return node == null ? null : node.value();
    }

    public boolean set(Object root, String path, Object value) {
        List<String> keys = parse(path);
        if (keys.isEmpty()) return false;

        Node parent = walk(root, keys.subList(0, keys.size() - 1));
        if (parent == null) return false;
        return write(parent, keys.getLast(), value);
    }

    public boolean setAll(Object root, Map<?, ?> values) {
        boolean allWritten = true;
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!set(root, String.valueOf(entry.getKey()), entry.getValue())) allWritten = false;
        }
        return allWritten;
    }

    public List<String> paths(Object root, int maxDepth) {
        List<String> paths = new ArrayList<>();
        collectPaths(gson.fromJson(gson.toJson(root), Object.class), "", 0, maxDepth, paths);
        return paths;
    }

    public List<String> paths(Object root) {
        return paths(root, Integer.MAX_VALUE);
    }

    public List<String> completions(Object root, String typed) {
        int hops = parse(typed).size();
        boolean startsNewLevel = !typed.isEmpty() && (typed.charAt(typed.length() - 1) == '.' || typed.charAt(typed.length() - 1) == '[');

        int level;
        if (typed.isEmpty()) level = 1;
        else if (startsNewLevel) level = hops + 1;
        else level = hops;

        // Walk one level deeper than we may return, so a complete path can reveal its children in
        // the same pass rather than walking the tree twice.
        List<String> candidates = new ArrayList<>();
        for (String it : paths(root, level + 1)) {
            if (it.regionMatches(true, 0, typed, 0, typed.length())) candidates.add(it);
        }
        boolean isCompletePath = false;
        if (!startsNewLevel) {
            for (String it : candidates) {
                if (it.equalsIgnoreCase(typed)) {
                    isCompletePath = true;
                    break;
                }
            }
        }
        int depth = isCompletePath ? level + 1 : level;

        List<String> out = new ArrayList<>();
        for (String it : candidates) {
            if (parse(it).size() <= depth) out.add(it);
        }
        return out;
    }

    private record Node(Object value, Type type) {
    }

    private Node walk(Object root, List<String> keys) {
        Node node = new Node(root, root.getClass());
        for (String key : keys) {
            node = child(node, key);
            if (node == null) return null;
        }
        return node;
    }

    private Node child(Node parent, String key) {
        Object container = parent.value();
        if (container instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value == null) return null;
            return new Node(value, mapValueType(parent.type()));
        }
        if (container instanceof List<?> list) {
            Integer index = toIntOrNull(key);
            if (index == null || index < 0 || index >= list.size()) return null;
            Object value = list.get(index);
            if (value == null) return null;
            return new Node(value, elementType(parent.type()));
        }

        Field field = fieldOf(container.getClass(), key);
        if (field == null) return null;
        Object value = read(field, container);
        if (value == null) return null;
        return new Node(value, resolve(parent.type(), field.getGenericType()));
    }

    @SuppressWarnings("unchecked")
    private boolean write(Node parent, String key, Object value) {
        Object container = parent.value();
        try {
            if (container instanceof Map<?, ?>) {
                ((Map<Object, Object>) container).put(key, convert(value, mapValueType(parent.type())));
                return true;
            } else if (container instanceof List<?> list) {
                return writeIndex(list, key, value, elementType(parent.type()));
            } else {
                return writeField(container, key, value, parent.type());
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean writeIndex(List<?> list, String key, Object value, Type type) {
        Integer index = toIntOrNull(key);
        if (index == null) return false;

        List<Object> target = (List<Object>) list;
        Object converted = convert(value, type);

        // appending is allowed, e.g. `legs[3]` on a 3 element list
        if (index == target.size()) {
            target.add(converted);
            return true;
        } else if (index >= 0 && index < target.size()) {
            target.set(index, converted);
            return true;
        } else {
            return false;
        }
    }

    private boolean writeField(Object container, String key, Object value, Type context) {
        Field field = fieldOf(container.getClass(), key);
        if (field == null) return false;
        Object converted = convert(value, resolve(context, field.getGenericType()));

        try {
            field.setAccessible(true);
            field.set(container, converted);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object convert(Object value, Type type) {
        return gson.fromJson(gson.toJsonTree(value), type);
    }

    private Type elementType(Type context) {
        if (context instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length > 0 && arguments[0] != null) return arguments[0];
        }
        return Object.class;
    }

    private Type mapValueType(Type context) {
        if (context instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length > 1 && arguments[1] != null) return arguments[1];
        }
        return Object.class;
    }

    private Type resolve(Type context, Type declared) {
        if (!(declared instanceof TypeVariable<?> variable)) return declared;

        if (!(context instanceof ParameterizedType parameterized)) return declared;
        if (!(parameterized.getRawType() instanceof Class<?> owner)) return declared;
        int index = -1;
        TypeVariable<?>[] parameters = owner.getTypeParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (Objects.equals(parameters[i].getGenericDeclaration(), variable.getGenericDeclaration())) {
                index = i;
                break;
            }
        }
        Type[] arguments = parameterized.getActualTypeArguments();
        if (index >= 0 && index < arguments.length && arguments[index] != null) return arguments[index];
        return declared;
    }

    private Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void collectPaths(Object value, String prefix, int depth, int maxDepth, List<String> out) {
        if (depth >= maxDepth) return;

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                out.add(path);
                collectPaths(entry.getValue(), path, depth + 1, maxDepth, out);
            }
        } else if (value instanceof List<?> list) {
            int index = 0;
            for (Object child : list) {
                String path = prefix + "[" + index + "]";
                out.add(path);
                collectPaths(child, path, depth + 1, maxDepth, out);
                index++;
            }
        }
    }

    private Field fieldOf(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field field = fields.computeIfAbsent(current, ObjectPath::declaredFieldsByName).get(name);
            if (field != null) return field;
            current = current.getSuperclass();
        }
        return null;
    }

    private static Map<String, Field> declaredFieldsByName(Class<?> type) {
        Map<String, Field> byName = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) byName.put(field.getName(), field);
        return byName;
    }

    private final ConcurrentHashMap<Class<?>, Map<String, Field>> fields = new ConcurrentHashMap<>();

    /** Splits `a.b[1].c` into `[a, b, 1, c]`. */
    public static List<String> parse(String path) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '.' || path.charAt(i) == '[' || path.charAt(i) == ']') {
                String part = path.substring(start, i).strip();
                if (!part.isEmpty()) out.add(part);
                start = i + 1;
            }
        }
        return out;
    }


    private static Integer toIntOrNull(String value) {
        if (value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
