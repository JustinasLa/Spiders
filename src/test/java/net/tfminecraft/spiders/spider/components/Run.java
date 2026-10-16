package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;

import java.util.function.IntPredicate;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

record Run(ECS app, ECSEntity entity, SpiderBody body, Locomotion locomotion, Gait gait, SpiderOptions options) {

    /** Walks forward until {@code onTick} returns true or the ticks run out. */
    void walkForward(int ticks, IntPredicate onTick) {
        for (int tick = 0; tick < ticks; tick++) {
            locomotion.walk(body.forwardDirection().multiply(gait.getMaxSpeed()));
            app.update();
            assertTrue(DoubleStream.of(body.getPosition().getX(), body.getPosition().getY(), body.getPosition().getZ()).allMatch(Double::isFinite), "Body left the finite world");
            if (onTick.test(tick)) return;
        }
    }

    void walkForward(int ticks) {
        walkForward(ticks, tick -> false);
    }

    Vector up() {
        return Maths.rotate(Maths.upVector(), body.getOrientation());
    }

    String describe(int tick) {
        return "t=" + tick + " pos=" + body.getPosition() + " up=" + up() + " grounded=" + groundedLegs(body) + "\n";
    }

    static int groundedLegs(SpiderBody body) {
        int count = 0;
        for (Leg leg : body.getLegs()) if (leg.isGrounded()) count++;
        return count;
    }
}
