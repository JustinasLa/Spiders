package net.tfminecraft.spiders;

import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.laser.LaserPoint;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.spider.presets.Presets;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Serializer;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.custom_items.CustomItemComponent;
import net.tfminecraft.spiders.utilities.custom_items.StaffTool;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import net.tfminecraft.spiders.utilities.persistence.UUIDDataType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SetupItems {
    private SetupItems() {}


    public static void setupItems() {
        ECS ecs = AppState.getEcs();

        CustomItemComponent spiderComponent = new CustomItemComponent("spider", Material.FERMENTED_SPIDER_EYE);
        StaffToolMenu.getStaffTools().add(new StaffTool(spiderComponent, "Cinderweaver Summoning Seal", "item/fermented_spider_eye",
            "Summons a Cinderweaver, then recalls it again.",
            "Right-click a floor, wall or ceiling to summon one there. Right-click again to recall it; the seal keeps its settings. Commands act on the spider linked to the seal in your hand."));
        spiderComponent.onGestureUse((player, item) -> {
            UUID storedUuid = getSpiderUUID(item);
            ECSEntity existing = storedUuid == null ? null : AppState.findSpiderByUUID(storedUuid);
            if (existing != null) {
                Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_WOOL_BREAK, .4f, .7f);

                setSpiderOptions(item, existing.query(SpiderOptions.class));

                existing.remove();
                setSpiderUUID(item, null);
                player.sendActionBar(Component.text("Spider removed"));
            } else {
                RayTraceResult result = Geometry.raycastGround(player.getWorld(), Overloads.getEyePosition(player), Overloads.getDirection(player), 100.0);
                if (result == null) return;
                Vector hitPosition = result.getHitPosition();
                Vector up = result.getHitBlockFace() != null ? result.getHitBlockFace().getDirection() : Maths.upVector();

                Overloads.playSound(player.getWorld(), hitPosition, Sound.ENTITY_SPIDER_AMBIENT, .4f, .7f);

                SpiderOptions stored = getSpiderOptions(item);
                SpiderOptions options = stored != null ? stored : Presets.defaultPreset();
                // placed on a wall or ceiling: it has to cling on
                if (up.getY() < .7) options.setClimbing(true);
                Vector yawDirection = Maths.rotate(Maths.forwardVector(), new Quaternionf().rotationY(Overloads.yawRadians(player)));
                Quaternionf orientation = SpiderBody.snapRotationAbout(
                    SpiderBody.orientationFrom(up, Overloads.getDirection(player), yawDirection),
                    up,
                    Maths.toRadians(45f)
                );

                ECSEntity entity = AppState.createSpider(player, player.getWorld(), hitPosition, options, orientation);
                if (entity == null) return;
                SpiderBody spider = entity.query(SpiderBody.class);
                if (spider == null) return;
                setSpiderUUID(item, spider.getUuid());

                player.sendActionBar(Component.text("Spider created"));
            }

        });


        CustomItemComponent disableLegComponent = new CustomItemComponent("disableLeg", Material.RABBIT_HIDE);
        StaffToolMenu.getStaffTools().add(new StaffTool(disableLegComponent, "Limb Binding Charm", "item/rabbit_hide",
            "Lames or frees a single leg.",
            "Hold it and look at a spider's foot to select that leg, then right-click to bind or free it."));
        Scheduler.onTick(it -> {
            Set<Player> players = new LinkedHashSet<>(disableLegComponent.getPlayersHoldingItem());
            for (PointDetector pointDetector : AppState.getEcs().query(PointDetector.class)) {
                pointDetector.setCheckPlayers(players);
            }
        });
        disableLegComponent.onGestureUse((player, item) -> {
            for (PointDetector pointDetector : AppState.getEcs().query(PointDetector.class)) {
                Leg selectedLeg = pointDetector.getSelectedLeg().get(player);
                if (selectedLeg == null) {
                    Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_SCULK_HIT, 1.0f, 2.0f);
                    return;
                }

                selectedLeg.setDisabled(!selectedLeg.isDisabled());
                Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_WOOL_HIT, 1.0f, 1.0f);
            }
        });

        CustomItemComponent toggleDebugComponent = new CustomItemComponent("toggleDebug", Material.BONE);
        StaffToolMenu.getStaffTools().add(new StaffTool(toggleDebugComponent, "Broodkeeper's Sight", "item/bone",
            "Shows the animation debug view for every spider.",
            "Right-click to show or hide leg targets, step zones and balance."));
        toggleDebugComponent.onGestureUse((player, item) -> {
            AppState.setRenderDebugVisuals(!AppState.getRenderDebugVisuals());

            float pitch = AppState.getRenderDebugVisuals() ? 2.0f : 1.5f;
            Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_SCULK_HIT, 1.0f, pitch);
        });


        CustomItemComponent switchRendererComponent = new CustomItemComponent("switchRenderer", Material.LIGHT_BLUE_DYE);
        StaffToolMenu.getStaffTools().add(new StaffTool(switchRendererComponent, "Spectral Form", "item/light_blue_dye",
            "Draws the nearest spider as a particle sketch instead of blocks.",
            "Right-click to switch the nearest spider between its solid body and particles."));
        switchRendererComponent.onGestureUse((player, item) -> {
            ECSEntity nearest = AppState.findNearestSpider(player);
            SpiderRenderer renderer = nearest == null ? null : nearest.query(SpiderRenderer.class);
            if (renderer == null) return;
            renderer.setUseParticles(!renderer.getUseParticles());

            if (renderer.getUseParticles()) {
                Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.ENTITY_AXOLOTL_ATTACK, 1.0f, 1.0f);
            } else {
                Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 1.0f);
            }
        });

        CustomItemComponent toggleCloakComponent = new CustomItemComponent("toggleCloak", Material.GREEN_DYE);
        StaffToolMenu.getStaffTools().add(new StaffTool(toggleCloakComponent, "Shroud Charm", "item/green_dye",
            "Shrouds the nearest spider so it copies whatever lies behind it.",
            "Right-click to shroud or unshroud the nearest spider."));
        toggleCloakComponent.onGestureUse((player, item) -> {
            ECSEntity entity = AppState.findNearestSpider(player);
            if (entity == null) return;
            Cloak cloak = entity.query(Cloak.class);
            if (cloak == null) return;
            cloak.toggleCloak(AppState.getEcs(), entity);
        });

        CustomItemComponent switchGaitComponent = new CustomItemComponent("switchGait", Material.BREEZE_ROD);
        StaffToolMenu.getStaffTools().add(new StaffTool(switchGaitComponent, "Hunting Instinct", "item/breeze_rod",
            "Switches the nearest spider between walking and galloping.",
            "Right-click to switch gait. Riders can also hold sprint to gallop."));
        switchGaitComponent.onGestureUse((player, item) -> {
            ECSEntity entity = AppState.findNearestSpider(player);
            if (entity == null) return;
            SpiderOptions options = entity.query(SpiderOptions.class);
            if (options == null) return;
            options.setGallop(!options.getGallop());
            Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_SCULK_HIT, 1.0f, 2.0f);
            player.sendActionBar(Component.text(!options.getGallop() ? "Walk mode" : "Gallop mode"));
        });

        CustomItemComponent toggleGripComponent = new CustomItemComponent("toggleGrip", Material.SLIME_BALL);
        StaffToolMenu.getStaffTools().add(new StaffTool(toggleGripComponent, "Clinging Charm", "item/slime_ball",
            "Lets the nearest spider climb walls and ceilings, or drop back to the floor.",
            "Right-click to switch wall climbing on or off."));
        toggleGripComponent.onGestureUse((player, item) -> {
            ECSEntity entity = AppState.findNearestSpider(player);
            if (entity == null) return;
            SpiderOptions options = entity.query(SpiderOptions.class);
            if (options == null) return;
            options.setClimbing(!options.getClimbing());
            Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.BLOCK_HONEY_BLOCK_HIT, 1.0f, 2.0f);
            player.sendActionBar(Component.text(!options.getClimbing() ? "Wall climbing off" : "Wall climbing on"));
        });

        CustomItemComponent laserPointerComponent = new CustomItemComponent("laserPointer", Material.REDSTONE_TORCH);
        StaffToolMenu.getStaffTools().add(new StaffTool(laserPointerComponent, "Brood Guide — aim to guide", "block/redstone_torch",
            "Spiders walk to wherever you point it.",
            "Hold it and aim at the ground. Nothing is drawn where it points."));

        CustomItemComponent comeHereComponent = new CustomItemComponent("comeHere", Material.CARROT_ON_A_STICK);
        StaffToolMenu.getStaffTools().add(new StaffTool(comeHereComponent, "Broodcall Whistle — hold to recall", "item/carrot_on_a_stick",
            "Calls the spiders to you.",
            "Hold it and they walk towards you; put it away to release them."));

        ecs.onTick(app -> {
            // mark laser for removal
            for (LaserPointExpire expire : ecs.query(LaserPointExpire.class)) {
                expire.setExpired(true);
            }
        });

        ecs.onTick(app -> {
            List<Triple<ECSEntity, LaserPoint, LaserPointExpire>> lasers = ecs.query(ECSEntity.class, LaserPoint.class, LaserPointExpire.class);

            // handle laser pointer
            for (Player player : laserPointerComponent.getPlayersHoldingItem()) {
                Vector direction = Overloads.getDirection(player);
                RayTraceResult result = Geometry.raycastGround(player.getWorld(), Overloads.getEyePosition(player), direction, 100.0);

                Vector hitPosition = result != null ? result.getHitPosition() :
                    Overloads.getEyePosition(player).add(direction.multiply(200));


                boolean isUsingFallback = result == null;
                boolean isVisible = !isUsingFallback && AppState.getMiscOptions().getShowLaser();

                spawnLaser(ecs, lasers, player, hitPosition, isVisible);
            }

            // handle carrot on a stick
            for (Player player : comeHereComponent.getPlayersHoldingItem()) {
                spawnLaser(ecs, lasers, player, Overloads.getEyePosition(player), false);
            }
        });

        ecs.onTick(app -> {
            for (Pair<ECSEntity, LaserPointExpire> pair : ecs.query(ECSEntity.class, LaserPointExpire.class)) {
                if (pair.second().getExpired()) pair.first().remove();
            }
        });
    }

    private static final class LaserPointExpire implements ECSComponent {
        private final Player owner;
        private boolean expired = false;

        LaserPointExpire(Player owner) {
            this.owner = owner;
        }

        Player getOwner() {
            return owner;
        }

        boolean getExpired() {
            return expired;
        }

        void setExpired(boolean expired) {
            this.expired = expired;
        }
    }

    private static void spawnLaser(
        ECS ecs,
        List<Triple<ECSEntity, LaserPoint, LaserPointExpire>> lasers,
        Player player,
        Vector position,
        boolean isVisible
    ) {
        Triple<ECSEntity, LaserPoint, LaserPointExpire> existing = null;
        for (Triple<ECSEntity, LaserPoint, LaserPointExpire> laser : lasers) {
            if (Objects.equals(laser.third().getOwner(), player)) {
                existing = laser;
                break;
            }
        }

        if (existing != null) {
            // update existing laser
            existing.second().setWorld(player.getWorld());
            existing.second().setPosition(position);
            existing.second().setVisible(isVisible);
            existing.third().setExpired(false);
        } else {
            // create new laser
            ecs.spawn(
                new LaserPointExpire(player),
                new LaserPoint(
                    player.getWorld(),
                    position,
                    isVisible
                )
            );
        }
    }

    private static final NamespacedKey SPIDER_UUID_KEY = Core.namespacedID("spider_uuid");
    private static final NamespacedKey SPIDER_OPTIONS_KEY = Core.namespacedID("spider_options");

    public static UUID heldSpiderUUID(Player player) {
        UUID mainHand = getSpiderUUID(player.getInventory().getItemInMainHand());
        return mainHand != null ? mainHand : getSpiderUUID(player.getInventory().getItemInOffHand());
    }

    private static UUID getSpiderUUID(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(SPIDER_UUID_KEY, UUIDDataType.INSTANCE);
    }

    private static void setSpiderUUID(ItemStack item, UUID value) {
        ItemMeta meta = item.getItemMeta();
        if (value == null) {
            meta.getPersistentDataContainer().remove(SPIDER_UUID_KEY);
        } else {
            meta.getPersistentDataContainer().set(SPIDER_UUID_KEY, UUIDDataType.INSTANCE, value);
        }
        item.setItemMeta(meta);
    }

    private static SpiderOptions getSpiderOptions(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        String json = itemMeta == null ? null : itemMeta.getPersistentDataContainer().get(SPIDER_OPTIONS_KEY, PersistentDataType.STRING);
        if (json == null) return null;

        try {
            SpiderOptions options = Serializer.deserialize(json, SpiderOptions.class);
            if (Cinderweaver.upgradeClockworkAppearance(options.getBodyPlan())) Cinderweaver.useOrganicSounds(options);
            return options;
        } catch (Exception e) {
            Core.getCurrentPlugin().getLogger().warning("Ignoring unreadable stored spider options: " + e);
            return null;
        }
    }

    private static void setSpiderOptions(ItemStack item, SpiderOptions value) {
        ItemMeta meta = item.getItemMeta();
        if (value == null) {
            meta.getPersistentDataContainer().remove(SPIDER_OPTIONS_KEY);
            item.setItemMeta(meta);
            return;
        }

        String json;
        try {
            json = Serializer.serialize(value);
        } catch (Exception e) {
            Core.getCurrentPlugin().getLogger().severe("Could not serialize spider options: " + e);
            return;
        }

        meta.getPersistentDataContainer().set(SPIDER_OPTIONS_KEY, PersistentDataType.STRING, json);
        item.setItemMeta(meta);
    }
}
