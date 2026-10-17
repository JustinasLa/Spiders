package net.tfminecraft.spiders.spider.components;

import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A block world for dormant mimics: solid ground at and below {@code floor}, air above, and chests whose
 * block entities carry the mimic tag. Blocks are cached, so the same coordinates are the same block.
 */
final class MimicWorld {
    /** Materials need a server to answer isAir/isSolid, so these stand in for air, ground and a plant. */
    static final Material AIR = material(true, false);
    static final Material GROUND = material(false, true);
    static final Material PLANT = material(false, false);
    static final Material WATER = material(false, false);

    final World world = mock(World.class);
    final List<Player> players = new ArrayList<>();
    final List<Entity> spawned = new ArrayList<>();
    final List<Item> dropped = new ArrayList<>();
    boolean chunksLoaded = true;
    boolean chunksGenerated = true;

    private final int floor;
    private final Map<List<Integer>, Block> blocks = new HashMap<>();
    private final Map<Block, Material> types = new HashMap<>();
    private final Map<Block, BlockData> data = new HashMap<>();
    private final Map<Block, BlockState> states = new HashMap<>();
    private final Map<List<Integer>, Chunk> chunks = new HashMap<>();
    private final Room room;

    MimicWorld(int floor) {
        this.floor = floor;
        this.room = new Room((x, y, z) -> type(block(x, y, z)) == GROUND);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location at = call.getArgument(0);
            return block(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        });
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
            .thenAnswer(call -> room.rayTrace(call.<Location>getArgument(0).toVector(), call.getArgument(1), call.<Double>getArgument(2)));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call -> chunksLoaded);
        when(world.isChunkGenerated(anyInt(), anyInt())).thenAnswer(call -> chunksGenerated);
        when(world.getPlayers()).thenAnswer(call -> List.copyOf(players));
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
        when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(call -> {
            Entity entity = (Entity) mock(call.<Class<?>>getArgument(1));
            call.<Consumer<Entity>>getArgument(2).accept(entity);
            spawned.add(entity);
            return entity;
        });
        when(world.dropItem(any(Location.class), any(ItemStack.class), any(Consumer.class))).thenAnswer(call -> {
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(call.getArgument(1));
            when(item.getLocation()).thenReturn(call.<Location>getArgument(0).clone());
            call.<Consumer<Item>>getArgument(2).accept(item);
            dropped.add(item);
            return item;
        });
    }

    private static Material material(boolean air, boolean solid) {
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(air);
        when(material.isSolid()).thenReturn(solid);
        return material;
    }

    Material type(Block block) {
        Material type = types.get(block);
        if (type != null) return type;
        return block.getY() <= floor ? GROUND : AIR;
    }

    void set(int x, int y, int z, Material type) {
        Block block = block(x, y, z);
        types.put(block, type);
        data.remove(block);
        states.remove(block);
    }

    /** A chest facing {@code facing}; tagged as a dormant mimic if {@code mimic}. */
    Block chest(int x, int y, int z, BlockFace facing, boolean mimic) {
        Block block = block(x, y, z);
        BlockData chest = MimicServer.blockData(Material.CHEST);
        ((org.bukkit.block.data.type.Chest) chest).setFacing(facing);
        block.setBlockData(chest, true);
        if (mimic) state(block).getPersistentDataContainer().set(tagKey(), PersistentDataType.BYTE, (byte) 1);
        return block;
    }

    /** Any key will do: the mimic tag's key is cached from whichever plugin asked first. */
    private static org.bukkit.NamespacedKey tagKey() {
        return new org.bukkit.NamespacedKey("tfmcspiders", "mimic");
    }

    /** The chest block entity at {@code block}. */
    Chest state(Block block) {
        return (Chest) block.getState(false);
    }

    /** Replace the block entity at {@code block}, e.g. with one that isn't a chest. */
    void setState(Block block, BlockState state) {
        states.put(block, state);
    }

    boolean isTagged(Block block) {
        return block.getState(false) instanceof Chest chest
            && chest.getPersistentDataContainer().has(tagKey(), PersistentDataType.BYTE);
    }

    Block block(int x, int y, int z) {
        return blocks.computeIfAbsent(List.of(x, y, z), key -> newBlock(x, y, z));
    }

    private Block newBlock(int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenAnswer(call -> new Location(world, x, y, z));
        when(block.getType()).thenAnswer(call -> type(block));
        when(block.isReplaceable()).thenAnswer(call -> {
            Material type = type(block);
            return type == AIR || type == PLANT || type == WATER;
        });
        when(block.isLiquid()).thenAnswer(call -> type(block) == WATER);
        when(block.getRelative(any(BlockFace.class))).thenAnswer(call -> {
            BlockFace face = call.getArgument(0);
            return block(x + face.getModX(), y + face.getModY(), z + face.getModZ());
        });
        doAnswer(call -> {
            set(x, y, z, call.getArgument(0));
            return null;
        }).when(block).setType(any(Material.class), anyBoolean());
        doAnswer(call -> {
            BlockData next = call.getArgument(0);
            if (type(block) != next.getMaterial()) {
                types.put(block, next.getMaterial());
                states.remove(block);
            }
            data.put(block, next);
            return null;
        }).when(block).setBlockData(any(BlockData.class), anyBoolean());
        when(block.getBlockData()).thenAnswer(call -> data.computeIfAbsent(block, it -> MimicServer.blockData(type(block))));
        when(block.getState()).thenAnswer(call -> stateOf(block));
        when(block.getState(anyBoolean())).thenAnswer(call -> stateOf(block));
        return block;
    }

    private BlockState stateOf(Block block) {
        return states.computeIfAbsent(block, it -> type(block) == Material.CHEST ? newChest(block) : plainState(block));
    }

    private static BlockState plainState(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        return state;
    }

    private static Chest newChest(Block block) {
        Chest chest = mock(Chest.class);
        boolean[] tagged = {false};
        boolean[] open = {false};
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(org.bukkit.NamespacedKey.class), eq(PersistentDataType.BYTE))).thenAnswer(call -> tagged[0]);
        doAnswer(call -> {
            tagged[0] = true;
            return null;
        }).when(pdc).set(any(org.bukkit.NamespacedKey.class), eq(PersistentDataType.BYTE), anyByte());
        when(chest.getPersistentDataContainer()).thenReturn(pdc);
        when(chest.getBlock()).thenReturn(block);
        when(chest.isOpen()).thenAnswer(call -> open[0]);
        doAnswer(call -> {
            open[0] = true;
            return null;
        }).when(chest).open();
        doAnswer(call -> {
            open[0] = false;
            return null;
        }).when(chest).close();
        when(chest.update(anyBoolean(), anyBoolean())).thenReturn(true);
        Inventory inventory = mock(Inventory.class);
        ItemStack[][] contents = {new ItemStack[27]};
        when(inventory.getContents()).thenAnswer(call -> contents[0].clone());
        doAnswer(call -> {
            contents[0] = call.getArgument(0);
            return null;
        }).when(inventory).setContents(any(ItemStack[].class));
        doAnswer(call -> {
            contents[0] = new ItemStack[27];
            return null;
        }).when(inventory).clear();
        when(inventory.getHolder(anyBoolean())).thenReturn(chest);
        when(chest.getBlockInventory()).thenReturn(inventory);
        when(chest.getInventory()).thenReturn(inventory);
        return chest;
    }

    private Chunk chunk(int cx, int cz) {
        return chunks.computeIfAbsent(List.of(cx, cz), key -> {
            Chunk chunk = mock(Chunk.class);
            when(chunk.getX()).thenReturn(cx);
            when(chunk.getZ()).thenReturn(cz);
            when(chunk.getTileEntities(any(Predicate.class), anyBoolean())).thenAnswer(call -> {
                Predicate<Block> test = call.getArgument(0);
                List<BlockState> out = new ArrayList<>();
                for (Block block : List.copyOf(blocks.values())) {
                    if (block.getX() >> 4 != cx || block.getZ() >> 4 != cz) continue;
                    if (!types.containsKey(block) && !states.containsKey(block)) continue;
                    if (test.test(block)) out.add(stateOf(block));
                }
                return out;
            });
            return chunk;
        });
    }
}
