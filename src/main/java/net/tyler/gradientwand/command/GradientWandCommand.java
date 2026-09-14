package net.tyler.gradientwand.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.WandSettings;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class GradientWandCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("gwand")
                        .then(enumOption("mode", WandSettings.Mode.values(), WandSettings::withMode))
                        .then(enumOption("axis", WandSettings.GradientAxis.values(), WandSettings::withAxis))
                        .then(enumOption("dither", WandSettings.Dither.values(), WandSettings::withDither))
                        .then(enumOption("grain", WandSettings.Grain.values(), WandSettings::withGrain))
                        .then(CommandManager.literal("jitter")
                                .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(context -> apply(context, settings ->
                                                settings.withJitter(FloatArgumentType.getFloat(context, "amount"))))))
                        .then(CommandManager.literal("width")
                                .then(CommandManager.argument("blocks", IntegerArgumentType.integer(
                                                WandSettings.MIN_WIDTH, WandSettings.MAX_WIDTH))
                                        .executes(context -> apply(context, settings ->
                                                settings.withWidth(IntegerArgumentType.getInteger(context, "blocks"))))))
                        .then(CommandManager.literal("show")
                                .executes(GradientWandCommand::show))));
    }

    // Builds "gwand <name> <value1|value2|...>" from an enum's values
    private static <T extends Enum<T>> LiteralArgumentBuilder<ServerCommandSource> enumOption(
            String name, T[] values, BiFunction<WandSettings, T, WandSettings> setter) {

        LiteralArgumentBuilder<ServerCommandSource> node = CommandManager.literal(name);

        for (T value : values) {
            node.then(CommandManager.literal(value.name().toLowerCase())
                    .executes(context -> apply(context, settings -> setter.apply(settings, value))));
        }

        return node;
    }

    private static int apply(CommandContext<ServerCommandSource> context, UnaryOperator<WandSettings> change)
            throws CommandSyntaxException {

        ServerCommandSource source = context.getSource();
        ItemStack stack = heldWand(source);

        if (stack == null) {
            source.sendError(Text.literal("Hold your gradient wand first"));
            return 0;
        }

        WandSettings updated = change.apply(WandSettings.from(stack));
        updated.save(stack);

        source.sendFeedback(() -> Text.literal("Wand set to " + updated.describe()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ItemStack stack = heldWand(source);

        if (stack == null) {
            source.sendError(Text.literal("Hold your gradient wand first"));
            return 0;
        }

        WandSettings settings = WandSettings.from(stack);
        source.sendFeedback(() -> Text.literal("Wand is " + settings.describe()), false);

        return Command.SINGLE_SUCCESS;
    }

    // The wand in the player's main hand, or null if they are not holding one
    private static ItemStack heldWand(ServerCommandSource source) throws CommandSyntaxException {
        ItemStack stack = source.getPlayerOrThrow().getMainHandStack();

        return stack.getItem() instanceof GradientWandItem ? stack : null;
    }
}