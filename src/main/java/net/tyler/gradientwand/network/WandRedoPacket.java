package net.tyler.gradientwand.network;

//? if <1.21 {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?} else {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//?}

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload: "put my last undone gradient back"
//? if <1.21 {
/*public record WandRedoPacket() implements FabricPacket {

    public static final PacketType<WandRedoPacket> TYPE =
            PacketType.create(GradientWand.id("wand_redo"), buf -> new WandRedoPacket());

    @Override
    public void write(PacketByteBuf buf) {
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> handle(player));
    }
*///?} else {
public record WandRedoPacket() implements CustomPayload {

    public static final CustomPayload.Id<WandRedoPacket> ID =
            new CustomPayload.Id<>(GradientWand.id("wand_redo"));

    public static final PacketCodec<RegistryByteBuf, WandRedoPacket> CODEC =
            PacketCodec.unit(new WandRedoPacket());

    @Override
    public CustomPayload.Id<WandRedoPacket> getId() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
//?}

    private static void handle(ServerPlayerEntity player) {
        int placed = UndoHistory.redo(player);

        // The shortage list has already been sent by then
        if (placed == UndoHistory.CANNOT_AFFORD) {
            return;
        }

        if (placed == UndoHistory.NOTHING) {
            player.sendMessage(Text.literal("Nothing to redo").formatted(Formatting.RED), true);
            return;
        }

        player.sendMessage(Text.literal("Redid " + placed + " blocks"), true);
    }
}
