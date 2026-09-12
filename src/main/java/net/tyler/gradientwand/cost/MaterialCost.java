package net.tyler.gradientwand.cost;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    public static Map<Item, Integer> available(PlayerEntity player) {
        Map<Item, Integer> available = new HashMap<>();
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);

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
    public static void report(PlayerEntity player, Map<Item, Integer> required, Map<Item, Integer> available) {
        player.sendMessage(Text.literal("Not enough blocks:").formatted(Formatting.GRAY), false);

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int have = available.getOrDefault(entry.getKey(), 0);
            int need = entry.getValue();

            player.sendMessage(Text.empty()
                    .append(entry.getKey().getName())
                    .append(Text.literal(": " + have + "/" + need))
                    .formatted(have >= need ? Formatting.GREEN : Formatting.RED), false);
        }
    }

    // Hands blocks back in legal stack sizes. Anything that will not fit is dropped at the
    // player's feet rather than destroyed.
    public static void refund(PlayerEntity player, Map<Item, Integer> refunds) {
        for (Map.Entry<Item, Integer> entry : refunds.entrySet()) {
            int remaining = entry.getValue();
            int max = new ItemStack(entry.getKey()).getMaxCount();

            while (remaining > 0) {
                int count = Math.min(remaining, max);

                player.getInventory().offerOrDrop(new ItemStack(entry.getKey(), count));
                remaining -= count;
            }
        }
    }

    // Inventory before hotbar, so the blocks you are holding are the last ones taken
    public static void consume(PlayerEntity player, Map<Item, Integer> required) {
        PlayerInventory inventory = player.getInventory();

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();

            for (int slot : consumeOrder()) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack stack = inventory.getStack(slot);

                if (!stack.isOf(entry.getKey())) {
                    continue;
                }

                int taken = Math.min(remaining, stack.getCount());

                inventory.removeStack(slot, taken);
                remaining -= taken;
            }
        }
    }

    private static int[] consumeOrder() {
        int hotbar = PlayerInventory.getHotbarSize();
        int[] order = new int[PlayerInventory.MAIN_SIZE];
        int next = 0;

        for (int slot = hotbar; slot < PlayerInventory.MAIN_SIZE; slot++) {
            order[next++] = slot;
        }

        for (int slot = 0; slot < hotbar; slot++) {
            order[next++] = slot;
        }

        return order;
    }
}