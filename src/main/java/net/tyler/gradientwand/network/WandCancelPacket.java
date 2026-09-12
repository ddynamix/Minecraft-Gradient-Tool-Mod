package net.tyler.gradientwand.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;

// No payload: "clear my selection". Sent on left click, whether or not a block was under the crosshair.
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
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> {
            ItemStack stack = player.getMainHandStack();


            if (stack.getItem() instanceof GradientWandItem) {
                GradientWandItem.cancelSelection(player, stack);
            }
        });
    }
}