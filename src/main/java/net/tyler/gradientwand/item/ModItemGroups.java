package net.tyler.gradientwand.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.tyler.gradientwand.GradientWand;

public class ModItemGroups {

    public static final ItemGroup GRADIENT_WAND_GROUP = Registry.register(Registries.ITEM_GROUP, new Identifier(
            GradientWand.MOD_ID, "gradient_wand"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.gradient_wand"))
                    .icon(() -> new ItemStack(ModItems.GRADIENT_WAND)).entries((displayContext, entries) ->
                    {
                        entries.add(ModItems.GRADIENT_WAND);

                        // add more items to groups here
                    }).build());

    public static void registerItemGroups() {
        GradientWand.LOGGER.info("Registering item groups for " + GradientWand.MOD_ID);
    }

}
