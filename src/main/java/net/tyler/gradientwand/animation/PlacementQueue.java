package net.tyler.gradientwand.animation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.tyler.gradientwand.cost.MaterialCost;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.undo.UndoHistory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

// Server side only, ticked from the main server thread, so no synchronisation is needed.
public class PlacementQueue {

    // Throughput rather than speed. A wall's wavefront is wider than this, so a wall always
    // advances exactly one diagonal per tick; a strip's is one block, so it advances four.
    private static final int BLOCKS_PER_TICK = 4;

    // A wide wavefront would fire dozens of identical sounds in one tick and simply clip
    private static final int SOUNDS_PER_TICK = 4;

    private static final List<Animation> ACTIVE = new ArrayList<>();

    private static class Animation {

        private final PlayerEntity player;
        private final World world;
        private final BlockPos origin;
        private final List<GradientWandItem.PlannedBlock> blocks;
        private final boolean paid;

        private final List<UndoHistory.Change> changes = new ArrayList<>();
        private final List<GradientWandItem.PlannedBlock> missed = new ArrayList<>();

        private int index;

        private Animation(PlayerEntity player, BlockPos origin,
                          List<GradientWandItem.PlannedBlock> blocks, boolean paid) {
            this.player = player;
            this.world = player.getWorld();
            this.origin = origin;
            this.blocks = blocks;
            this.paid = paid;
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    // "paid" is recorded here rather than checked at the end, so switching game mode part way
    // through cannot earn free blocks or refund ones that were never charged for.
    public static void start(PlayerEntity player, BlockPos origin,
                             List<GradientWandItem.PlannedBlock> blocks, boolean paid) {
        if (blocks.isEmpty()) {
            return;
        }

        List<GradientWandItem.PlannedBlock> ordered = new ArrayList<>(blocks);

        ordered.sort(Comparator.comparingInt(block -> waveDistance(origin, block.pos())));

        ACTIVE.add(new Animation(player, origin, ordered, paid));
    }

    private static void tick() {
        Iterator<Animation> animations = ACTIVE.iterator();

        while (animations.hasNext()) {
            Animation animation = animations.next();

            if (animation.player.isRemoved()) {
                animations.remove();
                continue;
            }

            advance(animation);

            if (animation.index >= animation.blocks.size()) {
                finish(animation);
                animations.remove();
            }
        }
    }

    private static void advance(Animation animation) {
        int placed = 0;
        int sounds = 0;

        while (animation.index < animation.blocks.size() && placed < BLOCKS_PER_TICK) {
            int distance = waveDistance(animation.origin, animation.blocks.get(animation.index).pos());

            // Always finish a whole wavefront, so the diagonal edge stays crisp
            while (animation.index < animation.blocks.size()
                    && waveDistance(animation.origin, animation.blocks.get(animation.index).pos()) == distance) {

                GradientWandItem.PlannedBlock block = animation.blocks.get(animation.index);

                if (placeOne(animation, block)) {
                    if (sounds < SOUNDS_PER_TICK) {
                        playPlaceSound(animation.world, block);
                        sounds++;
                    }
                } else {
                    // Paid for, but the world changed before the wave arrived
                    animation.missed.add(block);
                }

                animation.index++;
                placed++;
            }
        }
    }

    private static boolean placeOne(Animation animation, GradientWandItem.PlannedBlock block) {
        BlockPos pos = block.pos();
        BlockState before = animation.world.getBlockState(pos);

        if (!before.isReplaceable()) {
            return false;
        }

        if (!animation.world.setBlockState(pos, block.state(), Block.NOTIFY_LISTENERS)) {
            return false;
        }

        animation.changes.add(new UndoHistory.Change(pos, before, block.state()));

        return true;
    }

    // The same volume and pitch vanilla uses when a player places a block
    private static void playPlaceSound(World world, GradientWandItem.PlannedBlock block) {
        BlockSoundGroup group = block.state().getSoundGroup();

        world.playSound(null, block.pos(), group.getPlaceSound(), SoundCategory.BLOCKS,
                (group.getVolume() + 1.0f) / 2.0f, group.getPitch() * 0.8f);
    }

    private static void finish(Animation animation) {
        UndoHistory.record(animation.player, animation.changes);

        String note = "";

        if (animation.paid && !animation.missed.isEmpty()) {
            MaterialCost.refund(animation.player, MaterialCost.required(animation.missed));
            note = ", refunded " + animation.missed.size();
        }

        animation.player.sendMessage(Text.literal("Placed " + animation.changes.size()
                + " of " + animation.blocks.size() + " blocks" + note), true);
    }

    // Manhattan distance. On a wall this makes the wavefront a diagonal line sweeping out from
    // point A; on a strip it is simply the order along the line.
    private static int waveDistance(BlockPos from, BlockPos pos) {
        return Math.abs(pos.getX() - from.getX())
                + Math.abs(pos.getY() - from.getY())
                + Math.abs(pos.getZ() - from.getZ());
    }
}