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
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;

// No payload: "clear my selection". Sent on left click, whether or not a block was under the crosshair.
//? if <1.21 {
public record WandCancelPacket() implements FabricPacket {

    public static final PacketType<WandCancelPacket> TYPE =
            PacketType.create(GradientWand.id("wand_cancel"), buf -> new WandCancelPacket());

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
/*public record WandCancelPacket() implements CustomPayload {

    public static final CustomPayload.Id<WandCancelPacket> ID =
            new CustomPayload.Id<>(GradientWand.id("wand_cancel"));

    public static final PacketCodec<RegistryByteBuf, WandCancelPacket> CODEC =
            PacketCodec.unit(new WandCancelPacket());

    @Override
    public CustomPayload.Id<WandCancelPacket> getId() {
        return ID;
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> handle(context.player()));
    }
*///?}

    private static void handle(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();

        if (stack.getItem() instanceof GradientWandItem) {
            GradientWandItem.cancelSelection(player, stack);
        }
    }
}
