package net.tyler.gradientwand.item;

import net.minecraft.item.Item;
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
            new GradientWandItem(new Item.Settings().maxCount(1).fireproof(), WandTier.NETHERITE));

    public static Item of(WandTier tier) {
        return WANDS.get(tier);
    }

    // Looks like it does nothing now, but calling it is what loads this class, and loading the
    // class is what runs the static block above that registers all seven wands. The wands appear
    // in the mod's own tab only; they are deliberately not added to the vanilla ingredients tab.
    public static void registerModItems() {
        GradientWand.LOGGER.info("Registering Mod Items for " + GradientWand.MOD_ID);
    }

    // maxDamage and fireproof are mutually exclusive here on purpose: the netherite wand never
    // takes damage, so giving it a durability bar would leave a bar that never moves.
    private static Item registerWand(WandTier tier) {
        Item.Settings settings = new Item.Settings().maxCount(1);

        if (tier.unbreakable()) {
            settings.fireproof();
        } else {
            settings.maxDamage(tier.durability());
        }

        return registerItem(tier.itemId(), new GradientWandItem(settings, tier));
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, GradientWand.id(name), item);
    }
}
