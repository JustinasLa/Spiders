package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Splay {
    private Splay() {
    }

    private static Transformation lerp(Transformation transformation, Transformation newTransform, float lerpAmount) {

        transformation.getTranslation().lerp(newTransform.getTranslation(), lerpAmount);
        transformation.getScale().lerp(newTransform.getScale(), lerpAmount);
        transformation.getLeftRotation().slerp(newTransform.getLeftRotation(), lerpAmount);
        transformation.getRightRotation().slerp(newTransform.getRightRotation(), lerpAmount);

        return transformation;
    }

    public static Transformation clone(Transformation transformation) {
        return new Transformation(
            new Vector3f(transformation.getTranslation()),
            new Quaternionf(transformation.getLeftRotation()),
            new Vector3f(transformation.getScale()),
            new Quaternionf(transformation.getRightRotation())
        );
    }


    public static void splay(ECSEntity spiderEntity) {
        SpiderBody spider = spiderEntity.query(SpiderBody.class);
        if (spider == null) return;
        SpiderOptions options = spiderEntity.query(SpiderOptions.class);
        if (options == null) return;
        spiderEntity.remove();

        // detach and get this spider's entities (render ids nest as pairs rooted at the spider)
        List<BlockDisplay> entities = new ArrayList<>();
        for (var pair : RenderEntityTracker.getAll()) {
            Object id = pair.first();
            Entity entity = pair.second();
            if (!(entity instanceof BlockDisplay blockDisplay) || root(id) != spider) continue;
            entities.add(blockDisplay);
            RenderEntityTracker.detach(id);
        }

        Core.onPluginShutdown(() -> {
            for (BlockDisplay entity : entities) entity.remove();
        });


        List<BlockDisplayModelPiece> pieces = new ArrayList<>();
        for (BlockDisplayModelPiece piece : options.getBodyPlan().getBodyModel().getPieces()) {
            pieces.add(piece);
        }

        for (LegPlan legPlan : options.getBodyPlan().getLegs()) {
            for (SegmentPlan segment : legPlan.getSegments()) {
                for (BlockDisplayModelPiece piece : segment.getModel().getPieces()) pieces.add(piece);
            }
        }

        List<IndexedEntity> indexed = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) indexed.add(new IndexedEntity(i, entities.get(i)));
        Collections.shuffle(indexed);
        for (IndexedEntity indexedEntity : indexed) {
            int i = indexedEntity.index();
            BlockDisplay entity = indexedEntity.entity();
            Vector3f offset = Overloads.getPosition(entity).subtract(spider.getPosition()).toVector3f();
            Scheduler.runLater(3L + i / 4, () -> {
                splay(entity, offset);
            });
        }
    }

    private static Object root(Object id) {
        return id instanceof Pair<?, ?> pair ? root(pair.first()) : id;
    }

    private record IndexedEntity(int index, BlockDisplay entity) {
    }

    private static void splay(BlockDisplay entity, Vector3f offset) {
        Transformation start = clone(entity.getTransformation());

        Transformation end = clone(start);
        end.getTranslation()
            .add(offset)
            .normalize()
            .mul((float) ThreadLocalRandom.current().nextDouble(1.0, 3.0))
            .sub(offset);
        end.getScale().set(.35f);
        end.getLeftRotation().identity();
        end.getRightRotation().identity();

        entity.setInterpolationDuration(1);
        entity.setInterpolationDelay(0);

        float[] t = {.0f};
        Scheduler.interval(0, 1, it -> {
            t[0] = Maths.moveTowards(t[0], 1f, .07f);

            entity.setTransformation(lerp(clone(start), end, Maths.eased(t[0])));
            entity.setInterpolationDelay(0);

            if (t[0] >= 1) it.close();
        });
    }
}
