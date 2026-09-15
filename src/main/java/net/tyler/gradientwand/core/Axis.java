package net.tyler.gradientwand.core;

// The mod's own axis, so the core never has to name Direction.Axis. The platform maps between the
// two at its boundary; the ordering here matches X, Y, Z so index() lines up with the component
// order used throughout the geometry.
public enum Axis {

    X, Y, Z;

    public static Axis of(int index) {
        return switch (index) {
            case 0 -> X;
            case 1 -> Y;
            default -> Z;
        };
    }

    public int index() {
        return ordinal();
    }
}
