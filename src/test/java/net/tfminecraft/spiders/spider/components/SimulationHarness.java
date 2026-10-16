package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import net.tfminecraft.spiders.spider.configuration.PounceOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SimulationHarness {
    private SimulationHarness() {
    }

    /** Floor at y < 0, a wall at z >= 10 and a ceiling at y >= 8, open towards -z. */
    static boolean wallAndCeiling(int x, int y, int z) {
        return x >= -12 && x <= 12 && z >= -30 && z <= 13 && y >= -2 && y <= 10 && (y < 0 || z >= 10 || y >= 8);
    }

    /** A Cinderweaver body on {@code start} whose options always use one gait (gallop or walk). */
    static Run spawn(World world, double scale, boolean gallop, Vector start, boolean climbing, Quaternionf orientation) {
        Gait gait;
        if (gallop) {
            gait = Gait.defaultGallop();
            gait.setMaxSpeed(.24);
        } else {
            gait = Gait.defaultWalk();
            gait.setMaxSpeed(.12);
        }
        gait.scale(scale);
        if (climbing) {
            gait.setGrip(true);
            gait.setScanOrientation(OrientationMode.GroundOrientation);
        }
        BodyPlan plan = Cinderweaver.cinderweaverBodyPlan(scale);
        PounceOptions pounce = new PounceOptions();
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getGait()).thenReturn(gait);
        when(options.getWalkGait()).thenReturn(gait);
        when(options.getGallopGait()).thenReturn(gait);
        when(options.getGallop()).thenReturn(gallop);
        when(options.getClimbing()).thenReturn(climbing);
        when(options.getPounce()).thenReturn(pounce);
        when(options.getBodyPlan()).thenReturn(plan);

        ECS app = new ECS();
        SpiderBody.setupSpiderBody(app);
        Locomotion.setupLocomotion(app);
        Pounce.setupPounce(app);
        SpiderBody body = SpiderBody.create(world, start.clone().add(new Vector(0.0, gait.getStationary().getBodyHeight(), 0.0)), orientation);
        Locomotion locomotion = new Locomotion();
        ECSEntity entity = app.spawn(body, options, locomotion);
        return new Run(app, entity, body, locomotion, gait, options);
    }

    static Run spawn(World world, double scale, boolean gallop, Vector start, boolean climbing) {
        return spawn(world, scale, gallop, start, climbing, new Quaternionf());
    }

    static Run spawn(World world, double scale, boolean gallop, Vector start) {
        return spawn(world, scale, gallop, start, true);
    }
}
