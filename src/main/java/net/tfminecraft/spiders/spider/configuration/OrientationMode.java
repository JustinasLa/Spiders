package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.joml.Quaternionf;

public enum OrientationMode {
    YAxis,
    SpiderOrientation,
    GroundOrientation;

    public Quaternionf get(SpiderBody spider) {
        return switch (this) {
            case YAxis -> Maths.horizontal(spider.getOrientation());
            case SpiderOrientation -> spider.getOrientation();
            case GroundOrientation -> spider.getPreferredOrientation();
        };
    }
}
