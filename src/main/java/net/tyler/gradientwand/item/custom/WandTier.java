package net.tyler.gradientwand.item.custom;

import net.minecraft.item.ItemStack;

// Everything that differs between the seven wands. A durability of 0 means the tier never wears
// out at all, which is how vanilla marks an item as not damageable.
public enum WandTier {

    WOOD("wooden", "Wooden", 128, 16, 3),
    STONE("stone", "Stone", 256, 32, 5),
    COPPER("copper", "Copper", 312, 64, 5),
    IRON("iron", "Iron", 512, 128, 7),
    GOLD("gold", "Gold", 128, 128, 16),
    DIAMOND("diamond", "Diamond", 2056, 256, 32),
    NETHERITE("netherite", "Netherite", 0, 4096, 128);

    private final String name;
    private final String label;
    private final int durability;
    private final int maxBlocks;
    private final int maxWidth;

    WandTier(String name, String label, int durability, int maxBlocks, int maxWidth) {
        this.name = name;
        this.label = label;
        this.durability = durability;
        this.maxBlocks = maxBlocks;
        this.maxWidth = maxWidth;
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

    public boolean unbreakable() {
        return durability == 0;
    }

    // The tier of the wand in this stack. Anything that is not a wand gets the most permissive
    // tier, because every caller has already checked and the answer would go unused.
    public static WandTier of(ItemStack stack) {
        return stack.getItem() instanceof GradientWandItem wand ? wand.tier() : NETHERITE;
    }
}
