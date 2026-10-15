package net.tfminecraft.spiders.utilities.custom_entities;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class CustomEntities {
    private CustomEntities() {
    }

    public static List<Entity> allEntities() {
        List<Entity> out = new ArrayList<>();
        for (World world : Bukkit.getServer().getWorlds()) out.addAll(world.getEntities());
        return out;
    }


    public static void attach(Entity entity, CustomEntityComponent component) {
        entity.addScoreboardTag(component.getTag());
    }

    public static void detach(Entity entity, CustomEntityComponent component) {
        entity.removeScoreboardTag(component.getTag());
    }
}
