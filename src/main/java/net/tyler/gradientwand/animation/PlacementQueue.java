package net.tyler.gradientwand.animation;

//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
*///?}
//? if neoforge {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
// Only LivingEntity is version-specific here: it is where 1.21 declares getSlotForHand.
// Player stays outside the directive, or the 1.20.1 render imports it twice.
//? if >=1.21 {
import net.minecraft.world.entity.LivingEntity;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.tyler.gradientwand.core.GradientCore;
import net.tyler.gradientwand.cost.HungerCost;
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

        private final Player player;
        private final Level world;
        private final ItemStack wand;
        private final InteractionHand hand;
        private final BlockPos origin;
        private final List<GradientWandItem.PlannedBlock> blocks;
        private final boolean paid;

        private final List<UndoHistory.Change> changes = new ArrayList<>();
        private final List<GradientWandItem.PlannedBlock> missed = new ArrayList<>();

        private int index;
        private boolean broken;

        private Animation(Player player, ItemStack wand, InteractionHand hand, BlockPos origin,
                          List<GradientWandItem.PlannedBlock> blocks, boolean paid) {
            this.player = player;
            this.world = player.level();
            this.wand = wand;
            this.hand = hand;
            this.origin = origin;
            this.blocks = blocks;
            this.paid = paid;
        }
    }

    //? if fabric {
    /*public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }
    *///?}
    //? if neoforge {
    // Subscribed by GradientWand on the game bus; the server instance is not needed here
    public static void onServerTick(ServerTickEvent.Post event) {
        tick();
    }
    //?}

    // "paid" is recorded here rather than checked at the end, so switching game mode part way
    // through cannot earn free blocks or refund ones that were never charged for. The wand stack
    // is held by reference, which is what lets a break part way through stop the wave.
    public static void start(Player player, ItemStack wand, InteractionHand hand, BlockPos origin,
                             List<GradientWandItem.PlannedBlock> blocks, boolean paid) {
        if (blocks.isEmpty()) {
            return;
        }

        List<GradientWandItem.PlannedBlock> ordered = new ArrayList<>(blocks);

        ordered.sort(Comparator.comparingInt(block -> waveDistance(origin, block.pos())));

        ACTIVE.add(new Animation(player, wand, hand, origin, ordered, paid));
    }

    // Undo pressed while a wave is still travelling: stop it, put back whatever it managed to
    // place, and refund every block that was paid for. Returns how many were removed, or -1
    // when this player had nothing in flight.
    public static int cancel(Player player) {
        for (Animation animation : ACTIVE) {
            if (animation.player != player) {
                continue;
            }

            List<BlockState> refunds = new ArrayList<>();
            int reverted = 0;

            for (UndoHistory.Change change : animation.changes) {
                if (animation.world.getBlockState(change.pos()) != change.after()) {
                    continue; // someone else owns this spot now, so leave it alone
                }

                if (animation.world.setBlock(change.pos(), change.before(), Block.UPDATE_CLIENTS)) {
                    reverted++;
                    refunds.add(change.after());
                }
            }

            // Blocks the wave never reached, and ones it could not place, were still paid for
            for (int i = animation.index; i < animation.blocks.size(); i++) {
                refunds.add(animation.blocks.get(i).state());
            }

            for (GradientWandItem.PlannedBlock block : animation.missed) {
                refunds.add(block.state());
            }

            if (animation.paid && !refunds.isEmpty()) {
                MaterialCost.refund(player, MaterialCost.countStates(refunds));
            }

            ACTIVE.remove(animation);

            return reverted;
        }

        return -1;
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

            if (animation.broken || animation.index >= animation.blocks.size()) {
                finish(animation);
                animations.remove();
            }
        }
    }

    private static void advance(Animation animation) {
        int placed = 0;
        int sounds = 0;

        while (animation.index < animation.blocks.size() && placed < BLOCKS_PER_TICK && !animation.broken) {
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

                    charge(animation);
                } else {
                    // Paid for, but the world changed before the wave arrived
                    animation.missed.add(block);
                }

                animation.index++;
                placed++;

                // A broken wand stops the wave where it stands, part way through a wavefront
                // and all. Whatever is left gets refunded in finish().
                if (animation.broken) {
                    return;
                }
            }
        }
    }

    // One point of durability and a slice of hunger per block actually placed. Creative players
    // and the netherite wand both fall out of this for free: damage() ignores creative mode and
    // non-damageable items, and addExhaustion ignores creative too.
    private static void charge(Animation animation) {
        HungerCost.charge(animation.player, animation.wand);

        // 1.21 replaced the break-status callback with an EquipmentSlot: the overload sends the
        // break effect itself, so there is nothing left for a lambda to do.
        //? if <1.21 {
        /*animation.wand.hurtAndBreak(1, animation.player, player -> player.broadcastBreakEvent(animation.hand));
        *///?} else {
        animation.wand.hurtAndBreak(1, animation.player, LivingEntity.getSlotForHand(animation.hand));
        //?}

        // Breaking empties the stack, which is the only reliable signal that it is gone
        if (animation.wand.isEmpty()) {
            animation.broken = true;
        }
    }

    private static boolean placeOne(Animation animation, GradientWandItem.PlannedBlock block) {
        BlockPos pos = block.pos();
        BlockState before = animation.world.getBlockState(pos);

        if (!before.canBeReplaced()) {
            return false;
        }

        if (!animation.world.setBlock(pos, block.state(), Block.UPDATE_CLIENTS)) {
            return false;
        }

        animation.changes.add(new UndoHistory.Change(pos, before, block.state()));

        return true;
    }

    // The same volume and pitch vanilla uses when a player places a block
    private static void playPlaceSound(Level world, GradientWandItem.PlannedBlock block) {
        SoundType group = block.state().getSoundType();

        world.playSound(null, block.pos(), group.getPlaceSound(), SoundSource.BLOCKS,
                (group.getVolume() + 1.0f) / 2.0f, group.getPitch() * 0.8f);
    }

    private static void finish(Animation animation) {
        UndoHistory.record(animation.player, animation.changes, animation.paid);

        // Everything the wave never reached was paid for up front, so it comes back. After a wave
        // that ran to the end the tail is empty and this is exactly the old refund.
        List<GradientWandItem.PlannedBlock> unplaced = new ArrayList<>(animation.missed);

        for (int i = animation.index; i < animation.blocks.size(); i++) {
            unplaced.add(animation.blocks.get(i));
        }

        String note = "";

        if (animation.paid && !unplaced.isEmpty()) {
            MaterialCost.refund(animation.player, MaterialCost.required(unplaced));
            note = ", refunded " + unplaced.size();
        }

        if (animation.broken) {
            animation.player.displayClientMessage(Component.literal("Your wand broke after placing "
                    + animation.changes.size() + " blocks" + note).withStyle(ChatFormatting.RED), false);
            return;
        }

        animation.player.displayClientMessage(Component.literal("Placed " + animation.changes.size()
                + " of " + animation.blocks.size() + " blocks" + note), true);
    }

    // The wavefront ordering is part of the gradient's design, not of Minecraft, so it lives in
    // the core. Components rather than positions, to avoid allocating through a large sort.
    private static int waveDistance(BlockPos from, BlockPos pos) {
        return GradientCore.waveDistance(from.getX(), from.getY(), from.getZ(),
                pos.getX(), pos.getY(), pos.getZ());
    }
}
