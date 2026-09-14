package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.WandSettings;
import net.tyler.gradientwand.item.custom.WandTier;
import net.tyler.gradientwand.network.WandCancelPacket;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

    private static KeyBinding openMenu;

    // Edge detection by hand: vanilla drains attackKey.wasPressed() earlier in the tick
    private static boolean attackWasDown;

    public static void register() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gradient_wand.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.gradient_wand"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            ItemStack stack = client.player.getMainHandStack();
            boolean holdingWand = stack.getItem() instanceof GradientWandItem;

            // while, not if: the key can be pressed more than once between ticks
            while (openMenu.wasPressed()) {
                if (holdingWand) {
                    client.setScreen(new GradientWandScreen(WandSettings.from(stack),
                            WandTier.of(stack).maxWidth()));
                }
            }

            boolean attackDown = client.currentScreen == null && client.options.attackKey.isPressed();

            // Left click cancels, aimed at a block or at nothing at all
            if (attackDown && !attackWasDown && holdingWand) {
                ClientPlayNetworking.send(new WandCancelPacket());
            }

            attackWasDown = attackDown;
        });
    }
}