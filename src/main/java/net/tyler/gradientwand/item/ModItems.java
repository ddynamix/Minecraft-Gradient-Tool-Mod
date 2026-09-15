package net.tyler.gradientwand.item;

//? if fabric {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
*///?}
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
//?}

import net.minecraft.world.item.Item;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.WandTier;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {

    //? if fabric {
    /*// One wand per tier. Keyed by tier so the item group and anything else can walk them in order
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
            new GradientWandItem(new Item.Properties().stacksTo(1).fireResistant(), WandTier.NETHERITE));

    // Looks like it does nothing now, but calling it is what loads this class, and loading the
    // class is what runs the static block above that registers all seven wands.
    public static void registerModItems() {
        GradientWand.LOGGER.info("Registering Mod Items for " + GradientWand.MOD_ID);
    }

    public static Item of(WandTier tier) {
        return WANDS.get(tier);
    }

    private static Item registerWand(WandTier tier) {
        return registerItem(tier.itemId(), new GradientWandItem(propertiesFor(tier), tier));
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, GradientWand.id(name), item);
    }
    *///?}
    //? if neoforge {
    // NeoForge cannot register during class init: entries are collected here and handed to the
    // mod event bus, which flushes them at the right point in startup.
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(GradientWand.MOD_ID);

    // The holders are deliberately left unresolved. Calling value() or get() on one here would
    // throw, because nothing is in the registry until register(modBus) has been flushed during
    // startup. of() resolves them instead, and every caller of of() runs long after that.
    private static final Map<WandTier, DeferredItem<Item>> WANDS = new EnumMap<>(WandTier.class);

    static {
        for (WandTier tier : WandTier.values()) {
            WANDS.put(tier, ITEMS.register(tier.itemId(),
                    () -> new GradientWandItem(propertiesFor(tier), tier)));
        }
    }

    // The original wand, still registered so copies already sitting in a world keep loading. It is
    // deliberately not in any creative tab: the seven tiers replace it, and its sprite is gone.
    public static final DeferredItem<Item> GRADIENT_WAND = ITEMS.register("gradient_wand",
            () -> new GradientWandItem(new Item.Properties().stacksTo(1).fireResistant(), WandTier.NETHERITE));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);

        GradientWand.LOGGER.info("Registering Mod Items for " + GradientWand.MOD_ID);
    }

    public static Item of(WandTier tier) {
        return WANDS.get(tier).get();
    }
    //?}

    // durability and fireResistant are mutually exclusive here on purpose: the netherite wand never
    // takes damage, so giving it a durability bar would leave a bar that never moves.
    private static Item.Properties propertiesFor(WandTier tier) {
        Item.Properties settings = new Item.Properties().stacksTo(1);

        if (tier.unbreakable()) {
            settings.fireResistant();
        } else {
            settings.durability(tier.durability());
        }

        return settings;
    }
}
