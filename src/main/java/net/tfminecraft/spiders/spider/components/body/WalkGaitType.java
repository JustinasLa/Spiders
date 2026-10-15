package net.tfminecraft.spiders.spider.components.body;

import java.util.ArrayList;
import java.util.List;

public final class WalkGaitType {
    private WalkGaitType() {
    }

    public static List<Leg> getLegsInUpdateOrder(SpiderBody spider) {
        List<Leg> legs = spider.getLegs();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) if (LegLookUp.isDiagonal1(i)) indices.add(i);
        for (int i = 0; i < legs.size(); i++) if (LegLookUp.isDiagonal2(i)) indices.add(i);
        List<Leg> out = new ArrayList<>();
        for (int it : indices) out.add(spider.getLegs().get(it));
        return out;
    }

    public static boolean canMoveLeg(Leg leg) {
        SpiderBody spider = leg.getSpider();
        int index = spider.getLegs().indexOf(leg);

        // always move if the target is not on ground
        if (!leg.getTarget().isGrounded()) return true;

        leg.setPrimary(true);

        // ensure other pair is grounded
        // ignore if disabled, ignore if target is not grounded
        List<Leg> crossPair = GaitType.unIndexLeg(spider, LegLookUp.adjacent(index));
        for (Leg it : crossPair) {
            if (!it.isGrounded() && !it.isDisabled() && it.getTarget().isGrounded()) return false;
        }

        // cooldown
        for (Leg it : crossPair) {
            if (it.getTarget().isGrounded() && it.getTimeSinceStopMove() < leg.getOptions().getGait().getCrossPairCooldown()) return false;
        }
        List<Leg> samePair = GaitType.unIndexLeg(spider, LegLookUp.diagonal(index));
        for (Leg it : samePair) {
            if (it.getTarget().isGrounded() && it.getTimeSinceBeginMove() < leg.getOptions().getGait().getSamePairCooldown()) return false;
        }

        boolean wantsToMove = leg.isOutsideTriggerZone() || !leg.getTouchingGround();
        boolean alreadyAtTarget = leg.getEndEffector().distanceSquared(leg.getTarget().getPosition()) < 0.01;
        boolean onGround = false;
        for (Leg it : spider.getLegs()) {
            if (it.isGrounded()) {
                onGround = true;
                break;
            }
        }
        onGround = onGround || spider.getOnGround();

        return wantsToMove && !alreadyAtTarget && onGround;
    }
}
