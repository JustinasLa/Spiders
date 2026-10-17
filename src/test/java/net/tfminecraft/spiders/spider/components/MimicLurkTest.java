package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicLurkTest {
    private static final Vector CENTRE = new Vector(.5, 1.5, .5);

    private MimicServer server;
    private MimicDice dice;
    private MimicWorld blocks;
    private Runnable update;
    private Block chest;

    @BeforeEach
    void start() {
        server = new MimicServer();
        dice = new MimicDice();
        blocks = new MimicWorld(0);
        chest = blocks.chest(0, 1, 0, BlockFace.NORTH, true);
        MimicLurk.setup();
        assertEquals(5, server.tasks.get(0).period());
        update = server.timers().get(0);
    }

    @AfterEach
    void stop() {
        dice.close();
        server.close();
    }

    private void run(int updates) {
        for (int i = 0; i < updates; i++) update.run();
    }

    /** A player standing on the floor at ({@code x}, {@code z}), looking along {@code look}. */
    private Player stand(double x, double z, Vector look) {
        Player player = server.player(blocks.world, new Vector(x, 1, z), look);
        blocks.players.add(player);
        return player;
    }

    /** Turn {@code player} to look straight at the chest. */
    private static void watch(Player player) {
        Vector feet = player.getLocation().toVector();
        MimicServer.move(player, feet, CENTRE.clone().subtract(feet.clone().add(new Vector(0, 1.62, 0))));
    }

    /** How often the chest made {@code sound} (a pack sound). */
    private int heard(String sound) {
        return (int) mockingDetails(blocks.world).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("playSound") && ("tfmc_spiders:" + sound).equals(it.getArguments()[1]))
            .count();
    }

    private List<Entity> mouth() {
        return blocks.spawned.stream().filter(it -> !removed(it)).toList();
    }

    private static boolean removed(Entity entity) {
        return mockingDetails(entity).getInvocations().stream().map(Invocation::getMethod).anyMatch(it -> it.getName().equals("remove"));
    }

    private BlockFace facing() {
        return ((org.bukkit.block.data.type.Chest) chest.getBlockData()).getFacing();
    }

    private List<ECSEntity> woken() {
        return AppState.getEcs().getEntities().stream().filter(it -> it.query(Mimic.class) != null).toList();
    }

    @Test
    @DisplayName("turn your back on it and it breathes, turns to face you, then lifts its lid on a mouth until a while has passed")
    void itStirsBehindYourBack() {
        dice.nextFloat = 0f;
        Player player = stand(.5, 4.5, new Vector(0, 0, 1));

        run(3);
        assertEquals(0, heard(MimicTerror.BREATH));
        run(1);
        assertEquals(1, heard(MimicTerror.BREATH), "breathes once its first cooldown (20 ticks) is up");

        run(3);
        assertEquals(BlockFace.NORTH, facing());
        run(1);
        assertEquals(BlockFace.SOUTH, facing(), "turned to face the player after 40 ticks");
        assertEquals(1, heard(MimicTerror.CREAK));
        // turning keeps the block entity and its tag
        assertTrue(MimicChests.isMimic(chest));

        run(3);
        assertFalse(blocks.state(chest).isOpen());
        run(1);
        assertTrue(blocks.state(chest).isOpen(), "peeks after 60 ticks");
        assertEquals(2, heard(MimicTerror.BREATH));
        verify(blocks.world).playSound(new Location(blocks.world, .5, 1.5, .5), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, .5f, .7f);
        // a gullet, two glowing eyes with slit pupils, and a row of teeth
        long glowing = blocks.spawned.stream().filter(it -> mockingDetails(it).getInvocations().stream()
            .anyMatch(call -> call.getMethod().getName().equals("setBrightness"))).count();
        assertEquals(2, glowing);
        assertTrue(mouth().size() >= 10, "mouth parts: " + mouth().size());
        for (Entity part : blocks.spawned) verify((BlockDisplay) part).setPersistent(false);

        // it holds the lid up for three seconds, then lets it drop
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        run(1);
        assertFalse(blocks.state(chest).isOpen());
        assertEquals(1, heard(MimicTerror.CHOMP));
        // the mouth inside goes once the lid has closed
        run(2);
        assertFalse(mouth().isEmpty());
        run(1);
        assertTrue(mouth().isEmpty());
        assertTrue(woken().isEmpty(), "never got close enough to be taken");
        verify(player, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("look back at it and it snaps shut, and the lid won't lift again until it has closed")
    void lookingBackShutsIt() {
        dice.nextFloat = 0f;
        // the next peek is due at once, so only the closing lid holds it back
        dice.range = (origin, bound) -> origin == 20 * 20 ? 0 : origin;
        Player player = stand(.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        int spawned = blocks.spawned.size();

        watch(player);
        run(1);
        assertFalse(blocks.state(chest).isOpen());
        MimicServer.move(player, new Vector(.5, 1, 4.5), new Vector(0, 0, 1));
        run(2);
        assertFalse(blocks.state(chest).isOpen(), "peeked again before the lid had shut");
        assertEquals(spawned, blocks.spawned.size());
        run(1);
        assertTrue(blocks.state(chest).isOpen());
        assertEquals(2 * spawned, blocks.spawned.size());
    }

    @Test
    @DisplayName("walk off while it peeks and it shuts; out of range it just waits")
    void walkingAwayShutsIt() {
        dice.nextFloat = 0f;
        Player player = stand(.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        MimicServer.move(player, new Vector(.5, 1, 9.5), new Vector(0, 0, 1));
        run(1);
        assertFalse(blocks.state(chest).isOpen(), "shut when the player got more than 8 blocks off");

        // a second chest, peeking when everyone leaves its 12-block range
        Player other = stand(40.5, 4.5, new Vector(0, 0, 1));
        Block second = blocks.chest(40, 1, 0, BlockFace.SOUTH, true);
        // found at the next scan (the 17th update), and peeking twelve updates later
        run(15);
        assertTrue(blocks.state(second).isOpen());
        MimicServer.move(other, new Vector(40.5, 1, 30), new Vector(0, 0, 1));
        run(1);
        assertFalse(blocks.state(second).isOpen());
        run(20);
        assertFalse(blocks.state(second).isOpen());
    }

    @ParameterizedTest
    @EnumSource(value = BlockFace.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    @DisplayName("its teeth show on the side it turned to face")
    void theMouthFacesTheWayTheChestDoes(BlockFace side) {
        dice.nextFloat = 0f;
        // already facing the player, so it doesn't need to turn
        blocks.chest(0, 1, 0, side, true);
        chest = blocks.block(0, 1, 0);
        Vector away = side.getDirection();
        stand(.5 + 4 * away.getX(), .5 + 4 * away.getZ(), away);
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        assertEquals(side, facing());
        assertEquals(0, heard(MimicTerror.CREAK), "no need to turn");

        Vector3f teeth = new Vector3f();
        int count = 0;
        for (Entity part : blocks.spawned) {
            BlockDisplay display = (BlockDisplay) part;
            ArgumentCaptor<BlockData> block = ArgumentCaptor.forClass(BlockData.class);
            verify(display).setBlock(block.capture());
            if (block.getValue().getMaterial() != Material.BONE_BLOCK) continue;
            ArgumentCaptor<Matrix4f> transform = ArgumentCaptor.forClass(Matrix4f.class);
            verify(display).setTransformationMatrix(transform.capture());
            teeth.add(transform.getValue().transformPosition(new Vector3f(.5f, 0f, .5f)));
            count++;
        }
        teeth.div(count).sub(.5f, 0f, .5f);
        assertTrue(teeth.x * away.getX() + teeth.z * away.getZ() > .3, "teeth sit at " + teeth + " facing " + side);
    }

    @Test
    @DisplayName("a chest that has lost its facing still peeks, and doesn't try to turn")
    void unfacedChest() {
        dice.nextFloat = 0f;
        BlockData unfaced = mock(BlockData.class);
        when(unfaced.getMaterial()).thenReturn(Material.CHEST);
        chest.setBlockData(unfaced, false);
        stand(.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        assertEquals(0, heard(MimicTerror.CREAK));
    }

    @Test
    @DisplayName("right above it, or already faced, it has nowhere to turn")
    void nothingToTurnTowards() {
        dice.nextFloat = 0f;
        // directly above, looking up: unseen, but no direction to face
        stand(.5, .5, new Vector(0, 1, 0));
        // keep it from peeking, to watch the turning alone
        dice.range = (origin, bound) -> origin == 60 ? 10_000 : origin;
        run(20);
        assertEquals(0, heard(MimicTerror.CREAK));
        assertEquals(BlockFace.NORTH, facing());
    }

    @Test
    @DisplayName("it only peeks on a roll, and only turns for someone within ten blocks")
    void chanceAndRange() {
        // the dice say no: a back turned at four blocks gets no peek
        stand(.5, 4.5, new Vector(0, 0, 1));
        run(20);
        assertFalse(blocks.state(chest).isOpen());
        // eleven blocks off, it neither peeks nor turns
        dice.nextFloat = 0f;
        Block second = blocks.chest(60, 1, 0, BlockFace.NORTH, true);
        stand(60.5, 11.5, new Vector(0, 0, 1));
        // a spectator beside it doesn't count
        Player ghost = stand(60.5, 1.5, new Vector(0, 0, 1));
        when(ghost.getGameMode()).thenReturn(GameMode.SPECTATOR);
        run(40);
        assertFalse(blocks.state(second).isOpen());
        assertEquals(BlockFace.NORTH, ((org.bukkit.block.data.type.Chest) second.getBlockData()).getFacing());
        verify(blocks.state(chest)).open();
    }

    @Test
    @DisplayName("it finds nothing in chunks that aren't loaded")
    void unloadedChunksAreNotScanned() {
        dice.nextFloat = 0f;
        stand(.5, 4.5, new Vector(0, 0, 1));
        blocks.chunksLoaded = false;
        run(1);
        blocks.chunksLoaded = true;
        run(7 + 11);
        assertFalse(blocks.state(chest).isOpen(), "found before the next scan");
        run(1);
        assertTrue(blocks.state(chest).isOpen());
    }

    @Test
    @DisplayName("stand beside it for eight seconds and it takes you, growling two seconds before")
    void lingeringWakesIt() {
        Player player = stand(.5, 2, new Vector(0, 0, 1));
        watch(player);
        run(23);
        assertEquals(0, heard(MimicTerror.GROWL));
        run(1);
        assertEquals(1, heard(MimicTerror.GROWL));
        run(7);
        assertTrue(woken().isEmpty());
        assertTrue(MimicChests.isMimic(chest));
        run(1);
        assertEquals(1, woken().size());
        assertEquals(player.getUniqueId(), woken().get(0).query(Mimic.class).getWaker());
        assertEquals(Material.AIR, chest.getType());
    }

    @Test
    @DisplayName("adventurers are taken too, but not creative players, and stepping away resets its patience")
    void whoItTakes() {
        Player player = stand(.5, 2, new Vector(0, 0, 1));
        watch(player);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        run(40);
        assertTrue(woken().isEmpty());

        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        run(31);
        // a step back, out of reach, and it starts over
        MimicServer.move(player, new Vector(.5, 1, 4), new Vector(0, 0, -1));
        run(1);
        MimicServer.move(player, new Vector(.5, 1, 2), new Vector(0, 0, -1));
        watch(player);
        run(31);
        assertTrue(woken().isEmpty());
        run(1);
        assertEquals(1, woken().size());
    }

    @Test
    @DisplayName("only dormant mimics near someone who can be frightened are watched")
    void scanFindsOnlyDormantMimicsNearPrey() {
        dice.nextFloat = 0f;
        Block plain = blocks.chest(2, 1, 0, BlockFace.NORTH, false);
        Block hollow = blocks.chest(3, 1, 0, BlockFace.NORTH, true);
        blocks.setState(hollow, mock(BlockState.class));
        blocks.set(4, 1, 0, MimicWorld.GROUND);
        Player spectator = stand(.5, 4.5, new Vector(0, 0, 1));
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        run(20);
        assertFalse(blocks.state(chest).isOpen(), "nobody to frighten");

        when(spectator.getGameMode()).thenReturn(GameMode.SURVIVAL);
        // found at the next scan, five updates on, and peeking eleven after that
        run(5 + 11);
        assertTrue(blocks.state(chest).isOpen());
        assertFalse(blocks.state(plain).isOpen());
    }

    @Test
    @DisplayName("a chest whose chunk unloads, that is broken, or that nobody is near any more is forgotten, mouth and all")
    void forgottenChests() {
        dice.nextFloat = 0f;
        Player player = stand(.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertFalse(mouth().isEmpty());
        blocks.chunksLoaded = false;
        run(1);
        assertTrue(mouth().isEmpty(), "chunk unloaded");

        blocks.chunksLoaded = true;
        blocks.state(chest).close();
        run(8 * 3);
        assertFalse(mouth().isEmpty(), "found again at the next scan and peeked");
        blocks.set(0, 1, 0, MimicWorld.AIR);
        run(1);
        assertTrue(mouth().isEmpty(), "broken");

        Block other = blocks.chest(0, 1, 0, BlockFace.SOUTH, true);
        run(16);
        assertFalse(mouth().isEmpty());
        // everyone logs off: the next scan drops it
        server.players.clear();
        blocks.players.clear();
        run(8);
        assertTrue(mouth().isEmpty());
        assertTrue(MimicChests.isMimic(other));
        verify(player, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("a chest that wakes or is removed takes its mouth with it")
    void forgetClearsTheMouth() {
        dice.nextFloat = 0f;
        stand(.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertFalse(mouth().isEmpty());
        MimicLurk.forget(chest);
        assertTrue(mouth().isEmpty());
        MimicLurk.forget(chest);
    }

    @Test
    @DisplayName("on shutdown open lids are closed and every mouth is cleared")
    void shutdown() {
        dice.nextFloat = 0f;
        stand(.5, 4.5, new Vector(0, 0, 1));
        Block quiet = blocks.chest(40, 1, 40, BlockFace.NORTH, true);
        Player far = stand(40.5, 45, new Vector(0, 0, 1));
        MimicServer.move(far, new Vector(40.5, 1, 60), new Vector(0, 0, 1));
        Block broken = blocks.chest(-40, 1, 0, BlockFace.NORTH, true);
        stand(-39.5, 4.5, new Vector(0, 0, 1));
        run(12);
        assertTrue(blocks.state(chest).isOpen());
        assertTrue(blocks.state(broken).isOpen());
        // broken mid-peek, just before the server stops
        BlockState gone = mock(BlockState.class);
        blocks.setState(broken, gone);

        Core.shutdownCoreUtils(server.plugin);
        assertFalse(blocks.state(chest).isOpen());
        assertTrue(mouth().isEmpty());
        verify(blocks.state(quiet), never()).close();
        assertTrue(server.warnings.isEmpty());
        // nothing is watched any more
        run(1);
        assertFalse(blocks.state(chest).isOpen());
    }
}
