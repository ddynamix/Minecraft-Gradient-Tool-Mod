package net.tyler.gradientwand.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The properties here were each measured before being relied on, and several of them cost real
// debugging to get right: the ribbon used to lose 3% of its blocks to pinholes, and the wall used
// to come out as a solid box. They are asserted rather than remembered so that the port to a newer
// Minecraft fails loudly instead of quietly changing what the wand builds.
class GradientCoreTest {

    private static WandSettings settings(WandSettings.Mode mode, int width) {
        return new WandSettings(mode, WandSettings.GradientAxis.AUTO, WandSettings.Dither.NONE,
                WandSettings.Grain.LONGEST, WandSettings.Easing.LINEAR, 1.0f, width, 0L);
    }

    private static GradientCore.Plan plan(Pos from, Pos to, int palette, WandSettings set,
                                          Vec3 eye, Vec3 look, boolean anchorLeft) {
        return GradientCore.plan(new GradientCore.Selection(from, to, palette, set, eye, look, anchorLeft));
    }

    // ---------- ribbons ----------

    @Test
    @DisplayName("a diagonal ribbon has no pinholes: every column is a contiguous run")
    void ribbonsAreSolid() {
        Random rng = new Random(23);
        int gaps = 0;
        int columns = 0;

        for (int trial = 0; trial < 300; trial++) {
            Pos from = new Pos(0, 0, 0);
            Pos to = new Pos(rng.nextInt(41) - 20, rng.nextInt(41) - 20, rng.nextInt(41) - 20);

            if (from.equals(to)) {
                continue;
            }

            Vec3 eye = new Vec3(rng.nextDouble() * 30 - 15, rng.nextDouble() * 30 - 15,
                    rng.nextDouble() * 30 - 15);

            GradientCore.Plan result = plan(from, to, 4,
                    settings(WandSettings.Mode.RIBBON, 4 + rng.nextInt(12)), eye, new Vec3(0, 0, 1), false);

            Map<Long, List<Integer>> byColumn = new HashMap<>();

            for (GradientCore.PlannedCell cell : result.cells()) {
                long key = ((long) cell.pos().x() << 32) ^ (cell.pos().z() & 0xffffffffL);

                byColumn.computeIfAbsent(key, k -> new ArrayList<>()).add(cell.pos().y());
            }

            for (List<Integer> ys : byColumn.values()) {
                Collections.sort(ys);
                columns++;

                for (int i = 1; i < ys.size(); i++) {
                    if (ys.get(i) - ys.get(i - 1) > 1) {
                        gaps++;
                    }
                }
            }
        }

        assertTrue(columns > 10000, "expected a meaningful sample, got " + columns + " columns");
        assertEquals(0, gaps, "ribbon columns must be contiguous; found " + gaps + " gaps");
    }

    @Test
    @DisplayName("a ribbon never places two blocks in the same position")
    void ribbonsDoNotOverlap() {
        Random rng = new Random(31);

        for (int trial = 0; trial < 200; trial++) {
            Pos from = new Pos(0, 0, 0);
            Pos to = new Pos(rng.nextInt(31) - 15, rng.nextInt(31) - 15, rng.nextInt(31) - 15);

            if (from.equals(to)) {
                continue;
            }

            GradientCore.Plan result = plan(from, to, 3,
                    settings(WandSettings.Mode.RIBBON, 3 + rng.nextInt(8)),
                    new Vec3(5, 9, 5), new Vec3(0, 0, 1), false);

            Set<Long> seen = new HashSet<>();

            for (GradientCore.PlannedCell cell : result.cells()) {
                assertTrue(seen.add(cell.pos().asLong()), "duplicate position in a ribbon plan");
            }
        }
    }

    // ---------- walls ----------

    @Test
    @DisplayName("a wall is always exactly one block thick")
    void wallsAreOneBlockThick() {
        Random rng = new Random(41);

        for (int trial = 0; trial < 40000; trial++) {
            Pos from = new Pos(rng.nextInt(129) - 64, rng.nextInt(129) - 64, rng.nextInt(129) - 64);
            Pos to = new Pos(from.x() + rng.nextInt(41) - 20,
                    from.y() + rng.nextInt(41) - 20,
                    from.z() + rng.nextInt(41) - 20);

            Pos end = GradientCore.shapeEnd(from, to, settings(WandSettings.Mode.WALL, 1), rng.nextBoolean());

            int spanX = Math.abs(end.x() - from.x()) + 1;
            int spanY = Math.abs(end.y() - from.y()) + 1;
            int spanZ = Math.abs(end.z() - from.z()) + 1;

            assertEquals(1, Math.min(spanX, Math.min(spanY, spanZ)),
                    "a wall must collapse one axis, never stay a box");
        }
    }

    @Test
    @DisplayName("flattening leaves an already coplanar selection untouched")
    void wallsDoNotMoveCoplanarSelections() {
        Random rng = new Random(43);

        for (int trial = 0; trial < 20000; trial++) {
            Pos from = new Pos(rng.nextInt(65) - 32, rng.nextInt(65) - 32, rng.nextInt(65) - 32);
            int[] delta = {rng.nextInt(31) - 15, rng.nextInt(31) - 15, rng.nextInt(31) - 15};

            delta[rng.nextInt(3)] = 0; // already flat on one axis

            Pos to = new Pos(from.x() + delta[0], from.y() + delta[1], from.z() + delta[2]);
            Pos end = GradientCore.shapeEnd(from, to, settings(WandSettings.Mode.WALL, 1), false);

            assertEquals(to, end, "a selection that already shares a plane must not be moved");
        }
    }

    @Test
    @DisplayName("sneaking keeps the wall upright, never collapsing Y")
    void uprightWallsNeverCollapseY() {
        Random rng = new Random(47);

        for (int trial = 0; trial < 20000; trial++) {
            Pos from = new Pos(0, 0, 0);
            Pos to = new Pos(rng.nextInt(41) - 20, rng.nextInt(41) - 20, rng.nextInt(41) - 20);

            Pos end = GradientCore.shapeEnd(from, to, settings(WandSettings.Mode.WALL, 1), true);

            if (to.y() != from.y()) {
                assertNotEquals(from.y(), end.y(), "an upright wall must keep its height");
            }
        }
    }

    // ---------- the limit clamp ----------

    @Test
    @DisplayName("a clamped selection always fits, and is the largest that does")
    void clampFitsAndIsMaximal() {
        Random rng = new Random(53);

        for (int trial = 0; trial < 30000; trial++) {
            Pos from = new Pos(rng.nextInt(65) - 32, rng.nextInt(65) - 32, rng.nextInt(65) - 32);
            Pos to = new Pos(from.x() + rng.nextInt(33) - 16,
                    from.y() + rng.nextInt(33) - 16,
                    from.z() + rng.nextInt(33) - 16);

            WandSettings.Mode mode = WandSettings.Mode.values()[rng.nextInt(3)];
            WandSettings set = settings(mode, 1 + rng.nextInt(8));
            long limit = 1 + rng.nextInt(400);

            Pos shaped = GradientCore.shapeEnd(from, to, set, false);
            Pos got = GradientCore.resolveEnd(from, to, set, false, limit);

            long size = GradientCore.sizeOf(from, got, set);
            long floor = GradientCore.sizeOf(from, from, set);

            // The one selection that cannot be rescued is a ribbon whose width alone overruns the
            // limit: a ribbon 5 wide costs 5 blocks before the line has moved at all, so walking
            // the end point back to A cannot help. The core refuses those rather than clamping
            // them, and handleClick tells the player to narrow the ribbon.
            if (floor > limit) {
                assertEquals(from, got, "an unrescuable selection should collapse back to point A");
                continue;
            }

            assertTrue(size <= limit,
                    "clamped selection of " + size + " overran a limit of " + limit);

            if (!got.equals(shaped)) {
                assertTrue(size <= limit, "a shortened selection must fit");

                // and it must be the largest that fits: one step further has to overrun
                int steps = Math.max(Math.abs(shaped.x() - from.x()),
                        Math.max(Math.abs(shaped.y() - from.y()), Math.abs(shaped.z() - from.z())));

                for (int step = 0; step <= steps; step++) {
                    double t = steps == 0 ? 0.0 : (double) step / steps;
                    Pos at = new Pos(
                            from.x() + (int) Math.round(t * (shaped.x() - from.x())),
                            from.y() + (int) Math.round(t * (shaped.y() - from.y())),
                            from.z() + (int) Math.round(t * (shaped.z() - from.z())));

                    if (at.equals(got) && step < steps) {
                        double next = (double) (step + 1) / steps;
                        Pos beyond = new Pos(
                                from.x() + (int) Math.round(next * (shaped.x() - from.x())),
                                from.y() + (int) Math.round(next * (shaped.y() - from.y())),
                                from.z() + (int) Math.round(next * (shaped.z() - from.z())));

                        assertTrue(GradientCore.sizeOf(from, beyond, set) > limit,
                                "the clamp stopped short: one more step would still have fitted");
                        break;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("selection size never shrinks as the end point moves away, which the bisection needs")
    void sizeIsMonotonicAlongTheLine() {
        Random rng = new Random(59);

        for (int trial = 0; trial < 2000; trial++) {
            Pos from = new Pos(0, 0, 0);
            int dx = rng.nextInt(25) - 12;
            int dy = rng.nextInt(25) - 12;
            int dz = rng.nextInt(25) - 12;

            WandSettings.Mode mode = WandSettings.Mode.values()[rng.nextInt(3)];
            WandSettings set = settings(mode, 1 + rng.nextInt(6));

            int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            long previous = -1;

            for (int step = 0; step <= steps; step++) {
                double t = steps == 0 ? 0.0 : (double) step / steps;
                Pos at = new Pos((int) Math.round(t * dx), (int) Math.round(t * dy), (int) Math.round(t * dz));

                long size = GradientCore.sizeOf(from, mode == WandSettings.Mode.WALL
                        ? GradientCore.shapeEnd(from, at, set, false) : at, set);

                assertTrue(size >= previous, "size fell from " + previous + " to " + size);
                previous = size;
            }
        }
    }

    // ---------- grain ----------

    @Test
    @DisplayName("LONGEST follows the line, and the forced settings ignore the shape")
    void grainFollowsTheSetting() {
        Pos from = new Pos(0, 0, 0);
        Pos to = new Pos(20, 3, 1);

        assertEquals(Axis.X, plan(from, to, 2, settings(WandSettings.Mode.STRIP, 1), null, null, false).grain());

        assertNull(plan(from, to, 2,
                settings(WandSettings.Mode.STRIP, 1).withGrain(WandSettings.Grain.OFF),
                null, null, false).grain(), "OFF must leave blocks in their default orientation");

        assertEquals(Axis.Z, plan(from, to, 2,
                settings(WandSettings.Mode.STRIP, 1).withGrain(WandSettings.Grain.NORTH_SOUTH),
                null, null, false).grain());

        assertEquals(Axis.Y, plan(from, to, 2,
                settings(WandSettings.Mode.STRIP, 1).withGrain(WandSettings.Grain.UP_DOWN),
                null, null, false).grain());
    }

    @Test
    @DisplayName("SHORTEST on a wall lies in the face, never through its thickness")
    void shortestGrainStaysInTheWall() {
        Random rng = new Random(61);
        WandSettings set = settings(WandSettings.Mode.WALL, 1).withGrain(WandSettings.Grain.SHORTEST);

        for (int trial = 0; trial < 20000; trial++) {
            Pos from = new Pos(0, 0, 0);
            Pos to = new Pos(rng.nextInt(41) - 20, rng.nextInt(41) - 20, rng.nextInt(41) - 20);
            Pos end = GradientCore.shapeEnd(from, to, set, false);

            int[] span = {Math.abs(end.x() - from.x()) + 1,
                    Math.abs(end.y() - from.y()) + 1,
                    Math.abs(end.z() - from.z()) + 1};

            // Degenerate walls are a line or a single block, with no face for a grain to lie in
            int[] sorted = span.clone();
            java.util.Arrays.sort(sorted);

            if (sorted[1] == 1) {
                continue;
            }

            Axis grain = plan(from, end, 2, set, null, null, false).grain();

            assertTrue(span[grain.index()] > 1,
                    "SHORTEST pointed through the wall's one block of thickness");
        }
    }

    // ---------- easing ----------

    @Test
    @DisplayName("every easing curve runs forwards and reaches both ends of the palette")
    void easingNeverReversesTheGradient() {
        for (WandSettings.Easing easing : WandSettings.Easing.values()) {
            GradientCore.Plan result = plan(new Pos(0, 0, 0), new Pos(63, 0, 0), 4,
                    settings(WandSettings.Mode.STRIP, 1).withEasing(easing), null, null, false);

            int previous = -1;

            for (GradientCore.PlannedCell cell : result.cells()) {
                assertTrue(cell.palette() >= previous,
                        easing + " went backwards: " + previous + " then " + cell.palette());
                previous = cell.palette();
            }

            assertEquals(0, result.cells().get(0).palette(),
                    easing + " must start on the first palette entry");
            assertEquals(3, result.cells().get(result.cells().size() - 1).palette(),
                    easing + " must finish on the last palette entry");
        }
    }

    @Test
    @DisplayName("LINEAR is untouched, and each curve gives room to the end it names")
    void easingShiftsWhereTheGradientLingers() {
        int[] linear = bandCounts(WandSettings.Easing.LINEAR);
        int[] front = bandCounts(WandSettings.Easing.FRONT);
        int[] back = bandCounts(WandSettings.Easing.BACK);
        int[] ends = bandCounts(WandSettings.Easing.ENDS);

        // Exactly what the bands were before easing existed. LINEAR keeps the original expression
        // rather than going through the curve, so this must not move by a single block.
        assertEquals("[16, 16, 16, 16]", java.util.Arrays.toString(linear));

        assertTrue(front[0] > linear[0],
                "FRONT should hold the first entry longer, got " + java.util.Arrays.toString(front));
        assertTrue(back[3] > linear[3],
                "BACK should hold the last entry longer, got " + java.util.Arrays.toString(back));
        assertTrue(ends[0] > linear[0] && ends[3] > linear[3],
                "ENDS should favour both ends, got " + java.util.Arrays.toString(ends));

        // and the middle is what pays for it
        assertTrue(ends[1] < linear[1] && ends[2] < linear[2], "ENDS should thin out the middle");

        for (int[] counts : new int[][]{linear, front, back, ends}) {
            int total = 0;

            for (int count : counts) {
                total += count;
            }

            assertEquals(64, total, "every curve must still place all 64 blocks");
        }
    }

    private static int[] bandCounts(WandSettings.Easing easing) {
        GradientCore.Plan result = plan(new Pos(0, 0, 0), new Pos(63, 0, 0), 4,
                settings(WandSettings.Mode.STRIP, 1).withEasing(easing), null, null, false);

        int[] counts = new int[4];

        for (GradientCore.PlannedCell cell : result.cells()) {
            counts[cell.palette()]++;
        }

        return counts;
    }

    // ---------- dithering ----------

    @Test
    @DisplayName("with dither off the palette bands are exactly even")
    void bandsAreEvenWithoutDither() {
        GradientCore.Plan result = plan(new Pos(0, 0, 0), new Pos(63, 0, 0), 4,
                settings(WandSettings.Mode.STRIP, 1).withDither(WandSettings.Dither.NONE),
                null, null, false);

        int[] counts = new int[4];

        for (GradientCore.PlannedCell cell : result.cells()) {
            counts[cell.palette()]++;
        }

        assertArrayEqualsMessage(new int[]{16, 16, 16, 16}, counts);
    }

    @Test
    @DisplayName("dither moves a block at most one entry from where the blend puts it")
    void ditherMovesAtMostOneEntry() {
        int palette = 4;
        int length = 64;

        GradientCore.Plan dithered = plan(new Pos(0, 0, 0), new Pos(length - 1, 0, 0), palette,
                settings(WandSettings.Mode.STRIP, 1).withDither(WandSettings.Dither.ORDERED),
                null, null, false);

        // This used to assert that dither only ever moved a block *up* from its hard band, which
        // sounds conservative and was in fact the bug: on the band scale a blend runs off the end
        // of the palette halfway along, so clamping pinned the whole back half to the last entry.
        // A blend interpolates between entries, so the honest bound is one step either side of the
        // ideal position, which on the blend scale is t * (palette - 1).
        for (int i = 0; i < dithered.cells().size(); i++) {
            int actual = dithered.cells().get(i).palette();
            float ideal = (float) i * (palette - 1) / length;

            assertTrue(actual >= Math.floor(ideal) - 1 && actual <= Math.ceil(ideal) + 1,
                    "block " + i + " landed on entry " + actual + ", too far from the blend's "
                            + String.format("%.2f", ideal));
            assertTrue(actual >= 0 && actual < palette,
                    "dither pushed a block off the palette at index " + i);
        }
    }

    @Test
    @DisplayName("a dithered blend spends even time on the middle entries and half on each end")
    void ditheredBlendIsUnbiased() {
        // The regression this locks down: two blocks with dither on came out 29/71 instead of
        // 50/50, because the blend was computed on the hard-band scale and the clamp ate every
        // promotion past the end. It reads in game as linear lunging for its final colour, and no
        // palette-4 test caught it, because the skew shrinks as the palette grows.
        for (int palette : new int[]{2, 3, 4, 9}) {
            GradientCore.Plan result = plan(new Pos(0, 0, 0), new Pos(8191, 0, 0), palette,
                    settings(WandSettings.Mode.STRIP, 1).withDither(WandSettings.Dither.RANDOM),
                    null, null, false);

            int[] counts = new int[palette];

            for (GradientCore.PlannedCell cell : result.cells()) {
                counts[cell.palette()]++;
            }

            int total = result.cells().size();

            for (int entry = 0; entry < palette; entry++) {
                // Only the ends are pure at the very ends of the run, so they get half the time
                boolean end = entry == 0 || entry == palette - 1;
                double expected = (end ? 0.5 : 1.0) / (palette - 1);
                double actual = (double) counts[entry] / total;

                assertTrue(Math.abs(actual - expected) < 0.02,
                        "palette " + palette + " entry " + entry + " took "
                                + String.format("%.1f%%", actual * 100) + " of the run, expected "
                                + String.format("%.1f%%", expected * 100));
            }
        }
    }

    @Test
    @DisplayName("jitter 0 with dither on is exactly the hard bands, so the slider has no jump")
    void zeroJitterMatchesHardBands() {
        GradientCore.Plan plain = plan(new Pos(0, 0, 0), new Pos(63, 0, 0), 4,
                settings(WandSettings.Mode.STRIP, 1).withDither(WandSettings.Dither.NONE),
                null, null, false);

        for (WandSettings.Dither dither : WandSettings.Dither.values()) {
            GradientCore.Plan zero = plan(new Pos(0, 0, 0), new Pos(63, 0, 0), 4,
                    settings(WandSettings.Mode.STRIP, 1).withDither(dither).withJitter(0.0f),
                    null, null, false);

            assertEquals(plain.cells(), zero.cells(),
                    "dither " + dither + " at jitter 0 must match hard bands exactly");
        }
    }

    @Test
    @DisplayName("planning is deterministic, which is what keeps the preview honest")
    void planningIsDeterministic() {
        Random rng = new Random(67);

        for (int trial = 0; trial < 200; trial++) {
            Pos from = new Pos(0, 0, 0);
            Pos to = new Pos(rng.nextInt(21) - 10, rng.nextInt(21) - 10, rng.nextInt(21) - 10);

            WandSettings set = new WandSettings(
                    WandSettings.Mode.values()[rng.nextInt(3)],
                    WandSettings.GradientAxis.AUTO,
                    WandSettings.Dither.RANDOM,
                    WandSettings.Grain.LONGEST,
                    WandSettings.Easing.LINEAR,
                    1.0f, 1 + rng.nextInt(5), rng.nextLong());

            Vec3 eye = new Vec3(4.0, 9.0, 4.0);

            GradientCore.Plan first = plan(from, to, 4, set, eye, new Vec3(0, 0, 1), false);
            GradientCore.Plan second = plan(from, to, 4, set, eye, new Vec3(0, 0, 1), false);

            assertEquals(first.cells(), second.cells(),
                    "the same selection must plan identically every time, or the preview lies");
            assertEquals(first.grain(), second.grain());
        }
    }

    @Test
    @DisplayName("two seeds disagree about roughly half of dithered blocks")
    void seedsDecorrelate() {
        WandSettings one = settings(WandSettings.Mode.WALL, 1)
                .withDither(WandSettings.Dither.RANDOM).withSeed(1L);
        WandSettings two = one.withSeed(2L);

        GradientCore.Plan a = plan(new Pos(0, 0, 0), new Pos(40, 40, 0), 4, one, null, null, false);
        GradientCore.Plan b = plan(new Pos(0, 0, 0), new Pos(40, 40, 0), 4, two, null, null, false);

        int differing = 0;

        for (int i = 0; i < a.cells().size(); i++) {
            if (a.cells().get(i).palette() != b.cells().get(i).palette()) {
                differing++;
            }
        }

        double share = (double) differing / a.cells().size();

        // An earlier seed-mixing scheme changed under 1% of decisions between adjacent seeds,
        // which made the seed effectively useless. Anything in this band is healthy.
        assertTrue(share > 0.10 && share < 0.45,
                "expected seeds to decorrelate, but only " + String.format("%.2f%%", share * 100)
                        + " of blocks differed");
    }

    private static void assertArrayEqualsMessage(int[] expected, int[] actual) {
        assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(actual));
    }
}
