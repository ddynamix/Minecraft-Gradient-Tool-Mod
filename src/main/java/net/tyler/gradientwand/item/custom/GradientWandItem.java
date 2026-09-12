package net.tyler.gradientwand.item.custom;

import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class GradientWandItem extends Item {

    private static final String POINT_A_KEY = "PointA";

    // How far the wand reaches when you right-click the air
    private static final double AIR_RANGE = 16.0;
    // Safety net so a mis-click cannot try to fill thousands of blocks
    private static final int MAX_BLOCKS = 512;
    // How many blocks a line from one point to the other needs
    private static int blocksInLine(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());

        return Math.max(dx, Math.max(dy, dz)) + 1;
    }

    public GradientWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();

        if (!world.isClient() && player != null) {
            ItemStack stack = context.getStack();

            if (player.isSneaking()) {
                cancelSelection(player, stack);
            } else {
                handleClick(player, stack, context.getBlockPos().offset(context.getSide()));
            }
        }

        return ActionResult.success(world.isClient());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            if (user.isSneaking()) {
                cancelSelection(user, stack);
            } else if (getPointA(stack) == null) {
                user.sendMessage(Text.literal("Right-click a block to set point A first"), true);
            } else {
                handleClick(user, stack, raycastForPoint(user));
            }
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    // Shimmer while point A is saved
    @Override
    public boolean hasGlint(ItemStack stack) {
        return getPointA(stack) != null;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        BlockPos pointA = getPointA(stack);

        if (pointA != null) {
            tooltip.add(Text.literal("Point A: " + pointA.toShortString()).formatted(Formatting.AQUA));
        } else {
            tooltip.add(Text.literal("Right-click a block to set point A").formatted(Formatting.GRAY));
        }

        super.appendTooltip(stack, world, tooltip, context);
    }

    // Stops the wand dipping out of hand every time its NBT changes
    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    // Walks the line from one point to the other, placing palette blocks in even bands
    private static int drawGradient(PlayerEntity player, BlockPos from, BlockPos to, List<BlockState> palette) {
        World world = player.getWorld();

        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        int count = blocksInLine(from, to);
        int steps = count - 1;
        int placed = 0;

        for (int i = 0; i < count; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;

            BlockPos pos = new BlockPos(
                    from.getX() + (int) Math.round(t * dx),
                    from.getY() + (int) Math.round(t * dy),
                    from.getZ() + (int) Math.round(t * dz));

            BlockState state = palette.get(i * palette.size() / count);

            if (!world.getBlockState(pos).isReplaceable() || !world.canPlayerModifyAt(player, pos)) {
                continue;
            }

            if (world.setBlockState(pos, state)) {
                placed++;
            }
        }

        return placed;
    }

    private static void handleClick(PlayerEntity player, ItemStack stack, BlockPos pos) {
        BlockPos pointA = getPointA(stack);

        if (pointA == null) {
            setPointA(stack, pos);
            player.sendMessage(Text.literal("Point A set at " + pos.toShortString()), true);
            return;
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            player.sendMessage(Text.literal("Put some blocks in your hotbar first").formatted(Formatting.RED), true);
            return; // keep point A so you can fix your hotbar and click again
        }

        int count = blocksInLine(pointA, pos);

        if (count > MAX_BLOCKS) {
            player.sendMessage(Text.literal("That line is " + count + " blocks long, max is " + MAX_BLOCKS)
                    .formatted(Formatting.RED), true);
            return; // keep point A
        }

        int placed = drawGradient(player, pointA, pos, palette);

        player.sendMessage(Text.literal("Placed " + placed + " of " + count + " blocks"), true);

        clearPointA(stack);
    }

    // The blocks in the hotbar, left to right. Anything that is not a block is skipped.
    private static List<BlockState> readPalette(PlayerEntity player) {
        List<BlockState> palette = new ArrayList<>();

        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack slotStack = player.getInventory().getStack(slot);

            if (slotStack.getItem() instanceof BlockItem blockItem) {
                palette.add(blockItem.getBlock().getDefaultState());
            }
        }

        return palette;
    }

    // Works out which position you are looking at, up to AIR_RANGE blocks away
    private static BlockPos raycastForPoint(PlayerEntity player) {
        HitResult hit = player.raycast(AIR_RANGE, 1.0f, false);

        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos().offset(blockHit.getSide());
        }

        return BlockPos.ofFloored(hit.getPos());
    }

    private static void cancelSelection(PlayerEntity player, ItemStack stack) {
        if (getPointA(stack) == null) {
            return;
        }

        clearPointA(stack);
        player.sendMessage(Text.literal("Selection cleared"), true);
    }

    private static void setPointA(ItemStack stack, BlockPos pos) {
        stack.getOrCreateNbt().put(POINT_A_KEY, NbtHelper.fromBlockPos(pos));
    }

    // Returns null if no point A is saved
    private static BlockPos getPointA(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();

        if (nbt == null || !nbt.contains(POINT_A_KEY)) {
            return null;
        }

        return NbtHelper.toBlockPos(nbt.getCompound(POINT_A_KEY));
    }

    private static void clearPointA(ItemStack stack) {
        stack.removeSubNbt(POINT_A_KEY);
    }
}