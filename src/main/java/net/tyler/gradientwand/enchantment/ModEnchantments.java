package net.tyler.gradientwand.enchantment;

//? if <1.21 {
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.tyler.gradientwand.item.custom.GradientWandItem;
//?} else {
/*import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
*///?}

import net.minecraft.item.ItemStack;
import net.tyler.gradientwand.GradientWand;

// Capacity raises the tier's block cap; Stamina cuts the hunger cost.
//
// 1.21 made enchantments data driven and Enchantment final, so the two are defined in
// data/gradient_wand/enchantment/*.json there instead of being subclasses here. What the JSON
// expresses is what the old overrides did: supported_items pins them to the wand item tag, and
// leaving them out of #minecraft:in_enchanting_table is what keeps them off the enchanting table,
// in place of the old isTreasure() flag.
public class ModEnchantments {

    public static final int MAX_LEVEL = 3;

    //? if <1.21 {
    // Treasure, so the table never offers them; isAcceptableItem then restricts the anvil to wands
    public static final Enchantment CAPACITY = register("capacity", new WandEnchantment(Enchantment.Rarity.RARE));
    public static final Enchantment STAMINA = register("stamina", new WandEnchantment(Enchantment.Rarity.RARE));

    private static int levelOf(Enchantment enchantment, ItemStack stack) {
        return EnchantmentHelper.getLevel(enchantment, stack);
    }

    private static Enchantment register(String name, Enchantment enchantment) {
        return Registry.register(Registries.ENCHANTMENT, GradientWand.id(name), enchantment);
    }

    // Shared shape for both: three levels, held in the main hand, and only ever on a wand.
    private static class WandEnchantment extends Enchantment {

        private WandEnchantment(Rarity rarity) {
            super(rarity, EnchantmentTarget.BREAKABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        }

        // The anvil and /enchant both go through here, which is what keeps these off other tools.
        // The netherite wand has no durability bar, so this deliberately asks what the item is
        // rather than whether it is damageable.
        @Override
        public boolean isAcceptableItem(ItemStack stack) {
            return stack.getItem() instanceof GradientWandItem;
        }

        @Override
        public int getMaxLevel() {
            return MAX_LEVEL;
        }

        @Override
        public boolean isTreasure() {
            return true;
        }

        @Override
        public int getMinPower(int level) {
            return 15 + (level - 1) * 9;
        }

        @Override
        public int getMaxPower(int level) {
            return getMinPower(level) + 30;
        }
    }
    //?} else {
    /*// Keys, not entries. An enchantment now lives in a dynamic registry, so there is no static
    // instance to hold; a key is the stable thing, and it is all the level lookup below needs.
    public static final RegistryKey<Enchantment> CAPACITY = key("capacity");
    public static final RegistryKey<Enchantment> STAMINA = key("stamina");

    private static RegistryKey<Enchantment> key(String name) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, GradientWand.id(name));
    }

    // EnchantmentHelper.getLevel wants a RegistryEntry, and resolving one needs a registry manager
    // that these callers do not have. The stack already carries its enchantments as entries, so
    // matching on the key reads the level straight off the item instead.
    private static int levelOf(RegistryKey<Enchantment> key, ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);

        if (enchantments == null) {
            return 0;
        }

        for (var entry : enchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();

            if (enchantment.matchesKey(key)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }
    *///?}

    // How much of the tier's block cap this wand gets: half again per level.
    public static float capacityMultiplier(ItemStack stack) {
        return 1.0f + 0.5f * levelOf(CAPACITY, stack);
    }

    // What fraction of the usual hunger this wand costs. Level 3 returns exactly 0, which is what
    // makes the cost vanish rather than merely shrink.
    public static float hungerMultiplier(ItemStack stack) {
        int level = Math.min(levelOf(STAMINA, stack), MAX_LEVEL);

        return (float) (MAX_LEVEL - level) / MAX_LEVEL;
    }

    public static void register() {
        GradientWand.LOGGER.info("Registering enchantments for " + GradientWand.MOD_ID);
    }
}
