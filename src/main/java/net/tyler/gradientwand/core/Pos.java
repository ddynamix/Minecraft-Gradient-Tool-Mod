package net.tyler.gradientwand.core;

// An integer block position. The platform converts to and from BlockPos at its boundary.
public record Pos(int x, int y, int z) {

    // Minecraft packs a position into a long as 26 bits of X, 26 of Z and 12 of Y, and the random
    // dither hashes that packed value. The layout is reproduced here rather than invented: a
    // different packing would still hash fine, but it would shift every dithered gradient and
    // invalidate the uniformity and decorrelation these thresholds were measured for.
    private static final int SIZE_BITS_X = 26;
    private static final int SIZE_BITS_Z = 26;
    private static final int SIZE_BITS_Y = 64 - SIZE_BITS_X - SIZE_BITS_Z;

    private static final long BITS_X = (1L << SIZE_BITS_X) - 1L;
    private static final long BITS_Y = (1L << SIZE_BITS_Y) - 1L;
    private static final long BITS_Z = (1L << SIZE_BITS_Z) - 1L;

    private static final int BIT_SHIFT_Z = SIZE_BITS_Y;
    private static final int BIT_SHIFT_X = SIZE_BITS_Y + SIZE_BITS_Z;

    public long asLong() {
        long packed = 0L;

        packed |= ((long) x & BITS_X) << BIT_SHIFT_X;
        packed |= ((long) y & BITS_Y);
        packed |= ((long) z & BITS_Z) << BIT_SHIFT_Z;

        return packed;
    }

    // The middle of the block, which is what every distance and projection in the ribbon works in
    public Vec3 center() {
        return new Vec3(x + 0.5, y + 0.5, z + 0.5);
    }

    public int get(Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public static Pos of(int[] xyz) {
        return new Pos(xyz[0], xyz[1], xyz[2]);
    }
}
