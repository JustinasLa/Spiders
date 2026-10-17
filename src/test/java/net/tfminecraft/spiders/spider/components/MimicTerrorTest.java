package net.tfminecraft.spiders.spider.components;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicTerrorTest {
    private MimicServer server;
    private final World world = mock(World.class);

    @BeforeEach
    void start() {
        server = new MimicServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static String pack(String sound) {
        return "tfmc_spiders:" + sound;
    }

    @Test
    @DisplayName("its sounds come from the resource pack, for everyone near or for one player alone")
    void soundsAreThePacksAndCanBePrivate() {
        assertEquals("tfmc_spiders:mimic.chomp", MimicTerror.key(MimicTerror.CHOMP));
        MimicTerror.play(world, new Vector(1, 2, 3), MimicTerror.GROWL, .4f, 1.1f);
        verify(world).playSound(new Location(world, 1, 2, 3), pack(MimicTerror.GROWL), SoundCategory.HOSTILE, .4f, 1.1f);

        Player player = server.player(world, new Vector(0, 0, 0), new Vector(0, 0, 1));
        MimicTerror.playTo(player, new Vector(4, 5, 6), MimicTerror.BREATH, .7f, .9f);
        verify(player).playSound(new Location(world, 4, 5, 6), pack(MimicTerror.BREATH), SoundCategory.HOSTILE, .7f, .9f);
        verify(world, never()).playSound(any(Location.class), eq(pack(MimicTerror.BREATH)), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("the screech is layered, and each player in range also hears it loud from just beside them, towards the mimic")
    void screechIsLoudFromTheRightDirection() {
        Vector centre = new Vector(0, 64, 0);
        Player close = server.player(world, new Vector(1, 64 - 1.62, 0), new Vector(1, 0, 0));
        Player mid = server.player(world, new Vector(20, 64 - 1.62, 0), new Vector(1, 0, 0));
        Player far = server.player(world, new Vector(100, 64 - 1.62, 0), new Vector(1, 0, 0));
        when(world.getPlayers()).thenReturn(List.of(close, mid, far));

        MimicTerror.screech(world, centre, 1.2f);

        Location at = centre.toLocation(world);
        verify(world).playSound(at, pack(MimicTerror.SCREECH), SoundCategory.HOSTILE, 5f, 1.2f);
        verify(world).playSound(at, "minecraft:entity.fox.screech", SoundCategory.HOSTILE, 5f, .7f * 1.2f);
        verify(world).playSound(at, "minecraft:entity.ghast.scream", SoundCategory.HOSTILE, 5f, 1.6f * 1.2f);
        verify(world).playSound(at, "minecraft:entity.enderman.scream", SoundCategory.HOSTILE, 5f, .8f * 1.2f);
        verify(world).playSound(at, "minecraft:entity.phantom.death", SoundCategory.HOSTILE, 5f, .6f * 1.2f);

        // right on top of it, they hear it from the mimic itself
        verify(close, times(2)).playSound(at, pack(MimicTerror.SCREECH), SoundCategory.HOSTILE, 1f, 1.2f);
        verify(close, times(4)).playSound(eq(at), anyString(), eq(SoundCategory.HOSTILE), eq(.6f), anyFloat());
        // further off, two blocks from their ear, on the mimic's side
        ArgumentCaptor<Location> source = ArgumentCaptor.forClass(Location.class);
        verify(mid, times(2)).playSound(source.capture(), eq(pack(MimicTerror.SCREECH)), eq(SoundCategory.HOSTILE), eq(1f), eq(1.2f));
        assertEquals(18.0, source.getValue().getX(), 1e-9);
        assertEquals(64.0, source.getValue().getY(), 1e-9);
        verify(mid, times(4)).playSound(any(Location.class), anyString(), eq(SoundCategory.HOSTILE), eq(.6f), anyFloat());
        // out of range, nothing up close
        verify(far, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("a shriek and a chomp each layer a vanilla sound under the pack's")
    void shriekAndChomp() {
        MimicTerror.shriek(world, new Vector(0, 0, 0), 1f);
        Location at = new Location(world, 0, 0, 0);
        verify(world).playSound(at, pack(MimicTerror.SHRIEK), SoundCategory.HOSTILE, 2.5f, 1f);
        verify(world).playSound(at, "minecraft:entity.fox.screech", SoundCategory.HOSTILE, 1.5f, 1.3f);

        try (MimicDice dice = new MimicDice()) {
            dice.nextFloat = 1f;
            MimicTerror.chomp(world, new Vector(0, 0, 0), 2f);
        }
        verify(world).playSound(at, pack(MimicTerror.CHOMP), SoundCategory.HOSTILE, 2f, 1.15f);
        verify(world).playSound(at, "minecraft:block.chest.close", SoundCategory.HOSTILE, 2f * .7f, 1.3f);
        verify(world).playSound(at, "minecraft:entity.evoker_fangs.attack", SoundCategory.HOSTILE, 2f * .35f, 1.6f);
    }

    @Test
    @DisplayName("only living, online players who aren't spectating are prey")
    void whoIsPrey() {
        Player player = server.player(world, new Vector(), new Vector(0, 0, 1));
        assertTrue(MimicTerror.isPrey(player));
        assertFalse(MimicTerror.isPrey(null));
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(MimicTerror.isPrey(player));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(MimicTerror.isPrey(player), "creative players can still be frightened");
        when(player.isDead()).thenReturn(true);
        assertFalse(MimicTerror.isPrey(player));
        when(player.isDead()).thenReturn(false);
        when(player.isOnline()).thenReturn(false);
        assertFalse(MimicTerror.isPrey(player));
    }

    @Test
    @DisplayName("the jump scare wrenches their view, shakes the camera three times and flashes three frames")
    void jumpScare() {
        Player player = server.player(world, new Vector(0, 0, 0), new Vector(0, 0, 1));
        MimicTerror.stareInto(player, new Vector(1, 2, 3));
        verify(player).lookAt(1.0, 2.2, 3.0, LookAnchor.EYES);

        MimicTerror.jumpScare(player);
        verify(player).playSound(new Location(world, 0, 1.62, 0), pack(MimicTerror.STINGER), SoundCategory.HOSTILE, 1f, 1f);
        verify(player).playSound(any(Location.class), eq("minecraft:entity.warden.sonic_boom"), eq(SoundCategory.HOSTILE), eq(.35f), eq(1.8f));
        assertEquals(List.of(0L, 3L, 6L, 0L, 2L, 4L), server.pending().stream().map(MimicServer.Task::delay).toList());
        server.runLater();
        verify(player).sendHurtAnimation(0f);
        verify(player).sendHurtAnimation(120f);
        verify(player).sendHurtAnimation(240f);
        ArgumentCaptor<Title> titles = ArgumentCaptor.forClass(Title.class);
        verify(player, times(3)).showTitle(titles.capture());
        // the last frame lingers and fades
        assertEquals(java.time.Duration.ofMillis(300), titles.getAllValues().get(2).times().fadeOut());
        assertEquals(java.time.Duration.ZERO, titles.getAllValues().get(0).times().fadeOut());

        // logging off before the frames arrive spares them
        Player gone = server.player(world, new Vector(), new Vector(0, 0, 1));
        MimicTerror.jumpScare(gone);
        when(gone.isOnline()).thenReturn(false);
        server.runLater();
        verify(gone, never()).sendHurtAnimation(anyFloat());
        verify(gone, never()).showTitle(any(Title.class));
    }

    @Test
    @DisplayName("a bite hurts, throws them back from the mouth, and stuns, but leaves the last heart")
    void biteHurtsAndThrowsThemBack() {
        Player player = server.player(world, new Vector(3, 0, 0), new Vector(-1, 0, 0));
        Vector mouth = new Vector(0, 1, 0);
        MimicTerror.bite(player, mouth, 3.0);

        verify(player).damage(3.0);
        verify(player, never()).sendHurtAnimation(anyFloat());
        verify(player).lookAt(0.0, 1.2, 0.0, LookAnchor.EYES);
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(player).setVelocity(velocity.capture());
        assertEquals(.75, velocity.getValue().getX(), 1e-9);
        assertEquals(.42, velocity.getValue().getY(), 1e-9);
        assertEquals(0.0, velocity.getValue().getZ(), 1e-9);
        ArgumentCaptor<PotionEffect> effects = ArgumentCaptor.forClass(PotionEffect.class);
        verify(player, times(3)).addPotionEffect(effects.capture());
        assertEquals(List.of(PotionEffectType.NAUSEA, PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS),
            effects.getAllValues().stream().map(PotionEffect::getType).toList());
        verify(world).playSound(eq(mouth.toLocation(world)), eq(pack(MimicTerror.CHOMP)), eq(SoundCategory.HOSTILE), eq(2f), anyFloat());
        verify(world).playSound(eq(mouth.toLocation(world)), eq(pack(MimicTerror.GULP)), eq(SoundCategory.HOSTILE), eq(1f), anyFloat());
        verify(world).spawnParticle(eq(Particle.BLOCK), argThat(it -> it.toVector().equals(new Vector(3, 1.1, 0))), eq(40), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(BlockData.class));
        verify(world).spawnParticle(eq(Particle.DAMAGE_INDICATOR), any(Location.class), eq(6), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(world).spawnParticle(eq(Particle.BLOCK), eq(mouth.toLocation(world)), eq(12), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(BlockData.class));
    }

    @Test
    @DisplayName("bites never take the last heart, and only hurt players who can be hurt")
    void bitesNeverKill() {
        Vector mouth = new Vector(0, 1, 0);
        Player low = server.player(world, new Vector(3, 0, 0), new Vector(1, 0, 0));
        when(low.getHealth()).thenReturn(2.0);
        MimicTerror.bite(low, mouth, 3.0);
        verify(low, never()).damage(anyDouble());
        verify(low).sendHurtAnimation(0f);

        Player adventurer = server.player(world, new Vector(3, 0, 0), new Vector(1, 0, 0));
        when(adventurer.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(adventurer.getHealth()).thenReturn(3.0);
        MimicTerror.bite(adventurer, mouth, 3.0);
        verify(adventurer).damage(1.0);

        Player creative = server.player(world, new Vector(3, 0, 0), new Vector(1, 0, 0));
        when(creative.getGameMode()).thenReturn(GameMode.CREATIVE);
        MimicTerror.bite(creative, mouth, 3.0);
        verify(creative, never()).damage(anyDouble());
        verify(creative).sendHurtAnimation(0f);
    }

    @Test
    @DisplayName("bitten from right underneath, they are thrown back the way they face, or north if looking straight down")
    void biteFromUnderneath() {
        Vector mouth = new Vector(0, 1, 0);
        Player facing = server.player(world, new Vector(0, 0, 0), new Vector(1, 0, 0));
        MimicTerror.bite(facing, mouth, 3.0);
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(facing).setVelocity(velocity.capture());
        assertEquals(-.75, velocity.getValue().getX(), 1e-6);
        assertEquals(0.0, velocity.getValue().getZ(), 1e-6);

        Player down = server.player(world, new Vector(0, 0, 0), new Vector(0, -1, 0));
        MimicTerror.bite(down, mouth, 3.0);
        verify(down).setVelocity(velocity.capture());
        assertEquals(0.0, velocity.getValue().getX(), 1e-6);
        assertEquals(.75, velocity.getValue().getZ(), 1e-6);
    }

    @Test
    @DisplayName("the heartbeat is louder close by, and the warden's joins it within ten blocks")
    void heartbeat() {
        Player player = server.player(world, new Vector(0, 0, 0), new Vector(0, 0, 1));
        MimicTerror.heartbeat(player, 5);
        verify(player).playSound(new Location(world, 0, 1, 0), pack(MimicTerror.HEARTBEAT), SoundCategory.HOSTILE, 1.2f, 1f);
        verify(player).playSound(any(Location.class), eq("minecraft:entity.warden.heartbeat"), eq(SoundCategory.HOSTILE), eq(.5f), eq(1.2f));

        Player far = server.player(world, new Vector(0, 0, 0), new Vector(0, 0, 1));
        MimicTerror.heartbeat(far, 40);
        verify(far).playSound(new Location(world, 0, 1, 0), pack(MimicTerror.HEARTBEAT), SoundCategory.HOSTILE, .5f, 1f);
        verify(far, never()).playSound(any(Location.class), eq("minecraft:entity.warden.heartbeat"), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("the dark is topped up only as it fades")
    void darkness() {
        Player player = server.player(world, new Vector(), new Vector(0, 0, 1));
        MimicTerror.darken(player);
        ArgumentCaptor<PotionEffect> effect = ArgumentCaptor.forClass(PotionEffect.class);
        verify(player).addPotionEffect(effect.capture());
        assertEquals(PotionEffectType.DARKNESS, effect.getValue().getType());
        assertEquals(80, effect.getValue().getDuration());

        PotionEffect fading = mock(PotionEffect.class);
        when(fading.getDuration()).thenReturn(10);
        when(player.getPotionEffect(PotionEffectType.DARKNESS)).thenReturn(fading);
        MimicTerror.darken(player);
        verify(player, times(2)).addPotionEffect(any(PotionEffect.class));

        when(fading.getDuration()).thenReturn(60);
        MimicTerror.darken(player);
        verify(player, times(2)).addPotionEffect(any(PotionEffect.class));
    }

    @Test
    @DisplayName("the heartbeat's interval stays between 9 and 26 ticks")
    void heartbeatIntervalIsClamped() {
        assertEquals(9, MimicTerror.heartbeatInterval(-10));
        assertEquals(26, MimicTerror.heartbeatInterval(1000));
        int between = MimicTerror.heartbeatInterval(13);
        assertTrue(between > 9 && between < 26, "at 13 blocks: " + between);
    }
}
