package net.tyler.gradientwand.undo;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.tyler.gradientwand.cost.MaterialCost;

import java.util.*;

// Server side only, and only ever touched from the main server thread, so plain HashMaps are fine.
public class UndoHistory {

    private static final int MAX_STEPS = 8;

    public static final int NOTHING = -1;
    public static final int CANNOT_AFFORD = -2;

    // Recording the "after" state as well as the "before" is what lets undo leave later building alone
    public record Change(BlockPos pos, BlockState before, BlockState after) {
    }

    // "paid" travels with the step: undo refunds only what was charged, and redo re-charges it.
    // Deciding this per step rather than by checking creative later means switching game mode
    // between building and undoing cannot mint or destroy blocks.
    public record Step(List<Change> changes, boolean paid) {
    }

    // One history per player per dimension, so undoing in the Nether cannot reach into the Overworld
    private record HistoryKey(UUID player, RegistryKey<World> world) {
    }

    private static final Map<HistoryKey, Deque<Step>> UNDO = new HashMap<>();
    private static final Map<HistoryKey, Deque<Step>> REDO = new HashMap<>();

    public static void record(PlayerEntity player, List<Change> changes, boolean paid) {
        if (changes.isEmpty()) {
            return;
        }

        HistoryKey key = keyFor(player);
        Deque<Step> steps = UNDO.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        steps.push(new Step(changes, paid));

        while (steps.size() > MAX_STEPS) {
            steps.removeLast();
        }

        // A fresh build makes anything that was undone no longer repeatable
        REDO.remove(key);
    }

    // How many blocks were put back, or NOTHING when there was nothing to undo
    public static int undo(PlayerEntity player) {
        HistoryKey key = keyFor(player);
        Deque<Step> steps = UNDO.get(key);

        if (steps == null || steps.isEmpty()) {
            return NOTHING;
        }

        World world = player.getWorld();
        Step step = steps.pop();
        List<BlockState> refunds = new ArrayList<>();
        int restored = 0;

        for (Change change : step.changes()) {
            // Block states are interned, so identity is the right test for "this is still ours".
            // Anything built here since the gradient landed is left untouched.
            if (world.getBlockState(change.pos()) != change.after()) {
                continue;
            }

            if (world.setBlockState(change.pos(), change.before(), Block.NOTIFY_LISTENERS)) {
                restored++;
                refunds.add(change.after());
            }
        }

        if (step.paid() && !refunds.isEmpty()) {
            MaterialCost.refund(player, MaterialCost.countStates(refunds));
        }

        REDO.computeIfAbsent(key, ignored -> new ArrayDeque<>()).push(step);

        return restored;
    }

    // How many blocks went back in, NOTHING when there is nothing to redo, or CANNOT_AFFORD
    // when the blocks are no longer in the player's inventory
    public static int redo(PlayerEntity player) {
        HistoryKey key = keyFor(player);
        Deque<Step> steps = REDO.get(key);

        if (steps == null || steps.isEmpty()) {
            return NOTHING;
        }

        World world = player.getWorld();
        Step step = steps.peek();

        // Only what can still go back, mirroring undo's caution
        List<Change> applicable = new ArrayList<>();
        List<BlockState> cost = new ArrayList<>();

        for (Change change : step.changes()) {
            if (world.getBlockState(change.pos()) == change.before()) {
                applicable.add(change);
                cost.add(change.after());
            }
        }

        // Redo has to charge again, or undo followed by redo would be free blocks
        if (step.paid() && !cost.isEmpty()) {
            Map<Item, Integer> needed = MaterialCost.countStates(cost);
            Map<Item, Integer> held = MaterialCost.available(player);

            if (!MaterialCost.hasEnough(needed, held)) {
                MaterialCost.report(player, needed, held);
                return CANNOT_AFFORD;
            }

            MaterialCost.consume(player, needed);
        }

        int placed = 0;

        for (Change change : applicable) {
            if (world.setBlockState(change.pos(), change.after(), Block.NOTIFY_LISTENERS)) {
                placed++;
            }
        }

        steps.pop();
        UNDO.computeIfAbsent(key, ignored -> new ArrayDeque<>()).push(step);

        return placed;
    }

    public static void registerCleanup() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }

    private static void forget(PlayerEntity player) {
        UNDO.keySet().removeIf(key -> key.player().equals(player.getUuid()));
        REDO.keySet().removeIf(key -> key.player().equals(player.getUuid()));
    }

    private static HistoryKey keyFor(PlayerEntity player) {
        return new HistoryKey(player.getUuid(), player.getWorld().getRegistryKey());
    }
}