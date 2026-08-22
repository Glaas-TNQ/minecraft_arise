package com.luca.arise.client;

import com.luca.arise.AriseMod;
import com.luca.arise.client.hud.SystemHudElement;
import com.luca.arise.client.network.ClientPayloads;
import com.luca.arise.client.render.MarkerRenderer;
import com.luca.arise.client.render.ModModelLayers;
import com.luca.arise.client.render.ShadowRenderer;
import com.luca.arise.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class AriseModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// Agganciato dopo la barra dell'esperienza vanilla: cosi' l'ordine di disegno resta
		// prevedibile anche se altre mod aggiungono elementi.
		HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL,
				AriseMod.id("system_hud"), new SystemHudElement());

		ModModelLayers.register();
		EntityRendererRegistry.register(ModEntities.SHADOW, ShadowRenderer::new);
		EntityRendererRegistry.register(ModEntities.GATE, MarkerRenderer::new);

		AriseKeyMappings.register();
		ClientPayloads.register();
	}
}
