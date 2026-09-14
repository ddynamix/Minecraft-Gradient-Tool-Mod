package net.tyler.gradientwand.item.custom;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public record WandSettings(Mode mode, GradientAxis axis, Dither dither, float jitter, int width, long seed) {

    public static final int MIN_WIDTH = 1;
    public static final int MAX_WIDTH = 64;

    public enum Mode {
        STRIP, WALL, RIBBON
    }

    public enum GradientAxis {
        AUTO, HORIZONTAL, VERTICAL
    }

    public enum Dither {
        NONE, ORDERED, RANDOM
    }

    public static final WandSettings DEFAULT =
            new WandSettings(Mode.STRIP, GradientAxis.AUTO, Dither.NONE, 1.0f, 3, 0L);

    private static final String KEY = "Settings";

    // NBT is player-editable and survives mod updates, so never trust what is in it
    public static WandSettings from(ItemStack stack) {
        NbtCompound nbt = stack.getSubNbt(KEY);

        if (nbt == null) {
            return DEFAULT;
        }

        float jitter = nbt.contains("Jitter")
                ? Math.max(0.0f, Math.min(1.0f, nbt.getFloat("Jitter")))
                : DEFAULT.jitter();

        int width = nbt.contains("Width") ? clampWidth(nbt.getInt("Width")) : DEFAULT.width();

        return new WandSettings(
                readEnum(nbt, "Mode", Mode.class, DEFAULT.mode()),
                readEnum(nbt, "Axis", GradientAxis.class, DEFAULT.axis()),
                readEnum(nbt, "Dither", Dither.class, DEFAULT.dither()),
                jitter,
                width,
                nbt.getLong("Seed"));
    }

    public void save(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateSubNbt(KEY);

        nbt.putString("Mode", mode.name());
        nbt.putString("Axis", axis.name());
        nbt.putString("Dither", dither.name());
        nbt.putFloat("Jitter", jitter);
        nbt.putInt("Width", width);
        nbt.putLong("Seed", seed);
    }

    public static int clampWidth(int value) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, value));
    }

    public WandSettings withMode(Mode value) {
        return new WandSettings(value, axis, dither, jitter, width, seed);
    }

    public WandSettings withAxis(GradientAxis value) {
        return new WandSettings(mode, value, dither, jitter, width, seed);
    }

    public WandSettings withDither(Dither value) {
        return new WandSettings(mode, axis, value, jitter, width, seed);
    }

    public WandSettings withJitter(float value) {
        return new WandSettings(mode, axis, dither, value, width, seed);
    }

    public WandSettings withWidth(int value) {
        return new WandSettings(mode, axis, dither, jitter, clampWidth(value), seed);
    }

    public WandSettings withSeed(long value) {
        return new WandSettings(mode, axis, dither, jitter, width, value);
    }

    public String describe() {
        return String.format("mode %s, axis %s, dither %s, jitter %.2f, width %d",
                mode.name().toLowerCase(),
                axis.name().toLowerCase(),
                dither.name().toLowerCase(),
                jitter,
                width);
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