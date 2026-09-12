package net.tyler.gradientwand.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload: "put my last undone gradient back"
public record WandRedoPacket() implements FabricPacket {

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
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> {
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
        });
    }
}