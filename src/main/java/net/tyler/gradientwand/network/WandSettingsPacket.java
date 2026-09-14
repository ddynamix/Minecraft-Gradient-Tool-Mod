package net.tyler.gradientwand.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.WandSettings;

// Client to server: "set my wand to these settings". The seed is deliberately not included.
public record WandSettingsPacket(WandSettings.Mode mode, WandSettings.GradientAxis axis,
                                 WandSettings.Dither dither, WandSettings.Grain grain,
                                 float jitter, int width) implements FabricPacket {

    public static final PacketType<WandSettingsPacket> TYPE =
            PacketType.create(GradientWand.id("wand_settings"), WandSettingsPacket::new);

    public WandSettingsPacket(PacketByteBuf buf) {
        this(buf.readEnumConstant(WandSettings.Mode.class),
                buf.readEnumConstant(WandSettings.GradientAxis.class),
                buf.readEnumConstant(WandSettings.Dither.class),
                buf.readEnumConstant(WandSettings.Grain.class),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(mode);
        buf.writeEnumConstant(axis);
        buf.writeEnumConstant(dither);
        buf.writeEnumConstant(grain);
        buf.writeFloat(jitter);
        buf.writeVarInt(width);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void registerReceiver() {
        // Fabric runs this on the main server thread, so touching the stack here is safe
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, player, responseSender) -> {
            ItemStack stack = player.getMainHandStack();

            if (!(stack.getItem() instanceof GradientWandItem)) {
                return;
            }

            // Never trust the client: keep the server's own seed and clamp what came over the wire
            WandSettings.from(stack)
                    .withMode(packet.mode())
                    .withAxis(packet.axis())
                    .withDither(packet.dither())
                    .withGrain(packet.grain())
                    .withJitter(Math.max(0.0f, Math.min(1.0f, packet.jitter())))
                    .withWidth(packet.width())
                    .save(stack);
        });
    }
}
