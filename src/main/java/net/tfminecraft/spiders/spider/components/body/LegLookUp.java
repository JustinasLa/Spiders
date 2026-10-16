package net.tfminecraft.spiders.spider.components.body;

import java.util.List;

public final class LegLookUp {
    private LegLookUp() {
    }

    public static boolean isLeftLeg(int leg) {
        return leg % 2 == 0;
    }

    public static boolean isRightLeg(int leg) {
        return !isLeftLeg(leg);
    }

    public static int getPairIndex(int leg) {
        return leg / 2;
    }

    public static boolean isDiagonal1(int leg) {
        return getPairIndex(leg) % 2 == 0 ? isLeftLeg(leg) : isRightLeg(leg);
    }

    public static boolean isDiagonal2(int leg) {
        return !isDiagonal1(leg);
    }

    public static int diagonalFront(int leg) {
        return isLeftLeg(leg) ? leg - 1 : leg - 3;
    }

    public static int diagonalBack(int leg) {
        return isLeftLeg(leg) ? leg + 3 : leg + 1;
    }

    public static int front(int leg) {
        return leg - 2;
    }

    public static int back(int leg) {
        return leg + 2;
    }

    public static int horizontal(int leg) {
        return isLeftLeg(leg) ? leg + 1 : leg - 1;
    }

    public static List<Integer> diagonal(int leg) {
        return List.of(diagonalFront(leg), diagonalBack(leg));
    }

    public static List<Integer> adjacent(int leg) {
        return List.of(front(leg), back(leg), horizontal(leg));
    }
}
