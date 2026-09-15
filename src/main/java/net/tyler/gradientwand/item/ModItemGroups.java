package net.tyler.gradientwand.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.WandTier;

public class ModItemGroups {

    public static final ItemGroup GRADIENT_WAND_GROUP = Registry.register(Registries.ITEM_GROUP,
            GradientWand.id("gradient_wand"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.gradient_wand"))
                    .icon(() -> new ItemStack(ModItems.of(WandTier.NETHERITE))).entries((displayContext, entries) ->
                    {
                        // Enum order is tier order, so the tab reads wood through netherite
                        for (WandTier tier : WandTier.values()) {
                            entries.add(ModItems.of(tier));
                        }
                    }).build());

    public static void registerItemGroups() {
        GradientWand.LOGGER.info("Registering item groups for " + GradientWand.MOD_ID);
    }

}
