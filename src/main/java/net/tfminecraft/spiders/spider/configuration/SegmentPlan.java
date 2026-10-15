package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;

import java.util.ArrayList;

public class SegmentPlan {
    private double length;
    private IKJoint3D joint;
    private DisplayModel model;

    public SegmentPlan(double length, IKJoint3D joint, DisplayModel model) {
        this.length = length;
        this.joint = joint;
        this.model = model;
    }

    public SegmentPlan(double length, IKJoint3D joint) {
        this(length, joint, new DisplayModel(new ArrayList<>()));
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public IKJoint3D getJoint() {
        return joint;
    }

    public void setJoint(IKJoint3D joint) {
        this.joint = joint;
    }

    public DisplayModel getModel() {
        return model;
    }

    public void setModel(DisplayModel model) {
        this.model = model;
    }

    public SegmentPlan clone() {
        return new SegmentPlan(length, joint, model.clone());
    }
}
