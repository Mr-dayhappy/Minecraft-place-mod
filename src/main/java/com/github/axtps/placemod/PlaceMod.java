package com.github.axtps.placemod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceMod implements ModInitializer {
	public static final String MOD_ID = "minecraft-place-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Minecraft Place Mod initialized.");
	}
}