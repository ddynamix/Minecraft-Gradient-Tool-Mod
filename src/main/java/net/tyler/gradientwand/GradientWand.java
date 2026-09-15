package net.tyler.gradientwand;

//? if fabric {
/*import net.fabricmc.api.ModInitializer;
*///?}
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//?}

import net.minecraft.resources.ResourceLocation;

import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.command.GradientWandCommand;
import net.tyler.gradientwand.enchantment.ModEnchantments;
import net.tyler.gradientwand.loot.WandLoot;
import net.tyler.gradientwand.item.ModItemGroups;
import net.tyler.gradientwand.item.ModItems;
import net.tyler.gradientwand.item.custom.GradientWandItem;
import net.tyler.gradientwand.item.custom.SettingsNbt;
import net.tyler.gradientwand.network.WandCancelPacket;
import net.tyler.gradientwand.network.WandSettingsPacket;
import net.tyler.gradientwand.network.WandUndoPacket;
import net.tyler.gradientwand.network.WandRedoPacket;
import net.tyler.gradientwand.undo.UndoHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if neoforge {
@Mod(GradientWand.MOD_ID)
//?}
public class GradientWand
        //? if fabric {
        /*implements ModInitializer
        *///?}
{
	public static final String MOD_ID = "gradient_wand";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	//? if fabric {
	/*@Override
	public void onInitialize() {
		LOGGER.info("Gradient Wand starting on Fabric");

		// Before the items, so the component types exist by the time a wand can hold one
		SettingsNbt.register();
		ModItems.registerModItems();
		ModItemGroups.registerItemGroups();
		ModEnchantments.register();
		WandLoot.register();
		GradientWandItem.registerNoBlockBreaking();
		GradientWandCommand.register();
		WandSettingsPacket.registerReceiver();
		WandUndoPacket.registerReceiver();
		WandRedoPacket.registerReceiver();
		UndoHistory.registerCleanup();
		WandCancelPacket.registerReceiver();
		PlacementQueue.register();
	}
	*///?}
	//? if neoforge {
	// NeoForge has two buses. The mod bus carries startup events, which is where the deferred
	// registries and the payload registrar live; the game bus carries everything that happens
	// while a world is running. Fabric's single onInitialize covers both, so the calls that were
	// one list there are split across the two here.
	public GradientWand(IEventBus modBus) {
		LOGGER.info("Gradient Wand starting on NeoForge");

		SettingsNbt.register(modBus);
		ModItems.register(modBus);
		ModItemGroups.register(modBus);
		ModEnchantments.register();
		WandLoot.register(modBus);

		modBus.addListener(GradientWand::registerPayloads);

		NeoForge.EVENT_BUS.addListener(GradientWandItem::onLeftClickBlock);
		NeoForge.EVENT_BUS.addListener(GradientWandCommand::onRegisterCommands);
		NeoForge.EVENT_BUS.addListener(UndoHistory::onPlayerLoggedOut);
		NeoForge.EVENT_BUS.addListener(PlacementQueue::onServerTick);
		NeoForge.EVENT_BUS.addListener(WandLoot::onVillagerTrades);
	}

	// One registrar for every payload, rather than each packet registering itself
	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");

		WandSettingsPacket.register(registrar);
		WandUndoPacket.register(registrar);
		WandRedoPacket.register(registrar);
		WandCancelPacket.register(registrar);
	}
	//?}

	public static ResourceLocation id(String path) {
		return id(MOD_ID, path);
	}

	// Every ResourceLocation the mod builds goes through here, which is why 1.21 making the constructor
	// private is a one-line change rather than a change at every call site.
	public static ResourceLocation id(String namespace, String path) {
		//? if <1.21 {
		/*return new ResourceLocation(namespace, path);
		*///?} else
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}
}
