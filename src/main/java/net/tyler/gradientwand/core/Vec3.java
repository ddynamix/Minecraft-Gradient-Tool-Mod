package net.tyler.gradientwand.core;

// A plain double vector, deliberately matching Minecraft's Vec3d behaviour rather than merely
// resembling it. The ribbon geometry leans on normalize() and crossProduct(), so any difference
// here would quietly change which blocks a diagonal ribbon fills.
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double scale) {
        return new Vec3(x * scale, y * scale, z * scale);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    // this x other, the same operand order Vec3d.crossProduct uses
    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    // Returns zero below 1.0E-4 rather than dividing, exactly as Vec3d.normalize does. Callers in
    // the ribbon code rely on a degenerate vector collapsing instead of blowing up.
    public Vec3 normalize() {
        double length = length();

        return length < 1.0E-4 ? ZERO : new Vec3(x / length, y / length, z / length);
    }

    public double get(int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    public double get(Axis axis) {
        return get(axis.index());
    }
}
