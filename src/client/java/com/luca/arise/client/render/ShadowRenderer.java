package com.luca.arise.client.render;

import com.luca.arise.AriseMod;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.FxConfig;
import com.luca.arise.shadow.ShadowEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Disegna l'ombra: una sagoma umanoide translucida, tinta del colore scelto dal giocatore.
 *
 * <p>Tre cose insieme fanno l'aspetto "ombra", e nessuna delle tre da sola basterebbe: la
 * <em>geometria</em> incurvata di {@link ShadowModel}, la <em>trasparenza</em> che arriva dal
 * canale alfa della tinta (possibile solo perché il modello dichiara un materiale translucido), e
 * l'<em>allungamento</em> qui sotto, che rende la figura più alta e più stretta di una persona.
 * Il quarto elemento — l'aura di particellari del suo colore — sta in {@code ShadowEntity.tick},
 * perché è un effetto continuo e va generato sul client senza passare dalla rete.
 */
public class ShadowRenderer
		extends HumanoidMobRenderer<ShadowEntity, ShadowRenderer.State, ShadowModel> {

	/** Lo stato di render trasporta il colore: al momento del disegno l'entità non è leggibile. */
	public static class State extends HumanoidRenderState {
		public int color = 0xFFFFFF;
	}

	private static final Identifier TEXTURE = AriseMod.id("textures/entity/shadow.png");

	/** Quanto si stringe la figura sui due assi orizzontali mentre si allunga in verticale. */
	private static final float NARROWING = 0.88F;

	public ShadowRenderer(EntityRendererProvider.Context context) {
		super(context, new ShadowModel(context.bakeLayer(ModModelLayers.SHADOW)), 0.5F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ShadowEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.color = entity.getColor();
	}

	/**
	 * Alta e affusolata: la proporzione è ciò che si nota da lontano, prima del colore.
	 *
	 * <p>La scala si applica attorno ai piedi, quindi l'ombra resta appoggiata a terra. Tocca solo
	 * il modello: l'ingombro fisico dell'entità resta quello dichiarato in {@code ModEntities}, ed
	 * è giusto così — la sagoma che si vede non deve cambiare dove si può passare.
	 */
	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);

		float stretch = (float) AriseConfig.get().fx().shadowStretch();
		poseStack.scale(NARROWING, stretch, NARROWING);
	}

	/**
	 * La tinta moltiplica la texture e ne stabilisce l'opacità.
	 *
	 * <p>Con la texture quasi nera il risultato resta scuro anche con colori accesi — l'ombra
	 * resta un'ombra, ma si riconosce a colpo d'occhio di chi è.
	 */
	@Override
	protected int getModelTint(State state) {
		FxConfig fx = AriseConfig.get().fx();
		return fx.alpha() | (state.color & 0xFFFFFF);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
