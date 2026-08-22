package com.luca.arise.client.render;

import com.luca.arise.AriseMod;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.model.geom.ModelLayerLocation;

/** I layer dei modelli della mod. Registrati dall'entrypoint client, come tutto il resto. */
public final class ModModelLayers {

	public static final ModelLayerLocation SHADOW =
			new ModelLayerLocation(AriseMod.id("shadow"), "main");

	public static final ModelLayerLocation SHOPKEEPER =
			new ModelLayerLocation(AriseMod.id("shopkeeper"), "main");

	private ModModelLayers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(SHADOW, ShadowModel::createLayer);
		ModelLayerRegistry.registerModelLayer(SHOPKEEPER, ShopkeeperRenderer::createLayer);
	}
}
