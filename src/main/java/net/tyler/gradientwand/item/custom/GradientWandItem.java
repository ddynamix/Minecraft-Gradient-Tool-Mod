package net.tyler.gradientwand.item.custom;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.core.Axis;
import net.tyler.gradientwand.core.GradientCore;
import net.tyler.gradientwand.core.Pos;
import net.tyler.gradientwand.core.WandSettings;
import net.tyler.gradientwand.cost.HungerCost;
import net.tyler.gradientwand.cost.MaterialCost;
import net.tyler.gradientwand.enchantment.ModEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The Minecraft side of the wand: clicking, item data, cost, and turning the core's answer into
// real block states. Every decision about which positions to fill lives in GradientCore instead,
// which is why there is no geometry left in this file.
public class GradientWandItem extends Item {

    // How far the wand reaches when you right-click the air
    private static final double AIR_RANGE = 16.0;

    private final WandTier tier;

    public GradientWandItem(Item.Properties settings, WandTier tier) {
        super(settings);
        this.tier = tier;
    }

    public WandTier tier() {
        return tier;
    }

    // Item returns 0 here by default, which is what stops a plain Item being enchanted at a table.
    @Override
    public int getEnchantmentValue() {
        return tier.enchantability();
    }

    // Item asks for a durability bar here, which would shut the netherite wand out of enchanting
    // altogether. Capacity and Stamina are just as useful on a wand that never wears out.
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    // The wand never breaks blocks. Cancelling is handled client side instead, so that it works
    // when you are aiming at open air, which is the normal case once a preview is on screen.
    public static void registerNoBlockBreaking() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getItemInHand(hand);

            if (!(stack.getItem() instanceof GradientWandItem)) {
                return InteractionResult.PASS;
            }

            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();

        if (!world.isClientSide() && player != null) {
            ItemStack stack = context.getItemInHand();

            handleClick(player, stack, context.getHand(), context.getClickedPos().relative(context.getClickedFace()));
        }

        return InteractionResult.sidedSuccess(world.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (!world.isClientSide()) {
            if (getPointA(stack) == null) {
                user.displayClientMessage(Component.literal("Right-click a block to set point A first"), true);
            } else {
                handleClick(user, stack, hand, raycastForPoint(user));
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    // Shimmer while point A is saved, and whenever the wand is enchanted. The super call matters:
    // Item.hasGlint is what puts the glint on an enchanted item.
    @Override
    public boolean isFoil(ItemStack stack) {
        return getPointA(stack) != null || super.isFoil(stack);
    }

    // Stops the wand dipping out of hand every time its data changes. Fabric renamed this hook
    // when 1.20.5 replaced item NBT with components, so only the method name differs.
    //? if <1.21 {
    /*@Override
    public boolean allowNbtUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }
    *///?} else {
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }
    //?}

    // One position the wand intends to fill, and what goes there
    public record PlannedBlock(BlockPos pos, BlockState state) {
    }

    // What the wand would do if you clicked right now. Holds the real palette, which the core
    // never sees: the core works in palette indices and hands them back to be looked up here.
    public record GradientRequest(BlockPos from, BlockPos to, List<BlockState> palette,
                                  WandSettings settings, Vec3 eye, Vec3 look, boolean anchorLeft) {
    }

    // Both the preview and the click build the request here, so the two cannot drift apart
    private static GradientRequest requestFrom(Player player, BlockPos from, BlockPos to,
                                               List<BlockState> palette, WandSettings settings) {
        boolean ribbon = settings.mode() == WandSettings.Mode.RIBBON;

        return new GradientRequest(from, to, palette, settings,
                ribbon ? player.getEyePosition() : null,
                ribbon ? player.getViewVector(1.0f) : null,
                ribbon && player.isShiftKeyDown());
    }

    public static GradientRequest requestFor(Player player, ItemStack stack) {
        BlockPos from = getPointA(stack);

        if (from == null) {
            return null;
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            return null;
        }

        WandSettings settings = SettingsNbt.read(stack);

        BlockPos raw = raycastForPoint(player);
        BlockPos to = resolveEnd(player, stack, from, raw, settings);

        // Only fails now when even a single step is too big, which means a ribbon whose width
        // alone is over the limit. Everything else has already been shortened to fit.
        if (GradientCore.sizeOf(toCore(from), toCore(to), settings) > limitFor(player, stack)) {
            return null;
        }

        return requestFrom(player, from, to, palette, settings);
    }

    // The core decides the positions and which palette entry each takes; turning that into block
    // states, and pointing any grained block the right way, is this side's job.
    public static List<PlannedBlock> plan(GradientRequest request) {
        GradientCore.Plan plan = GradientCore.plan(new GradientCore.Selection(
                toCore(request.from()),
                toCore(request.to()),
                request.palette().size(),
                request.settings(),
                toCore(request.eye()),
                toCore(request.look()),
                request.anchorLeft()));

        List<PlannedBlock> blocks = new ArrayList<>(plan.cells().size());

        for (GradientCore.PlannedCell cell : plan.cells()) {
            BlockState state = request.palette().get(cell.palette());

            blocks.add(new PlannedBlock(toMc(cell.pos()), orient(state, plan.grain())));
        }

        return blocks;
    }

    // Logs, pillars, basalt and the like carry an axis. BlockStateProperties.AXIS accepts all three values on
    // every block that has it, so this can never throw, and blocks without one fall straight
    // through. This is the only part of the grain feature that has to know what a block is.
    private static BlockState orient(BlockState state, Axis grain) {
        if (grain == null || !state.hasProperty(BlockStateProperties.AXIS)) {
            return state;
        }

        return state.setValue(BlockStateProperties.AXIS, toMc(grain));
    }

    // Where point B ends up once the mode, sneaking and the wand's own limits have had their say
    private static BlockPos resolveEnd(Player player, ItemStack stack, BlockPos from,
                                       BlockPos raw, WandSettings settings) {
        return toMc(GradientCore.resolveEnd(toCore(from), toCore(raw), settings,
                player.isShiftKeyDown(), limitFor(player, stack)));
    }

    // The most blocks this wand could place right now. The cap belongs to the wand rather than the
    // shape, and in survival durability can bite first. Capacity raises the cap and nothing else.
    private static long limitFor(Player player, ItemStack stack) {
        long cap = (long) (WandTier.of(stack).maxBlocks() * ModEnchantments.capacityMultiplier(stack));

        if (player.isCreative()) {
            return cap;
        }

        int left = usesLeft(stack);

        return left < 0 ? cap : Math.min(cap, left);
    }

    // How many more blocks this wand can place before it breaks, or -1 when it never will
    private static int usesLeft(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : -1;
    }

    // Which of the two limits actually bit, so the message can say something useful
    private static String limitReason(Player player, ItemStack stack) {
        int left = usesLeft(stack);

        if (!player.isCreative() && left >= 0 && left < WandTier.of(stack).maxBlocks()) {
            return left + " durability left";
        }

        return WandTier.of(stack).label() + " wand limit";
    }

    // After clamping, a selection can never outrun the durability, so the only warning left is
    // that this build spends the very last of it.
    private static void warnIfWandWillBreak(Player player, ItemStack stack, int blocks) {
        int left = usesLeft(stack);

        if (player.isCreative() || left < 0 || blocks < left) {
            return;
        }

        player.displayClientMessage(Component.literal("This build uses the last of your wand, so it will break")
                .withStyle(ChatFormatting.RED), false);
    }

    // The positions that would actually change. The cost has to be based on these, not on the
    // whole plan, because occupied positions are skipped.
    private static List<PlannedBlock> placeable(Player player, List<PlannedBlock> planned) {
        Level world = player.level();
        List<PlannedBlock> free = new ArrayList<>();

        for (PlannedBlock block : planned) {
            BlockPos pos = block.pos();

            if (world.getBlockState(pos).canBeReplaced() && world.mayInteract(player, pos)) {
                free.add(block);
            }
        }

        return free;
    }

    private static void handleClick(Player player, ItemStack stack, InteractionHand hand, BlockPos pos) {
        BlockPos pointA = getPointA(stack);

        if (pointA == null) {
            setPointA(stack, pos);

            // A fresh seed per selection, so dithering is stable while you aim
            SettingsNbt.write(stack, SettingsNbt.read(stack)
                    .withSeed(player.level().getRandom().nextLong()));

            player.displayClientMessage(Component.literal("Point A set at " + pos.toShortString()), true);
            return;
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            player.displayClientMessage(Component.literal("Put some blocks in your hotbar first").withStyle(ChatFormatting.RED), true);
            clearPointA(stack);
            return;
        }

        // Checked before anything is spent, and only when starting a build: a build already under
        // way is allowed to empty the bar and still finish.
        if (!HungerCost.canStart(player, stack)) {
            player.displayClientMessage(Component.literal("You are too hungry to build, eat something first")
                    .withStyle(ChatFormatting.RED), true);
            clearPointA(stack);
            return;
        }

        WandSettings settings = SettingsNbt.read(stack);
        BlockPos wanted = toMc(GradientCore.shapeEnd(toCore(pointA), toCore(pos), settings, player.isShiftKeyDown()));
        BlockPos end = resolveEnd(player, stack, pointA, pos, settings);
        long size = GradientCore.sizeOf(toCore(pointA), toCore(end), settings);
        long limit = limitFor(player, stack);

        // Shortening the line cannot rescue a ribbon whose width alone is over the limit, so that
        // is the one selection still worth refusing, and the message says what would help
        if (size > limit) {
            player.displayClientMessage(Component.literal("A ribbon " + settings.width() + " wide needs " + size
                    + " blocks and this wand can manage " + limit).withStyle(ChatFormatting.RED), true);
            clearPointA(stack);
            return;
        }

        List<PlannedBlock> planned = plan(requestFrom(player, pointA, end, palette, settings));
        List<PlannedBlock> free = placeable(player, planned);

        // Recorded now, not at the end: switching game mode mid wave must not change who pays
        boolean paid = !player.isCreative();

        // Creative players never pay, and never see the shortage list
        if (paid) {
            Map<Item, Integer> needed = MaterialCost.required(free);
            Map<Item, Integer> held = MaterialCost.available(player);

            if (!MaterialCost.hasEnough(needed, held)) {
                MaterialCost.report(player, needed, held);
                clearPointA(stack);
                return;
            }

            MaterialCost.consume(player, needed);
        }

        if (!end.equals(wanted)) {
            player.displayClientMessage(Component.literal("Stretched as far as this wand reaches: "
                    + free.size() + " blocks, " + limitReason(player, stack)), true);
        }

        warnIfWandWillBreak(player, stack, free.size());

        // The wave places them over the next few ticks, and reports when it finishes
        PlacementQueue.start(player, stack, hand, pointA, free, paid);

        clearPointA(stack);
    }

    // The blocks in the hotbar, left to right. Anything that is not a block is skipped.
    private static List<BlockState> readPalette(Player player) {
        List<BlockState> palette = new ArrayList<>();

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack slotStack = player.getInventory().getItem(slot);

            if (slotStack.getItem() instanceof BlockItem blockItem) {
                palette.add(blockItem.getBlock().defaultBlockState());
            }
        }

        return palette;
    }

    // Works out which position you are looking at, up to AIR_RANGE blocks away
    private static BlockPos raycastForPoint(Player player) {
        HitResult hit = player.pick(AIR_RANGE, 1.0f, false);

        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }

        return BlockPos.containing(hit.getLocation());
    }

    public static void cancelSelection(Player player, ItemStack stack) {
        if (getPointA(stack) == null) {
            return;
        }

        clearPointA(stack);
        player.displayClientMessage(Component.literal("Selection cleared"), true);
    }

    // Point A is stored alongside the settings, so all the version-specific persistence lives in
    // one place. These stay as wrappers because a dozen call sites read better for them.
    private static void setPointA(ItemStack stack, BlockPos pos) {
        SettingsNbt.writePointA(stack, pos);
    }

    // Returns null if no point A is saved
    private static BlockPos getPointA(ItemStack stack) {
        return SettingsNbt.readPointA(stack);
    }

    private static void clearPointA(ItemStack stack) {
        SettingsNbt.clearPointA(stack);
    }

    // The whole boundary between Minecraft's types and the core's, in one place

    private static Pos toCore(BlockPos pos) {
        return new Pos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos toMc(Pos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    // Null for every mode but ribbon, where the core reads the player's eye and facing
    private static net.tyler.gradientwand.core.Vec3 toCore(Vec3 vec) {
        return vec == null ? null : new net.tyler.gradientwand.core.Vec3(vec.x, vec.y, vec.z);
    }

    private static Direction.Axis toMc(Axis axis) {
        return switch (axis) {
            case X -> Direction.Axis.X;
            case Y -> Direction.Axis.Y;
            case Z -> Direction.Axis.Z;
        };
    }
}
