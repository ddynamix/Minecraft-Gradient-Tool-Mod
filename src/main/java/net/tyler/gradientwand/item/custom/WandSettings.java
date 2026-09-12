package net.tyler.gradientwand.item.custom;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public record WandSettings(Mode mode, GradientAxis axis, Dither dither, float jitter, long seed) {

    public enum Mode {
        STRIP, WALL
    }

    public enum GradientAxis {
        AUTO, X, Y, Z
    }

    public enum Dither {
        NONE, ORDERED, RANDOM
    }

    public static final WandSettings DEFAULT =
            new WandSettings(Mode.STRIP, GradientAxis.AUTO, Dither.NONE, 0.0f, 0L);

    private static final String KEY = "Settings";

    // NBT is player-editable and survives mod updates, so never trust what is in it
    public static WandSettings from(ItemStack stack) {
        NbtCompound nbt = stack.getSubNbt(KEY);

        if (nbt == null) {
            return DEFAULT;
        }

        float jitter = Math.max(0.0f, Math.min(1.0f, nbt.getFloat("Jitter")));

        return new WandSettings(
                readEnum(nbt, "Mode", Mode.class, DEFAULT.mode()),
                readEnum(nbt, "Axis", GradientAxis.class, DEFAULT.axis()),
                readEnum(nbt, "Dither", Dither.class, DEFAULT.dither()),
                jitter,
                nbt.getLong("Seed"));
    }

    public void save(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateSubNbt(KEY);

        nbt.putString("Mode", mode.name());
        nbt.putString("Axis", axis.name());
        nbt.putString("Dither", dither.name());
        nbt.putFloat("Jitter", jitter);
        nbt.putLong("Seed", seed);
    }

    public WandSettings withMode(Mode value) {
        return new WandSettings(value, axis, dither, jitter, seed);
    }

    public WandSettings withAxis(GradientAxis value) {
        return new WandSettings(mode, value, dither, jitter, seed);
    }

    public WandSettings withDither(Dither value) {
        return new WandSettings(mode, axis, value, jitter, seed);
    }

    public WandSettings withJitter(float value) {
        return new WandSettings(mode, axis, dither, value, seed);
    }

    public WandSettings withSeed(long value) {
        return new WandSettings(mode, axis, dither, jitter, value);
    }

    public String describe() {
        return String.format("mode %s, axis %s, dither %s, jitter %.2f",
                mode.name().toLowerCase(),
                axis.name().toLowerCase(),
                dither.name().toLowerCase(),
                jitter);
    }

    // Returns the fallback when the key is missing or holds a value this version does not know
    private static <T extends Enum<T>> T readEnum(NbtCompound nbt, String key, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, nbt.getString(key));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}