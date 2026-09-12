package net.tyler.gradientwand.undo;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Server side only, and only ever touched from the main server thread, so a plain HashMap is fine.
public class UndoHistory {

    private static final int MAX_STEPS = 8;

    // Recording the "after" state as well as the "before" is what lets undo leave later building alone
    public record Change(BlockPos pos, BlockState before, BlockState after) {
    }

    // One history per player per dimension, so undoing in the Nether cannot reach into the Overworld
    private record HistoryKey(UUID player, RegistryKey<World> world) {
    }

    private static final Map<HistoryKey, Deque<List<Change>>> HISTORY = new HashMap<>();

    public static void record(PlayerEntity player, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }

        Deque<List<Change>> steps = HISTORY.computeIfAbsent(keyFor(player), key -> new ArrayDeque<>());

        steps.push(changes);

        while (steps.size() > MAX_STEPS) {
            steps.removeLast();
        }
    }

    // How many blocks were put back, or -1 when there was nothing to undo
    public static int undo(PlayerEntity player) {
        Deque<List<Change>> steps = HISTORY.get(keyFor(player));

        if (steps == null || steps.isEmpty()) {
            return -1;
        }

        World world = player.getWorld();
        int restored = 0;

        for (Change change : steps.pop()) {
            // Block states are interned, so identity is the right test for "this is still ours".
            // Anything built here since the gradient landed is left untouched.
            if (world.getBlockState(change.pos()) != change.after()) {
                continue;
            }

            if (world.setBlockState(change.pos(), change.before(), Block.NOTIFY_LISTENERS)) {
                restored++;
            }
        }

        return restored;
    }

    public static void registerCleanup() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }

    private static void forget(PlayerEntity player) {
        HISTORY.keySet().removeIf(key -> key.player().equals(player.getUuid()));
    }

    private static HistoryKey keyFor(PlayerEntity player) {
        return new HistoryKey(player.getUuid(), player.getWorld().getRegistryKey());
    }
}