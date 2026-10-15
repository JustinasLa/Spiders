package net.tfminecraft.spiders.spider.components.body;

import java.util.ArrayList;
import java.util.List;

public enum GaitType {
    WALK,
    GALLOP;

    public boolean canMoveLeg(Leg leg) {
        return switch (this) {
            case WALK -> WalkGaitType.canMoveLeg(leg);
            case GALLOP -> GallopGaitType.canMoveLeg(leg);
        };
    }

    public List<Leg> getLegsInUpdateOrder(SpiderBody spider) {
        return switch (this) {
            case WALK -> WalkGaitType.getLegsInUpdateOrder(spider);
            case GALLOP -> GallopGaitType.getLegsInUpdateOrder(spider);
        };
    }

//fun hasCooldown(leg: Leg, cooldown: Int): Boolean {
//    return /*leg.isMoving && */leg.target.isGrounded && leg.timeSinceBeginMove < cooldown
//}

    public static List<Leg> unIndexLeg(SpiderBody spider, List<Integer> indices) {
        List<Leg> out = new ArrayList<>();
        List<Leg> legs = spider.getLegs();
        for (int it : indices) {
            if (it >= 0 && it < legs.size()) out.add(legs.get(it));
        }
        return out;
    }
}
