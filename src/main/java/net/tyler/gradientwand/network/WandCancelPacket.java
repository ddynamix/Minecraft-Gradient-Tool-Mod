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
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;

// No payload: "clear my selection". Sent on left click, whether or not a block was under the crosshair.
//? if <1.21 {
/*public record WandCancelPacket() implements FabricPacket {

    public static final PacketType<WandCancelPacket> TYPE =
            PacketType.create(GradientWand.id("wand_cancel"), buf -> new WandCancelPacket());

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
public record WandCancelPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WandCancelPacket> ID =
            new CustomPacketPayload.Type<>(GradientWand.id("wand_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandCancelPacket> CODEC =
            StreamCodec.unit(new WandCancelPacket());

    @Override
    public CustomPacketPayload.Type<WandCancelPacket> type() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
//?}

    private static void handle(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof GradientWandItem) {
            GradientWandItem.cancelSelection(player, stack);
        }
    }
}
