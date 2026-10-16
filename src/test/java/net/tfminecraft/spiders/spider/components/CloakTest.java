package net.tfminecraft.spiders.spider.components;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloakTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    private final Vector piece = new Vector(0.5, 65.0, 0.5);

    private Player player(Vector eye, World world) {
        Player player = mock(Player.class);
        when(player.getEyeLocation()).thenAnswer(call -> eye.toLocation(world));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        return player;
    }

    private RayTraceResult hit(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(data);
        return new RayTraceResult(new Vector(), block, BlockFace.UP);
    }

    /**
     * Looking along +z past the piece there is water over a sand bed; any other direction finds stone.
     * Only a fluid-aware cast sees the water.
     */
    private World world(Function<World, List<Player>> players) {
        World world = mock(World.class);
        List<Player> list = players.apply(world);
        when(world.getPlayers()).thenReturn(list);
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
            any(FluidCollisionMode.class), eq(true))).thenAnswer(call -> {
            Vector direction = call.<Vector>getArgument(1).clone().normalize();
            boolean fluids = call.<FluidCollisionMode>getArgument(3) == FluidCollisionMode.ALWAYS;
            if (direction.getZ() > .9) return hit(fluids ? Material.WATER : Material.SAND);
            return hit(Material.STONE);
        });
        return world;
    }

    private static Material material(BlockData data) {
        return data == null ? null : data.getMaterial();
    }

    @Test
    @DisplayName("sees the water behind the spider from the nearest player, not the bed under it")
    void seesTheWaterBehindTheSpiderFromTheNearestPlayerNotTheBedUnderIt() {
        // the far player is listed first and looks along -x; the near one looks along +z through the piece
        World world = world(w -> List.of(player(new Vector(200.5, 65.0, 0.5), w), player(new Vector(0.5, 65.0, -5.0), w)));
        assertEquals(Material.WATER, material(Cloak.shroudSees(world, piece)));
    }

    @Test
    @DisplayName("without a nearby player it copies the ground")
    void withoutANearbyPlayerItCopiesTheGround() {
        World world = world(w -> List.of(player(new Vector(0.5, 65.0, -500.0), w)));
        assertEquals(Material.STONE, material(Cloak.shroudSees(world, piece)));
    }

    @Test
    @DisplayName("spectators do not steer the shroud")
    void spectatorsDoNotSteerTheShroud() {
        World world = world(w -> {
            Player spectator = player(new Vector(0.5, 65.0, -5.0), w);
            when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
            return List.of(spectator);
        });
        assertEquals(Material.STONE, material(Cloak.shroudSees(world, piece)));
    }

    @Test
    @DisplayName("the rider and vanished staff do not steer the shroud")
    void theRiderAndVanishedStaffDoNotSteerTheShroud() {
        Player[] rider = new Player[1];
        World world = world(w -> {
            rider[0] = player(new Vector(0.5, 65.0, -5.0), w);
            Player vanished = player(new Vector(0.5, 65.0, -6.0), w);
            MetadataValue flag = mock(MetadataValue.class);
            when(flag.asBoolean()).thenReturn(true);
            when(vanished.getMetadata("vanished")).thenReturn(List.of(flag));
            return List.of(rider[0], vanished);
        });
        assertEquals(Material.STONE, material(Cloak.shroudSees(world, piece, rider[0])));
        assertEquals(Material.WATER, material(Cloak.shroudSees(world, piece, null)), "a bystander would see the water");
    }

    @Test
    @DisplayName("the nearest player steers it even when listed first, and a cleared vanish flag counts")
    void theNearestPlayerSteersItEvenWhenListedFirst() {
        World world = world(w -> {
            Player near = player(new Vector(0.5, 65.0, -5.0), w);
            MetadataValue cleared = mock(MetadataValue.class);
            when(near.getMetadata("vanished")).thenReturn(List.of(cleared));
            return List.of(near, player(new Vector(200.5, 65.0, 0.5), w));
        });
        assertEquals(Material.WATER, material(Cloak.shroudSees(world, piece)));
    }

    @Test
    @DisplayName("a player standing in the piece has no line of sight, so it copies the ground")
    void aPlayerStandingInThePieceHasNoLineOfSight() {
        World world = world(w -> List.of(player(piece.clone(), w)));
        assertEquals(Material.STONE, material(Cloak.shroudSees(world, piece)));
        verify(world).rayTraceBlocks(any(Location.class), eq(new Vector(0.0, -1.0, 0.0)), eq(5.0), eq(FluidCollisionMode.ALWAYS), eq(true));
    }

    @Test
    @DisplayName("open air or a hit without a block shows nothing")
    void openAirOrAHitWithoutABlockShowsNothing() {
        World world = mock(World.class);
        when(world.getPlayers()).thenReturn(List.of());
        assertNull(Cloak.shroudSees(world, piece));
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), eq(true)))
            .thenReturn(new RayTraceResult(new Vector(), BlockFace.UP));
        assertNull(Cloak.shroudSees(world, piece));
    }
}
