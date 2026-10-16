package net.tfminecraft.spiders.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.SetupItems;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Which spider a command or a staff tool acts on. */
public final class SpiderTargets {
    private SpiderTargets() {
    }

    /** Staff tools reach spiders this far away. */
    public static final double TOOL_RANGE = 64.0;

    /**
     * The spider linked to the Summoning Seal the player holds, or else the one nearest to where the
     * command runs: the player, a command block, or wherever {@code /execute at|in} put it.
     */
    public static ECSEntity forCommand(CommandSourceStack source) {
        Player player = source.getExecutor() instanceof Player executor ? executor
            : source.getSender() instanceof Player sender ? sender : null;
        ECSEntity held = player == null ? null : heldSpider(player);
        if (held != null) return held;
        Location location = source.getLocation();
        return location.getWorld() == null ? null : AppState.findNearestSpider(location);
    }

    /** The spider linked to a seal the player holds, or else the nearest one within {@link #TOOL_RANGE}. */
    public static ECSEntity forTool(Player player) {
        ECSEntity held = heldSpider(player);
        return held != null ? held : AppState.findNearestSpider(player.getLocation(), TOOL_RANGE);
    }

    private static ECSEntity heldSpider(Player player) {
        UUID held = SetupItems.heldSpiderUUID(player);
        return held == null ? null : AppState.findSpiderByUUID(held);
    }
}
