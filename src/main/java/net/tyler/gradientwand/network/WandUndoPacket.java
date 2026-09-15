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

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload at all: the whole message is "undo my last gradient".
//
// 1.20.5 replaced Fabric's own FabricPacket with vanilla's CustomPacketPayload, and NeoForge
// registers payloads through one event instead of per class, so the plumbing below has three
// shapes. What the packet actually does lives in handle(), shared by all of them, so the
// behaviour cannot drift apart while the wiring differs.
//? if <1.21 {
/*public record WandUndoPacket() implements FabricPacket {

    public static final PacketType<WandUndoPacket> TYPE =
            PacketType.create(GradientWand.id("wand_undo"), buf -> new WandUndoPacket());

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> handle(player));
    }
*///?}
//? if >=1.21 && fabric {
/*public record WandUndoPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandUndoPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_undo"));

    // A packet with no fields is always the same value, so the codec reads and writes nothing
    public static final StreamCodec<RegistryFriendlyByteBuf, WandUndoPacket> CODEC =
            StreamCodec.unit(new WandUndoPacket());

    @Override
    public CustomPacketPayload.Type<WandUndoPacket> type() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
*///?}
//? if neoforge {
public record WandUndoPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandUndoPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_undo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandUndoPacket> CODEC =
            StreamCodec.unit(new WandUndoPacket());

    @Override
    public CustomPacketPayload.Type<WandUndoPacket> type() {
        return ID;
    }

    // The registrar comes from RegisterPayloadHandlersEvent, which GradientWand hands in
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(ID, CODEC, (payload, context) -> handle((ServerPlayer) context.player()));
    }
//?}

    // Shared by every loader: the handler always runs on the main server thread
    private static void handle(ServerPlayer player) {
        // A wave still building is cancelled rather than queued behind
        int cancelled = PlacementQueue.cancel(player);

        if (cancelled >= 0) {
            player.displayClientMessage(Component.literal("Cancelled the build, " + cancelled + " blocks removed"), true);
            return;
        }

        int restored = UndoHistory.undo(player);

        if (restored < 0) {
            player.displayClientMessage(Component.literal("Nothing to undo").withStyle(ChatFormatting.RED), true);
            return;
        }

        player.displayClientMessage(Component.literal("Undid " + restored + " blocks"), true);
    }
}
