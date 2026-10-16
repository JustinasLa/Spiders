package net.tfminecraft.spiders;

import net.tfminecraft.spiders.commands.SetupCommands;
import net.tfminecraft.spiders.laser.LaserPoint;
import net.tfminecraft.spiders.spider.SpiderActivity;
import net.tfminecraft.spiders.spider.SpiderSystems;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.custom_items.CustomItems;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

@SuppressWarnings("unused")
public class SpiderAnimationPlugin extends JavaPlugin {
    public void writeAndSaveConfig() {
//            for ((key, value) in options) {
//                instance.config.set(key, Serializer.toMap(value()))
//            }
//            instance.saveConfig()
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling Spider Animation plugin");
        Core.shutdownCoreUtils(this);
        AppState.getEcs().getEntities().clear();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling Spider Animation plugin");

        Core.setupCoreUtils(this);
        // displays left behind by a crash or a failed shutdown
        RenderEntityTracker.removeAllByTag();

        ECS ecs = AppState.getEcs();
        ecs.setLogger(getLogger());

        SetupCommands.setupCommands(this);
        SetupItems.setupItems();
        CustomItems.setupStaffTools();
        StaffToolMenu.setupStaffToolMenu();
        SpiderSystems.setupSpider(ecs);
        SpiderActivity.setupRemoveBroken(ecs, getLogger());
        LaserPoint.setupLaserPointer(ecs);
        Events.addEventListener(new Listener() {
            // a spider can't outlive its world (unless another plugin keeps the world loaded)
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onWorldUnload(WorldUnloadEvent event) {
                for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
                    if (Objects.equals(pair.second().getWorld(), event.getWorld())) pair.first().remove();
                }
            }
        });

        ecs.start();
        Scheduler.onTick(it -> {
            // sync AppState properties
            for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
                SpiderRenderer renderer = pair.first().query(SpiderRenderer.class);
                if (renderer != null) renderer.setRenderDebugVisuals(AppState.getRenderDebugVisuals());
            }

            SpiderActivity.updateSleep(ecs);
            ecs.update();
            ecs.render();
        });

        getLogger().info("Cinderweavers ready. Staff permission: " + StaffAccess.PERMISSION + "; limit: " + AppState.MAX_SPIDERS + ".");
    }
}
