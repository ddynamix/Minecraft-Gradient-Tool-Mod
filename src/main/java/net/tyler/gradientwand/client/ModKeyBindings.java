package net.tyler.gradientwand.client;

//? if fabric {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
*///?}
//? if neoforge {
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.SettingsNbt;
import net.tyler.gradientwand.item.custom.WandTier;
import net.tyler.gradientwand.network.WandCancelPacket;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

    private static KeyMapping openMenu;

    // Read fresh every time rather than cached, so rebinding the key in Controls shows up in the
    // tooltip straight away. Tooltips are rebuilt every frame, so there is nothing to invalidate.
    public static Component menuKeyLabel() {
        if (openMenu == null || openMenu.isUnbound()) {
            return Component.translatable("key.keyboard.unknown");
        }

        return openMenu.getTranslatedKeyMessage();
    }

    // Edge detection by hand: vanilla drains keyAttack.consumeClick() earlier in the tick
    private static boolean attackWasDown;

    // The binding itself is identical on both loaders; only who is told about it differs
    private static KeyMapping newBinding() {
        return new KeyMapping(
                "key.gradient_wand.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.gradient_wand");
    }

    //? if fabric {
    /*public static void register() {
        openMenu = KeyBindingHelper.registerKeyBinding(newBinding());

        ClientTickEvents.END_CLIENT_TICK.register(ModKeyBindings::tick);
    }
    *///?}
    //? if neoforge {
    // NeoForge splits this in two: the binding is registered on the mod bus during startup, and
    // the per-tick polling is a separate game-bus event. GradientWandClient wires both up.
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        openMenu = newBinding();

        event.register(openMenu);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        tick(Minecraft.getInstance());
    }
    //?}

    // Shared body: polling the key and reacting to left click is the same on every loader
    private static void tick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        ItemStack stack = client.player.getMainHandItem();
        boolean holdingWand = stack.getItem() instanceof GradientWandItem;

        // while, not if: the key can be pressed more than once between ticks
        while (openMenu.consumeClick()) {
            if (holdingWand) {
                client.setScreen(new GradientWandScreen(SettingsNbt.read(stack),
                        WandTier.of(stack).maxWidth()));
            }
        }

        boolean attackDown = client.screen == null && client.options.keyAttack.isDown();

        // Left click cancels, aimed at a block or at nothing at all
        if (attackDown && !attackWasDown && holdingWand) {
            sendCancel();
        }

        attackWasDown = attackDown;
    }

    // The only line in the tick body that is loader specific
    private static void sendCancel() {
        //? if fabric {
        /*ClientPlayNetworking.send(new WandCancelPacket());
        *///?}
        //? if neoforge {
        PacketDistributor.sendToServer(new WandCancelPacket());
        //?}
    }
}
