package com.luca.arise.client.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Renderer per le entità che <em>non hanno un corpo</em>: il varco di un Gate, il terminale
 * dell'Associazione.
 *
 * <p>Queste entità esistono per essere cliccate e per portare un nome fluttuante; quello che si
 * vede sono i particellari che il server disegna attorno a loro. Disegnare anche un modello
 * significherebbe inventare una geometria che nessuno ha chiesto — e una texture in più da
 * mantenere.
 *
 * <p>Il nome sopra la testa lo disegna la classe base, purché l'entità abbia un nome custom
 * visibile. È tutto quello che serve.
 */
public class MarkerRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

	public MarkerRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
