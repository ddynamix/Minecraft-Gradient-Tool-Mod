package net.tyler.gradientwand.network;

//? if <1.21 {
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
//?} else {
/*import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
*///?}

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.core.WandSettings;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.SettingsNbt;

// Client to server: "set my wand to these settings". The seed is deliberately not included.
//? if <1.21 {
public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 float jitter, int width) implements FabricPacket {

    public static final PacketType<WandSettingsPacket> TYPE =
            PacketType.create(GradientWand.id("wand_settings"), WandSettingsPacket::new);

    public WandSettingsPacket(PacketByteBuf buf) {
        this(buf.readEnumConstant(WandSettings.Mode.class),
                buf.readEnumConstant(WandSettings.GradientAxis.class),
                buf.readEnumConstant(WandSettings.Dither.class),
                buf.readEnumConstant(WandSettings.Grain.class),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(mode);
        buf.writeEnumConstant(axis);
        buf.writeEnumConstant(dither);
        buf.writeEnumConstant(grain);
        buf.writeFloat(jitter);
        buf.writeVarInt(width);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> handle(player, packet));
    }
//?} else {
/*public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 float jitter, int width) implements CustomPayload {

    public static final CustomPayload.Id<WandSettingsPacket> ID =
            new CustomPayload.Id<>(GradientWand.id("wand_settings"));

    // 1.21 has no enum codec, so each one travels as its ordinal. Reading back through
    // getEnumConstants keeps it symmetrical with the old readEnumConstant/writeEnumConstant pair.
    private static <T extends Enum<T>> PacketCodec<ByteBuf, T> enumOf(Class<T> type) {
        T[] values = type.getEnumConstants();

        return PacketCodecs.indexed(index -> values[index], Enum::ordinal);
    }

    public static final PacketCodec<RegistryByteBuf, WandSettingsPacket> CODEC = PacketCodec.tuple(
            enumOf(WandSettings.Mode.class), WandSettingsPacket::mode,
            enumOf(WandSettings.GradientAxis.class), WandSettingsPacket::axis,
            enumOf(WandSettings.Dither.class), WandSettingsPacket::dither,
            enumOf(WandSettings.Grain.class), WandSettingsPacket::grain,
            PacketCodecs.FLOAT, WandSettingsPacket::jitter,
            PacketCodecs.VAR_INT, WandSettingsPacket::width,
            WandSettingsPacket::new);

    @Override
    public CustomPayload.Id<WandSettingsPacket> getId() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player(), payload));
    }
*///?}

    // Fabric runs this on the main server thread on both versions, so touching the stack is safe
    private static void handle(ServerPlayerEntity player, WandSettingsPacket packet) {
        ItemStack stack = player.getMainHandStack();

        if (!(stack.getItem() instanceof GradientWandItem)) {
            return;
        }

        // Never trust the client: keep the server's own seed and clamp what came over the wire.
        // SettingsNbt.write clamps the width to what this tier allows.
        SettingsNbt.write(stack, SettingsNbt.read(stack)
                .withMode(packet.mode())
                .withAxis(packet.axis())
                .withDither(packet.dither())
                .withGrain(packet.grain())
                .withJitter(Math.max(0.0f, Math.min(1.0f, packet.jitter())))
                .withWidth(packet.width()));
    }
}
