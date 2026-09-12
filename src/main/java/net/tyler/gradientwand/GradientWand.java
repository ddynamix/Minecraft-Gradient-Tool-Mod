package net.tyler.gradientwand;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import net.tyler.gradientwand.animation.PlacementQueue;
import net.tyler.gradientwand.command.GradientWandCommand;
import net.tyler.gradientwand.item.ModItemGroups;
import net.tyler.gradientwand.item.ModItems;
import net.tyler.gradientwand.item.custom.GradientWandItem;
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
		ModItems.registerModItems();
		ModItemGroups.registerItemGroups();
		GradientWandItem.registerNoBlockBreaking();
		GradientWandCommand.register();
		WandSettingsPacket.registerReceiver();
		WandUndoPacket.registerReceiver();
		WandRedoPacket.registerReceiver();
		UndoHistory.registerCleanup();
		WandCancelPacket.registerReceiver();
		PlacementQueue.register();
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
