package net.tyler.gradientwand.item.custom;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
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
import net.minecraft.util.math.Direction;
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

    // Left-clicking with the wand clears the selection instead of breaking the block
    public static void registerCancelOnAttack() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (!(stack.getItem() instanceof GradientWandItem)) {
                return ActionResult.PASS;
            }

            if (!world.isClient()) {
                cancelSelection(player, stack);
            }

            return ActionResult.SUCCESS;
        });
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();

        if (!world.isClient() && player != null) {
            ItemStack stack = context.getStack();

            handleClick(player, stack, context.getBlockPos().offset(context.getSide()));
        }

        return ActionResult.success(world.isClient());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            if (getPointA(stack) == null) {
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
            tooltip.add(Text.literal("Sneak to lock to one axis").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Left-click to cancel").formatted(Formatting.DARK_GRAY));
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
    // One position the wand intends to fill, and what goes there
    public record PlannedBlock(BlockPos pos, BlockState state) {
    }

    // Every block this gradient would place, from point A to point B. Pure maths, no world access.
    private static List<PlannedBlock> planLine(BlockPos from, BlockPos to, List<BlockState> palette) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        int count = blocksInLine(from, to);
        int steps = count - 1;

        List<PlannedBlock> planned = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;

            BlockPos pos = new BlockPos(
                    from.getX() + (int) Math.round(t * dx),
                    from.getY() + (int) Math.round(t * dy),
                    from.getZ() + (int) Math.round(t * dz));

            planned.add(new PlannedBlock(pos, palette.get(i * palette.size() / count)));
        }

        return planned;
    }

    public static List<PlannedBlock> previewFor(PlayerEntity player, ItemStack stack) {
        BlockPos from = getPointA(stack);

        if (from == null) {
            return List.of();
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            return List.of();
        }

        BlockPos raw = raycastForPoint(player);
        BlockPos to = player.isSneaking() ? snapToAxis(from, raw, dominantAxis(from, raw)) : raw;

        if (blocksInLine(from, to) > MAX_BLOCKS) {
            return List.of();
        }

        return planLine(from, to, palette);
    }

    // Places the planned blocks, skipping spots that are not free
    private static int drawGradient(PlayerEntity player, BlockPos from, BlockPos to, List<BlockState> palette) {
        World world = player.getWorld();
        int placed = 0;

        for (PlannedBlock block : planLine(from, to, palette)) {
            BlockPos pos = block.pos();

            if (!world.getBlockState(pos).isReplaceable() || !world.canPlayerModifyAt(player, pos)) {
                continue;
            }

            if (world.setBlockState(pos, block.state())) {
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

        BlockPos end = pos;
        String note = "";

        if (player.isSneaking()) {
            Direction.Axis axis = dominantAxis(pointA, pos);
            end = snapToAxis(pointA, pos, axis);
            note = " locked to " + axis.asString().toUpperCase();
        }

        int count = blocksInLine(pointA, end);

        if (count > MAX_BLOCKS) {
            player.sendMessage(Text.literal("That line is " + count + " blocks long, max is " + MAX_BLOCKS)
                    .formatted(Formatting.RED), true);
            return; // keep point A
        }

        int placed = drawGradient(player, pointA, end, palette);

        player.sendMessage(Text.literal("Placed " + placed + " of " + count + " blocks" + note), true);

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

    // Which axis the two points are furthest apart on
    private static Direction.Axis dominantAxis(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());

        if (dx >= dy && dx >= dz) {
            return Direction.Axis.X;
        }

        if (dy >= dz) {
            return Direction.Axis.Y;
        }

        return Direction.Axis.Z;
    }

    // Keeps the end point's coordinate on one axis and takes the other two from the start
    private static BlockPos snapToAxis(BlockPos from, BlockPos to, Direction.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(to.getX(), from.getY(), from.getZ());
            case Y -> new BlockPos(from.getX(), to.getY(), from.getZ());
            case Z -> new BlockPos(from.getX(), from.getY(), to.getZ());
        };
    }
}