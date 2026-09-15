package net.tyler.gradientwand.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.tyler.gradientwand.core.WandSettings;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.SettingsNbt;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class GradientWandCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("gwand")
                        .then(enumOption("mode", WandSettings.Mode.values(), WandSettings::withMode))
                        .then(enumOption("axis", WandSettings.GradientAxis.values(), WandSettings::withAxis))
                        .then(enumOption("dither", WandSettings.Dither.values(), WandSettings::withDither))
                        .then(enumOption("grain", WandSettings.Grain.values(), WandSettings::withGrain))
                        .then(enumOption("easing", WandSettings.Easing.values(), WandSettings::withEasing))
                        .then(Commands.literal("jitter")
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(context -> apply(context, settings ->
                                                settings.withJitter(FloatArgumentType.getFloat(context, "amount"))))))
                        .then(Commands.literal("width")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(
                                                WandSettings.MIN_WIDTH, WandSettings.MAX_WIDTH))
                                        .executes(context -> apply(context, settings ->
                                                settings.withWidth(IntegerArgumentType.getInteger(context, "blocks"))))))
                        .then(Commands.literal("show")
                                .executes(GradientWandCommand::show))));
    }

    // Builds "gwand <name> <value1|value2|...>" from an enum's values
    private static <T extends Enum<T>> LiteralArgumentBuilder<CommandSourceStack> enumOption(
            String name, T[] values, BiFunction<WandSettings, T, WandSettings> setter) {

        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);

        for (T value : values) {
            node.then(Commands.literal(value.name().toLowerCase())
                    .executes(context -> apply(context, settings -> setter.apply(settings, value))));
        }

        return node;
    }

    private static int apply(CommandContext<CommandSourceStack> context, UnaryOperator<WandSettings> change)
            throws CommandSyntaxException {

        CommandSourceStack source = context.getSource();
        ItemStack stack = heldWand(source);

        if (stack == null) {
            source.sendFailure(Component.literal("Hold your gradient wand first"));
            return 0;
        }

        SettingsNbt.write(stack, change.apply(SettingsNbt.read(stack)));

        // Read it back rather than echoing what was asked for: a narrow tier may have clamped the
        // width on the way in, and the player should see what the wand actually holds
        source.sendSuccess(() -> Component.literal("Wand set to " + SettingsNbt.read(stack).describe()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ItemStack stack = heldWand(source);

        if (stack == null) {
            source.sendFailure(Component.literal("Hold your gradient wand first"));
            return 0;
        }

        WandSettings settings = SettingsNbt.read(stack);
        source.sendSuccess(() -> Component.literal("Wand is " + settings.describe()), false);

        return Command.SINGLE_SUCCESS;
    }

    // The wand in the player's main hand, or null if they are not holding one
    private static ItemStack heldWand(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack stack = source.getPlayerOrException().getMainHandItem();

        return stack.getItem() instanceof GradientWandItem ? stack : null;
    }
}