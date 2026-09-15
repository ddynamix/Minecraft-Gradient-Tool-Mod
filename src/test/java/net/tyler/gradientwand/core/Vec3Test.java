package net.tyler.gradientwand.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Vec3 deliberately copies Vec3d's behaviour rather than merely resembling it. The ribbon geometry
// leans on both of these, so a "cleaner" reimplementation would change which blocks get filled.
class Vec3Test {

    private static final double EPSILON = 1.0E-9;

    @Test
    @DisplayName("normalize collapses to zero below 1.0E-4, as Vec3d does")
    void normalizeCollapsesTinyVectors() {
        Vec3 tiny = new Vec3(1.0E-5, 0.0, 0.0);

        assertEquals(Vec3.ZERO, tiny.normalize(),
                "a vector shorter than 1.0E-4 must collapse rather than blow up when divided");

        Vec3 ordinary = new Vec3(0.0, 3.0, 4.0).normalize();

        assertEquals(0.0, ordinary.x(), EPSILON);
        assertEquals(0.6, ordinary.y(), EPSILON);
        assertEquals(0.8, ordinary.z(), EPSILON);
    }

    @Test
    @DisplayName("cross product uses this x other, matching Vec3d's operand order")
    void crossProductOperandOrder() {
        Vec3 x = new Vec3(1.0, 0.0, 0.0);
        Vec3 y = new Vec3(0.0, 1.0, 0.0);

        // x cross y is +z; the reverse is -z. Getting this backwards flips every ribbon.
        assertEquals(new Vec3(0.0, 0.0, 1.0), x.cross(y));
        assertEquals(new Vec3(0.0, 0.0, -1.0), y.cross(x));
    }

    @Test
    @DisplayName("a cross product is perpendicular to both inputs")
    void crossProductIsPerpendicular() {
        java.util.Random rng = new java.util.Random(11);

        for (int i = 0; i < 5000; i++) {
            Vec3 a = new Vec3(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian());
            Vec3 b = new Vec3(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian());
            Vec3 cross = a.cross(b);

            assertTrue(Math.abs(cross.dot(a)) < 1.0E-9, "cross must be perpendicular to a");
            assertTrue(Math.abs(cross.dot(b)) < 1.0E-9, "cross must be perpendicular to b");
        }
    }
}
