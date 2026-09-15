package net.tyler.gradientwand.item;

//? if fabric {
/*import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
*///?}
//? if neoforge {
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
//?}

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.item.custom.WandTier;

public class ModItemGroups {

    //? if fabric {
    /*public static final CreativeModeTab GRADIENT_WAND_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            GradientWand.id("gradient_wand"),
            FabricItemGroup.builder().title(Component.translatable("itemgroup.gradient_wand"))
                    .icon(() -> new ItemStack(ModItems.of(WandTier.NETHERITE))).displayItems((displayContext, entries) ->
                    {
                        // Enum order is tier order, so the tab reads wood through netherite
                        for (WandTier tier : WandTier.values()) {
                            entries.accept(ModItems.of(tier));
                        }
                    }).build());

    public static void registerItemGroups() {
        GradientWand.LOGGER.info("Registering item groups for " + GradientWand.MOD_ID);
    }
    *///?}
    //? if neoforge {
    // Vanilla's own CreativeModeTab.builder(), reached through a deferred registry rather than
    // Fabric's helper. The tab contents are identical.
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GradientWand.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> GRADIENT_WAND_GROUP =
            TABS.register("gradient_wand", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.gradient_wand"))
                    .icon(() -> new ItemStack(ModItems.of(WandTier.NETHERITE)))
                    .displayItems((displayContext, entries) -> {
                        // Enum order is tier order, so the tab reads wood through netherite
                        for (WandTier tier : WandTier.values()) {
                            entries.accept(ModItems.of(tier));
                        }
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);

        GradientWand.LOGGER.info("Registering item groups for " + GradientWand.MOD_ID);
    }
    //?}
}
