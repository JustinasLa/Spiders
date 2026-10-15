package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.utilities.Core;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * After a mimic runs off, its prey keeps hearing it for a while: skittering, breathing, a creak or a
 * far-off shriek, always from just behind them, and only they hear it.
 */
final class MimicHaunt {
    private MimicHaunt() {
    }

    private static final int TICKS = 10;

    private static final class Haunting {
        int ticksLeft;
        int next = ThreadLocalRandom.current().nextInt(100, 200);

        Haunting(int ticks) {
            ticksLeft = ticks;
        }
    }

    private static final Map<UUID, Haunting> haunted = new HashMap<>();

    static void setup() {
        Bukkit.getScheduler().runTaskTimer(Core.getCurrentPlugin(), MimicHaunt::update, TICKS, TICKS);
    }

    /** Haunt {@code player} for at least {@code ticks}. */
    static void haunt(Player player, int ticks) {
        Haunting haunting = haunted.computeIfAbsent(player.getUniqueId(), uuid -> new Haunting(ticks));
        haunting.ticksLeft = Math.max(haunting.ticksLeft, ticks);
    }

    private static void update() {
        Iterator<Map.Entry<UUID, Haunting>> it = haunted.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Haunting> entry = it.next();
            Haunting haunting = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            haunting.ticksLeft -= TICKS;
            if (player == null || !MimicTerror.isPrey(player) || haunting.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            haunting.next -= TICKS;
            if (haunting.next > 0) continue;
            haunting.next = ThreadLocalRandom.current().nextInt(140, 400);
            whisper(player);
        }
    }

    /** One sound from somewhere behind them, a few blocks off. */
    private static void whisper(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector look = player.getEyeLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-6) look = new Vector(0, 0, 1);
        look.normalize();
        Vector side = new Vector(-look.getZ(), 0, look.getX());
        Vector behind = player.getEyeLocation().toVector()
            .subtract(look.multiply(random.nextDouble(4, 9)))
            .add(side.multiply(random.nextDouble(-3, 3)))
            .add(new Vector(0, random.nextDouble(-1.2, .3), 0));
        switch (random.nextInt(6)) {
            case 0, 1 -> MimicTerror.playTo(player, behind, MimicTerror.SKITTER, .6f, .8f + random.nextFloat() * .3f);
            case 2 -> MimicTerror.playTo(player, behind, MimicTerror.BREATH, .7f, .75f + random.nextFloat() * .2f);
            case 3 -> MimicTerror.playTo(player, behind, MimicTerror.CREAK, .6f, .6f + random.nextFloat() * .3f);
            case 4 -> MimicTerror.playTo(player, behind.add(behind.clone().subtract(player.getEyeLocation().toVector()).multiply(3)), MimicTerror.SHRIEK, 1f, .6f);
            default -> {
                MimicTerror.playTo(player, behind, MimicTerror.CHOMP, .45f, 1.1f);
                MimicTerror.heartbeat(player, 6);
            }
        }
    }
}
