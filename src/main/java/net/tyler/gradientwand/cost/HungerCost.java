package net.tyler.gradientwand.cost;

import net.minecraft.entity.player.PlayerEntity;

// Building is work, so it costs hunger. All the rules live here rather than being spread between
// the item and the placement queue, because the charge and the gate have to agree on what
// "half a bar" means.
public class HungerCost {

    private static final int BLOCKS_PER_STACK = 64;

    // Vanilla spends 4 exhaustion per food point, and one food point is half a shank on the
    // hunger bar, so spreading 4 exhaustion across a stack costs exactly half a bar per stack.
    private static final float PER_BLOCK = 4.0f / BLOCKS_PER_STACK;

    // Half a bar, the same as a stack costs. A build cannot start below this.
    private static final int MINIMUM_FOOD = 1;

    // Exhaustion rather than a direct food change, so saturation soaks it up first exactly as it
    // does for sprinting or mining. addExhaustion already ignores creative players.
    public static void charge(PlayerEntity player) {
        player.addExhaustion(PER_BLOCK);
    }

    // A build that empties the bar part way is still allowed to finish: it is the next one that
    // gets stopped, which is why this is only ever asked before a build starts.
    public static boolean canStart(PlayerEntity player) {
        return player.isCreative() || player.getHungerManager().getFoodLevel() >= MINIMUM_FOOD;
    }
}
