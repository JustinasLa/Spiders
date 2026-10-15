package net.tfminecraft.spiders.spider.components.body;

import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.utilities.inverse_kinematics.DampedLeastSquaresIKSolver3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChain3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainPose3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainSegment3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKEvaluator3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSolver3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKTarget3D;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public class SpiderLegIK {
    private final LegPlan plan;
    private final IKChain3D chain;
    private final IKChainPose3D pose;
    private final IKSolver3D solver;
    private final IKTarget3D target;

    private List<IKChainWorldNode3D> nodes = List.of();

    public SpiderLegIK(LegPlan plan, IKChain3D chain, IKChainPose3D pose, IKSolver3D solver, IKTarget3D target) {
        this.plan = plan;
        this.chain = chain;
        this.pose = pose;
        this.solver = solver;
        this.target = target;
    }

    public LegPlan getPlan() {
        return plan;
    }

    public IKChain3D getChain() {
        return chain;
    }

    public IKChainPose3D getPose() {
        return pose;
    }

    public List<IKChainWorldNode3D> getNodes() {
        return nodes;
    }

    public List<Vector> getSegmentTipPositions() {
        List<Vector> out = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) out.add(nodes.get(i).getPosition());
        return out;
    }

    public List<Quaternionf> getSegmentModelRotations() {
        List<Quaternionf> out = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) out.add(new Quaternionf(nodes.get(i).getWorldRotation()));
        return out;
    }

    public Vector attachmentPosition(Vector spiderPosition, Quaternionf spiderOrientation) {
        return Maths.rotate(plan.getAttachmentPosition().clone(), spiderOrientation).add(spiderPosition);
    }

    public void solve(Vector spiderPosition, Quaternionf spiderOrientation, Vector endEffector) {
        pose.getPosition().copy(attachmentPosition(spiderPosition, spiderOrientation));
        pose.getOrientation().set(spiderOrientation).mul(plan.getRestOrientation());

        target.getPosition().copy(endEffector);

        solver.solve(chain, pose, target);

        nodes = chain.getWorldNodes(pose);
    }

    public static SpiderLegIK createSpiderLegIK(LegPlan plan) {
        List<IKChainSegment3D> segments = new ArrayList<>();
        for (SegmentPlan it : plan.getSegments()) segments.add(new IKChainSegment3D(it.getLength(), it.getJoint()));
        IKChain3D chain = new IKChain3D(
            segments
        );

        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.01);

        return new SpiderLegIK(
            plan,
            chain,
            chain.createPose(new Vector(0.0, 0.0, 0.0), new Quaternionf(plan.getRestOrientation())),
            DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D(evaluator),
            new IKTarget3D(new Vector(0.0, 0.0, 0.0))
        );
    }
}
