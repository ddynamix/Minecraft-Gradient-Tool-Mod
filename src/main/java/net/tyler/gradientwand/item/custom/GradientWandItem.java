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
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.tyler.gradientwand.cost.HungerCost;
import net.tyler.gradientwand.cost.MaterialCost;
import net.tyler.gradientwand.animation.PlacementQueue;

import java.util.*;

public class GradientWandItem extends Item {

    private static final String POINT_A_KEY = "PointA";
    // How far the wand reaches when you right-click the air
    private static final double AIR_RANGE = 16.0;
    // How many blocks a line from one point to the other needs
    private static int blocksInLine(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());

        return Math.max(dx, Math.max(dy, dz)) + 1;
    }

    private final WandTier tier;

    public GradientWandItem(Settings settings, WandTier tier) {
        super(settings);
        this.tier = tier;
    }

    public WandTier tier() {
        return tier;
    }

    // The wand never breaks blocks. Cancelling is handled client side instead, so that it works
    // when you are aiming at open air, which is the normal case once a preview is on screen.
    public static void registerNoBlockBreaking() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (!(stack.getItem() instanceof GradientWandItem)) {
                return ActionResult.PASS;
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

            handleClick(player, stack, context.getHand(), context.getBlockPos().offset(context.getSide()));
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
                handleClick(user, stack, hand, raycastForPoint(user));
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
            tooltip.add(Text.literal(sneakHint(WandSettings.from(stack))).formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Left-click to cancel").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("Right-click a block to set point A").formatted(Formatting.GRAY));
        }

        tooltip.add(Text.literal(tier.label() + ": up to " + tier.maxBlocks() + " blocks, width up to "
                + tier.maxWidth()).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal(WandSettings.from(stack).describe()).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }

    // Sneaking means something different in every mode, so say which one
    private static String sneakHint(WandSettings settings) {
        return switch (settings.mode()) {
            case WALL -> "Sneak to keep the wall upright";
            case RIBBON -> "Sneak to grow the width from one side";
            case STRIP -> "Sneak to lock to one axis";
        };
    }

    // Stops the wand dipping out of hand every time its NBT changes
    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    // One position the wand intends to fill, and what goes there
    public record PlannedBlock(BlockPos pos, BlockState state) {
    }

    // A spread ordering of the 64 cells in a 4x4x4 block, so neighbouring positions get very
    // different thresholds. Built by greedy maximum-distance placement and then measured: no
    // axis-aligned plane is all-low or all-high, which is the artefact that shows up as banding.
    private static final int[] DITHER_4 = {
            0, 16, 24, 18, 36, 40,  2, 42,  4, 20, 26, 22, 38, 44,  6, 46,
            28,  8, 30, 10, 48, 56, 50, 58, 32, 12, 34, 14, 52, 60, 54, 62,
            27, 23,  5, 21,  7, 47, 39, 45, 25, 19,  1, 17,  3, 43, 37, 41,
            35, 15, 33, 13, 55, 63, 53, 61, 31, 11, 29,  9, 51, 59, 49, 57
    };

    // Which palette entry a position gets. With dither off this is exactly the integer division
    // from Step 5. With it on, a position part way between two entries sometimes takes the next.
    private static BlockState pick(List<BlockState> palette, int along, int count,
                                   BlockPos pos, WandSettings settings) {
        float exact = (float) along * palette.size() / count;
        int band = (int) exact;

        if (settings.dither() != WandSettings.Dither.NONE && settings.jitter() > 0.0f) {
            float threshold = settings.dither() == WandSettings.Dither.ORDERED
                    ? orderedThreshold(pos)
                    : randomThreshold(settings.seed(), pos);

            if (threshold < (exact - band) * settings.jitter()) {
                band++;
            }
        }

        return palette.get(Math.min(band, palette.size() - 1));
    }

    // Regular repeating pattern, needs no seed and looks the same on every machine.
    // "& 3" is the low two bits, which is the right answer for negative coordinates too.
    private static float orderedThreshold(BlockPos pos) {
        int index = ((pos.getX() & 3) << 4) | ((pos.getY() & 3) << 2) | (pos.getZ() & 3);

        return (DITHER_4[index] + 0.5f) / 64.0f;
    }

    // A hash, not a random number generator. The same position and seed always give the same
    // value, which is what keeps the preview steady and identical to what gets placed.
    private static float randomThreshold(long seed, BlockPos pos) {
        long h = (seed * 0xD6E8FEB86659FD93L) ^ (pos.asLong() * 0x9E3779B97F4A7C15L);

        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;

        return (h >>> 40) / (float) (1 << 24);
    }

    // Logs, pillars, basalt and the like carry an axis. Turning it to run along the longest side
    // is what stops a horizontal beam of oak looking like a stack of tree trunks. Blocks without
    // an axis fall straight through, and Properties.AXIS accepts all three values on every block
    // that has it, so this can never throw.
    private static BlockState orient(BlockState state, Direction.Axis grain) {
        if (grain == null || !state.contains(Properties.AXIS)) {
            return state;
        }

        return state.with(Properties.AXIS, grain);
    }

    // Which way the grain runs, or null when the setting is off. LONGEST on a strip is the line's
    // own direction; on a wall it is the longest edge of the rectangle. No default branch on
    // purpose: adding a Grain value later will not compile until it is handled here.
    private static Direction.Axis grainOf(BlockPos from, BlockPos to, WandSettings settings) {
        return switch (settings.grain()) {
            case OFF -> null;
            case LONGEST -> dominantAxis(from, to);
            case SHORTEST -> middleAxis(from, to);
            case EAST_WEST -> Direction.Axis.X;
            case NORTH_SOUTH -> Direction.Axis.Z;
            case UP_DOWN -> Direction.Axis.Y;
        };
    }

    // A ribbon's two sides are its length and its width, and neither is bound to a world axis,
    // so whichever one the setting asks for is snapped to the nearest axis.
    private static Direction.Axis ribbonGrain(Vec3d spine, Vec3d side, int count, int width,
                                              WandSettings settings) {
        boolean lengthWins = count >= width;

        return switch (settings.grain()) {
            case OFF -> null;
            case LONGEST -> axisOf(dominantOf(lengthWins ? spine : side));
            case SHORTEST -> axisOf(dominantOf(lengthWins ? side : spine));
            case EAST_WEST -> Direction.Axis.X;
            case NORTH_SOUTH -> Direction.Axis.Z;
            case UP_DOWN -> Direction.Axis.Y;
        };
    }

    private static Direction.Axis axisOf(int index) {
        return switch (index) {
            case 0 -> Direction.Axis.X;
            case 1 -> Direction.Axis.Y;
            default -> Direction.Axis.Z;
        };
    }

    private static List<PlannedBlock> planRibbon(GradientRequest request) {
        BlockPos from = request.from();
        BlockPos to = request.to();
        List<BlockState> palette = request.palette();
        WandSettings settings = request.settings();

        Vec3d origin = from.toCenterPos();
        Vec3d line = to.toCenterPos().subtract(origin);
        Vec3d spine = line.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : line.normalize();
        Vec3d normal = ribbonNormal(from, to, request.eye());
        Vec3d side = ribbonSide(from, to, request.eye(), request.look());

        double length = Math.sqrt(line.lengthSquared());
        int width = settings.width();
        int count = blocksInLine(from, to);

        // Sneaking grows the width from one edge instead of from the middle
        int lowest = request.anchorLeft() ? -(width - 1) : -((width - 1) / 2);
        int highest = lowest + width - 1;

        // Walk one block per column along the axis the wall faces most. That guarantees exactly
        // one block per column, so the surface is solid instead of full of diagonal pinholes.
        int axis = dominantOf(normal);
        int first = (axis + 1) % 3;
        int second = (axis + 2) % 3;

        int[] min = new int[3];
        int[] max = new int[3];

        boundsOf(origin, spine, side, length, lowest, highest, min, max);

        List<PlannedBlock> planned = new ArrayList<>();
        Direction.Axis grain = ribbonGrain(spine, side, count, width, settings);
        double plane = normal.dotProduct(origin);

        for (int u = min[first]; u <= max[first]; u++) {
            for (int v = min[second]; v <= max[second]; v++) {
                double known = component(normal, first) * (u + 0.5) + component(normal, second) * (v + 0.5);
                int along = (int) Math.round((plane - known) / component(normal, axis) - 0.5);

                int[] xyz = new int[3];

                xyz[first] = u;
                xyz[second] = v;
                xyz[axis] = along;

                BlockPos pos = new BlockPos(xyz[0], xyz[1], xyz[2]);
                Vec3d offset = pos.toCenterPos().subtract(origin);

                double down = offset.dotProduct(spine);
                double across = offset.dotProduct(side);

                if (down < -0.5 || down > length + 0.5) {
                    continue;
                }

                if (across < lowest - 0.5 || across > highest + 0.5) {
                    continue;
                }

                // The gradient runs along the line, so read the band off this block's own
                // position rather than off a spine step
                int step = Math.max(0, Math.min(count - 1,
                        (int) Math.round(length < 1.0E-6 ? 0.0 : down / length * (count - 1))));

                planned.add(new PlannedBlock(pos,
                        orient(pick(palette, step, count, pos, settings), grain)));
            }
        }

        return planned;
    }

    // The corner of the block grid the ribbon can possibly touch
    private static void boundsOf(Vec3d origin, Vec3d spine, Vec3d side, double length,
                                 int lowest, int highest, int[] min, int[] max) {
        for (int i = 0; i < 3; i++) {
            min[i] = Integer.MAX_VALUE;
            max[i] = Integer.MIN_VALUE;
        }

        for (double down : new double[]{0.0, length}) {
            for (double across : new double[]{lowest - 0.5, highest + 0.5}) {
                Vec3d corner = origin.add(spine.multiply(down)).add(side.multiply(across));

                for (int i = 0; i < 3; i++) {
                    int value = (int) Math.floor(component(corner, i));

                    min[i] = Math.min(min[i], value - 1);
                    max[i] = Math.max(max[i], value + 1);
                }
            }
        }
    }

    private static int dominantOf(Vec3d vector) {
        double x = Math.abs(vector.x);
        double y = Math.abs(vector.y);
        double z = Math.abs(vector.z);

        if (x >= y && x >= z) {
            return 0;
        }

        return y >= z ? 1 : 2;
    }

    private static double component(Vec3d vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    // Perpendicular to the line, pointing at the player. Shared by the side vector and the
    // column walk, so the two can never disagree about which way the wall faces.
    private static Vec3d ribbonNormal(BlockPos from, BlockPos to, Vec3d eye) {
        Vec3d line = to.toCenterPos().subtract(from.toCenterPos());
        Vec3d spine = line.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : line.normalize();

        Vec3d middle = from.toCenterPos().add(to.toCenterPos()).multiply(0.5);
        Vec3d toPlayer = eye == null ? new Vec3d(0.0, 1.0, 0.0) : eye.subtract(middle);

        Vec3d normal = toPlayer.subtract(spine.multiply(toPlayer.dotProduct(spine)));

        if (normal.lengthSquared() < 1.0E-4) {
            // Standing on the line itself: fall back to as upright as the line allows
            Vec3d up = new Vec3d(0.0, 1.0, 0.0);

            normal = up.subtract(spine.multiply(up.dotProduct(spine)));

            if (normal.lengthSquared() < 1.0E-4) {
                Vec3d east = new Vec3d(1.0, 0.0, 0.0);

                normal = east.subtract(spine.multiply(east.dotProduct(spine)));
            }
        }

        return normal.normalize();
    }

    // Perpendicular to the line, inside the plane whose normal points at the player
    private static Vec3d ribbonSide(BlockPos from, BlockPos to, Vec3d eye, Vec3d look) {
        Vec3d line = to.toCenterPos().subtract(from.toCenterPos());
        Vec3d spine = line.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : line.normalize();

        Vec3d side = spine.crossProduct(ribbonNormal(from, to, eye)).normalize();
        Vec3d left = look == null
                ? new Vec3d(0.0, 0.0, 1.0)
                : new Vec3d(0.0, 1.0, 0.0).crossProduct(look);

        if (left.lengthSquared() > 1.0E-6 && Math.abs(side.dotProduct(left.normalize())) > 0.1) {
            // Point it at the player's left where that means anything
            return side.dotProduct(left.normalize()) < 0.0 ? side.multiply(-1.0) : side;
        }

        // A ribbon growing vertically has no left, so sneaking builds downwards
        return side.y > 0.0 ? side.multiply(-1.0) : side;
    }

    private static List<PlannedBlock> planLine(BlockPos from, BlockPos to,
                                               List<BlockState> palette, WandSettings settings) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        int count = blocksInLine(from, to);
        int steps = count - 1;

        Direction.Axis grain = grainOf(from, to, settings);

        List<PlannedBlock> planned = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;

            BlockPos pos = new BlockPos(
                    from.getX() + (int) Math.round(t * dx),
                    from.getY() + (int) Math.round(t * dy),
                    from.getZ() + (int) Math.round(t * dz));

            planned.add(new PlannedBlock(pos, orient(pick(palette, i, count, pos, settings), grain)));
        }

        return planned;
    }

    public record GradientRequest(BlockPos from, BlockPos to, List<BlockState> palette,
                                  WandSettings settings, Vec3d eye, Vec3d look, boolean anchorLeft) {
    }

    // Both the preview and the click build the request here, so the two cannot drift apart
    private static GradientRequest requestFrom(PlayerEntity player, BlockPos from, BlockPos to,
                                               List<BlockState> palette, WandSettings settings) {
        boolean ribbon = settings.mode() == WandSettings.Mode.RIBBON;

        return new GradientRequest(from, to, palette, settings,
                ribbon ? player.getEyePos() : null,
                ribbon ? player.getRotationVec(1.0f) : null,
                ribbon && player.isSneaking());
    }

    // What the wand would do if you clicked right now, or null when there is nothing to show
    public static GradientRequest requestFor(PlayerEntity player, ItemStack stack) {
        BlockPos from = getPointA(stack);

        if (from == null) {
            return null;
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            return null;
        }

        WandSettings settings = WandSettings.from(stack);

        BlockPos raw = raycastForPoint(player);
        BlockPos to = resolveEnd(player, stack, from, raw, settings);

        // Only fails now when even a single step is too big, which means a ribbon whose width
        // alone is over the limit. Everything else has already been shortened to fit.
        if (sizeOf(from, to, settings) > limitFor(player, stack)) {
            return null;
        }

        return requestFrom(player, from, to, palette, settings);
    }

    public static List<PlannedBlock> plan(GradientRequest request) {
        if (request.settings().mode() == WandSettings.Mode.RIBBON) {
            return planRibbon(request);
        }

        if (request.settings().mode() == WandSettings.Mode.WALL) {
            return fillBetween(request.from(), request.to(), request.palette(),
                    resolveGradientAxis(request.settings().axis(), request.from(), request.to()),
                    request.settings());
        }

        return planLine(request.from(), request.to(), request.palette(), request.settings());
    }

    // Where point B ends up once the mode, sneaking and the wand's own limits have had their say.
    // The preview and the click both go through here, so the two can never disagree.
    public static BlockPos resolveEnd(PlayerEntity player, ItemStack stack, BlockPos from,
                                      BlockPos raw, WandSettings settings) {
        return clampToLimit(from, shapeEnd(player, from, raw, settings), settings,
                limitFor(player, stack));
    }

    // Point B as the mode and sneaking alone would have it, before any limit is applied. Kept
    // separate so a click can tell whether the wand shortened the selection.
    private static BlockPos shapeEnd(PlayerEntity player, BlockPos from, BlockPos raw, WandSettings settings) {
        // A ribbon is never snapped: sneaking shifts where its width grows from, nothing more
        if (settings.mode() == WandSettings.Mode.RIBBON) {
            return raw;
        }

        // A wall is always exactly one block thick. The axis the two points differ least on is
        // collapsed onto point A, so when they already share a plane this changes nothing at all
        // and only a genuine box gets squashed. Sneaking keeps Y in the wall, so it stands upright.
        if (settings.mode() == WandSettings.Mode.WALL) {
            return flatten(from, raw, wallNormal(from, raw, player.isSneaking()));
        }

        if (player.isSneaking()) {
            return snapToAxis(from, raw, dominantAxis(from, raw));
        }

        return raw;
    }

    // Which axis gets squashed to a single block thick
    private static Direction.Axis wallNormal(BlockPos from, BlockPos to, boolean upright) {
        // Sneaking keeps Y inside the wall, so the wall always stands upright
        if (upright) {
            int dx = Math.abs(to.getX() - from.getX());
            int dz = Math.abs(to.getZ() - from.getZ());

            return dx <= dz ? Direction.Axis.X : Direction.Axis.Z;
        }

        // The axis to collapse is the thinnest one, which is exactly the shortest side
        return shortestAxis(from, to);
    }

    // Collapses one axis, turning the selection into a flat rectangle
    private static BlockPos flatten(BlockPos from, BlockPos to, Direction.Axis normal) {
        return switch (normal) {
            case X -> new BlockPos(from.getX(), to.getY(), to.getZ());
            case Y -> new BlockPos(to.getX(), from.getY(), to.getZ());
            case Z -> new BlockPos(to.getX(), to.getY(), from.getZ());
        };
    }

    // HORIZONTAL and VERTICAL mean "along the wall", not a world axis, so a wall running north
    // to south and one running east to west both do the sensible thing. Length never comes into it.
    private static Direction.Axis resolveGradientAxis(WandSettings.GradientAxis choice, BlockPos from, BlockPos to) {
        return switch (choice) {
            case VERTICAL -> sizeAlong(from, to, Direction.Axis.Y) > 1
                    ? Direction.Axis.Y
                    : dominantAxis(from, to);
            case HORIZONTAL -> resolveHorizontal(from, to);
            case AUTO -> dominantAxis(from, to);
        };
    }

    // Whichever of X and Z actually lies in the wall. An upright wall only has one of them, so
    // there is nothing to guess. A flat floor has both, and only then does the longer side win.
    private static Direction.Axis resolveHorizontal(BlockPos from, BlockPos to) {
        int sizeX = sizeAlong(from, to, Direction.Axis.X);
        int sizeZ = sizeAlong(from, to, Direction.Axis.Z);

        if (sizeX <= 1 && sizeZ <= 1) {
            return dominantAxis(from, to);
        }

        return sizeX >= sizeZ ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static int sizeAlong(BlockPos from, BlockPos to, Direction.Axis axis) {
        return switch (axis) {
            case X -> Math.abs(to.getX() - from.getX()) + 1;
            case Y -> Math.abs(to.getY() - from.getY()) + 1;
            case Z -> Math.abs(to.getZ() - from.getZ()) + 1;
        };
    }

    // Fills every position between the two corners, the gradient running along one axis
    private static List<PlannedBlock> fillBetween(BlockPos from, BlockPos to, List<BlockState> palette,
                                                  Direction.Axis axis, WandSettings settings) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        int start = switch (axis) {
            case X -> minX;
            case Y -> minY;
            case Z -> minZ;
        };

        int count = sizeAlong(from, to, axis);

        Direction.Axis grain = grainOf(from, to, settings);

        List<PlannedBlock> planned = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int coordinate = switch (axis) {
                        case X -> x;
                        case Y -> y;
                        case Z -> z;
                    };
                    BlockPos pos = new BlockPos(x, y, z);

                    planned.add(new PlannedBlock(pos,
                            orient(pick(palette, coordinate - start, count, pos, settings), grain)));
                }
            }
        }

        return planned;
    }
    // long, not int: a 1300 block cube already overflows an int
    private static long blocksInBox(BlockPos from, BlockPos to) {
        long sizeX = Math.abs(to.getX() - from.getX()) + 1L;
        long sizeY = Math.abs(to.getY() - from.getY()) + 1L;
        long sizeZ = Math.abs(to.getZ() - from.getZ()) + 1L;

        return sizeX * sizeY * sizeZ;
    }

    private static long sizeOf(BlockPos from, BlockPos to, WandSettings settings) {
        return switch (settings.mode()) {
            case WALL -> blocksInBox(from, to);
            case RIBBON -> (long) blocksInLine(from, to) * settings.width();
            case STRIP -> blocksInLine(from, to);
        };
    }

    // The most blocks this wand could place right now. The cap belongs to the wand rather than the
    // shape, so a wooden wand places 16 whatever the mode, and in survival durability can bite
    // first. Creative players never wear a wand down, so only the cap applies to them.
    private static long limitFor(PlayerEntity player, ItemStack stack) {
        long cap = WandTier.of(stack).maxBlocks();

        if (player.isCreative()) {
            return cap;
        }

        int left = usesLeft(stack);

        return left < 0 ? cap : Math.min(cap, left);
    }

    // How many more blocks this wand can place before it breaks, or -1 when it never will
    private static int usesLeft(ItemStack stack) {
        return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : -1;
    }

    // Which of the two limits actually bit, so the message can say something useful
    private static String limitReason(PlayerEntity player, ItemStack stack) {
        int left = usesLeft(stack);

        if (!player.isCreative() && left >= 0 && left < WandTier.of(stack).maxBlocks()) {
            return left + " durability left";
        }

        return WandTier.of(stack).label() + " wand limit";
    }

    // After clamping, a selection can never outrun the durability, so the only warning left is
    // that this build spends the very last of it.
    private static void warnIfWandWillBreak(PlayerEntity player, ItemStack stack, int blocks) {
        int left = usesLeft(stack);

        if (player.isCreative() || left < 0 || blocks < left) {
            return;
        }

        player.sendMessage(Text.literal("This build uses the last of your wand, so it will break")
                .formatted(Formatting.RED), false);
    }

    // Walks the end point back toward A until the selection fits what the wand can actually do,
    // so aiming too far builds as far as it reaches instead of being refused outright.
    private static BlockPos clampToLimit(BlockPos from, BlockPos to, WandSettings settings, long limit) {
        if (sizeOf(from, to, settings) <= limit) {
            return to;
        }

        int steps = Math.max(Math.abs(to.getX() - from.getX()),
                Math.max(Math.abs(to.getY() - from.getY()), Math.abs(to.getZ() - from.getZ())));

        // A selection only ever grows as its end point moves away from A, so the furthest one that
        // still fits can be found by bisection instead of by trying every step in turn
        int low = 0;
        int high = steps;

        while (low < high) {
            int middle = (low + high + 1) / 2;

            if (sizeOf(from, stepAlong(from, to, middle, steps), settings) <= limit) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return stepAlong(from, to, low, steps);
    }

    // The point that many whole steps of the way from A to B, rounded exactly as planLine does
    private static BlockPos stepAlong(BlockPos from, BlockPos to, int step, int steps) {
        if (steps == 0) {
            return from;
        }

        double t = (double) step / steps;

        return new BlockPos(
                from.getX() + (int) Math.round(t * (to.getX() - from.getX())),
                from.getY() + (int) Math.round(t * (to.getY() - from.getY())),
                from.getZ() + (int) Math.round(t * (to.getZ() - from.getZ())));
    }

    // The positions that would actually change. The cost has to be based on these, not on the
    // whole plan, because occupied positions are skipped.
    private static List<PlannedBlock> placeable(PlayerEntity player, List<PlannedBlock> planned) {
        World world = player.getWorld();
        List<PlannedBlock> free = new ArrayList<>();

        for (PlannedBlock block : planned) {
            BlockPos pos = block.pos();

            if (world.getBlockState(pos).isReplaceable() && world.canPlayerModifyAt(player, pos)) {
                free.add(block);
            }
        }

        return free;
    }

    private static void handleClick(PlayerEntity player, ItemStack stack, Hand hand, BlockPos pos) {
        BlockPos pointA = getPointA(stack);

        if (pointA == null) {
            setPointA(stack, pos);

            // A fresh seed per selection, so dithering is stable while you aim
            WandSettings.from(stack)
                    .withSeed(player.getWorld().getRandom().nextLong())
                    .save(stack);

            player.sendMessage(Text.literal("Point A set at " + pos.toShortString()), true);
            return;
        }

        List<BlockState> palette = readPalette(player);

        if (palette.isEmpty()) {
            player.sendMessage(Text.literal("Put some blocks in your hotbar first").formatted(Formatting.RED), true);
            clearPointA(stack);
            return;
        }

        // Checked before anything is spent, and only when starting a build: a build already under
        // way is allowed to empty the bar and still finish.
        if (!HungerCost.canStart(player)) {
            player.sendMessage(Text.literal("You are too hungry to build, eat something first")
                    .formatted(Formatting.RED), true);
            clearPointA(stack);
            return;
        }

        WandSettings settings = WandSettings.from(stack);
        BlockPos wanted = shapeEnd(player, pointA, pos, settings);
        BlockPos end = resolveEnd(player, stack, pointA, pos, settings);
        long size = sizeOf(pointA, end, settings);
        long limit = limitFor(player, stack);

        // Shortening the line cannot rescue a ribbon whose width alone is over the limit, so that
        // is the one selection still worth refusing, and the message says what would help
        if (size > limit) {
            player.sendMessage(Text.literal("A ribbon " + settings.width() + " wide needs " + size
                    + " blocks and this wand can manage " + limit).formatted(Formatting.RED), true);
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
            player.sendMessage(Text.literal("Stretched as far as this wand reaches: "
                    + free.size() + " blocks, " + limitReason(player, stack)), true);
        }

        warnIfWandWillBreak(player, stack, free.size());

        // The wave places them over the next few ticks, and reports when it finishes
        PlacementQueue.start(player, stack, hand, pointA, free, paid);

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

    public static void cancelSelection(PlayerEntity player, ItemStack stack) {
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

    // Which axis the two points are closest together on. On a wall that is the single block of
    // thickness, which is why this is the axis the wall collapses along and not the one the
    // SHORTEST grain setting uses.
    private static Direction.Axis shortestAxis(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());

        if (dx <= dy && dx <= dz) {
            return Direction.Axis.X;
        }

        if (dy <= dz) {
            return Direction.Axis.Y;
        }

        return Direction.Axis.Z;
    }

    // The second shortest of the three extents: the one axis that is neither the longest nor the
    // shortest. On a wall the shortest is the single block of thickness, so this is the shorter of
    // the two edges that actually lie in the wall, which is what "shortest side" means to look at.
    private static Direction.Axis middleAxis(BlockPos from, BlockPos to) {
        Direction.Axis longest = dominantAxis(from, to);
        Direction.Axis shortest = shortestAxis(from, to);

        if (longest != Direction.Axis.X && shortest != Direction.Axis.X) {
            return Direction.Axis.X;
        }

        if (longest != Direction.Axis.Y && shortest != Direction.Axis.Y) {
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