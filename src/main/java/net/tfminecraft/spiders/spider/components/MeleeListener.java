package net.tfminecraft.spiders.spider.components;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Players hit the spider by swinging at its drawn shape within their reach. Swings at the hidden
 * saddle pig (which sits inside the body) count as hits on the spider instead of attacks on the pig.
 */
public class MeleeListener implements Listener {
    private final ECS app;
    private final Function<Player, Double> reachOf;

    public MeleeListener(ECS app, Function<Player, Double> reachOf) {
        this.app = app;
        this.reachOf = reachOf;
    }

    public MeleeListener(ECS app) {
        this(app, MeleeListener::attackReach);
    }

    /** The tick each player last right-clicked: the client swings its arm for those too (placing, opening). */
    private final Map<Player, Integer> lastUse = new WeakHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) lastUse.put(event.getPlayer(), Core.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        lastUse.put(event.getPlayer(), Core.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        // the swing that comes with a right-click is not an attack
        Integer used = lastUse.get(event.getPlayer());
        if (used != null && Core.getCurrentTick() - used <= 1) return;
        strike(event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackSeat(PrePlayerAttackEntityEvent event) {
        Pair<ECSEntity, Mountable> seated = null;
        for (Pair<ECSEntity, Mountable> row : app.query(ECSEntity.class, Mountable.class)) {
            Mountable mountable = row.second();
            if (Objects.equals(event.getAttacked(), mountable.getCurrentPig()) || Objects.equals(event.getAttacked(), mountable.getCurrentMarker())) {
                seated = row;
                break;
            }
        }
        if (seated == null) return;
        event.setCancelled(true);
        strike(event.getPlayer(), new Pair<>(seated.first(), event.getAttacked().getLocation().toVector()));
    }

    /** {@code seat}: the spider whose seat was attacked, and where, used when the swing ray misses the drawn shape. */
    private void strike(Player player, Pair<ECSEntity, Vector> seat) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        double charge = (double) player.getAttackCooldown();
        if (charge < HitDetector.MIN_CHARGE) return;

        Location eyeLocation = player.getEyeLocation();
        Vector eye = eyeLocation.toVector();
        Vector look = eyeLocation.getDirection().normalize();
        double reach = reachOf.apply(player);

        Triple<ECSEntity, Double, Vector> best = null;
        for (Triple<ECSEntity, SpiderBody, SpiderOptions> row : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class)) {
            ECSEntity entity = row.first();
            SpiderBody spider = row.second();
            SpiderOptions options = row.third();
            if (!Objects.equals(spider.getWorld(), player.getWorld()) || entity.query(HitDetector.class) == null) continue;
            Mountable mountable = entity.query(Mountable.class);
            if (Objects.equals(mountable != null ? mountable.getRider() : null, player)) continue;
            double near = reach + SpiderShape.reach(options.getBodyPlan());
            if (spider.getPosition().distanceSquared(eye) > near * near) continue;
            Pair<Double, Vector> hit = SpiderShape.firstHit(SpiderShape.shapePieces(spider, options.getBodyPlan()), eye, eye.clone().add(look.clone().multiply(reach)));
            if (hit == null) continue;
            double t = hit.first();
            Vector point = hit.second();
            if (best == null || t < best.second()) best = new Triple<>(entity, t, point);
        }
        if (best == null && seat != null) best = new Triple<>(seat.first(), 0.0, seat.second());
        if (best == null) return;
        ECSEntity entity = best.first();
        double t = best.second();
        Vector point = best.third();

        // a block in the way takes the swing (e.g. mining next to a leg)
        if (seat == null && t > 0 && player.getWorld().rayTraceBlocks(eyeLocation, look, t * reach, FluidCollisionMode.NEVER, true) != null) return;

        SpiderBody spider = entity.query(SpiderBody.class);
        if (spider == null) return;
        SpiderOptions options = entity.query(SpiderOptions.class);
        if (options == null) return;
        HitDetector.knockBack(spider, options, look, point, HitDetector.MELEE_WEIGHT * charge);
        player.resetCooldown();
        app.emit(new SpiderHitEvent(entity, spider, player, point, true));
    }

    private static double attackReach(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attribute != null) return attribute.getValue();
        return player.getGameMode() == GameMode.CREATIVE ? 5.0 : 3.0;
    }
}
