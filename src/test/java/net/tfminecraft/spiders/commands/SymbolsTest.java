package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.tfminecraft.spiders.spider.presets.AnimatedPalettes;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolsTest {
    /** Palettes are made of block data. */
    private final CommandTestSupport server = new CommandTestSupport();

    @AfterEach
    void close() {
        server.close();
    }

    private static String failure(Symbols.Symbol symbol, String arguments) {
        return assertThrows(CommandSyntaxException.class, () -> symbol.invoke(arguments)).getRawMessage().getString();
    }

    private static String failure(String raw) {
        return assertThrows(CommandSyntaxException.class, () -> Symbols.resolveSymbol(raw)).getRawMessage().getString();
    }

    private static List<String> paletteNames() {
        List<String> names = new ArrayList<>();
        for (AnimatedPalettes palette : AnimatedPalettes.values()) names.add("PALETTE_" + palette.name());
        return names;
    }

    @Test
    @DisplayName("symbol names resolve ignoring case and surrounding space")
    void symbolNamesResolve() throws CommandSyntaxException {
        assertInstanceOf(DisplayModel.class, Symbols.resolveSymbol("empty"));
        assertTrue(((DisplayModel) Symbols.resolveSymbol(" EMPTY {} ")).getPieces().isEmpty());
        assertEquals(AnimatedPalettes.RED_EYES.getPalette().size(), ((List<?>) Symbols.resolveSymbol("palette_red_eyes")).size());
    }

    @Test
    @DisplayName("anything that isn't a known symbol is left for JSON, unless it has symbol arguments")
    void otherValuesAreLeftForJson() {
        assertDoesNotResolve("1.5");
        assertDoesNotResolve("{\"x\": 1}");
        assertDoesNotResolve("GroundOrientation");
        assertEquals("Unknown symbol \"BIG_BOT\", expected one of EMPTY, " + String.join(", ", paletteNames()), failure("BIG_BOT {}"));
    }

    private static void assertDoesNotResolve(String raw) {
        try {
            assertNull(Symbols.resolveSymbol(raw), raw);
        } catch (CommandSyntaxException e) {
            throw new AssertionError(raw, e);
        }
    }

    @Test
    @DisplayName("symbols without parameters refuse arguments")
    void symbolsWithoutParametersRefuseArguments() {
        assertEquals("\"size\" is not an argument of EMPTY, it takes no arguments", failure("EMPTY {\"size\": 1}"));
        assertEquals("Malformed arguments {size:} for EMPTY, expected {  }", failure("EMPTY {size:}"));
    }

    @Test
    @DisplayName("symbols are suggested for values they can replace")
    void symbolsAreSuggestedForValuesTheyCanReplace() {
        assertEquals(List.of(new Pair<>("EMPTY", "DisplayModel")), Symbols.suggestSymbolsFor(DisplayModel.empty()));
        List<Pair<String, String>> palettes = Symbols.suggestSymbolsFor(AnimatedPalettes.CYAN_EYES.getPalette());
        assertEquals(paletteNames(), palettes.stream().map(Pair::first).toList());
        assertEquals("List<PaletteEntry>", palettes.getFirst().second());
        assertEquals(paletteNames().size(), Symbols.suggestSymbolsFor(List.of()).size(), "an empty list could be a palette");
        assertEquals(List.of(), Symbols.suggestSymbolsFor(List.of("eye")));
        assertEquals(List.of(), Symbols.suggestSymbolsFor(1.0));
    }

    /** A symbol with parameters, as the upstream robot presets had; none of the current symbols take any. */
    private final Symbols.Symbol sized = new Symbols.Symbol("SIZED", List.of(
        new Symbols.SymbolParameter("segments", 4.0),
        new Symbols.SymbolParameter("name", "leg")
    ), arguments -> arguments);

    @Test
    @DisplayName("parameters default, match ignoring case and are converted to the default's type")
    void parametersDefaultAndConvert() throws CommandSyntaxException {
        assertEquals(" (segments, name)", sized.hint());
        assertEquals("", new Symbols.Symbol("PLAIN", arguments -> arguments).hint());
        assertEquals(Map.of("segments", 4.0, "name", "leg"), sized.invoke(null));
        assertEquals(Map.of("segments", 4.0, "name", "leg"), sized.invoke("  "));
        assertEquals(Map.of("segments", 6.0, "name", "leg"), sized.invoke("{\"SEGMENTS\": 6}"));
        assertEquals(Map.of("segments", 4.0, "name", "front"), sized.invoke("{\"name\": \"front\", \"segments\": null}"));
    }

    @Test
    @DisplayName("unknown, malformed and mistyped parameters are refused")
    void badParametersAreRefused() {
        assertEquals("\"legs\" is not an argument of SIZED, expected segments, name", failure(sized, "{\"legs\": 2}"));
        assertEquals("Malformed arguments {segments:} for SIZED, expected { segments: 4.0, name: \"leg\" }", failure(sized, "{segments:}"));
        assertTrue(failure(sized, "{\"segments\": \"many\"}").startsWith("\"segments\" must be "));
    }
}
