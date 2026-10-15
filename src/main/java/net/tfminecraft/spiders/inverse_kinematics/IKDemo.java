package net.tfminecraft.spiders.inverse_kinematics;

import net.tfminecraft.spiders.spider.presets.SpiderLegModel;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.inverse_kinematics.DampedLeastSquaresIKSolver3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChain3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainPose3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainSegment3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKDebugRenderer;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKEvaluation3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKEvaluator3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKHingeJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSolver3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSwingTwistJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKTarget3D;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class IKDemo implements ECSComponent {
    private static final double TICK_SECONDS = 1.0 / 20.0;
    private static final double TARGET_LERP_PER_SECOND = 1.5;

    private static final int RETARGET_INTERVAL_MIN_TICKS = 44;
    private static final int RETARGET_INTERVAL_MAX_TICKS = 84;

    private final World world;
    private final IKChain3D chain;
    private final IKChainPose3D pose;
    private final IKSolver3D solver;
    private final IKEvaluator3D evaluator;
    private final List<DisplayModel> models;

    private final Vector targetOrigin;

    private IKTarget3D desiredTarget;

    private final IKTarget3D target;

    private int retargetTimer;

    private IKEvaluation3D evaluation;

    private boolean renderDebugVisuals = false;

    public IKDemo(
        World world,
        IKChain3D chain,
        IKChainPose3D pose,
        IKSolver3D solver,
        IKEvaluator3D evaluator,
        List<DisplayModel> models
    ) {
        this.world = world;
        this.chain = chain;
        this.pose = pose;
        this.solver = solver;
        this.evaluator = evaluator;
        this.models = models;

        this.targetOrigin = pose.getPosition().clone()
            .add(Maths.rotate(IKChain3D.getCHAIN_AXIS(), pose.getOrientation()).multiply(0.7));

        this.desiredTarget = randomTarget();

        this.target = desiredTarget.clone();

        this.retargetTimer = randomRetargetTicks();

        this.evaluation = evaluator.evaluate(chain, pose, target);
    }

    public World getWorld() {
        return world;
    }

    public IKChain3D getChain() {
        return chain;
    }

    public IKChainPose3D getPose() {
        return pose;
    }

    public IKSolver3D getSolver() {
        return solver;
    }

    public IKEvaluator3D getEvaluator() {
        return evaluator;
    }

    public List<DisplayModel> getModels() {
        return models;
    }

    public IKTarget3D getTarget() {
        return target;
    }

    public IKEvaluation3D getEvaluation() {
        return evaluation;
    }

    public boolean getRenderDebugVisuals() {
        return renderDebugVisuals;
    }

    public void setRenderDebugVisuals(boolean renderDebugVisuals) {
        this.renderDebugVisuals = renderDebugVisuals;
    }

    public void update() {
        retargetTimer -= 1;
        if (retargetTimer <= 0) {
            desiredTarget = randomTarget();
            retargetTimer = randomRetargetTicks();
        }

        // Exponential smoothing, framerate independent
        double targetLerp = 1 - Math.exp(-TICK_SECONDS * TARGET_LERP_PER_SECOND);
        target.lerp(desiredTarget, targetLerp);

        IKChainPose3D solvedPose = pose.clone();
        solver.solve(chain, solvedPose, target);

        pose.copy(solvedPose);

        evaluation = evaluator.evaluate(chain, pose, target);
    }

    public RenderGroup render() {
        RenderGroup group = new RenderGroup();
        List<IKChainWorldNode3D> nodes = chain.getWorldNodes(pose);

        for (int index = 0; index < chain.getSegments().size(); index++) {
            IKChainWorldNode3D start = nodes.get(index);
            IKChainWorldNode3D end = nodes.get(index + 1);

            Matrix4f transform = new Matrix4f()
                .rotate(end.getWorldRotation());

            for (BlockDisplayModelPiece piece : models.get(index).getPieces()) {
                group.set(new Pair<>(index, piece), RenderUtilities.renderBlock(
                    world,
                    start.getPosition(),
                    it -> {
                        it.setTeleportDuration(1);
                        it.setInterpolationDuration(1);
                        it.setBrightness(new Display.Brightness(0, 15));
                    },
                    it -> {
                        RenderUtilities.interpolateTransform(it, new Matrix4f(transform).mul(piece.getTransform()));
                        it.setBlock(piece.getBlock());
                        it.setBrightness(piece.getBrightness());
                    }
                ));
            }
        }

        group.set("target", RenderUtilities.renderBlock(
            world,
            target.getPosition(),
            it -> {
                it.setTeleportDuration(1);
                it.setBrightness(new Display.Brightness(15, 15));
                it.setTransformation(Geometry.centredTransform(.3f, .3f, .3f));
            },
            it -> {
                if (evaluation.isSolved()) {
                    it.setBlock(Material.LIME_CONCRETE.createBlockData());
                } else {
                    it.setBlock(Material.RED_CONCRETE.createBlockData());
                }
            }
        ));

        group.set("endEffector", RenderUtilities.renderBlock(
            world,
            nodes.get(nodes.size() - 1).getPosition(),
            it -> {
                it.setTeleportDuration(1);
                it.setBrightness(new Display.Brightness(15, 15));
                it.setTransformation(Geometry.centredTransform(.16f, .16f, .16f));
            },
            it -> it.setBlock(Material.DIAMOND_BLOCK.createBlockData())
        ));

        if (renderDebugVisuals) {
            group.set("ikGuides", IKDebugRenderer.renderIKGuides(
                world,
                chain,
                pose,
                target
            ));
        }

        return group;
    }

    private int randomRetargetTicks() {
        return ThreadLocalRandom.current().nextInt(RETARGET_INTERVAL_MIN_TICKS, RETARGET_INTERVAL_MAX_TICKS);
    }

    private Vector randomVectorInRadius(double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double theta = random.nextDouble(0.0, Math.PI * 2);
        double vertical = random.nextDouble(-1.0, 1.0);
        double horizontal = Math.sqrt(1 - vertical * vertical);
        double length = Math.cbrt(random.nextDouble()) * (max - min) + min;

        return new Vector(
            horizontal * Math.cos(theta),
            vertical,
            horizontal * Math.sin(theta)
        ).multiply(length);
    }

    private IKTarget3D randomTarget() {
        double totalReach = chain.getTotalLength();
        Vector position = randomVectorInRadius(totalReach * 0.2, totalReach * 0.8).add(targetOrigin);
        return new IKTarget3D(position);
    }

    public static IKDemo create(World world, Location location) {
        IKChain3D chain = createDemoChain();
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.01);

        return new IKDemo(
            world,
            chain,
            chain.createPose(location.toVector(), new Quaternionf().rotateX((float) (-Math.PI / 2))),
            DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D(evaluator),
            evaluator,
            legModelsFor(chain.getSegments())
        );
    }

    private static IKChain3D createDemoChain() {
        return new IKChain3D(
            List.of(
                new IKChainSegment3D(
                    0.80,
                    new IKSwingTwistJoint3D(
                        0.20 * Math.PI,
                        -Math.PI,
                        Math.PI,
                        new Vector(0, 1, 0)
                    )
                ),
                new IKChainSegment3D(
                    1.05,
                    new IKHingeJoint3D(
                        -0.30 * Math.PI,
                        0.30 * Math.PI,
                        new Vector(1, 0, 0),
                        new Vector(0, 0, 1)
                    )
                ),
                new IKChainSegment3D(
                    0.95,
                    new IKHingeJoint3D(
                        -0.30 * Math.PI,
                        0.30 * Math.PI,
                        new Vector(1, 0, 0),
                        new Vector(0, 0, 1)
                    )
                ),
                new IKChainSegment3D(
                    0.80,
                    new IKSwingTwistJoint3D(
                        0.22 * Math.PI,
                        -0.4 * Math.PI,
                        0.4 * Math.PI,
                        new Vector(0, 1, 0)
                    )
                ),
                new IKChainSegment3D(
                    0.65,
                    new IKSwingTwistJoint3D(
                        0.25 * Math.PI,
                        -0.4 * Math.PI,
                        0.4 * Math.PI,
                        new Vector(0, 1, 0)
                    )
                )
            )
        );
    }

    private static List<DisplayModel> legModelsFor(List<IKChainSegment3D> segments) {
        List<DisplayModel> models = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            IKChainSegment3D segment = segments.get(index);
            DisplayModel model;
            if (index == 0) {
                model = SpiderLegModel.getBASE();
            } else if (index == segments.size() - 2) {
                model = SpiderLegModel.getTIBIA();
            } else if (index == segments.size() - 1) {
                model = SpiderLegModel.getTIP();
            } else {
                model = SpiderLegModel.getFEMUR();
            }

            DisplayModel scaled = model.clone();
            scaled.scale(1f, 1f, (float) segment.getLength());
            models.add(scaled);
        }
        return models;
    }

    public static void setupIKDemo(ECS app) {
        app.onTick(ecs -> {
            for (IKDemo demo : app.query(IKDemo.class)) {
                demo.update();
            }
        });

        app.onRender(ecs -> {
            for (IKDemo demo : app.query(IKDemo.class)) {
                demo.render().submit(demo);
            }
        });
    }
}
