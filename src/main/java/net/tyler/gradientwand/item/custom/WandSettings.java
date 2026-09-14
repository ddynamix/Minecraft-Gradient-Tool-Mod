package net.tyler.gradientwand.item.custom;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public record WandSettings(Mode mode, GradientAxis axis, Dither dither, Grain grain,
                           float jitter, int width, long seed) {

    public static final int MIN_WIDTH = 1;
    // The widest any tier allows. Each wand clamps further, in from() and save() below.
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

        int tierMax = WandTier.of(stack).maxWidth();
        int width = nbt.contains("Width")
                ? clampWidth(nbt.getInt("Width"), tierMax)
                : Math.min(DEFAULT.width(), tierMax);

        return new WandSettings(
                readEnum(nbt, "Mode", Mode.class, DEFAULT.mode()),
                readEnum(nbt, "Axis", GradientAxis.class, DEFAULT.axis()),
                readEnum(nbt, "Dither", Dither.class, DEFAULT.dither()),
                readEnum(nbt, "Grain", Grain.class, DEFAULT.grain()),
                jitter,
                width,
                nbt.getLong("Seed"));
    }

    public void save(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateSubNbt(KEY);

        nbt.putString("Mode", mode.name());
        nbt.putString("Axis", axis.name());
        nbt.putString("Dither", dither.name());
        nbt.putString("Grain", grain.name());
        nbt.putFloat("Jitter", jitter);
        // Clamped here rather than at every caller: this is the one place settings reach an item,
        // so a width the tier does not allow can never be stored in the first place
        nbt.putInt("Width", clampWidth(width, WandTier.of(stack).maxWidth()));
        nbt.putLong("Seed", seed);
    }

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

    // Returns the fallback when the key is missing or holds a value this version does not know
    private static <T extends Enum<T>> T readEnum(NbtCompound nbt, String key, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, nbt.getString(key));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
