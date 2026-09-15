package net.tyler.gradientwand.network;

//? if <1.21 {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?} else {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload: "put my last undone gradient back"
//? if <1.21 {
/*public record WandRedoPacket() implements FabricPacket {

    public static final PacketType<WandRedoPacket> TYPE =
            PacketType.create(GradientWand.id("wand_redo"), buf -> new WandRedoPacket());

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
*///?} else {
public record WandRedoPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandRedoPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_redo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandRedoPacket> CODEC =
            StreamCodec.unit(new WandRedoPacket());

    @Override
    public CustomPacketPayload.Type<WandRedoPacket> type() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
//?}

    private static void handle(ServerPlayer player) {
        int placed = UndoHistory.redo(player);

        // The shortage list has already been sent by then
        if (placed == UndoHistory.CANNOT_AFFORD) {
            return;
        }

        if (placed == UndoHistory.NOTHING) {
            player.displayClientMessage(Component.literal("Nothing to redo").withStyle(ChatFormatting.RED), true);
            return;
        }

        player.displayClientMessage(Component.literal("Redid " + placed + " blocks"), true);
    }
}
