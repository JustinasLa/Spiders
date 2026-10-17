package net.tfminecraft.spiders.spider.components;

import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicChestsTest {
    private MimicServer server;
    private MimicDice dice;
    /** Ground up to y = 0, so the floor is at y = 1. */
    private MimicWorld blocks;

    @BeforeEach
    void start() {
        server = new MimicServer();
        dice = new MimicDice();
        blocks = new MimicWorld(0);
    }

    @AfterEach
    void stop() {
        dice.close();
        server.close();
    }

    private static ItemStack item() {
        ItemStack item = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(item.clone()).thenReturn(copy);
        return item;
    }

    private Player staff() {
        Player player = server.player(blocks.world, new Vector(3, 1, 0), new Vector(-1, 0, 0));
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        return player;
    }

    private static List<ECSEntity> mimics() {
        return AppState.getEcs().getEntities().stream().filter(it -> it.query(Mimic.class) != null).toList();
    }

    @Test
    @DisplayName("only a chest carrying the mimic tag is a mimic")
    void onlyTaggedChestsAreMimics() {
        Block mimic = blocks.chest(0, 1, 0, BlockFace.NORTH, true);
        Block chest = blocks.chest(1, 1, 0, BlockFace.NORTH, false);
        assertTrue(MimicChests.isMimic(mimic));
        assertTrue(MimicChests.isMimic((InventoryHolder) blocks.state(mimic)));
        assertFalse(MimicChests.isMimic(chest));
        assertFalse(MimicChests.isMimic(blocks.block(2, 1, 0)), "air");
        assertFalse(MimicChests.isMimic((InventoryHolder) null));
        assertFalse(MimicChests.isMimic(mock(InventoryHolder.class)), "a barrel or a minecart");
        // a chest block whose block entity is missing or not a container
        Block broken = blocks.chest(3, 1, 0, BlockFace.NORTH, true);
        blocks.setState(broken, mock(BlockState.class));
        assertFalse(MimicChests.isMimic(broken));
    }

    @Test
    @DisplayName("a mimic can sit in air or a plant on solid ground, but not in water, in a wall or in mid-air")
    void whereAMimicCanSit() {
        assertTrue(MimicChests.canRestIn(blocks.block(0, 1, 0)));
        blocks.set(1, 1, 0, MimicWorld.PLANT);
        assertTrue(MimicChests.canRestIn(blocks.block(1, 1, 0)));
        blocks.set(2, 1, 0, MimicWorld.WATER);
        assertFalse(MimicChests.canRestIn(blocks.block(2, 1, 0)));
        assertFalse(MimicChests.canRestIn(blocks.block(0, 0, 0)), "inside the ground");
        assertFalse(MimicChests.canRestIn(blocks.block(0, 5, 0)), "floating");
    }

    @Test
    @DisplayName("placing a dormant mimic makes a single tagged chest facing the given way")
    void placeMakesATaggedChest() {
        Block block = blocks.block(0, 1, 0);
        assertTrue(MimicChests.place(block, BlockFace.EAST));
        assertEquals(Material.CHEST, block.getType());
        org.bukkit.block.data.type.Chest data = (org.bukkit.block.data.type.Chest) block.getBlockData();
        assertEquals(BlockFace.EAST, data.getFacing());
        verify(data).setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        assertTrue(MimicChests.isMimic(block));
        verify(blocks.state(block)).update(true, false);

        assertFalse(MimicChests.place(blocks.block(0, 0, 0), BlockFace.EAST), "into the ground");
        assertEquals(MimicWorld.GROUND, blocks.block(0, 0, 0).getType());
    }

    @Test
    @DisplayName("placing fails if the server doesn't make a chest block entity")
    void placeWithoutABlockEntity() {
        Block block = mock(Block.class);
        Block below = blocks.block(0, 0, 0);
        when(block.getType()).thenReturn(MimicWorld.AIR);
        when(block.getRelative(BlockFace.DOWN)).thenReturn(below);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        assertFalse(MimicChests.place(block, BlockFace.NORTH));
        verify(block).setBlockData(any(BlockData.class), eq(true));
        verify(state, never()).update(anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("it sits down in the cell it stands over, or the nearest free neighbour")
    void restCellNearPicksTheNearestFreeFloor() {
        Vector standing = new Vector(.5, 1 + MimicShape.RESTING_HEIGHT, .8);
        assertSame(blocks.block(0, 1, 0), MimicChests.restCellNear(blocks.world, standing));
        blocks.set(0, 1, 0, MimicWorld.WATER);
        assertSame(blocks.block(0, 1, 1), MimicChests.restCellNear(blocks.world, standing), "the neighbour it leans towards");

        // standing half-sunk in a block, it won't sit inside it: only the neighbouring cells count
        Vector sunk = new Vector(5.5, .5, 5.5);
        Block cell = MimicChests.restCellNear(blocks.world, sunk);
        assertNotEquals(blocks.block(5, 1, 5), cell);
        assertEquals(1, cell.getY());
        assertTrue(Math.abs(cell.getX() - 5) <= 1 && Math.abs(cell.getZ() - 5) <= 1);
    }

    @Test
    @DisplayName("nowhere to sit: high in the air, or boxed in")
    void restCellNearFindsNothing() {
        assertNull(MimicChests.restCellNear(blocks.world, new Vector(.5, 10, .5)));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) blocks.set(10 + x, 1, 10 + z, MimicWorld.WATER);
        assertNull(MimicChests.restCellNear(blocks.world, new Vector(10.5, 1.5, 10.5)));
    }

    @Test
    @DisplayName("woken by a player, it empties itself, becomes a creature facing the chest's way and spits the loot as it springs")
    void wokenByAPlayer() {
        Block block = blocks.chest(0, 1, 0, BlockFace.EAST, true);
        Chest chest = blocks.state(block);
        ItemStack first = item();
        ItemStack second = item();
        chest.getBlockInventory().setContents(new ItemStack[]{first, null, second});
        chest.open();
        Player player = server.player(blocks.world, new Vector(3, 1, 0), new Vector(-1, 0, 0));

        assertTrue(MimicChests.wake(block, player));

        assertFalse(chest.isOpen());
        assertEquals(Material.AIR, block.getType());
        assertTrue(java.util.Arrays.stream(chest.getBlockInventory().getContents()).allMatch(java.util.Objects::isNull), "emptied");
        List<ECSEntity> mimics = mimics();
        assertEquals(1, mimics.size());
        Mimic mimic = mimics.get(0).query(Mimic.class);
        assertEquals(player.getUniqueId(), mimic.getWaker());
        assertEquals(MimicState.AMBUSH, mimic.getState());
        SpiderBody body = mimics.get(0).query(SpiderBody.class);
        assertEquals(new Vector(.5, 1 + MimicShape.RESTING_HEIGHT, .5), body.getPosition());
        assertEquals(1.0, body.forwardDirection().getX(), 1e-6);

        // the lid only lifts a crack, like any chest's
        verify(blocks.world).playSound(new Location(blocks.world, .5, 1 + MimicShape.RESTING_HEIGHT, .5), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, .5f, .85f);
        verify(blocks.world, never()).playSound(any(Location.class), eq("tfmc_spiders:" + MimicTerror.SCREECH), any(SoundCategory.class), anyFloat(), anyFloat());
        // the loot comes out as it springs, not before
        assertTrue(blocks.dropped.isEmpty());
        assertEquals(List.of((long) Mimic.AMBUSH_TICKS), server.pending().stream().map(MimicServer.Task::delay).toList());
        server.runLater();
        assertEquals(List.of(first.clone(), second.clone()), blocks.dropped.stream().map(Item::getItemStack).toList());
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(blocks.dropped.get(0)).setVelocity(velocity.capture());
        // east at a quarter block a tick, up, and the low end of its scatter
        assertEquals(new Vector(.25 - .08, .3, -.08), velocity.getValue());
        assertEquals(new Vector(.5, 1 + MimicShape.RESTING_HEIGHT + .4, .5), blocks.dropped.get(0).getLocation().toVector());
        verify(blocks.world).spawnParticle(eq(Particle.BLOCK), any(Location.class), eq(24), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(BlockData.class));
    }

    @Test
    @DisplayName("woken with nobody to blame, it screams twice, spits its loot at once and just stands up")
    void wokenByNobody() {
        Block block = blocks.chest(0, 1, 0, BlockFace.EAST, true);
        // block data that has lost its facing: it faces south
        BlockData unfaced = mock(BlockData.class);
        when(unfaced.getMaterial()).thenReturn(Material.CHEST);
        block.setBlockData(unfaced, false);
        blocks.state(block).getBlockInventory().setContents(new ItemStack[]{item()});

        assertTrue(MimicChests.wake(block, null));

        Mimic mimic = mimics().get(0).query(Mimic.class);
        assertNull(mimic.getWaker());
        assertEquals(MimicState.WAKING, mimic.getState());
        Location centre = new Location(blocks.world, .5, 1 + MimicShape.RESTING_HEIGHT, .5);
        verify(blocks.world).playSound(centre, Sound.BLOCK_CHEST_OPEN, SoundCategory.HOSTILE, 1.5f, .6f);
        verify(blocks.world).playSound(centre, "tfmc_spiders:" + MimicTerror.SCREECH, SoundCategory.HOSTILE, 5f, 1f);
        assertEquals(1, blocks.dropped.size(), "spat out at once");
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(blocks.dropped.get(0)).setVelocity(velocity.capture());
        // south, plus the low end of its scatter
        assertEquals(.25 - .08, velocity.getValue().getZ(), 1e-9);

        assertEquals(List.of(6L), server.pending().stream().map(MimicServer.Task::delay).toList());
        server.runLater();
        verify(blocks.world).playSound(centre, "tfmc_spiders:" + MimicTerror.SCREECH, SoundCategory.HOSTILE, 5f, 1.15f);
    }

    @Test
    @DisplayName("with the creature limit reached it only rattles, and tells staff why")
    void limitReached() {
        ECS ecs = AppState.getEcs();
        for (int i = 0; i < AppState.MAX_SPIDERS; i++) ecs.spawn(SpiderBody.create(blocks.world, new Vector(100, 1, 100)));
        Block block = blocks.chest(0, 1, 0, BlockFace.EAST, true);
        Player staff = staff();
        Player regular = server.player(blocks.world, new Vector(3, 1, 0), new Vector(-1, 0, 0));

        assertFalse(MimicChests.wake(block, staff));
        assertFalse(MimicChests.wake(block, regular));
        assertFalse(MimicChests.wake(block, null));

        verify(staff).sendActionBar(any(Component.class));
        verify(regular, never()).sendActionBar(any(Component.class));
        verify(blocks.world, org.mockito.Mockito.times(3)).playSound(new Location(blocks.world, .5, 1.5, .5), Sound.BLOCK_CHEST_LOCKED, .6f, .7f);
        assertTrue(MimicChests.isMimic(block), "still a dormant chest");
        assertEquals(AppState.MAX_SPIDERS, AppState.spiderCount());
    }

    @Test
    @DisplayName("only a chest block entity can wake")
    void onlyChestsWake() {
        assertFalse(MimicChests.wake(blocks.block(0, 1, 0), null));
        assertTrue(mimics().isEmpty());
    }

    @Test
    @DisplayName("if the creature can't be spawned after all, nothing is scheduled")
    void spawnFails() {
        Block block = blocks.chest(0, 1, 0, BlockFace.EAST, true);
        try (MockedStatic<AppState> app = mockStatic(AppState.class, CALLS_REAL_METHODS)) {
            app.when(() -> AppState.spawnMimic(any(), any(), any(), any())).thenReturn(null);
            assertFalse(MimicChests.wake(block, null));
        }
        assertTrue(server.pending().isEmpty());
        assertTrue(blocks.dropped.isEmpty());
    }

    @Test
    @DisplayName("resting, it becomes a chest in the cell it chose, facing the way it chose, and the creature is gone")
    void restInTheChosenCell() {
        ECSEntity entity = new ECSEntity();
        SpiderBody body = SpiderBody.create(blocks.world, new Vector(.5, 1.5, .5));
        Mimic mimic = mock(Mimic.class);
        Block cell = blocks.block(2, 1, 0);
        when(mimic.getRestCell()).thenReturn(cell);
        when(mimic.getRestFacing()).thenReturn(BlockFace.WEST);

        assertTrue(MimicChests.rest(entity, body, mimic));
        assertTrue(MimicChests.isMimic(blocks.block(2, 1, 0)));
        assertEquals(BlockFace.WEST, ((org.bukkit.block.data.type.Chest) blocks.block(2, 1, 0).getBlockData()).getFacing());
        verify(blocks.world).playSound(new Location(blocks.world, 2.5, 1.5, .5), Sound.BLOCK_CHEST_CLOSE, .8f, .7f);
        assertTrue(entity.getScheduledForRemoval());
    }

    @Test
    @DisplayName("if its cell was taken, or it never chose one, it rests where it stands")
    void restFallsBackToWhereItStands() {
        SpiderBody body = SpiderBody.create(blocks.world, new Vector(.5, 1.5, .5));
        Mimic taken = mock(Mimic.class);
        blocks.set(2, 1, 0, MimicWorld.WATER);
        Block water = blocks.block(2, 1, 0);
        when(taken.getRestCell()).thenReturn(water);
        when(taken.getRestFacing()).thenReturn(BlockFace.SOUTH);
        assertTrue(MimicChests.rest(new ECSEntity(), body, taken));
        assertTrue(MimicChests.isMimic(blocks.block(0, 1, 0)));

        SpiderBody elsewhere = SpiderBody.create(blocks.world, new Vector(5.5, 1.5, 5.5));
        Mimic undecided = mock(Mimic.class);
        when(undecided.getRestFacing()).thenReturn(BlockFace.SOUTH);
        assertTrue(MimicChests.rest(new ECSEntity(), elsewhere, undecided));
        assertTrue(MimicChests.isMimic(blocks.block(5, 1, 5)));
    }

    @Test
    @DisplayName("with nowhere to rest, or a cell that won't take a chest, it stays a creature")
    void restFails() {
        ECSEntity entity = new ECSEntity();
        Mimic undecided = mock(Mimic.class);
        assertFalse(MimicChests.rest(entity, SpiderBody.create(blocks.world, new Vector(.5, 20, .5)), undecided));

        Block odd = mock(Block.class);
        Block ground = blocks.block(0, 0, 0);
        when(odd.getType()).thenReturn(MimicWorld.AIR);
        when(odd.getRelative(BlockFace.DOWN)).thenReturn(ground);
        when(odd.getState()).thenReturn(mock(BlockState.class));
        Mimic stubborn = mock(Mimic.class);
        when(stubborn.getRestCell()).thenReturn(odd);
        assertFalse(MimicChests.rest(entity, SpiderBody.create(blocks.world, new Vector(.5, 20, .5)), stubborn));
        assertFalse(entity.getScheduledForRemoval());
    }

    @Test
    @DisplayName("at shutdown it drops straight into a chest facing its way, if there is floor beneath it")
    void restNow() {
        SpiderBody body = SpiderBody.create(blocks.world, new Vector(.5, 1.5, .5), new Quaternionf().rotationY((float) Math.toRadians(-90)));
        assertTrue(MimicChests.restNow(body));
        assertEquals(BlockFace.WEST, ((org.bukkit.block.data.type.Chest) blocks.block(0, 1, 0).getBlockData()).getFacing());
        assertTrue(MimicChests.isMimic(blocks.block(0, 1, 0)));

        assertFalse(MimicChests.restNow(SpiderBody.create(blocks.world, new Vector(.5, 20, .5))));
    }
}
