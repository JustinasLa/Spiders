package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PointDetector implements ECSComponent {
    private Set<Player> checkPlayers = Set.of();
    private final Map<Player, Leg> selectedLeg = new LinkedHashMap<>();

    public Set<Player> getCheckPlayers() {
        return checkPlayers;
    }

    public void setCheckPlayers(Set<Player> checkPlayers) {
        this.checkPlayers = checkPlayers;
    }

    public Map<Player, Leg> getSelectedLeg() {
        return selectedLeg;
    }

    private static Leg rayCastLeg(SpiderBody spider, SpiderOptions options, World world, Vector rayOrigin, Vector rayDirection) {
        if (!Objects.equals(spider.getWorld(), world)) return null;

        double tolerance = options.getWalkGait().getStationary().getBodyHeight() * .15;
        for (Leg leg : spider.getLegs()) {
            boolean lookingAt = Geometry.lookingAtPoint(rayOrigin, rayDirection, leg.getEndEffector(), tolerance);
            if (lookingAt) return leg;
        }
        return null;
    }

    public static void setupPointDetector(ECS app) {
        app.onTick(ecs -> {
            for (Triple<SpiderBody, PointDetector, SpiderOptions> it : app.query(SpiderBody.class, PointDetector.class, SpiderOptions.class)) {
                SpiderBody spider = it.first();
                PointDetector pointDetector = it.second();
                SpiderOptions options = it.third();

                pointDetector.getSelectedLeg().clear();

                for (Player player : pointDetector.getCheckPlayers()) {
                    Leg leg = rayCastLeg(spider, options, player.getWorld(), Overloads.getEyePosition(player), Overloads.getDirection(player));
                    if (leg == null) continue;
                    pointDetector.getSelectedLeg().put(player, leg);
                }
            }
        });
    }
}
