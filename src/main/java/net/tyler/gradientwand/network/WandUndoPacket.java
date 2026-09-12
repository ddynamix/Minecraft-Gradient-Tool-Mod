package net.tyler.gradientwand.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload at all: the whole message is "undo my last gradient"
public record WandUndoPacket() implements FabricPacket {

    public static final PacketType<WandUndoPacket> TYPE =
            PacketType.create(GradientWand.id("wand_undo"), buf -> new WandUndoPacket());

    @Override
    public void write(PacketByteBuf buf) {
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> {
            // A wave still building is cancelled rather than queued behind
            int cancelled = PlacementQueue.cancel(player);

            if (cancelled >= 0) {
                player.sendMessage(Text.literal("Cancelled the build, " + cancelled + " blocks removed"), true);
                return;
            }

            int restored = UndoHistory.undo(player);

            if (restored < 0) {
                player.sendMessage(Text.literal("Nothing to undo").formatted(Formatting.RED), true);
                return;
            }

            player.sendMessage(Text.literal("Undid " + restored + " blocks"), true);
        });
    }
}