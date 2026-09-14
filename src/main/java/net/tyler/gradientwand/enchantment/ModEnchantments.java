package net.tyler.gradientwand.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.GradientWandItem;

public class ModEnchantments {

    public static final int MAX_LEVEL = 3;

    // Both are treasure enchantments, and that is not decoration. EnchantmentHelper's table
    // candidate list filters by the EnchantmentTarget enum rather than by isAcceptableItem, so a
    // wand-only enchantment declared against a vanilla target would still be offered on pickaxes
    // and swords. Treasure keeps them out of the table entirely; the anvil, which does use
    // isAcceptableItem, then restricts them to wands properly.
    public static final Enchantment CAPACITY = register("capacity", new WandEnchantment(Enchantment.Rarity.RARE));
    public static final Enchantment STAMINA = register("stamina", new WandEnchantment(Enchantment.Rarity.RARE));

    // How much of the tier's block cap this wand gets: half again per level.
    public static float capacityMultiplier(ItemStack stack) {
        return 1.0f + 0.5f * EnchantmentHelper.getLevel(CAPACITY, stack);
    }

    // What fraction of the usual hunger this wand costs. Level 3 returns exactly 0, which is what
    // makes the cost vanish rather than merely shrink.
    public static float hungerMultiplier(ItemStack stack) {
        int level = Math.min(EnchantmentHelper.getLevel(STAMINA, stack), MAX_LEVEL);

        return (float) (MAX_LEVEL - level) / MAX_LEVEL;
    }

    public static void register() {
        GradientWand.LOGGER.info("Registering enchantments for " + GradientWand.MOD_ID);
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
}
