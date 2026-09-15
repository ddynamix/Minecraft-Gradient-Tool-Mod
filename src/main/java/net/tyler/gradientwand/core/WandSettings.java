package net.tyler.gradientwand.core;

// Everything the player can choose about a gradient, and nothing about how it is stored. Reading
// and writing this to an item is the platform's job, which is what keeps this record usable on a
// Minecraft version where item data works completely differently.
public record WandSettings(Mode mode, GradientAxis axis, Dither dither, Grain grain,
                           float jitter, int width, long seed) {

    public static final int MIN_WIDTH = 1;

    // The widest any tier allows. Each wand clamps further, at the platform boundary.
    public static final int MAX_WIDTH = 128;

    public enum Mode {
        STRIP, WALL, RIBBON
    }

    public enum GradientAxis {
        AUTO, HORIZONTAL, VERTICAL
    }

    public enum Dither {
        NONE, ORDERED, RANDOM
    }

    // Which way blocks that have a grain, like logs and pillars, get turned. OFF leaves them
    // exactly as they come out of the hotbar, LONGEST and SHORTEST follow the shape of the
    // selection, and the last three ignore the shape and force a world axis.
    public enum Grain {
        OFF, LONGEST, SHORTEST, EAST_WEST, NORTH_SOUTH, UP_DOWN
    }

    public static final WandSettings DEFAULT =
            new WandSettings(Mode.STRIP, GradientAxis.AUTO, Dither.NONE, Grain.LONGEST, 1.0f, 3, 0L);

    public static int clampWidth(int value) {
        return clampWidth(value, MAX_WIDTH);
    }

    public static int clampWidth(int value, int max) {
        return Math.max(MIN_WIDTH, Math.min(max, value));
    }

    public WandSettings withMode(Mode value) {
        return new WandSettings(value, axis, dither, grain, jitter, width, seed);
    }

    public WandSettings withAxis(GradientAxis value) {
        return new WandSettings(mode, value, dither, grain, jitter, width, seed);
    }

    public WandSettings withDither(Dither value) {
        return new WandSettings(mode, axis, value, grain, jitter, width, seed);
    }

    public WandSettings withGrain(Grain value) {
        return new WandSettings(mode, axis, dither, value, jitter, width, seed);
    }

    public WandSettings withJitter(float value) {
        return new WandSettings(mode, axis, dither, grain, value, width, seed);
    }

    public WandSettings withWidth(int value) {
        return new WandSettings(mode, axis, dither, grain, jitter, clampWidth(value), seed);
    }

    public WandSettings withSeed(long value) {
        return new WandSettings(mode, axis, dither, grain, jitter, width, value);
    }

    public String describe() {
        return String.format("mode %s, axis %s, dither %s, grain %s, jitter %.2f, width %d",
                mode.name().toLowerCase(),
                axis.name().toLowerCase(),
                dither.name().toLowerCase(),
                grain.name().toLowerCase(),
                jitter,
                width);
    }
}
