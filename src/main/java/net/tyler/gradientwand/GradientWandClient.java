package net.tyler.gradientwand;

import net.fabricmc.api.ClientModInitializer;
import net.tyler.gradientwand.client.GradientPreviewRenderer;

public class GradientWandClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GradientPreviewRenderer.register();
    }
}