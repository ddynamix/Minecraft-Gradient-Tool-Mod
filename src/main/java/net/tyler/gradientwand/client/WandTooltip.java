package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.tyler.gradientwand.item.custom.GradientWandItem;

import java.util.List;

// The menu hint lives here rather than in GradientWandItem.appendTooltip on purpose. KeyBinding is
// a client only class, and GradientWandItem is common code that also loads on a dedicated server,
// so reaching for the bound key from there risks a NoClassDefFoundError. This callback only ever
// runs on a client, which is the one place the key is knowable at all.
public class WandTooltip {

    public static void register() {
        // 1.21 added a TooltipType parameter, so the lambda takes four arguments there
        //? if <1.21 {
        /*ItemTooltipCallback.EVENT.register((stack, context, lines) -> addMenuHint(stack, lines));
        *///?} else {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addMenuHint(stack, lines));
        //?}
    }

    // Shared by both versions: only the callback's shape differs, never what it adds
    private static void addMenuHint(ItemStack stack, List<Component> lines) {
        if (!(stack.getItem() instanceof GradientWandItem)) {
            return;
        }

        // Looked up per frame, so rebinding the key in Controls is reflected immediately
        lines.add(Component.translatable("tooltip.gradient_wand.menu", ModKeyBindings.menuKeyLabel())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
