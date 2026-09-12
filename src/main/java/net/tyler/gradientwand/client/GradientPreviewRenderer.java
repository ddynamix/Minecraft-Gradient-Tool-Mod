package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.tyler.gradientwand.item.custom.GradientWandItem;

import java.util.List;

public class GradientPreviewRenderer {

    private static final float ALPHA = 0.9f;
    private static final double GROW = 0.002;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(GradientPreviewRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (!(stack.getItem() instanceof GradientWandItem)) {
            return;
        }

        List<GradientWandItem.PlannedBlock> planned = GradientWandItem.previewFor(player, stack);

        if (planned.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (GradientWandItem.PlannedBlock block : planned) {
            BlockPos pos = block.pos();

            if (!context.world().getBlockState(pos).isReplaceable()) {
                continue;
            }

            int rgb = block.state().getMapColor(context.world(), pos).color;

            if (rgb == 0) {
                rgb = 0xFFFFFF;
            }

            float red = ((rgb >> 16) & 0xFF) / 255.0f;
            float green = ((rgb >> 8) & 0xFF) / 255.0f;
            float blue = (rgb & 0xFF) / 255.0f;

            WorldRenderer.drawBox(matrices, lines,
                    pos.getX() - GROW, pos.getY() - GROW, pos.getZ() - GROW,
                    pos.getX() + 1 + GROW, pos.getY() + 1 + GROW, pos.getZ() + 1 + GROW,
                    red, green, blue, ALPHA);
        }

        matrices.pop();
    }
}