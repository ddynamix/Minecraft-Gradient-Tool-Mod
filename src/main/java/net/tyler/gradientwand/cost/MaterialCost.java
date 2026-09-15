package net.tyler.gradientwand.cost;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.tyler.gradientwand.item.custom.GradientWandItem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MaterialCost {

    // LinkedHashMap so the shortage list reads in gradient order rather than hash order
    public static Map<Item, Integer> required(List<GradientWandItem.PlannedBlock> planned) {
        Map<Item, Integer> required = new LinkedHashMap<>();

        for (GradientWandItem.PlannedBlock block : planned) {
            required.merge(block.state().getBlock().asItem(), 1, Integer::sum);
        }

        return required;
    }

    // The same count, taken from raw block states. Undo and redo work from history, which
    // stores states rather than planned blocks.
    public static Map<Item, Integer> countStates(List<BlockState> states) {
        Map<Item, Integer> counts = new LinkedHashMap<>();

        for (BlockState state : states) {
            counts.merge(state.getBlock().asItem(), 1, Integer::sum);
        }

        return counts;
    }

    public static Map<Item, Integer> available(Player player) {
        Map<Item, Integer> available = new HashMap<>();
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        return available;
    }

    public static boolean hasEnough(Map<Item, Integer> required, Map<Item, Integer> available) {
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }

        return true;
    }

    // Lists everything the gradient needs, green where there is enough and red where there is not
    public static void report(Player player, Map<Item, Integer> required, Map<Item, Integer> available) {
        player.displayClientMessage(Component.literal("Not enough blocks:").withStyle(ChatFormatting.GRAY), false);

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int have = available.getOrDefault(entry.getKey(), 0);
            int need = entry.getValue();

            player.displayClientMessage(Component.empty()
                    .append(entry.getKey().getDescription())
                    .append(Component.literal(": " + have + "/" + need))
                    .withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }
    }

    // Hands blocks back in legal stack sizes. Anything that will not fit is dropped at the
    // player's feet rather than destroyed.
    public static void refund(Player player, Map<Item, Integer> refunds) {
        for (Map.Entry<Item, Integer> entry : refunds.entrySet()) {
            int remaining = entry.getValue();
            int max = new ItemStack(entry.getKey()).getMaxStackSize();

            while (remaining > 0) {
                int count = Math.min(remaining, max);

                player.getInventory().placeItemBackInInventory(new ItemStack(entry.getKey(), count));
                remaining -= count;
            }
        }
    }

    // Inventory before hotbar, so the blocks you are holding are the last ones taken
    public static void consume(Player player, Map<Item, Integer> required) {
        Inventory inventory = player.getInventory();

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();

            for (int slot : consumeOrder()) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack stack = inventory.getItem(slot);

                if (!stack.is(entry.getKey())) {
                    continue;
                }

                int taken = Math.min(remaining, stack.getCount());

                inventory.removeItem(slot, taken);
                remaining -= taken;
            }
        }
    }

    private static int[] consumeOrder() {
        int hotbar = Inventory.getSelectionSize();
        int[] order = new int[Inventory.INVENTORY_SIZE];
        int next = 0;

        for (int slot = hotbar; slot < Inventory.INVENTORY_SIZE; slot++) {
            order[next++] = slot;
        }

        for (int slot = 0; slot < hotbar; slot++) {
            order[next++] = slot;
        }

        return order;
    }
}