package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.PaletteEntry;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.Quintuple;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.rendering.RenderVisibility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpiderRenderer implements ECSComponent {
    private boolean useParticles = false;

    public boolean getUseParticles() {
        return useParticles;
    }

    public void setUseParticles(boolean useParticles) {
        this.useParticles = useParticles;
    }

    public static void setupRenderer(ECS app) {
        // apply eye blinking effect
        Scheduler.interval(0, 10, it -> {
            for (Pair<SpiderBody, SpiderOptions> row : app.query(SpiderBody.class, SpiderOptions.class)) {
                SpiderOptions options = row.second();
                List<BlockDisplayModelPiece> pieces = piecesTagged(options, "eye");

                if (ThreadLocalRandom.current().nextBoolean()) continue;
                for (BlockDisplayModelPiece piece : pieces) {
                    PaletteEntry entry = random(options.getBodyPlan().getEyePalette());
                    piece.setBlock(entry.getBlock());
                    piece.setBrightness(entry.getBrightness());
                }
            }
        });

        // apply blinking lights effect
        Scheduler.interval(0, 5, it -> {
            for (Pair<SpiderBody, SpiderOptions> row : app.query(SpiderBody.class, SpiderOptions.class)) {
                SpiderOptions options = row.second();
                List<BlockDisplayModelPiece> pieces = piecesTagged(options, "blinking_lights");

                if (ThreadLocalRandom.current().nextBoolean()) continue;
                for (BlockDisplayModelPiece piece : pieces) {
                    PaletteEntry entry = random(options.getBodyPlan().getBlinkingPalette());
                    piece.setBlock(entry.getBlock());
                    piece.setBrightness(entry.getBrightness());
                }
            }
        });

        app.onRender(ecs -> {
            List<Player> debugViewers = AppState.getDebugViewers();
            for (Quintuple<SpiderBody, Cloak, PointDetector, SpiderRenderer, SpiderOptions> row : app.query(SpiderBody.class, Cloak.class, PointDetector.class, SpiderRenderer.class, SpiderOptions.class)) {
                SpiderBody spider = row.first();
                Cloak cloak = row.second();
                PointDetector pointDetector = row.third();
                SpiderRenderer renderer = row.fourth();
                SpiderOptions options = row.fifth();

                if (renderer.getUseParticles()) {
                    SpiderParticleRenderer.renderSpider(spider);
                } else {
                    RenderSpiderEntities.renderSpider(spider, cloak, options).submit(spider);
                }


                if (!debugViewers.isEmpty()) RenderVisibility.onlyFor(debugViewers, () ->
                    SpiderDebugRenderEntities.spiderDebugRenderEntities(spider, pointDetector, options).submit(new Pair<>(spider, "debug")));
            }
        });
    }

    private static List<BlockDisplayModelPiece> piecesTagged(SpiderOptions options, String tag) {
        List<BlockDisplayModelPiece> pieces = new ArrayList<>();
        for (BlockDisplayModelPiece piece : options.getBodyPlan().getBodyModel().getPieces()) {
            if (piece.getTags().contains(tag)) pieces.add(piece);
        }
        return pieces;
    }

    private static <T> T random(List<T> list) {
        if (list.isEmpty()) throw new java.util.NoSuchElementException("Collection is empty.");
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private static final class SpiderParticleRenderer {
        private SpiderParticleRenderer() {
        }

//    fun renderTarget(location: Location) {
//        location.world?.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.RED, 1f))
//    }

        static void renderSpider(SpiderBody spider) {
            for (Leg leg : spider.getLegs()) {
                World world = leg.getSpider().getWorld();
                List<IKChainWorldNode3D> positions = leg.getIk().getNodes();
                if (positions.isEmpty()) throw new java.util.NoSuchElementException("List is empty.");
                Location current = positions.get(0).getPosition().toLocation(world);

                for (int i = 0; i < positions.size() - 1; i++) {
                    IKChainWorldNode3D node = positions.get(i + 1);
                    double thickness = (positions.size() - i - 2) * 0.025;
                    renderLine(current, node.getPosition(), thickness);
                    current = node.getPosition().toLocation(world);
                }
            }
        }

        static void renderLine(Location point1, Vector point2, double thickness) {
            double gap = .05;

            double amount = point1.toVector().distance(point2) / gap;
            Vector step = point2.clone().subtract(point1.toVector()).multiply(1 / amount);

            Location current = point1.clone();

            for (int i = 0; i <= (int) amount; i++) {
                World world = point1.getWorld();
                if (world != null) world.spawnParticle(Particle.BUBBLE, current, 1, thickness, thickness, thickness, 0.0);
                current.add(step);
            }
        }
    }
}
