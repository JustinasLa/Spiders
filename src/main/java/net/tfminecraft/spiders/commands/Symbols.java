package net.tfminecraft.spiders.commands;

import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.tfminecraft.spiders.spider.configuration.PaletteEntry;
import net.tfminecraft.spiders.spider.presets.AnimatedPalettes;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Serializer;
import net.tfminecraft.spiders.utilities.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Symbols {
    private Symbols() {}

    public static Object resolveSymbol(String raw) throws CommandSyntaxException {
        Pair<String, String> parsed = parseSymbol(raw);
        if (parsed == null) return null;
        String name = parsed.first();
        String arguments = parsed.second();
        Symbol symbol = null;
        for (Symbol candidate : allSymbols()) {
            if (candidate.name.equalsIgnoreCase(name)) {
                symbol = candidate;
                break;
            }
        }

        if (symbol == null) {
            if (arguments != null) {
                List<String> names = new ArrayList<>();
                for (Symbol candidate : allSymbols()) names.add(candidate.name);
                String expected = String.join(", ", names);
                throw invalid("Unknown symbol \"" + name + "\", expected one of " + expected);
            }
            return null;
        }

        return symbol.invoke(arguments);
    }

    public static List<Pair<String, String>> suggestSymbolsFor(Object value) {
        List<Pair<String, String>> suggestions = new ArrayList<>();
        for (Map.Entry<Predicate<Object>, List<Symbol>> entry : symbolList().entrySet()) {
            if (!entry.getKey().test(value)) continue;
            for (Symbol symbol : entry.getValue()) {
                suggestions.add(new Pair<>(symbol.name, SetupCommands.describeType(value) + symbol.hint()));
            }
        }
        return suggestions;
    }

    static final class SymbolParameter {
        final String name;
        final Object defaultValue;

        SymbolParameter(String name, Object defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }
    }

    @FunctionalInterface
    interface Producer {
        Object produce(Map<String, Object> arguments) throws CommandSyntaxException;
    }

    static final class Symbol {
        final String name;
        final List<SymbolParameter> parameters;
        final Producer produce;
        final String signature;

        Symbol(String name, List<SymbolParameter> parameters, Producer produce) {
            this.name = name;
            this.parameters = parameters;
            this.produce = produce;
            List<String> names = new ArrayList<>();
            for (SymbolParameter parameter : parameters) names.add(parameter.name);
            this.signature = String.join(", ", names);
        }

        Symbol(String name, Producer produce) {
            this(name, List.of(), produce);
        }

        /** The arguments it takes, for a suggestion's tooltip. */
        String hint() {
            return parameters.isEmpty() ? "" : " (" + signature + ")";
        }

        Object invoke(String arguments) throws CommandSyntaxException {
            return produce.produce(resolve(arguments));
        }

        private Map<String, Object> resolve(String arguments) throws CommandSyntaxException {
            if (arguments == null || Strings.isBlank(arguments)) {
                Map<String, Object> defaults = new LinkedHashMap<>();
                for (SymbolParameter parameter : parameters) defaults.put(parameter.name, parameter.defaultValue);
                return defaults;
            }

            Map<String, Object> supplied = parseArguments(arguments);

            // check for invalid fields
            for (String key : supplied.keySet()) {
                boolean known = false;
                for (SymbolParameter parameter : parameters) {
                    if (parameter.name.equalsIgnoreCase(key)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    String expected = parameters.isEmpty() ? ", it takes no arguments" : ", expected " + signature;
                    throw invalid("\"" + key + "\" is not an argument of " + name + expected);
                }
            }

            // resolve with defaults
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (SymbolParameter parameter : parameters) {
                Map.Entry<String, Object> entry = null;
                for (Map.Entry<String, Object> candidate : supplied.entrySet()) {
                    if (candidate.getKey().equalsIgnoreCase(parameter.name)) {
                        entry = candidate;
                        break;
                    }
                }
                resolved.put(parameter.name, entry == null ? parameter.defaultValue : coerce(parameter, entry.getValue()));
            }
            return resolved;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> parseArguments(String literal) throws CommandSyntaxException {
            try {
                return (Map<String, Object>) Serializer.getGson().fromJson(literal, Map.class);
            } catch (JsonSyntaxException e) {
                List<String> parts = new ArrayList<>();
                for (SymbolParameter parameter : parameters) parts.add(parameter.name + ": " + Serializer.serialize(parameter.defaultValue));
                String expected = String.join(", ", parts);
                throw invalid("Malformed arguments " + literal + " for " + name + ", expected { " + expected + " }");
            }
        }

        private Object coerce(SymbolParameter parameter, Object value) throws CommandSyntaxException {
            if (value == null) return parameter.defaultValue;

            Class<?> type = parameter.defaultValue.getClass();
            try {
                return Serializer.getGson().fromJson(Serializer.getGson().toJsonTree(value), type);
            } catch (Exception e) {
                throw invalid("\"" + parameter.name + "\" must be " + SetupCommands.describeType(type));
            }
        }

    }

    private static final class SymbolListHolder {
        static final Map<Predicate<Object>, List<Symbol>> SYMBOL_LIST = createSymbolList();
    }

    private static Map<Predicate<Object>, List<Symbol>> symbolList() {
        return SymbolListHolder.SYMBOL_LIST;
    }

    private static List<Symbol> allSymbols() {
        List<Symbol> all = new ArrayList<>();
        for (List<Symbol> symbols : symbolList().values()) all.addAll(symbols);
        return all;
    }

    private static Map<Predicate<Object>, List<Symbol>> createSymbolList() {
        Map<Predicate<Object>, List<Symbol>> symbols = new LinkedHashMap<>();
        symbols.put(value -> value instanceof DisplayModel, List.of(
            new Symbol("EMPTY", arguments -> DisplayModel.empty())
        ));

        List<Symbol> palettes = new ArrayList<>();
        for (AnimatedPalettes palette : AnimatedPalettes.values()) {
            palettes.add(new Symbol("PALETTE_" + palette.name(), arguments -> palette.getPalette()));
        }
        symbols.put(value -> {
            if (!(value instanceof List<?> list)) return false;
            for (Object element : list) if (!(element instanceof PaletteEntry)) return false;
            return true;
        }, palettes);

        return symbols;
    }

    private static final Pattern SPLIT_REGEX = Pattern.compile("^([A-Za-z_]\\w*)\\s*(\\{.*})?$", Pattern.DOTALL);

    private static Pair<String, String> parseSymbol(String raw) {
        Matcher match = SPLIT_REGEX.matcher(Strings.trim(raw));
        if (!match.matches()) return null;
        // the arguments, if given, are at least "{}"
        return new Pair<>(match.group(1), match.group(2));
    }

    private static CommandSyntaxException invalid(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }

}
