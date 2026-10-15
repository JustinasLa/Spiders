package net.tfminecraft.spiders;

import net.tfminecraft.spiders.commands.SetupCommands;
import net.tfminecraft.spiders.laser.LaserPoint;
import net.tfminecraft.spiders.spider.SpiderSystems;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.custom_items.CustomItems;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

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

        ECS ecs = AppState.getEcs();

        SetupCommands.setupCommands(this);
        SetupItems.setupItems();
        CustomItems.setupStaffTools();
        SpiderSystems.setupSpider(ecs);
        LaserPoint.setupLaserPointer(ecs);

        ecs.start();
        Scheduler.onTick(it -> {
            // sync AppState properties
            for (Pair<ECSEntity, SpiderBody> pair : ecs.query(ECSEntity.class, SpiderBody.class)) {
                SpiderRenderer renderer = pair.first().query(SpiderRenderer.class);
                if (renderer != null) renderer.setRenderDebugVisuals(AppState.getRenderDebugVisuals());
            }

            ecs.update();
            ecs.render();
        });

        getLogger().info("Cinderweavers ready. Staff permission: " + StaffAccess.PERMISSION + "; limit: " + AppState.MAX_SPIDERS + ".");
    }
}
