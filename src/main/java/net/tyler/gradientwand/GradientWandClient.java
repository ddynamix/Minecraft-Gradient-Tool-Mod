package net.tyler.gradientwand;

//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;
*///?}
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
//?}

import net.tyler.gradientwand.client.GradientPreviewRenderer;
import net.tyler.gradientwand.client.ModKeyBindings;
import net.tyler.gradientwand.client.WandTooltip;

//? if neoforge {
// dist = Dist.CLIENT keeps this class, and everything it touches, off a dedicated server
@Mod(value = GradientWand.MOD_ID, dist = Dist.CLIENT)
//?}
public class GradientWandClient
        //? if fabric {
        /*implements ClientModInitializer
        *///?}
{
    //? if fabric {
    /*@Override
    public void onInitializeClient() {
        GradientPreviewRenderer.register();
        ModKeyBindings.register();
        WandTooltip.register();
    }
    *///?}
    //? if neoforge {
    // The key binding is a startup (mod bus) event; the rest happen while a world is running,
    // so they go on the game bus. Fabric registers all of them the same way, which is why this
    // split exists only here.
    public GradientWandClient(IEventBus modBus) {
        modBus.addListener(ModKeyBindings::registerKeys);

        NeoForge.EVENT_BUS.addListener(ModKeyBindings::onClientTick);
        NeoForge.EVENT_BUS.addListener(GradientPreviewRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WandTooltip::onItemTooltip);
    }
    //?}
}
