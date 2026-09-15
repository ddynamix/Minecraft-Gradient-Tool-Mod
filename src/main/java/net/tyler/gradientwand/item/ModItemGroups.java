package net.tyler.gradientwand.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.WandTier;

public class ModItemGroups {

    public static final CreativeModeTab GRADIENT_WAND_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            GradientWand.id("gradient_wand"),
            FabricItemGroup.builder().title(Component.translatable("itemgroup.gradient_wand"))
                    .icon(() -> new ItemStack(ModItems.of(WandTier.NETHERITE))).displayItems((displayContext, entries) ->
                    {
                        // Enum order is tier order, so the tab reads wood through netherite
                        for (WandTier tier : WandTier.values()) {
                            entries.accept(ModItems.of(tier));
                        }
                    }).build());

    public static void registerItemGroups() {
        GradientWand.LOGGER.info("Registering item groups for " + GradientWand.MOD_ID);
    }

}
