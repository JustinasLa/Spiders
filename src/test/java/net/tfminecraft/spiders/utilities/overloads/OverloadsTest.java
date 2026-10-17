package net.tfminecraft.spiders.utilities.overloads;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverloadsTest {
    private final World world = mock(World.class);

    @Test
    @DisplayName("spawning and sounds take a vector in the given world")
    void spawnAndSoundTakeAVector() {
        Zombie zombie = mock(Zombie.class);
        Consumer<Zombie> init = it -> {
        };
        when(world.spawn(new Location(world, 1, 2, 3), Zombie.class, init)).thenReturn(zombie);
        assertSame(zombie, Overloads.spawnEntity(world, new Vector(1, 2, 3), Zombie.class, init));

        Overloads.playSound(world, new Vector(4, 5, 6), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        verify(world).playSound(new Location(world, 4, 5, 6), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
    }

    @Test
    @DisplayName("an entity's position, eyes, facing and angles come from its location")
    void entityLocationShortcuts() {
        Entity entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(world, 1, 2, 3, 90f, 45f));
        LivingEntity living = mock(LivingEntity.class);
        when(living.getEyeLocation()).thenReturn(new Location(world, 1, 3.6, 3));

        assertEquals(new Vector(1, 2, 3), Overloads.getPosition(entity));
        assertEquals(new Vector(1, 3.6, 3), Overloads.getEyePosition(living));
        assertEquals(new Location(world, 0, 0, 0, 90f, 45f).getDirection(), Overloads.getDirection(entity));
        assertEquals(90f, Overloads.getYaw(entity));
        assertEquals(45f, Overloads.getPitch(entity));
        assertEquals((float) -Math.toRadians(90), Overloads.yawRadians(entity), 1e-6);
        assertEquals((float) Math.toRadians(45), Overloads.pitchRadians(entity), 1e-6);
    }
}
