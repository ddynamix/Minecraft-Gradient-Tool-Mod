package net.tyler.gradientwand.network;

//? if <1.21 {
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
*///?}

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.undo.UndoHistory;

// No payload at all: the whole message is "undo my last gradient".
//
// 1.20.5 replaced Fabric's own FabricPacket with vanilla's CustomPayload, so the plumbing below
// diverges completely between versions. What the packet actually does lives in handle(), shared by
// both, so the behaviour cannot drift apart while the wiring differs.
//? if <1.21 {
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
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> handle(player));
    }
//?} else {
/*public record WandUndoPacket() implements CustomPayload {

    public static final CustomPayload.Id<WandUndoPacket> ID =
            new CustomPayload.Id<>(GradientWand.id("wand_undo"));

    // A packet with no fields is always the same value, so the codec reads and writes nothing
    public static final PacketCodec<RegistryByteBuf, WandUndoPacket> CODEC =
            PacketCodec.unit(new WandUndoPacket());

    @Override
    public CustomPayload.Id<WandUndoPacket> getId() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
*///?}

    // Shared by both versions: Fabric hands this to the main server thread either way
    private static void handle(ServerPlayerEntity player) {
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
    }
}
