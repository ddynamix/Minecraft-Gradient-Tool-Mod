package net.tyler.gradientwand.item.custom;

import net.minecraft.world.item.ItemStack;

// Everything that differs between the seven wands. A durability of 0 means the tier never wears
// out at all, which is how vanilla marks an item as not damageable.
public enum WandTier {

    // Enchantability is taken straight from the matching vanilla tool material, so a wand behaves
    // at the table the way a player already expects that metal to. Copper has no vanilla tool, so
    // it sits between stone and diamond.
    WOOD("wooden", "Wooden", 128, 16, 3, 15),
    STONE("stone", "Stone", 256, 32, 5, 5),
    COPPER("copper", "Copper", 312, 64, 5, 8),
    IRON("iron", "Iron", 512, 128, 7, 14),
    GOLD("gold", "Gold", 128, 128, 16, 22),
    DIAMOND("diamond", "Diamond", 2056, 256, 32, 10),
    NETHERITE("netherite", "Netherite", 0, 4096, 128, 15);

    private final String name;
    private final String label;
    private final int durability;
    private final int maxBlocks;
    private final int maxWidth;
    private final int enchantability;

    WandTier(String name, String label, int durability, int maxBlocks, int maxWidth, int enchantability) {
        this.name = name;
        this.label = label;
        this.durability = durability;
        this.maxBlocks = maxBlocks;
        this.maxWidth = maxWidth;
        this.enchantability = enchantability;
    }

    // wooden_gradient_wand, stone_gradient_wand, and so on
    public String itemId() {
        return name + "_gradient_wand";
    }

    // The sprite file, which is named after the material alone
    public String texture() {
        return name + "_wand";
    }

    public String label() {
        return label;
    }

    public int durability() {
        return durability;
    }

    public int maxBlocks() {
        return maxBlocks;
    }

    public int maxWidth() {
        return maxWidth;
    }

    // Netherite carries a real value despite never wearing out, so that the two wand enchantments
    // are open to it. It still cannot take Unbreaking or Mending: those target BREAKABLE, and a
    // wand with no durability bar fails that test whatever its enchantability says.
    public int enchantability() {
        return enchantability;
    }

    public boolean unbreakable() {
        return durability == 0;
    }

    // The tier of the wand in this stack. Anything that is not a wand gets the most permissive
    // tier, because every caller has already checked and the answer would go unused.
    public static WandTier of(ItemStack stack) {
        return stack.getItem() instanceof GradientWandItem wand ? wand.tier() : NETHERITE;
    }
}
