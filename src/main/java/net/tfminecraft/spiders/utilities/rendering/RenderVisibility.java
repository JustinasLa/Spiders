package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.Core;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Entities rendered inside {@link #onlyFor} are drawn for those players only; nobody else is ever sent them. */
public final class RenderVisibility {
    private RenderVisibility() {
    }

    private static Collection<? extends Player> viewers = null;

    /** Runs [render] so that every entity it submits is visible to [players] alone. */
    public static void onlyFor(Collection<? extends Player> players, Runnable render) {
        Collection<? extends Player> outer = viewers;
        viewers = players;
        try {
            render.run();
        } finally {
            viewers = outer;
        }
    }

    /** Called as an entity spawns, before anyone is sent it. */
    static void onSpawn(Entity entity) {
        if (viewers != null) entity.setVisibleByDefault(false);
    }

    /** Shows the entity to the current viewers and hides it from players who stopped viewing. */
    static void apply(Entity entity) {
        if (viewers == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = viewers.contains(player);
            if (shouldSee == player.canSee(entity)) continue;
            if (shouldSee) player.showEntity(Core.getCurrentPlugin(), entity);
            else player.hideEntity(Core.getCurrentPlugin(), entity);
        }
    }
}
