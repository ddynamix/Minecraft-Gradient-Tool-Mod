package net.tyler.gradientwand.core;

import java.util.ArrayList;
import java.util.List;

// Every decision the wand makes about which positions to fill and which palette entry goes where.
// Deliberately free of any Minecraft import: this is the part that has to survive a port to another
// Minecraft version or another mod loader unchanged, and it is the part whose behaviour has been
// measured rather than assumed.
public final class GradientCore {

    private GradientCore() {
    }

    // One position the wand intends to fill, and which hotbar slot it takes its block from. An
    // index rather than a block, because the core has no idea what a block is.
    public record PlannedCell(Pos pos, int palette) {
    }

    // A finished plan: the cells, and the one axis any grained block among them should run along.
    // A null grain means leave every block in its default orientation.
    public record Plan(List<PlannedCell> cells, Axis grain) {
    }

    // Everything a plan depends on. The eye and look vectors are only consulted for ribbons, where
    // the wall has to face the player at the moment they set point B.
    public record Selection(Pos from, Pos to, int paletteSize, WandSettings settings,
                            Vec3 eye, Vec3 look, boolean anchorLeft) {
    }

    // How many blocks a line from one point to the other needs
    public static int blocksInLine(Pos from, Pos to) {
        int dx = Math.abs(to.x() - from.x());
        int dy = Math.abs(to.y() - from.y());
        int dz = Math.abs(to.z() - from.z());

        return Math.max(dx, Math.max(dy, dz)) + 1;
    }

    public static Plan plan(Selection selection) {
        if (selection.settings().mode() == WandSettings.Mode.RIBBON) {
            return planRibbon(selection);
        }

        if (selection.settings().mode() == WandSettings.Mode.WALL) {
            return fillBetween(selection.from(), selection.to(), selection.paletteSize(),
                    resolveGradientAxis(selection.settings().axis(), selection.from(), selection.to()),
                    selection.settings());
        }

        return planLine(selection.from(), selection.to(), selection.paletteSize(), selection.settings());
    }

    // A spread ordering of the 64 cells in a 4x4x4 block, so neighbouring positions get very
    // different thresholds. Built by greedy maximum-distance placement and then measured: no
    // axis-aligned plane is all-low or all-high, which is the artefact that shows up as banding.
    private static final int[] DITHER_4 = {
            0, 16, 24, 18, 36, 40,  2, 42,  4, 20, 26, 22, 38, 44,  6, 46,
            28,  8, 30, 10, 48, 56, 50, 58, 32, 12, 34, 14, 52, 60, 54, 62,
            27, 23,  5, 21,  7, 47, 39, 45, 25, 19,  1, 17,  3, 43, 37, 41,
            35, 15, 33, 13, 55, 63, 53, 61, 31, 11, 29,  9, 51, 59, 49, 57
    };

    // Which palette entry a position gets. With dither off this is exactly an integer division.
    // With it on, a position part way between two entries sometimes takes the next.
    private static int pick(int paletteSize, int along, int count, Pos pos, WandSettings settings) {
        float exact = (float) along * paletteSize / count;
        int band = (int) exact;

        if (settings.dither() != WandSettings.Dither.NONE && settings.jitter() > 0.0f) {
            float threshold = settings.dither() == WandSettings.Dither.ORDERED
                    ? orderedThreshold(pos)
                    : randomThreshold(settings.seed(), pos);

            if (threshold < (exact - band) * settings.jitter()) {
                band++;
            }
        }

        return Math.min(band, paletteSize - 1);
    }

    // Regular repeating pattern, needs no seed and looks the same on every machine.
    // "& 3" is the low two bits, which is the right answer for negative coordinates too.
    private static float orderedThreshold(Pos pos) {
        int index = ((pos.x() & 3) << 4) | ((pos.y() & 3) << 2) | (pos.z() & 3);

        return (DITHER_4[index] + 0.5f) / 64.0f;
    }

    // A hash, not a random number generator. The same position and seed always give the same
    // value, which is what keeps the preview steady and identical to what gets placed.
    private static float randomThreshold(long seed, Pos pos) {
        long h = (seed * 0xD6E8FEB86659FD93L) ^ (pos.asLong() * 0x9E3779B97F4A7C15L);

        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        return (h >>> 40) / (float) (1 << 24);
    }

    private static Plan planRibbon(Selection selection) {
        Pos from = selection.from();
        Pos to = selection.to();
        int paletteSize = selection.paletteSize();
        WandSettings settings = selection.settings();

        Vec3 origin = from.center();
        Vec3 line = to.center().subtract(origin);
        Vec3 spine = line.lengthSquared() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : line.normalize();
        Vec3 normal = ribbonNormal(from, to, selection.eye());
        Vec3 side = ribbonSide(from, to, selection.eye(), selection.look());

        double length = Math.sqrt(line.lengthSquared());
        int width = settings.width();
        int count = blocksInLine(from, to);

        // Sneaking grows the width from one edge instead of from the middle
        int lowest = selection.anchorLeft() ? -(width - 1) : -((width - 1) / 2);
        int highest = lowest + width - 1;

        // Walk one block per column along the axis the wall faces most. That guarantees exactly
        // one block per column, so the surface is solid instead of full of diagonal pinholes.
        int axis = dominantOf(normal);
        int first = (axis + 1) % 3;
        int second = (axis + 2) % 3;

        int[] min = new int[3];
        int[] max = new int[3];

        boundsOf(origin, spine, side, length, lowest, highest, min, max);

        List<PlannedCell> planned = new ArrayList<>();
        Axis grain = ribbonGrain(spine, side, count, width, settings);
        double plane = normal.dot(origin);

        for (int u = min[first]; u <= max[first]; u++) {
            for (int v = min[second]; v <= max[second]; v++) {
                double known = normal.get(first) * (u + 0.5) + normal.get(second) * (v + 0.5);
                int along = (int) Math.round((plane - known) / normal.get(axis) - 0.5);

                int[] xyz = new int[3];

                xyz[first] = u;
                xyz[second] = v;
                xyz[axis] = along;

                Pos pos = Pos.of(xyz);
                Vec3 offset = pos.center().subtract(origin);

                double down = offset.dot(spine);
                double across = offset.dot(side);

                if (down < -0.5 || down > length + 0.5) {
                    continue;
                }

                if (across < lowest - 0.5 || across > highest + 0.5) {
                    continue;
                }

                // The gradient runs along the line, so read the band off this block's own
                // position rather than off a spine step
                int step = Math.max(0, Math.min(count - 1,
                        (int) Math.round(length < 1.0E-6 ? 0.0 : down / length * (count - 1))));

                planned.add(new PlannedCell(pos, pick(paletteSize, step, count, pos, settings)));
            }
        }

        return new Plan(planned, grain);
    }

    // The corner of the block grid the ribbon can possibly touch
    private static void boundsOf(Vec3 origin, Vec3 spine, Vec3 side, double length,
                                 int lowest, int highest, int[] min, int[] max) {
        for (int i = 0; i < 3; i++) {
            min[i] = Integer.MAX_VALUE;
            max[i] = Integer.MIN_VALUE;
        }

        for (double down : new double[]{0.0, length}) {
            for (double across : new double[]{lowest - 0.5, highest + 0.5}) {
                Vec3 corner = origin.add(spine.multiply(down)).add(side.multiply(across));

                for (int i = 0; i < 3; i++) {
                    int value = (int) Math.floor(corner.get(i));

                    min[i] = Math.min(min[i], value - 1);
                    max[i] = Math.max(max[i], value + 1);
                }
            }
        }
    }

    private static int dominantOf(Vec3 vector) {
        double x = Math.abs(vector.x());
        double y = Math.abs(vector.y());
        double z = Math.abs(vector.z());

        if (x >= y && x >= z) {
            return 0;
        }

        return y >= z ? 1 : 2;
    }

    // Perpendicular to the line, pointing at the player. Shared by the side vector and the
    // column walk, so the two can never disagree about which way the wall faces.
    private static Vec3 ribbonNormal(Pos from, Pos to, Vec3 eye) {
        Vec3 line = to.center().subtract(from.center());
        Vec3 spine = line.lengthSquared() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : line.normalize();

        Vec3 middle = from.center().add(to.center()).multiply(0.5);
        Vec3 toPlayer = eye == null ? new Vec3(0.0, 1.0, 0.0) : eye.subtract(middle);

        Vec3 normal = toPlayer.subtract(spine.multiply(toPlayer.dot(spine)));

        if (normal.lengthSquared() < 1.0E-4) {
            // Standing on the line itself: fall back to as upright as the line allows
            Vec3 up = new Vec3(0.0, 1.0, 0.0);

            normal = up.subtract(spine.multiply(up.dot(spine)));

            if (normal.lengthSquared() < 1.0E-4) {
                Vec3 east = new Vec3(1.0, 0.0, 0.0);

                normal = east.subtract(spine.multiply(east.dot(spine)));
            }
        }

        return normal.normalize();
    }

    // Perpendicular to the line, inside the plane whose normal points at the player
    private static Vec3 ribbonSide(Pos from, Pos to, Vec3 eye, Vec3 look) {
        Vec3 line = to.center().subtract(from.center());
        Vec3 spine = line.lengthSquared() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : line.normalize();

        Vec3 side = spine.cross(ribbonNormal(from, to, eye)).normalize();
        Vec3 left = look == null
                ? new Vec3(0.0, 0.0, 1.0)
                : new Vec3(0.0, 1.0, 0.0).cross(look);

        if (left.lengthSquared() > 1.0E-6 && Math.abs(side.dot(left.normalize())) > 0.1) {
            // Point it at the player's left where that means anything
            return side.dot(left.normalize()) < 0.0 ? side.multiply(-1.0) : side;
        }

        // A ribbon growing vertically has no left, so sneaking builds downwards
        return side.y() > 0.0 ? side.multiply(-1.0) : side;
    }

    private static Plan planLine(Pos from, Pos to, int paletteSize, WandSettings settings) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();

        int count = blocksInLine(from, to);
        int steps = count - 1;

        Axis grain = grainOf(from, to, settings);

        List<PlannedCell> planned = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;

            Pos pos = new Pos(
                    from.x() + (int) Math.round(t * dx),
                    from.y() + (int) Math.round(t * dy),
                    from.z() + (int) Math.round(t * dz));

            planned.add(new PlannedCell(pos, pick(paletteSize, i, count, pos, settings)));
        }

        return new Plan(planned, grain);
    }

    // Fills every position between the two corners, the gradient running along one axis
    private static Plan fillBetween(Pos from, Pos to, int paletteSize, Axis axis, WandSettings settings) {
        int minX = Math.min(from.x(), to.x());
        int minY = Math.min(from.y(), to.y());
        int minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x());
        int maxY = Math.max(from.y(), to.y());
        int maxZ = Math.max(from.z(), to.z());

        int start = switch (axis) {
            case X -> minX;
            case Y -> minY;
            case Z -> minZ;
        };

        int count = sizeAlong(from, to, axis);

        Axis grain = grainOf(from, to, settings);

        List<PlannedCell> planned = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int coordinate = switch (axis) {
                        case X -> x;
                        case Y -> y;
                        case Z -> z;
                    };
                    Pos pos = new Pos(x, y, z);

                    planned.add(new PlannedCell(pos,
                            pick(paletteSize, coordinate - start, count, pos, settings)));
                }
            }
        }

        return new Plan(planned, grain);
    }

    // Which way the grain runs, or null when the setting is off. LONGEST on a strip is the line's
    // own direction; on a wall it is the longest edge of the rectangle. No default branch on
    // purpose: adding a Grain value later will not compile until it is handled here.
    private static Axis grainOf(Pos from, Pos to, WandSettings settings) {
        return switch (settings.grain()) {
            case OFF -> null;
            case LONGEST -> dominantAxis(from, to);
            case SHORTEST -> middleAxis(from, to);
            case EAST_WEST -> Axis.X;
            case NORTH_SOUTH -> Axis.Z;
            case UP_DOWN -> Axis.Y;
        };
    }

    // A ribbon's two sides are its length and its width, and neither is bound to a world axis,
    // so whichever one the setting asks for is snapped to the nearest axis.
    private static Axis ribbonGrain(Vec3 spine, Vec3 side, int count, int width, WandSettings settings) {
        boolean lengthWins = count >= width;

        return switch (settings.grain()) {
            case OFF -> null;
            case LONGEST -> Axis.of(dominantOf(lengthWins ? spine : side));
            case SHORTEST -> Axis.of(dominantOf(lengthWins ? side : spine));
            case EAST_WEST -> Axis.X;
            case NORTH_SOUTH -> Axis.Z;
            case UP_DOWN -> Axis.Y;
        };
    }

    // Where point B ends up once the mode, sneaking and the wand's own limits have had their say.
    // The preview and the click both go through here, so the two can never disagree.
    public static Pos resolveEnd(Pos from, Pos raw, WandSettings settings, boolean sneaking, long limit) {
        return clampToLimit(from, shapeEnd(from, raw, settings, sneaking), settings, limit);
    }

    // Point B as the mode and sneaking alone would have it, before any limit is applied. Kept
    // separate so a click can tell whether the wand shortened the selection.
    public static Pos shapeEnd(Pos from, Pos raw, WandSettings settings, boolean sneaking) {
        // A ribbon is never snapped: sneaking shifts where its width grows from, nothing more
        if (settings.mode() == WandSettings.Mode.RIBBON) {
            return raw;
        }

        // A wall is always exactly one block thick. The axis the two points differ least on is
        // collapsed onto point A, so when they already share a plane this changes nothing at all
        // and only a genuine box gets squashed. Sneaking keeps Y in the wall, so it stands upright.
        if (settings.mode() == WandSettings.Mode.WALL) {
            return flatten(from, raw, wallNormal(from, raw, sneaking));
        }

        if (sneaking) {
            return snapToAxis(from, raw, dominantAxis(from, raw));
        }

        return raw;
    }

    // Which axis gets squashed to a single block thick
    private static Axis wallNormal(Pos from, Pos to, boolean upright) {
        // Sneaking keeps Y inside the wall, so the wall always stands upright
        if (upright) {
            int dx = Math.abs(to.x() - from.x());
            int dz = Math.abs(to.z() - from.z());

            return dx <= dz ? Axis.X : Axis.Z;
        }

        // The axis to collapse is the thinnest one, which is exactly the shortest side
        return shortestAxis(from, to);
    }

    // Collapses one axis, turning the selection into a flat rectangle
    private static Pos flatten(Pos from, Pos to, Axis normal) {
        return switch (normal) {
            case X -> new Pos(from.x(), to.y(), to.z());
            case Y -> new Pos(to.x(), from.y(), to.z());
            case Z -> new Pos(to.x(), to.y(), from.z());
        };
    }

    // HORIZONTAL and VERTICAL mean "along the wall", not a world axis, so a wall running north
    // to south and one running east to west both do the sensible thing. Length never comes into it.
    private static Axis resolveGradientAxis(WandSettings.GradientAxis choice, Pos from, Pos to) {
        return switch (choice) {
            case VERTICAL -> sizeAlong(from, to, Axis.Y) > 1 ? Axis.Y : dominantAxis(from, to);
            case HORIZONTAL -> resolveHorizontal(from, to);
            case AUTO -> dominantAxis(from, to);
        };
    }

    // Whichever of X and Z actually lies in the wall. An upright wall only has one of them, so
    // there is nothing to guess. A flat floor has both, and only then does the longer side win.
    private static Axis resolveHorizontal(Pos from, Pos to) {
        int sizeX = sizeAlong(from, to, Axis.X);
        int sizeZ = sizeAlong(from, to, Axis.Z);

        if (sizeX <= 1 && sizeZ <= 1) {
            return dominantAxis(from, to);
        }

        return sizeX >= sizeZ ? Axis.X : Axis.Z;
    }

    private static int sizeAlong(Pos from, Pos to, Axis axis) {
        return Math.abs(to.get(axis) - from.get(axis)) + 1;
    }

    // long, not int: a 1300 block cube already overflows an int
    private static long blocksInBox(Pos from, Pos to) {
        long sizeX = Math.abs(to.x() - from.x()) + 1L;
        long sizeY = Math.abs(to.y() - from.y()) + 1L;
        long sizeZ = Math.abs(to.z() - from.z()) + 1L;

        return sizeX * sizeY * sizeZ;
    }

    public static long sizeOf(Pos from, Pos to, WandSettings settings) {
        return switch (settings.mode()) {
            case WALL -> blocksInBox(from, to);
            case RIBBON -> (long) blocksInLine(from, to) * settings.width();
            case STRIP -> blocksInLine(from, to);
        };
    }

    // Walks the end point back toward A until the selection fits what the wand can actually do,
    // so aiming too far builds as far as it reaches instead of being refused outright.
    private static Pos clampToLimit(Pos from, Pos to, WandSettings settings, long limit) {
        if (sizeOf(from, to, settings) <= limit) {
            return to;
        }

        int steps = Math.max(Math.abs(to.x() - from.x()),
                Math.max(Math.abs(to.y() - from.y()), Math.abs(to.z() - from.z())));

        // A selection only ever grows as its end point moves away from A, so the furthest one that
        // still fits can be found by bisection instead of by trying every step in turn
        int low = 0;
        int high = steps;

        while (low < high) {
            int middle = (low + high + 1) / 2;

            if (sizeOf(from, stepAlong(from, to, middle, steps), settings) <= limit) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return stepAlong(from, to, low, steps);
    }

    // The point that many whole steps of the way from A to B, rounded exactly as planLine does
    private static Pos stepAlong(Pos from, Pos to, int step, int steps) {
        if (steps == 0) {
            return from;
        }

        double t = (double) step / steps;

        return new Pos(
                from.x() + (int) Math.round(t * (to.x() - from.x())),
                from.y() + (int) Math.round(t * (to.y() - from.y())),
                from.z() + (int) Math.round(t * (to.z() - from.z())));
    }

    // Which axis the two points are furthest apart on
    private static Axis dominantAxis(Pos from, Pos to) {
        int dx = Math.abs(to.x() - from.x());
        int dy = Math.abs(to.y() - from.y());
        int dz = Math.abs(to.z() - from.z());

        if (dx >= dy && dx >= dz) {
            return Axis.X;
        }

        return dy >= dz ? Axis.Y : Axis.Z;
    }

    // Which axis the two points are closest together on. On a wall that is the single block of
    // thickness, which is why this is the axis the wall collapses along and not the one the
    // SHORTEST grain setting uses.
    private static Axis shortestAxis(Pos from, Pos to) {
        int dx = Math.abs(to.x() - from.x());
        int dy = Math.abs(to.y() - from.y());
        int dz = Math.abs(to.z() - from.z());

        if (dx <= dy && dx <= dz) {
            return Axis.X;
        }

        return dy <= dz ? Axis.Y : Axis.Z;
    }

    // The second shortest of the three extents: the one axis that is neither the longest nor the
    // shortest. On a wall the shortest is the single block of thickness, so this is the shorter of
    // the two edges that actually lie in the wall, which is what "shortest side" means to look at.
    private static Axis middleAxis(Pos from, Pos to) {
        Axis longest = dominantAxis(from, to);
        Axis shortest = shortestAxis(from, to);

        if (longest != Axis.X && shortest != Axis.X) {
            return Axis.X;
        }

        if (longest != Axis.Y && shortest != Axis.Y) {
            return Axis.Y;
        }

        return Axis.Z;
    }

    // Keeps the end point's coordinate on one axis and takes the other two from the start
    private static Pos snapToAxis(Pos from, Pos to, Axis axis) {
        return switch (axis) {
            case X -> new Pos(to.x(), from.y(), from.z());
            case Y -> new Pos(from.x(), to.y(), from.z());
            case Z -> new Pos(from.x(), from.y(), to.z());
        };
    }

    // Manhattan distance. On a wall this makes the wavefront a diagonal line sweeping out from
    // point A; on a strip it is simply the order along the line. Takes loose components as well as
    // positions, so the placement queue can sort thousands of blocks without allocating a Pos for
    // every comparison.
    public static int waveDistance(int fromX, int fromY, int fromZ, int x, int y, int z) {
        return Math.abs(x - fromX) + Math.abs(y - fromY) + Math.abs(z - fromZ);
    }

    public static int waveDistance(Pos from, Pos pos) {
        return waveDistance(from.x(), from.y(), from.z(), pos.x(), pos.y(), pos.z());
    }
}
