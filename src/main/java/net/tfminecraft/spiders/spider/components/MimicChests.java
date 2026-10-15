package net.tfminecraft.spiders.spider.components;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Dormant mimics are real chest blocks tagged in their block entity data, so they survive restarts. */
public final class MimicChests {
    private MimicChests() {
    }

    private static NamespacedKey key;

    private static NamespacedKey getKey() {
        if (key == null) key = Core.namespacedID("mimic");
        return key;
    }

    public static boolean isMimic(Block block) {
        if (block.getType() != Material.CHEST) return false;
        return isMimic(block.getState(false) instanceof InventoryHolder holder ? holder : null);
    }

    public static boolean isMimic(InventoryHolder holder) {
        return holder instanceof Chest chest && chest.getPersistentDataContainer().has(getKey(), PersistentDataType.BYTE);
    }

    public static boolean canRestIn(Block block) {
        return (block.getType().isAir() || (block.isReplaceable() && !block.isLiquid())) && block.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    /** Place a dormant mimic facing [facing]. */
    public static boolean place(Block block, BlockFace facing) {
        if (!canRestIn(block)) return false;
        BlockData data = Material.CHEST.createBlockData(it -> {
            org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) it;
            chestData.setFacing(facing);
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        });
        block.setBlockData(data, true);
        if (!(block.getState() instanceof Chest state)) return false;
        state.getPersistentDataContainer().set(getKey(), PersistentDataType.BYTE, (byte) 1);
        state.update(true, false);
        return true;
    }

    private static final double SCREECH_RANGE = 80.0;
    private static final long SECOND_SHRIEK_TICKS = 6L;
    private static final List<Pair<Sound, Float>> SCREECH = List.of(
        new Pair<>(Sound.ENTITY_FOX_SCREECH, .7f),
        new Pair<>(Sound.ENTITY_GHAST_SCREAM, 1.6f),
        new Pair<>(Sound.ENTITY_ENDERMAN_SCREAM, .8f),
        new Pair<>(Sound.ENTITY_PHANTOM_DEATH, .6f)
    );

    /**
     * A sound can't play louder than volume 1 (higher only widens its range), so the screech is
     * layered, and each player within range also hears it at full volume from just beside them,
     * towards the mimic, so it stays loud far off and still comes from the right direction.
     */
    private static void screech(World world, Vector centre, float pitch) {
        Location location = centre.toLocation(world);
        for (Pair<Sound, Float> entry : SCREECH) world.playSound(location, entry.first(), SoundCategory.HOSTILE, 5f, entry.second() * pitch);
        for (Player player : world.getPlayers()) {
            Vector ear = player.getEyeLocation().toVector();
            Vector toMimic = centre.clone().subtract(ear);
            double distance = toMimic.length();
            if (distance > SCREECH_RANGE) continue;
            Vector source = distance < 2 ? centre : ear.add(toMimic.multiply(2 / distance));
            for (int i = 0; i < 2; i++) {
                for (Pair<Sound, Float> entry : SCREECH) {
                    player.playSound(source.toLocation(world), entry.first(), SoundCategory.HOSTILE, 1f, entry.second() * pitch);
                }
            }
        }
    }

    private static void screech(World world, Vector centre) {
        screech(world, centre, 1f);
    }

    /** Free floor next to [position], nearest first: the cell it stands over, then its neighbours. */
    public static Block restCellNear(World world, Vector position) {
        RayTraceResult hit = Geometry.raycastGround(world, position, Maths.downVector(), 4.0);
        if (hit == null) return null;
        Vector ground = hit.getHitPosition();
        int y = (int) Math.round(ground.getY());
        int x = (int) Math.floor(position.getX());
        int z = (int) Math.floor(position.getZ());
        List<Block> cells = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block it = world.getBlockAt(x + dx, y, z + dz);
                if (Math.abs(ground.getY() - y) < .02 || it.getX() != x || it.getZ() != z) cells.add(it);
            }
        }
        cells.sort(Comparator.comparingDouble(it -> new Vector(it.getX() + .5, position.getY(), it.getZ() + .5).distanceSquared(position)));
        for (Block cell : cells) {
            if (canRestIn(cell)) return cell;
        }
        return null;
    }

    public static boolean wake(Block block, Player waker) {
        World world = block.getWorld();
        if (!(block.getState(false) instanceof Chest chest)) return false;
        if (AppState.spiderCount() >= AppState.MAX_SPIDERS) {
            Overloads.playSound(world, block.getLocation().toCenterLocation().toVector(), Sound.BLOCK_CHEST_LOCKED, .6f, .7f);
            if (waker != null && StaffAccess.canUse(waker)) waker.sendActionBar(Component.text(
                "The mimic stirs, but " + AppState.MAX_SPIDERS + " creatures are already awake.", NamedTextColor.GRAY));
            return false;
        }

        BlockFace facing = block.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.SOUTH;
        List<ItemStack> loot = new ArrayList<>();
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null) loot.add(item.clone());
        }
        chest.getBlockInventory().clear();
        BlockData blockData = block.getBlockData();
        block.setType(Material.AIR, true);

        Vector floor = new Vector(block.getX() + .5, (double) block.getY(), block.getZ() + .5);
        if (AppState.spawnMimic(world, floor, facing.getDirection(), waker != null ? waker.getUniqueId() : null) == null) return false;

        Vector centre = floor.clone().add(new Vector(0.0, MimicShape.RESTING_HEIGHT, 0.0));
        world.playSound(centre.toLocation(world), Sound.BLOCK_CHEST_OPEN, SoundCategory.HOSTILE, 1.5f, .6f);
        screech(world, centre);
        // a second, higher shriek on its heels
        Bukkit.getScheduler().runTaskLater(Core.getCurrentPlugin(), (Runnable) () -> screech(world, centre, 1.15f), SECOND_SHRIEK_TICKS);
        world.spawnParticle(Particle.BLOCK, centre.toLocation(world), 24, .3, .3, .3, 0.0, blockData);

        // Anything stuffed inside is spat out of its mouth.
        Vector mouth = centre.clone().add(new Vector(0.0, .4, 0.0));
        for (ItemStack item : loot) world.dropItem(mouth.toLocation(world), item, it -> {
            it.setVelocity(facing.getDirection().multiply(.25).add(new Vector(ThreadLocalRandom.current().nextDouble(-.08, .08), .3, ThreadLocalRandom.current().nextDouble(-.08, .08))));
        });
        return true;
    }

    /** Become a chest again at the chosen cell. */
    public static boolean rest(ECSEntity entity, SpiderBody body, Mimic mimic) {
        Block restCell = mimic.getRestCell();
        Block cell = restCell != null && canRestIn(restCell) ? restCell : restCellNear(body.getWorld(), body.getPosition());
        if (cell == null) return false;
        if (!place(cell, mimic.getRestFacing())) return false;
        Overloads.playSound(body.getWorld(), cell.getLocation().toCenterLocation().toVector(), Sound.BLOCK_CHEST_CLOSE, .8f, .7f);
        entity.remove();
        return true;
    }

    /** Server stopping: drop straight back into chest form so the mimic isn't lost. */
    public static boolean restNow(SpiderBody body) {
        Block cell = restCellNear(body.getWorld(), body.getPosition());
        if (cell == null) return false;
        return place(cell, Mimic.cardinalFacing(body.forwardDirection()));
    }
}
