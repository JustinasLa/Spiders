package net.tfminecraft.spiders;

import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.laser.LaserPoint;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.spider.configuration.SoundPlayer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Serializer;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupItemsTest {
    private final World world = mock(World.class);
    private AppTestSupport app;
    private Player player;

    @BeforeEach
    void setup() {
        app = new AppTestSupport();
        SetupItems.setupItems();
        // standing at the origin, looking straight down
        player = app.staff(new Location(world, 0.5, 64.0, 0.5, 0f, 90f));
    }

    @AfterEach
    void restore() {
        AppState.getMiscOptions().setShowLaser(false);
        app.close();
    }

    private void aimAt(Vector hit, BlockFace face) {
        RayTraceResult result = face == null ? new RayTraceResult(hit) : new RayTraceResult(hit, face);
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(result);
    }

    private void aimAtNothing() {
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(null);
    }

    private void use(ItemStack item) {
        app.fire(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    private void heard(Sound sound, float pitch) {
        verify(world).playSound(any(Location.class), eq(sound), eq(1.0f), eq(pitch));
    }

    private ECSEntity spiderNearby(ECSComponent... parts) {
        ECSEntity entity = AppState.getEcs().spawn(SpiderBody.create(world, new Vector(4.5, 64.0, 0.5)));
        for (ECSComponent part : parts) entity.addComponent(part);
        return entity;
    }

    private static UUID linked(AppTestSupport.Tool seal) {
        return (UUID) seal.stored("spider_uuid");
    }

    // Summoning Seal

    @Test
    @DisplayName("the seal summons a Cinderweaver where it is aimed and links to it")
    void theSealSummonsWhereAimed() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);

        use(seal.item);

        assertEquals(1, AppState.spiderCount());
        ECSEntity spider = AppState.liveSpiders().getFirst().first();
        SpiderBody body = spider.query(SpiderBody.class);
        assertEquals(body.getUuid(), linked(seal));
        assertEquals(5.5, body.getPosition().getX(), 1e-9);
        assertTrue(body.getPosition().getY() > 63.0);
        SpiderOptions options = spider.query(SpiderOptions.class);
        assertEquals(CinderweaverSize.HUNTER.getScale(), options.getBodyPlan().getScale(), 1e-9);
        assertFalse(options.getClimbing());
        verify(world).playSound(argThat((Location at) -> at.toVector().equals(new Vector(5.5, 63.0, 0.5))), eq(Sound.ENTITY_SPIDER_AMBIENT), eq(.4f), eq(.7f));
        verify(player).sendActionBar(Component.text("Spider created"));
        verify(seal.item).setItemMeta(seal.meta);
    }

    @Test
    @DisplayName("summoned onto a wall a spider clings on; a hit without a face counts as floor")
    void summonedOntoAWallASpiderClings() {
        AppTestSupport.Tool wall = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        aimAt(new Vector(0.5, 65.0, 6.0), BlockFace.NORTH);
        use(wall.item);
        assertTrue(AppState.findSpiderByUUID(linked(wall)).query(SpiderOptions.class).getClimbing());

        AppTestSupport.Tool floor = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        aimAt(new Vector(0.5, 63.0, 6.0), null);
        use(floor.item);
        assertFalse(AppState.findSpiderByUUID(linked(floor)).query(SpiderOptions.class).getClimbing());
    }

    @Test
    @DisplayName("aiming the seal at nothing in reach does nothing")
    void aimingAtNothingDoesNothing() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        aimAtNothing();

        use(seal.item);

        assertEquals(0, AppState.spiderCount());
        assertNull(linked(seal));
        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    @DisplayName("using the seal again recalls its spider and keeps the spider's settings for next time")
    void theSealRecallsAndKeepsSettings() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);
        use(seal.item);
        ECSEntity first = AppState.findSpiderByUUID(linked(seal));
        SpiderOptions tuned = first.query(SpiderOptions.class);
        tuned.setGallop(true);
        tuned.getWalkGait().setMaxSpeed(.05);

        use(seal.item);

        assertTrue(first.getScheduledForRemoval());
        assertNull(linked(seal));
        assertInstanceOf(String.class, seal.stored("spider_options"));
        verify(world).playSound(any(Location.class), eq(Sound.BLOCK_WOOL_BREAK), eq(.4f), eq(.7f));
        verify(player).sendActionBar(Component.text("Spider removed"));

        use(seal.item);

        ECSEntity second = AppState.findSpiderByUUID(linked(seal));
        assertNotNull(second);
        assertNotEquals(first, second);
        SpiderOptions restored = second.query(SpiderOptions.class);
        assertTrue(restored.getGallop());
        assertEquals(.05, restored.getWalkGait().getMaxSpeed(), 1e-9);
        assertSame(Sound.ENTITY_SPIDER_STEP, restored.getSound().getStep().getSound());
    }

    @Test
    @DisplayName("a seal from the clockwork days summons the organic look and sounds")
    void aClockworkSealSummonsTheOrganicLook() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        SpiderOptions clockwork = Cinderweaver.cinderweaver();
        clockwork.getBodyPlan().getBodyModel().getPieces().getFirst().setBlock(AppTestSupport.blockData(Material.BLAST_FURNACE));
        clockwork.getSound().setStep(new SoundPlayer(Sound.BLOCK_NETHERITE_BLOCK_STEP, .2f, 1f));
        seal.store.put(Core.namespacedID("spider_options"), Serializer.serialize(clockwork));
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);

        use(seal.item);

        SpiderOptions options = AppState.findSpiderByUUID(linked(seal)).query(SpiderOptions.class);
        assertTrue(options.getBodyPlan().getBodyModel().getPieces().stream().noneMatch(piece -> piece.getBlock().getMaterial() == Material.BLAST_FURNACE));
        assertSame(Sound.ENTITY_SPIDER_STEP, options.getSound().getStep().getSound());
    }

    @Test
    @DisplayName("unreadable stored settings are ignored with a warning and the default spider is summoned")
    void unreadableSettingsAreIgnored() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        seal.store.put(Core.namespacedID("spider_options"), "{not json");
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);

        use(seal.item);

        verify(app.logger).warning(contains("Ignoring unreadable stored spider options"));
        SpiderOptions options = AppState.findSpiderByUUID(linked(seal)).query(SpiderOptions.class);
        assertEquals(CinderweaverSize.HUNTER.getScale(), options.getBodyPlan().getScale(), 1e-9);
    }

    @Test
    @DisplayName("a seal whose spider is gone summons a new one")
    void aSealWhoseSpiderIsGoneSummonsANewOne() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        UUID gone = UUID.randomUUID();
        seal.store.put(Core.namespacedID("spider_uuid"), gone);
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);

        use(seal.item);

        assertEquals(1, AppState.spiderCount());
        assertNotEquals(gone, linked(seal));
    }

    @Test
    @DisplayName("at the spider limit the seal summons nothing and stays unlinked")
    void atTheLimitTheSealSummonsNothing() {
        for (int i = 0; i < AppState.MAX_SPIDERS; i++) AppState.getEcs().spawn(SpiderBody.create(world, new Vector(i, 64.0, 0.0)));
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        ItemStack bare = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE).item;
        when(bare.getItemMeta()).thenReturn(null);
        aimAt(new Vector(5.5, 63.0, 0.5), BlockFace.UP);

        use(seal.item);
        use(bare);

        assertEquals(AppState.MAX_SPIDERS, AppState.spiderCount());
        assertNull(linked(seal));
        verify(player, never()).sendActionBar(Component.text("Spider created"));
    }

    @Test
    @DisplayName("recalling a spider without settings clears the seal's old settings")
    void recallingASpiderWithoutSettingsClearsOldOnes() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        ECSEntity bare = spiderNearby();
        seal.store.put(Core.namespacedID("spider_uuid"), bare.query(SpiderBody.class).getUuid());
        seal.store.put(Core.namespacedID("spider_options"), "{}");

        use(seal.item);

        assertTrue(bare.getScheduledForRemoval());
        assertNull(seal.stored("spider_options"));
        assertNull(linked(seal));
    }

    @Test
    @DisplayName("settings that cannot be saved are reported and the spider is still recalled")
    void unsavableSettingsAreReported() {
        AppTestSupport.Tool seal = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        SpiderOptions broken = new SpiderOptions();
        broken.getWalkGait().setMaxSpeed(Double.NaN);
        ECSEntity spider = spiderNearby(broken);
        seal.store.put(Core.namespacedID("spider_uuid"), spider.query(SpiderBody.class).getUuid());

        use(seal.item);

        verify(app.logger).severe(contains("Could not serialize spider options"));
        assertNull(seal.stored("spider_options"));
        assertTrue(spider.getScheduledForRemoval());
        assertNull(linked(seal));
    }

    @Test
    @DisplayName("the spider linked to a seal in either hand is the held spider")
    void theSealInEitherHandIsHeld() {
        assertNull(SetupItems.heldSpiderUUID(player));

        UUID id = UUID.randomUUID();
        AppTestSupport.Tool offHand = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        offHand.store.put(Core.namespacedID("spider_uuid"), id);
        when(player.getInventory().getItemInOffHand()).thenReturn(offHand.item);
        assertEquals(id, SetupItems.heldSpiderUUID(player));

        UUID main = UUID.randomUUID();
        AppTestSupport.Tool mainHand = AppTestSupport.tool(Material.FERMENTED_SPIDER_EYE);
        mainHand.store.put(Core.namespacedID("spider_uuid"), main);
        when(player.getInventory().getItemInMainHand()).thenReturn(mainHand.item);
        assertEquals(main, SetupItems.heldSpiderUUID(player));
    }

    // Limb Binding Charm

    @Test
    @DisplayName("the Limb Binding Charm frees a bound leg, and sounds dull when no leg is selected")
    void theLimbBindingCharmTogglesTheSelectedLeg() {
        ItemStack charm = AppTestSupport.tool(Material.RABBIT_HIDE).item;
        PointDetector detector = new PointDetector();
        AppState.getEcs().spawn(detector);

        use(charm);
        heard(Sound.BLOCK_SCULK_HIT, 2.0f);

        Leg leg = mock(Leg.class);
        when(leg.isDisabled()).thenReturn(true);
        detector.getSelectedLeg().put(player, leg);
        use(charm);
        verify(leg).setDisabled(false);
        heard(Sound.BLOCK_WOOL_HIT, 1.0f);
    }

    @Test
    @DisplayName("each tick, leg selection follows the staff holding the Limb Binding Charm")
    void legSelectionFollowsCharmHolders() {
        PointDetector detector = new PointDetector();
        AppState.getEcs().spawn(detector);
        app.staff(new Location(world, 0.0, 64.0, 0.0));
        ItemStack charm = AppTestSupport.tool(Material.RABBIT_HIDE).item;
        when(player.getInventory().getItemInMainHand()).thenReturn(charm);

        app.runNewTickHandlers();

        assertEquals(java.util.Set.of(player), detector.getCheckPlayers());
    }

    // Broodkeeper's Sight

    @Test
    @DisplayName("Broodkeeper's Sight turns the debug view on, then off")
    void broodkeepersSightTogglesTheDebugView() {
        ItemStack sight = AppTestSupport.tool(Material.BONE).item;

        use(sight);
        assertEquals(List.of(player), AppState.getDebugViewers());
        heard(Sound.BLOCK_SCULK_HIT, 2.0f);
        verify(player).sendActionBar(Component.text("Debug view on (only you see it)"));

        use(sight);
        assertTrue(AppState.getDebugViewers().isEmpty());
        heard(Sound.BLOCK_SCULK_HIT, 1.5f);
        verify(player).sendActionBar(Component.text("Debug view off"));
    }

    // tools acting on the nearest spider

    @Test
    @DisplayName("tools say so when no spider is in reach")
    void toolsSaySoWhenNoSpiderIsInReach() {
        for (Material material : List.of(Material.LIGHT_BLUE_DYE, Material.GREEN_DYE, Material.BREEZE_ROD, Material.SLIME_BALL)) {
            use(AppTestSupport.tool(material).item);
        }
        verify(player, org.mockito.Mockito.times(4)).sendActionBar(Component.text("No Cinderweaver within 64 blocks"));
        verify(world, never()).playSound(any(Location.class), any(Sound.class), org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    @DisplayName("tools leave a spider alone when it lacks the part they change")
    void toolsLeaveSpidersWithoutThePartAlone() {
        spiderNearby();
        for (Material material : List.of(Material.LIGHT_BLUE_DYE, Material.GREEN_DYE, Material.BREEZE_ROD, Material.SLIME_BALL)) {
            use(AppTestSupport.tool(material).item);
        }
        verify(player, never()).sendActionBar(any(Component.class));
        verify(world, never()).playSound(any(Location.class), any(Sound.class), org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    @DisplayName("Spectral Form switches the nearest spider between particles and blocks")
    void spectralFormSwitchesTheRenderer() {
        SpiderRenderer renderer = new SpiderRenderer();
        spiderNearby(renderer);
        ItemStack form = AppTestSupport.tool(Material.LIGHT_BLUE_DYE).item;

        use(form);
        assertTrue(renderer.getUseParticles());
        heard(Sound.ENTITY_AXOLOTL_ATTACK, 1.0f);

        use(form);
        assertFalse(renderer.getUseParticles());
        heard(Sound.ENTITY_SPIDER_AMBIENT, 1.0f);
    }

    @Test
    @DisplayName("the Shroud Charm shrouds the nearest spider, then unshrouds it")
    void theShroudCharmTogglesTheCloak() {
        Cloak cloak = new Cloak();
        spiderNearby(cloak);
        ItemStack charm = AppTestSupport.tool(Material.GREEN_DYE).item;

        use(charm);
        assertTrue(cloak.getActive());
        heard(Sound.BLOCK_SCULK_HIT, 2.0f);
        verify(player).sendActionBar(Component.text("Shroud on"));

        use(charm);
        assertFalse(cloak.getActive());
        heard(Sound.BLOCK_SCULK_HIT, 1.5f);
        verify(player).sendActionBar(Component.text("Shroud off"));
    }

    @Test
    @DisplayName("Hunting Instinct switches the nearest spider between walking and galloping")
    void huntingInstinctSwitchesTheGait() {
        SpiderOptions options = new SpiderOptions();
        spiderNearby(options);
        ItemStack instinct = AppTestSupport.tool(Material.BREEZE_ROD).item;

        use(instinct);
        assertTrue(options.getGallop());
        verify(player).sendActionBar(Component.text("Gallop mode"));

        use(instinct);
        assertFalse(options.getGallop());
        verify(player).sendActionBar(Component.text("Walk mode"));
        verify(world, org.mockito.Mockito.times(2)).playSound(any(Location.class), eq(Sound.BLOCK_SCULK_HIT), eq(1.0f), eq(2.0f));
    }

    @Test
    @DisplayName("the Clinging Charm switches wall climbing, but never for a mimic")
    void theClingingCharmSwitchesClimbing() {
        SpiderOptions options = new SpiderOptions();
        ECSEntity spider = spiderNearby(options);
        ItemStack charm = AppTestSupport.tool(Material.SLIME_BALL).item;

        use(charm);
        assertTrue(options.getClimbing());
        verify(player).sendActionBar(Component.text("Wall climbing on"));

        use(charm);
        assertFalse(options.getClimbing());
        verify(player).sendActionBar(Component.text("Wall climbing off"));
        verify(world, org.mockito.Mockito.times(2)).playSound(any(Location.class), eq(Sound.BLOCK_HONEY_BLOCK_HIT), eq(1.0f), eq(2.0f));

        spider.addComponent(mock(Mimic.class));
        use(charm);
        assertFalse(options.getClimbing());
        verify(player).sendActionBar(Component.text("Mimics keep to the floor"));
    }

    // Brood Guide and Broodcall Whistle

    private LaserPoint laserOf(Player owner) {
        List<LaserPoint> lasers = AppState.getEcs().query(LaserPoint.class);
        for (LaserPoint laser : lasers) {
            if (laser.getHolder().equals(owner.getLocation().toVector())) return laser;
        }
        return null;
    }

    @Test
    @DisplayName("the Brood Guide marks where it points, shown only when staff turn the marker on")
    void theBroodGuideMarksWhereItPoints() {
        ItemStack guide = AppTestSupport.tool(Material.REDSTONE_TORCH).item;
        when(player.getInventory().getItemInMainHand()).thenReturn(guide);
        aimAt(new Vector(0.5, 63.0, 0.5), BlockFace.UP);

        AppState.getEcs().update();
        assertEquals(1, AppState.getEcs().query(LaserPoint.class).size());
        LaserPoint laser = laserOf(player);
        assertEquals(new Vector(0.5, 63.0, 0.5), laser.getPosition());
        assertEquals(world, laser.getWorld());
        assertFalse(laser.isVisible());

        AppState.getMiscOptions().setShowLaser(true);
        aimAt(new Vector(2.5, 63.0, 0.5), BlockFace.UP);
        AppState.getEcs().update();
        assertEquals(1, AppState.getEcs().query(LaserPoint.class).size(), "the same marker moves");
        assertSame(laser, laserOf(player));
        assertEquals(new Vector(2.5, 63.0, 0.5), laser.getPosition());
        assertTrue(laser.isVisible());

        aimAtNothing();
        AppState.getEcs().update();
        assertFalse(laser.isVisible(), "pointing at the sky draws nothing");
        assertEquals(65.62 - 200.0, laser.getPosition().getY(), 1e-6);

        when(player.getInventory().getItemInMainHand()).thenReturn(mock(ItemStack.class));
        AppState.getEcs().update();
        assertTrue(AppState.getEcs().query(LaserPoint.class).isEmpty(), "put away, the marker goes");
    }

    @Test
    @DisplayName("the Broodcall Whistle calls spiders to each staff member holding it")
    void theWhistleCallsSpidersToEachHolder() {
        Player other = app.staff(new Location(world, 30.5, 64.0, 0.5));
        ItemStack whistle = AppTestSupport.tool(Material.CARROT_ON_A_STICK).item;
        when(player.getInventory().getItemInOffHand()).thenReturn(whistle);
        ItemStack otherWhistle = AppTestSupport.tool(Material.CARROT_ON_A_STICK).item;
        when(other.getInventory().getItemInMainHand()).thenReturn(otherWhistle);

        AppState.getEcs().update();
        AppState.getEcs().update();

        assertEquals(2, AppState.getEcs().query(LaserPoint.class).size());
        LaserPoint mine = laserOf(player);
        LaserPoint theirs = laserOf(other);
        assertEquals(new Vector(0.5, 65.62, 0.5), mine.getPosition());
        assertEquals(new Vector(30.5, 65.62, 0.5), theirs.getPosition());
        assertFalse(mine.isVisible());
        assertFalse(theirs.isVisible());
    }
}
