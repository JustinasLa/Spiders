package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class SpiderRepulsion {
    private static final double REPULSION_STRENGTH = 3.0;
    private static final double SEPARATION_FACTOR = 1.2;

    private SpiderRepulsion() {
    }

    public static void setupSpiderRepulsion(ECS app) {
        app.onTick(ecs -> {
            List<Triple<SpiderBody, SpiderOptions, Locomotion>> spiders = new ArrayList<>(app.query(SpiderBody.class, SpiderOptions.class, Locomotion.class));

            for (int i = 0; i < spiders.size(); i++) {
                for (int j = i + 1; j < spiders.size(); j++) {
                    SpiderBody bodyA = spiders.get(i).first();
                    SpiderOptions optionsA = spiders.get(i).second();
                    Locomotion locomotionA = spiders.get(i).third();
                    SpiderBody bodyB = spiders.get(j).first();
                    SpiderOptions optionsB = spiders.get(j).second();
                    Locomotion locomotionB = spiders.get(j).third();

                    if (bodyA.getWorld() != bodyB.getWorld()) continue;

                    Vector delta = bodyB.getPosition().clone().subtract(bodyA.getPosition());
                    double distance = delta.length();

                    double separation = separationDistance(optionsA, optionsB);
                    if (separation <= 0.0 || distance >= separation) continue;

                    Vector away = delta.setY(0.0);
                    if (away.lengthSquared() < 1e-8) {
                        // same column
                        away.setX(bodyA.getUuid().compareTo(bodyB.getUuid()) < 0 ? 1.0 : -1.0);
                    }
                    away.normalize();

                    double overlap = 1.0 - distance / separation;
                    locomotionA.push(away.clone().multiply(-pushSpeed(optionsA, overlap)));
                    locomotionB.push(away.clone().multiply(pushSpeed(optionsB, overlap)));
                }
            }
        });
    }

    private static double pushSpeed(SpiderOptions options, double overlap) {
        return options.getGait().getMaxSpeed() * overlap * REPULSION_STRENGTH;
    }

    private static double footprintRadius(SpiderOptions options) {
        Double max = null;
        for (LegPlan it : options.getBodyPlan().getLegs()) {
            double value = Geometry.horizontalLength(it.getRestPosition());
            max = max == null ? value : Math.max(max, value);
        }
        return max != null ? max : 0.0;
    }

    private static double separationDistance(SpiderOptions a, SpiderOptions b) {
        return (footprintRadius(a) + footprintRadius(b)) * SEPARATION_FACTOR;
    }
}
