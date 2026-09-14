package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.tyler.gradientwand.item.custom.GradientWandItem;

// The menu hint lives here rather than in GradientWandItem.appendTooltip on purpose. KeyBinding is
// a client only class, and GradientWandItem is common code that also loads on a dedicated server,
// so reaching for the bound key from there risks a NoClassDefFoundError. This callback only ever
// runs on a client, which is the one place the key is knowable at all.
public class WandTooltip {

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (!(stack.getItem() instanceof GradientWandItem)) {
                return;
            }

            // Looked up per frame, so rebinding the key in Controls is reflected immediately
            lines.add(Text.translatable("tooltip.gradient_wand.menu", ModKeyBindings.menuKeyLabel())
                    .formatted(Formatting.DARK_GRAY));
        });
    }
}
