package net.tyler.gradientwand.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.WandSettings;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

    private static KeyBinding openMenu;

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

            // while, not if: the key can be pressed more than once between ticks
            while (openMenu.wasPressed()) {
                ItemStack stack = client.player.getMainHandStack();

                if (stack.getItem() instanceof GradientWandItem) {
                    client.setScreen(new GradientWandScreen(WandSettings.from(stack)));
                }
            }
        });
    }
}