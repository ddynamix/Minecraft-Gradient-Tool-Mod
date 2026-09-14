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
import net.tyler.gradientwand.item.custom.WandTier;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

    // One wand per tier. Keyed by tier so the item group and anything else can walk them in order
    // rather than naming all seven by hand.
    private static final Map<WandTier, Item> WANDS = new EnumMap<>(WandTier.class);

    static {
        for (WandTier tier : WandTier.values()) {
            WANDS.put(tier, registerWand(tier));
        }
    }

    // The original wand, still registered so copies already sitting in a world keep loading. It is
    // deliberately not in any creative tab: the seven tiers replace it, and its sprite is gone.
    public static final Item GRADIENT_WAND = registerItem("gradient_wand",
            new GradientWandItem(new FabricItemSettings().maxCount(1).fireproof(), WandTier.NETHERITE));

    public static Item of(WandTier tier) {
        return WANDS.get(tier);
    }

    public static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
        for (WandTier tier : WandTier.values()) {
            entries.add(of(tier));
        }
    }

    public static void registerModItems() {
        GradientWand.LOGGER.info("Registering Mod Items for " + GradientWand.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(ModItems::addItemsToIngredientItemGroup);
    }

    // maxDamage and fireproof are mutually exclusive here on purpose: the netherite wand never
    // takes damage, so giving it a durability bar would leave a bar that never moves.
    private static Item registerWand(WandTier tier) {
        FabricItemSettings settings = new FabricItemSettings().maxCount(1);

        if (tier.unbreakable()) {
            settings.fireproof();
        } else {
            settings.maxDamage(tier.durability());
        }

        return registerItem(tier.itemId(), new GradientWandItem(settings, tier));
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(GradientWand.MOD_ID, name), item);
    }
}
