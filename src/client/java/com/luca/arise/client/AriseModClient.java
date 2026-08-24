package com.luca.arise.client;

import com.luca.arise.AriseMod;
import com.luca.arise.client.hud.QuestTrackerElement;
import com.luca.arise.client.hud.SystemHudElement;
import com.luca.arise.client.network.ClientPayloads;
import com.luca.arise.client.render.MarkerRenderer;
import com.luca.arise.client.render.ModModelLayers;
import com.luca.arise.client.render.ShadowRenderer;
import com.luca.arise.client.render.ShopkeeperRenderer;
import com.luca.arise.client.screen.HunterMenuScreen;
import com.luca.arise.client.screen.MachineScreen;
import com.luca.arise.registry.ModEntities;
import com.luca.arise.registry.ModMenus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.gui.screens.MenuScreens;

public class AriseModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Agganciato dopo la barra dell'esperienza vanilla: cosi' l'ordine di disegno resta
		// prevedibile anche se altre mod aggiungono elementi.
		HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL,
				AriseMod.id("system_hud"), new SystemHudElement());

		// Il tracciato dell'incarico, sul bordo destro. Dopo il riquadro del Sistema perche' lo
		// interroga: F6 spegne tutti e due.
		HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL,
				AriseMod.id("quest_tracker"), new QuestTrackerElement());

		ModModelLayers.register();
		EntityRendererRegistry.register(ModEntities.SHADOW, ShadowRenderer::new);
		EntityRendererRegistry.register(ModEntities.GATE, MarkerRenderer::new);
		EntityRendererRegistry.register(ModEntities.SHOPKEEPER, ShopkeeperRenderer::new);

		// Il menu del Cacciatore arriva dal server: qui si dice solo con che schermata disegnarlo.
		MenuScreens.register(ModMenus.HUNTER, HunterMenuScreen::new);

		// Una schermata sola per tutti e quattro i macchinari: quale sia lo dice il menu.
		MenuScreens.register(ModMenus.MACHINE, MachineScreen::new);

		// Uscire da un mondo butta il terreno della mappa. Un mondo diverso e' un terreno diverso, e
		// un riquadro tenuto attraverso quel confine mostrerebbe il posto sbagliato senza dirlo —
		// in singleplayer si passa da un mondo all'altro senza mai chiudere il gioco.
		ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> com.luca.arise.client.map.TerrainTiles.clear());

		AriseKeyMappings.register();
		HunterButton.register();
		ClientPayloads.register();
	}
}
