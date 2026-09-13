package dev.pageh.helper;

public final class Direction {
    private static final int[] NEXT = {1, 4, 2, 3};
    private static final int[] PREV = {2, 3, 1, 4};
    public static int effect(boolean forward, boolean reverse, int rotation, int speed) {
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation");
        if (speed != 0 && speed != 64 && speed != 128) throw new IllegalArgumentException("speed");
        return ((forward != reverse) ? NEXT[rotation] : PREV[rotation]) | speed;
    }
    private Direction() {}
}
