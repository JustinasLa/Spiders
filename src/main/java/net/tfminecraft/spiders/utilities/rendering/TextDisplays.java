package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TextDisplays {
    private TextDisplays() {
    }

    public static Matrix4f getTextDisplayUnitSquare() {
        return new Matrix4f()
            .translate(-0.1f + .5f, -0.5f + .5f, 0f)
            .scale(8.0f, 4.0f, 1f); //  + 0.003f  + 0.001f
    }

    // Left aligned
    public static List<Matrix4f> getTextDisplayUnitTriangle() {
        return List.of(
            // Left
            new Matrix4f().scale(.5f).mul(getTextDisplayUnitSquare()),
            // Right
            Maths.shear(new Matrix4f().scale(.5f).translate(1f, 0f, 0f), 0f, 0f, -1f, 0f, 0f, 0f).mul(getTextDisplayUnitSquare()),
            // Top
            Maths.shear(new Matrix4f().scale(.5f).translate(0f, 1f, 0f), -1f, 0f, 0f, 0f, 0f, 0f).mul(getTextDisplayUnitSquare())
        );
    }

    // Right aligned
//private val textDisplayUnitTriangleMutable = listOf(
//    // Right
//    Matrix4f().scale(.5f).shear(1f, 0f, 0f).mul(textDisplayUnitSquare),
//    // Left
//    Matrix4f().scale(.5f).translate(1f, 0f, 0f).mul(textDisplayUnitSquare),
//    // Top
//    Matrix4f().scale(.5f).translate(1f,0f,0f).shear(0f, 1f, 0f).mul(textDisplayUnitSquare),
//)

    public static TextDisplayTriangleResult textDisplayTriangle(
        Vector3f point1,
        Vector3f point2,
        Vector3f point3
    ) {
        Vector3f p2 = new Vector3f(point2).sub(point1);
        Vector3f p3 = new Vector3f(point3).sub(point1);

        Vector3f zAxis = new Vector3f(p2).cross(p3).normalize();
        Vector3f xAxis = new Vector3f(p2).normalize();
        Vector3f yAxis = new Vector3f(zAxis).cross(xAxis).normalize();

        float width = p2.length();
        float height = new Vector3f(p3).dot(yAxis);
        float p3Width = new Vector3f(p3).dot(xAxis);

        Quaternionf rotation = new Quaternionf().lookAlong(new Vector3f(zAxis).mul(-1f), yAxis).conjugate();

        float shear = p3Width / width;
        // val shear = - (1f - p3Width / width)

        Matrix4f transform = Maths.shear(new Matrix4f()
            .translate(point1)
            .rotate(rotation)
            .scale(width, height, 1f), 0f, 0f, shear, 0f, 0f, 0f);

        List<Matrix4f> transforms = new ArrayList<>();
        for (Matrix4f unit : getTextDisplayUnitTriangle()) transforms.add(new Matrix4f(transform).mul(unit));

        return new TextDisplayTriangleResult(
            transforms,
            xAxis,
            yAxis,
            zAxis,
            height,
            width,
            rotation,
            shear
        );
    }



    public static void cull(TextDisplay display) {
        Vector3f visiblePosition = Overloads.getPosition(display).toVector3f().add(display.getTransformation().getTranslation());
        Vector3f normal = Maths.normal(display.getTransformation());

        for (Player player : Bukkit.getOnlinePlayers()) {
            Vector3f direction = Overloads.getEyePosition(player).toVector3f().sub(visiblePosition);
            float angle = direction.angle(normal);

            boolean isFacing = angle < Maths.toRadians(95.0f);
            if (isFacing) {
                player.showEntity(Core.getCurrentPlugin(), display);
            } else {
                player.hideEntity(Core.getCurrentPlugin(), display);
            }
        }
    }

    public static void interpolateTriangleTransform(TextDisplay display, Matrix4f matrix) {
        Transformation oldTransformation = display.getTransformation();
        display.setTransformationMatrix(matrix);

        Vector3f rightRotationChange = new Quaternionf(oldTransformation.getRightRotation()).difference(display.getTransformation().getRightRotation())
            .getEulerAnglesXYZ(new Vector3f());

        if (Math.abs(rightRotationChange.z) >= Maths.toRadians(45f)) {
            Transformation transformation = display.getTransformation();
            float rot = Maths.toRadians(-90f) * Math.signum(rightRotationChange.z);

            transformation.getLeftRotation().rotateZ(-rot);
            Vector3f scale = transformation.getScale();
            scale.set(scale.y, scale.x, scale.z);
            transformation.getRightRotation().set(new Quaternionf().rotateZ(rot).mul(transformation.getRightRotation()));
            display.setTransformation(transformation);
        }

        if (Objects.equals(oldTransformation, display.getTransformation())) return;
        display.setInterpolationDelay(0);
    }
}
