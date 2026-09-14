package net.tyler.gradientwand;

import net.fabricmc.api.ClientModInitializer;
import net.tyler.gradientwand.client.GradientPreviewRenderer;
import net.tyler.gradientwand.client.ModKeyBindings;
import net.tyler.gradientwand.client.WandTooltip;

public class GradientWandClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GradientPreviewRenderer.register();
        ModKeyBindings.register();
        WandTooltip.register();
    }
}