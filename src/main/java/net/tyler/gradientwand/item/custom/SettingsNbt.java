package net.tyler.gradientwand.item.custom;

//? if <1.21 {
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
//?} else {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
*///?}

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.core.WandSettings;

// The whole of the mod's persistence: the wand's settings and its saved point A. 1.20.5 replaced
// item NBT with data components, so everything about *how* data is stored diverges here, while the
// core WandSettings record it reads and writes is identical on every version.
//
// Only readRaw/writeRaw and the point A accessors differ. Clamping lives in clamp() below, shared
// by both, so the rule that a width can never exceed what the tier allows is written once.
public class SettingsNbt {

    //? if <1.21 {
    private static final String KEY = "Settings";
    private static final String POINT_A_KEY = "PointA";

    // Nothing to register: on this version the data is plain item NBT
    public static void register() {
    }

    private static WandSettings readRaw(ItemStack stack) {
        NbtCompound nbt = stack.getSubNbt(KEY);

        if (nbt == null) {
            return WandSettings.DEFAULT;
        }

        float jitter = nbt.contains("Jitter") ? nbt.getFloat("Jitter") : WandSettings.DEFAULT.jitter();
        int width = nbt.contains("Width") ? nbt.getInt("Width") : WandSettings.DEFAULT.width();

        return new WandSettings(
                readEnum(nbt, "Mode", WandSettings.Mode.class, WandSettings.DEFAULT.mode()),
                readEnum(nbt, "Axis", WandSettings.GradientAxis.class, WandSettings.DEFAULT.axis()),
                readEnum(nbt, "Dither", WandSettings.Dither.class, WandSettings.DEFAULT.dither()),
                readEnum(nbt, "Grain", WandSettings.Grain.class, WandSettings.DEFAULT.grain()),
                jitter,
                width,
                nbt.getLong("Seed"));
    }

    private static void writeRaw(ItemStack stack, WandSettings settings) {
        NbtCompound nbt = stack.getOrCreateSubNbt(KEY);

        nbt.putString("Mode", settings.mode().name());
        nbt.putString("Axis", settings.axis().name());
        nbt.putString("Dither", settings.dither().name());
        nbt.putString("Grain", settings.grain().name());
        nbt.putFloat("Jitter", settings.jitter());
        nbt.putInt("Width", settings.width());
        nbt.putLong("Seed", settings.seed());
    }

    // Returns null if no point A is saved
    public static BlockPos readPointA(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();

        if (nbt == null || !nbt.contains(POINT_A_KEY)) {
            return null;
        }

        return NbtHelper.toBlockPos(nbt.getCompound(POINT_A_KEY));
    }

    public static void writePointA(ItemStack stack, BlockPos pos) {
        stack.getOrCreateNbt().put(POINT_A_KEY, NbtHelper.fromBlockPos(pos));
    }

    public static void clearPointA(ItemStack stack) {
        stack.removeSubNbt(POINT_A_KEY);
    }

    // NBT is player-editable, so an unknown value falls back rather than throwing
    private static <T extends Enum<T>> T readEnum(NbtCompound nbt, String key, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, nbt.getString(key));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
    //?} else {
    /*// Enums travel as their names, the same as the old NBT form, so a wand keeps its settings
    // across the version jump. An unrecognised name falls back instead of throwing, because this
    // data is still player-editable through /data.
    private static <T extends Enum<T>> Codec<T> enumCodec(Class<T> type, T fallback) {
        return Codec.STRING.xmap(name -> {
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }, Enum::name);
    }

    // optionalFieldOf everywhere: a wand written by an older build is missing keys, and should
    // read as the default rather than failing to decode at all
    public static final Codec<WandSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            enumCodec(WandSettings.Mode.class, WandSettings.DEFAULT.mode())
                    .optionalFieldOf("mode", WandSettings.DEFAULT.mode()).forGetter(WandSettings::mode),
            enumCodec(WandSettings.GradientAxis.class, WandSettings.DEFAULT.axis())
                    .optionalFieldOf("axis", WandSettings.DEFAULT.axis()).forGetter(WandSettings::axis),
            enumCodec(WandSettings.Dither.class, WandSettings.DEFAULT.dither())
                    .optionalFieldOf("dither", WandSettings.DEFAULT.dither()).forGetter(WandSettings::dither),
            enumCodec(WandSettings.Grain.class, WandSettings.DEFAULT.grain())
                    .optionalFieldOf("grain", WandSettings.DEFAULT.grain()).forGetter(WandSettings::grain),
            Codec.FLOAT.optionalFieldOf("jitter", WandSettings.DEFAULT.jitter()).forGetter(WandSettings::jitter),
            Codec.INT.optionalFieldOf("width", WandSettings.DEFAULT.width()).forGetter(WandSettings::width),
            Codec.LONG.optionalFieldOf("seed", WandSettings.DEFAULT.seed()).forGetter(WandSettings::seed)
    ).apply(instance, WandSettings::new));

    // Both carry a packet codec as well as a persistence codec. That is not optional here: the
    // preview renderer runs on the client and reads the settings and point A off the held stack,
    // so a component that never syncs would leave the preview blank on a server.
    public static final ComponentType<WandSettings> SETTINGS = ComponentType.<WandSettings>builder()
            .codec(CODEC)
            .packetCodec(PacketCodecs.codec(CODEC))
            .build();

    public static final ComponentType<BlockPos> POINT_A = ComponentType.<BlockPos>builder()
            .codec(BlockPos.CODEC)
            .packetCodec(BlockPos.PACKET_CODEC)
            .build();

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE, GradientWand.id("settings"), SETTINGS);
        Registry.register(Registries.DATA_COMPONENT_TYPE, GradientWand.id("point_a"), POINT_A);
    }

    private static WandSettings readRaw(ItemStack stack) {
        return stack.getOrDefault(SETTINGS, WandSettings.DEFAULT);
    }

    private static void writeRaw(ItemStack stack, WandSettings settings) {
        stack.set(SETTINGS, settings);
    }

    // Returns null if no point A is saved
    public static BlockPos readPointA(ItemStack stack) {
        return stack.get(POINT_A);
    }

    public static void writePointA(ItemStack stack, BlockPos pos) {
        stack.set(POINT_A, pos);
    }

    public static void clearPointA(ItemStack stack) {
        stack.remove(POINT_A);
    }
    *///?}

    public static WandSettings read(ItemStack stack) {
        return clamp(stack, readRaw(stack));
    }

    public static void write(ItemStack stack, WandSettings settings) {
        writeRaw(stack, clamp(stack, settings));
    }

    // Applied on the way in and on the way out, so a width the tier does not allow can never be
    // stored and an older wand carrying one still reads back as something legal.
    private static WandSettings clamp(ItemStack stack, WandSettings settings) {
        int tierMax = WandTier.of(stack).maxWidth();

        return settings
                .withJitter(Math.max(0.0f, Math.min(1.0f, settings.jitter())))
                .withWidth(WandSettings.clampWidth(settings.width(), tierMax));
    }
}
