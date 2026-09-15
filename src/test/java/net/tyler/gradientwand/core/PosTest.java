package net.tyler.gradientwand.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Pos.asLong reproduces Minecraft's own bit packing, and the random dither hashes that packed
// value. These expectations were taken from the real BlockPos.asLong, so if anyone ever "tidies"
// the packing the dither pattern would silently shift and these tests are what catches it.
class PosTest {

    @Test
    @DisplayName("asLong matches Minecraft's 26/12/26 packing")
    void asLongMatchesVanillaPacking() {
        assertEquals(0L, new Pos(0, 0, 0).asLong());
        assertEquals(274877919234L, new Pos(1, 2, 3).asLong());
        assertEquals(-1L, new Pos(-1, -1, -1).asLong());
        assertEquals(27762667577408L, new Pos(100, 64, -250).asLong());
        assertEquals(-8246337085439995968L, new Pos(-30000000, -64, 30000000).asLong());
    }

    @Test
    @DisplayName("distinct positions pack to distinct longs across the build range")
    void packingIsInjective() {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.Random rng = new java.util.Random(4);

        for (int i = 0; i < 20000; i++) {
            Pos pos = new Pos(rng.nextInt(4_000_000) - 2_000_000,
                    rng.nextInt(512) - 64,
                    rng.nextInt(4_000_000) - 2_000_000);

            assertEquals(true, seen.add(pos.asLong()),
                    "two different positions packed to the same long: " + pos);
        }
    }

    @Test
    @DisplayName("center is the middle of the block")
    void centerIsBlockMiddle() {
        Vec3 middle = new Pos(3, -2, 7).center();

        assertEquals(3.5, middle.x());
        assertEquals(-1.5, middle.y());
        assertEquals(7.5, middle.z());
    }
}
