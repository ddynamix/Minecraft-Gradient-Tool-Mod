package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.tyler.gradientwand.item.custom.GradientWandItem;
//? if <1.21 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?}

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GradientPreviewRenderer {

    private static final float ALPHA = 0.9f;

    // One unit edge of the lattice: its lower corner and which way it runs
    private record EdgeKey(int x, int y, int z, Direction.Axis axis) {
    }

    private record PreviewEdge(int x, int y, int z, Direction.Axis axis,
                               float red, float green, float blue) {
    }

    // Client-only state, rebuilt only when something the preview depends on changes
    private static GradientWandItem.GradientRequest cachedRequest;
    private static List<PreviewEdge> cachedEdges = List.of();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(GradientPreviewRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (!(stack.getItem() instanceof GradientWandItem)) {
            return;
        }

        GradientWandItem.GradientRequest request = GradientWandItem.requestFor(player, stack);

        if (request == null) {
            cachedRequest = null;
            cachedEdges = List.of();
            return;
        }

        if (!request.equals(cachedRequest)) {
            cachedRequest = request;
            cachedEdges = buildEdges(GradientWandItem.plan(request), context.world());
        }

        if (cachedEdges.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        MatrixStack.Entry entry = matrices.peek();

        // 1.21 changed both halves of this: vertex and normal take the whole MatrixStack.Entry
        // rather than the position and normal matrices, and next() is gone because a vertex is
        // finished as soon as its attributes are written.
        //? if <1.21 {
        Matrix4f position = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();

        for (PreviewEdge edge : cachedEdges) {
            int dx = edge.axis() == Direction.Axis.X ? 1 : 0;
            int dy = edge.axis() == Direction.Axis.Y ? 1 : 0;
            int dz = edge.axis() == Direction.Axis.Z ? 1 : 0;

            // RenderLayer.getLines() wants a position, a colour and a normal on every vertex
            lines.vertex(position, edge.x(), edge.y(), edge.z())
                    .color(edge.red(), edge.green(), edge.blue(), ALPHA)
                    .normal(normal, dx, dy, dz)
                    .next();

            lines.vertex(position, edge.x() + dx, edge.y() + dy, edge.z() + dz)
                    .color(edge.red(), edge.green(), edge.blue(), ALPHA)
                    .normal(normal, dx, dy, dz)
                    .next();
        }
        //?} else {
        /*for (PreviewEdge edge : cachedEdges) {
            int dx = edge.axis() == Direction.Axis.X ? 1 : 0;
            int dy = edge.axis() == Direction.Axis.Y ? 1 : 0;
            int dz = edge.axis() == Direction.Axis.Z ? 1 : 0;

            // RenderLayer.getLines() wants a position, a colour and a normal on every vertex
            lines.vertex(entry, edge.x(), edge.y(), edge.z())
                    .color(edge.red(), edge.green(), edge.blue(), ALPHA)
                    .normal(entry, dx, dy, dz);

            lines.vertex(entry, edge.x() + dx, edge.y() + dy, edge.z() + dz)
                    .color(edge.red(), edge.green(), edge.blue(), ALPHA)
                    .normal(entry, dx, dy, dz);
        }
        *///?}

        matrices.pop();
    }

    // Turns the planned blocks into a deduplicated set of edges: faces buried inside the shape
    // are skipped, and an edge two blocks share is only stored once.
    private static List<PreviewEdge> buildEdges(List<GradientWandItem.PlannedBlock> planned, ClientWorld world) {
        Set<BlockPos> filled = new HashSet<>();

        for (GradientWandItem.PlannedBlock block : planned) {
            filled.add(block.pos());
        }

        Map<EdgeKey, PreviewEdge> edges = new LinkedHashMap<>();

        for (GradientWandItem.PlannedBlock block : planned) {
            BlockPos pos = block.pos();

            if (!world.getBlockState(pos).isReplaceable()) {
                continue;
            }

            int rgb = block.state().getMapColor(world, pos).color;

            if (rgb == 0) {
                rgb = 0xFFFFFF;
            }

            float red = ((rgb >> 16) & 0xFF) / 255.0f;
            float green = ((rgb >> 8) & 0xFF) / 255.0f;
            float blue = (rgb & 0xFF) / 255.0f;

            for (Direction direction : Direction.values()) {
                if (filled.contains(pos.offset(direction))) {
                    continue; // this face is buried against another planned block
                }

                addFaceEdges(edges, pos, direction, red, green, blue);
            }
        }

        return new ArrayList<>(edges.values());
    }

    private static void addFaceEdges(Map<EdgeKey, PreviewEdge> edges, BlockPos pos, Direction direction,
                                     float red, float green, float blue) {
        // The lowest corner of this face
        int bx = pos.getX() + (direction == Direction.EAST ? 1 : 0);
        int by = pos.getY() + (direction == Direction.UP ? 1 : 0);
        int bz = pos.getZ() + (direction == Direction.SOUTH ? 1 : 0);

        // The two axes the face spans
        Direction.Axis first;
        Direction.Axis second;

        switch (direction.getAxis()) {
            case X -> {
                first = Direction.Axis.Y;
                second = Direction.Axis.Z;
            }
            case Y -> {
                first = Direction.Axis.X;
                second = Direction.Axis.Z;
            }
            default -> {
                first = Direction.Axis.X;
                second = Direction.Axis.Y;
            }
        }

        int fx = first == Direction.Axis.X ? 1 : 0;
        int fy = first == Direction.Axis.Y ? 1 : 0;
        int fz = first == Direction.Axis.Z ? 1 : 0;

        int sx = second == Direction.Axis.X ? 1 : 0;
        int sy = second == Direction.Axis.Y ? 1 : 0;
        int sz = second == Direction.Axis.Z ? 1 : 0;

        put(edges, bx, by, bz, first, red, green, blue);
        put(edges, bx + sx, by + sy, bz + sz, first, red, green, blue);
        put(edges, bx, by, bz, second, red, green, blue);
        put(edges, bx + fx, by + fy, bz + fz, second, red, green, blue);
    }

    // First block to claim an edge sets its colour
    private static void put(Map<EdgeKey, PreviewEdge> edges, int x, int y, int z, Direction.Axis axis,
                            float red, float green, float blue) {
        edges.putIfAbsent(new EdgeKey(x, y, z, axis), new PreviewEdge(x, y, z, axis, red, green, blue));
    }
}