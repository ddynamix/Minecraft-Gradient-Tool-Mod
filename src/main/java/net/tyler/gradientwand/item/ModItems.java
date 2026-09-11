package net.tyler.gradientwand.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;

public class ModItems {
    public static final Item GRADIENT_WAND = registerItem("gradient_wand",
            new GradientWandItem(new FabricItemSettings().maxCount(1)));

    public static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
        entries.add(GRADIENT_WAND);
    }

    public static void registerModItems() {
        GradientWand.LOGGER.info("Registering Mod Items for " + GradientWand.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(ModItems::addItemsToIngredientItemGroup);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(GradientWand.MOD_ID, name), item);
    }
}
