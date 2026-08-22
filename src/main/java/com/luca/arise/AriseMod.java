package com.luca.arise;

import com.luca.arise.command.AriseCommands;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.event.CityEvents;
import com.luca.arise.event.ProgressEvents;
import com.luca.arise.fx.ModSounds;
import com.luca.arise.network.ModPayloads;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.registry.ModEntities;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AriseMod implements ModInitializer {
	public static final String MOD_ID = "arise";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		AriseConfig.load();
		ModAttachments.init();
		ModEntities.init();
		ModSounds.init();
		ModPayloads.register();
		ProgressEvents.register();
		CityEvents.register();
		AriseCommands.register();

		LOGGER.info("Sistema inizializzato.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
