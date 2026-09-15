package net.tyler.gradientwand;

import net.fabricmc.api.ModInitializer;

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

public class GradientWand implements ModInitializer {
	public static final String MOD_ID = "gradient_wand";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
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
