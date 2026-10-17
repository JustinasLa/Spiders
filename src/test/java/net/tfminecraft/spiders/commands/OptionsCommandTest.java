package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.AnimatedPalettes;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.bukkit.block.BlockType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.tfminecraft.spiders.commands.CommandTestSupport.lastMessage;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class OptionsCommandTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    private final Player staff = staff("Warden", commands.at(0.5, 64.0, 0.5));
    private final CommandSourceStack source = source(staff);

    @AfterEach
    void close() {
        AppState.getMiscOptions().setShowLaser(false);
        commands.close();
    }

    private SpiderOptions cinderweaver() {
        return spider(commands.world, new Vector(2.5, 64.0, 0.5), Cinderweaver.cinderweaver(0.8)).query(SpiderOptions.class);
    }

    /** Suggestion text to tooltip, in order. */
    private Map<String, String> tooltips(String input) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Suggestion suggestion : commands.suggestions(source, input).getList()) {
            out.put(suggestion.getText(), suggestion.getTooltip() == null ? null : suggestion.getTooltip().getString());
        }
        return out;
    }

    /** Options with a few extra kinds of value, for the suggestions. */
    static final class TaggedOptions extends SpiderOptions {
        String plain = "eye";
        String numeric = "12";
        String spaced = "a b";
        char mark = 'x';
        AtomicBoolean armed = new AtomicBoolean(true);
    }

    @Test
    @DisplayName("options shows a value, a whole group, or why the path is wrong")
    void optionsShowsAValue() throws CommandSyntaxException {
        assertEquals(1, commands.run(source, "spider options global.showLaser"));
        assertEquals("global.showLaser is false", lastMessage(staff));
        commands.run(source, "spider options global");
        assertEquals("global is {\"showLaser\":false}", lastMessage(staff));
        assertEquals("\"nope\" does not exist in global", commands.error(source, "spider options global.nope"));
        assertEquals("Unknown option group \"nope\", expected one of spider_options, global", commands.error(source, "spider options nope.x"));
        assertEquals("No spider found", commands.error(source, "spider options spider_options.gallop"));

        cinderweaver();
        commands.run(source, "spider options spider_options.bodyPlan.scale");
        assertEquals("spider_options.bodyPlan.scale is 0.8", lastMessage(staff));
    }

    @Test
    @DisplayName("global options are set in place, from JSON or raw text")
    void globalOptionsAreSetInPlace() throws CommandSyntaxException {
        assertEquals(1, commands.run(source, "spider options global.showLaser set true"));
        assertTrue(AppState.getMiscOptions().getShowLaser());
        assertEquals("Set global.showLaser to true", lastMessage(staff));

        commands.run(source, "spider options global set {\"showLaser\": false}");
        assertFalse(AppState.getMiscOptions().getShowLaser());
        assertEquals("Set global to {\"showLaser\": false}", lastMessage(staff));

        assertEquals("Could not set global.nope", commands.error(source, "spider options global.nope set 1"));
        assertEquals("Could not set global", commands.error(source, "spider options global set null"));
        assertEquals("Could not set global", commands.error(source, "spider options global set 5"));
        assertEquals("Could not set global", commands.error(source, "spider options global set {oops"));
    }

    @Test
    @DisplayName("spider options are edited as a draft and only swapped in if within the limits")
    void spiderOptionsAreEditedAsADraft() throws CommandSyntaxException {
        SpiderOptions options = cinderweaver();
        commands.run(source, "spider options spider_options.gallop set true");
        assertTrue(options.getGallop());

        commands.run(source, "spider options spider_options.walkGait.scanOrientation set GroundOrientation");
        assertEquals(OrientationMode.GroundOrientation, options.getWalkGait().getScanOrientation());

        // unparseable as JSON: kept as raw text, here a block id
        commands.run(source, "spider options \"spider_options.bodyPlan.bodyModel.pieces[0].block\" set minecraft:stone");
        assertEquals(Material.STONE, options.getBodyPlan().getBodyModel().getPieces().getFirst().getBlock().getMaterial());
        assertEquals("Set spider_options.bodyPlan.bodyModel.pieces[0].block to minecraft:stone", lastMessage(staff));

        // symbols
        commands.run(source, "spider options spider_options.bodyPlan.eyePalette set PALETTE_RED_EYES");
        assertEquals(AnimatedPalettes.RED_EYES.getPalette().size(), options.getBodyPlan().getEyePalette().size());
        commands.run(source, "spider options spider_options.bodyPlan.bodyModel set empty");
        assertTrue(options.getBodyPlan().getBodyModel().getPieces().isEmpty());

        assertEquals("Could not set spider_options.bodyPlan.scale: scale must be between 0.25 and 2.5",
            commands.error(source, "spider options spider_options.bodyPlan.scale set 10"));
        assertEquals(0.8, options.getBodyPlan().getScale(), 1e-9, "the refused draft was thrown away");
        assertEquals("Could not set spider_options.nope", commands.error(source, "spider options spider_options.nope set 1"));
    }

    @Test
    @DisplayName("reset puts back the default, for one option or a whole group")
    void resetPutsBackTheDefault() throws CommandSyntaxException {
        AppState.getMiscOptions().setShowLaser(true);
        assertEquals(1, commands.run(source, "spider options global.showLaser reset"));
        assertFalse(AppState.getMiscOptions().getShowLaser());
        assertEquals("Reset global.showLaser to false", lastMessage(staff));
        AppState.getMiscOptions().setShowLaser(true);
        commands.run(source, "spider options global reset");
        assertFalse(AppState.getMiscOptions().getShowLaser());
        assertEquals("Reset all of global", lastMessage(staff));
        assertEquals("\"global.nope\" does not exist", commands.error(source, "spider options global.nope reset"));

        SpiderOptions options = cinderweaver();
        options.setGallop(true);
        options.getBodyPlan().scale(2.0);
        commands.run(source, "spider options spider_options.gallop reset");
        assertFalse(options.getGallop());
        options.setGallop(true);
        commands.run(source, "spider options spider_options reset");
        assertEquals("Reset all of spider_options", lastMessage(staff));
        assertFalse(options.getGallop());
        assertEquals(0.8, options.getBodyPlan().getScale(), 1e-9);
    }

    @Test
    @DisplayName("reset is refused when the result would still be out of bounds or the path is missing")
    void resetIsRefusedWhenStillOutOfBounds() {
        SpiderOptions options = cinderweaver();
        options.getBodyPlan().setLegs(new ArrayList<>());
        options.setGallop(true);
        assertEquals("Could not reset spider_options.gallop: a spider needs 1 to 12 legs", commands.error(source, "spider options spider_options.gallop reset"));
        assertTrue(options.getGallop());
        assertEquals("Could not reset spider_options.bodyPlan.legs[5].segments",
            commands.error(source, "spider options \"spider_options.bodyPlan.legs[5].segments\" reset"));
    }

    @Test
    @DisplayName("a mimic's options can be read but not changed")
    void aMimicsOptionsCanBeReadButNotChanged() throws CommandSyntaxException {
        SpiderOptions options = Cinderweaver.cinderweaver(0.8);
        spider(commands.world, new Vector(2.5, 64.0, 0.5), options, mock(Mimic.class));
        commands.run(source, "spider options spider_options.gallop");
        assertEquals("spider_options.gallop is false", lastMessage(staff));
        assertEquals("Mimics can't be changed with /spider options.", commands.error(source, "spider options spider_options.gallop set true"));
        assertEquals("Mimics can't be changed with /spider options.", commands.error(source, "spider options spider_options.gallop reset"));
        assertFalse(options.getGallop());
    }

    @Test
    @DisplayName("if the spider is gone by the mimic check, the edit goes ahead on the options already found")
    void theMimicCheckToleratesAVanishedSpider() throws CommandSyntaxException {
        ECSEntity entity = spider(commands.world, new Vector(2.5, 64.0, 0.5), Cinderweaver.cinderweaver(0.8));
        try (MockedStatic<SpiderTargets> targets = mockStatic(SpiderTargets.class)) {
            targets.when(() -> SpiderTargets.forCommand(any())).thenReturn(entity, (ECSEntity) null);
            commands.run(source, "spider options spider_options.gallop set true");
        }
        assertTrue(entity.query(SpiderOptions.class).getGallop());
    }

    @Test
    @DisplayName("paths complete group by group, quoting those that need it")
    void pathsComplete() {
        assertEquals(List.of("global", "spider_options"), commands.suggest(source, "spider options "));
        assertEquals(List.of("global"), commands.suggest(source, "spider options GLO"));
        // the group itself is what was typed, so only its options are new
        assertEquals(List.of("global.showLaser"), commands.suggest(source, "spider options global"));
        assertEquals(List.of(), commands.suggest(source, "spider options nope."));

        // without a spider, the default preset's paths are offered
        List<String> plan = commands.suggest(source, "spider options spider_options.bodyPlan.");
        assertTrue(plan.contains("spider_options.bodyPlan.scale"), plan.toString());
        assertTrue(plan.contains("spider_options.bodyPlan.legs"), plan.toString());
        assertEquals(List.of("spider_options.bodyPlan.legs"), commands.suggest(source, "spider options \"spider_options.bodyPlan.leg"));
        List<String> legs = commands.suggest(source, "spider options spider_options.bodyPlan.legs");
        assertEquals("\"spider_options.bodyPlan.legs[0]\"", legs.getFirst());
        assertEquals(8, legs.size(), legs.toString());
    }

    @Test
    @DisplayName("values suggest what fits the current value, with its type")
    void valuesSuggestWhatFits() {
        cinderweaver();
        assertEquals(Map.of("true", "boolean", "false", "boolean"), tooltips("spider options global.showLaser set "));
        assertEquals(Map.of("GroundOrientation", "OrientationMode value", "SpiderOrientation", "OrientationMode value", "YAxis", "OrientationMode value"),
            tooltips("spider options spider_options.walkGait.scanOrientation set "));
        assertEquals(Map.of("0.8", "Double"), tooltips("spider options spider_options.bodyPlan.scale set "));
        assertEquals(Map.of("EMPTY", "DisplayModel"), tooltips("spider options spider_options.bodyPlan.bodyModel set "));

        Map<String, String> palettes = tooltips("spider options spider_options.bodyPlan.eyePalette set ");
        assertEquals(AnimatedPalettes.values().length, palettes.size());
        assertEquals("List<PaletteEntry>", palettes.get("PALETTE_RED_EYES"));

        Map<String, String> blocks = tooltips("spider options \"spider_options.bodyPlan.bodyModel.pieces[0].block\" set ");
        assertEquals("BlockData", blocks.get("minecraft:stone"));
        assertEquals("BlockData", blocks.get("minecraft:chest"));
        assertEquals(SetupCommands.getBlockIds().size(), blocks.size());
        assertTrue(SetupCommands.getBlockIds().stream().noneMatch(id -> id.contains("legacy")), "legacy materials have no key");

        Map<String, String> vector =tooltips("spider options \"spider_options.bodyPlan.legs[0].restPosition\" set ");
        assertEquals(1, vector.size());
        assertTrue(vector.keySet().iterator().next().startsWith("{\"x\":"), vector.toString());
        assertEquals("Vector", vector.values().iterator().next());

        assertEquals(Map.of("BodyPlan", "BodyPlan"), tooltips("spider options spider_options.bodyPlan set "), "too long to suggest as JSON");
        assertEquals(Map.of(), tooltips("spider options spider_options.nope set "));
        assertEquals(Map.of(), tooltips("spider options nope.x set "));
    }

    @Test
    @DisplayName("materials without a block type, like items, aren't suggested as blocks")
    @SuppressWarnings("unchecked")
    void itemsAreNotSuggestedAsBlocks() throws ReflectiveOperationException {
        // the test registry gives every material a block type, so take DIAMOND's away while the list is built
        Field blockType = Material.class.getDeclaredField("blockType");
        blockType.setAccessible(true);
        Supplier<BlockType> original = (Supplier<BlockType>) blockType.get(Material.DIAMOND);
        Method createBlockIds = Class.forName(SetupCommands.class.getName() + "$BlockIdsHolder").getDeclaredMethod("createBlockIds");
        createBlockIds.setAccessible(true);
        List<String> ids;
        try {
            blockType.set(Material.DIAMOND, (Supplier<BlockType>) () -> null);
            ids = (List<String>) createBlockIds.invoke(null);
        } finally {
            blockType.set(Material.DIAMOND, original);
        }
        assertFalse(ids.contains("minecraft:diamond"));
        assertTrue(ids.contains("minecraft:diamond_block"));
    }

    @Test
    @DisplayName("text values are quoted only where JSON would read them as something else")
    void textValuesAreQuotedOnlyWhereNeeded() {
        spider(commands.world, new Vector(2.5, 64.0, 0.5), new TaggedOptions());
        assertEquals(Map.of("eye", "String"), tooltips("spider options spider_options.plain set "));
        assertEquals(Map.of("\"12\"", "String"), tooltips("spider options spider_options.numeric set "));
        assertEquals(Map.of("a b", "String"), tooltips("spider options spider_options.spaced set "));
        assertEquals(Map.of("x", "Character"), tooltips("spider options spider_options.mark set "));
        // JSON that isn't text is suggested as JSON
        assertEquals(Map.of("true", "AtomicBoolean"), tooltips("spider options spider_options.armed set "));
    }

    @Test
    @DisplayName("types are described for suggestions and errors")
    void typesAreDescribed() {
        assertEquals("List<unknown>", SetupCommands.describeType(List.of()));
        assertEquals("List<String>", SetupCommands.describeType(List.of("a")));
        assertEquals("List<Integer>", SetupCommands.describeType(new LinkedHashSet<>(List.of(1))));
        assertEquals("List<unknown>", SetupCommands.describeType(Set.of()));
        assertEquals("map", SetupCommands.describeType(Map.of()));
        assertEquals("Double", SetupCommands.describeType(1.0));
    }
}
