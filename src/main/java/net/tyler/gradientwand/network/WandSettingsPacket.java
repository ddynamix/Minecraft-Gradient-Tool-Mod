package net.tyler.gradientwand.network;

//? if <1.21 {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
*///?}
//? if >=1.21 && fabric {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
*///?}
//? if neoforge {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//?}

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.core.WandSettings;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.SettingsNbt;

// Client to server: "set my wand to these settings". The seed is deliberately not included.
//? if <1.21 {
/*public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 WandSettings.Easing easing,
                                 float jitter, int width) implements FabricPacket {

    public static final PacketType<WandSettingsPacket> TYPE =
            PacketType.create(GradientWand.id("wand_settings"), WandSettingsPacket::new);

    public WandSettingsPacket(FriendlyByteBuf buf) {
        this(buf.readEnum(WandSettings.Mode.class),
                buf.readEnum(WandSettings.GradientAxis.class),
                buf.readEnum(WandSettings.Dither.class),
                buf.readEnum(WandSettings.Grain.class),
                buf.readEnum(WandSettings.Easing.class),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeEnum(axis);
        buf.writeEnum(dither);
        buf.writeEnum(grain);
        buf.writeEnum(easing);
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
*///?}
//? if >=1.21 && fabric {
/*public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 WandSettings.Easing easing,
                                 float jitter, int width) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandSettingsPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_settings"));

    // StreamCodec.tuple stops at six components and this packet has seven, so the fields are
    // written and read by hand. RegistryFriendlyByteBuf extends FriendlyByteBuf, so the enum helpers are the
    // same ones the 1.20.1 branch uses and the wire order matches it exactly.
    public static final StreamCodec<RegistryFriendlyByteBuf, WandSettingsPacket> CODEC =
            StreamCodec.ofMember(WandSettingsPacket::encode, WandSettingsPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeEnum(axis);
        buf.writeEnum(dither);
        buf.writeEnum(grain);
        buf.writeEnum(easing);
        buf.writeFloat(jitter);
        buf.writeVarInt(width);
    }

    private static WandSettingsPacket decode(RegistryFriendlyByteBuf buf) {
        return new WandSettingsPacket(
                buf.readEnum(WandSettings.Mode.class),
                buf.readEnum(WandSettings.GradientAxis.class),
                buf.readEnum(WandSettings.Dither.class),
                buf.readEnum(WandSettings.Grain.class),
                buf.readEnum(WandSettings.Easing.class),
                buf.readFloat(),
                buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<WandSettingsPacket> type() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player(), payload));
    }
*///?}
//? if neoforge {
public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 WandSettings.Easing easing,
                                 float jitter, int width) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandSettingsPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_settings"));

    // Identical wire order to the Fabric branch, so a wand behaves the same on either loader
    public static final StreamCodec<RegistryFriendlyByteBuf, WandSettingsPacket> CODEC =
            StreamCodec.ofMember(WandSettingsPacket::encode, WandSettingsPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeEnum(axis);
        buf.writeEnum(dither);
        buf.writeEnum(grain);
        buf.writeEnum(easing);
        buf.writeFloat(jitter);
        buf.writeVarInt(width);
    }

    private static WandSettingsPacket decode(RegistryFriendlyByteBuf buf) {
        return new WandSettingsPacket(
                buf.readEnum(WandSettings.Mode.class),
                buf.readEnum(WandSettings.GradientAxis.class),
                buf.readEnum(WandSettings.Dither.class),
                buf.readEnum(WandSettings.Grain.class),
                buf.readEnum(WandSettings.Easing.class),
                buf.readFloat(),
                buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<WandSettingsPacket> type() {
        return ID;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(ID, CODEC, (payload, context) -> handle((ServerPlayer) context.player(), payload));
    }
//?}

    // Runs on the main server thread on every loader, so touching the stack is safe
    private static void handle(ServerPlayer player, WandSettingsPacket packet) {
        ItemStack stack = player.getMainHandItem();

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
                .withEasing(packet.easing())
                .withJitter(Math.max(0.0f, Math.min(1.0f, packet.jitter())))
                .withWidth(packet.width()));
    }
}
