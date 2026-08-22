package com.luca.arise.client.render;

import com.luca.arise.npc.Shopkeeper;
import com.luca.arise.npc.ShopkeeperEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Disegna chi sta dietro il bancone: una persona, con una delle nove skin di Minecraft.
 *
 * <p>Sono le skin predefinite — quelle che il gioco propone a chi non ne ha scelta una — e stanno
 * gia' in ogni installazione. Nove volti diversi, disegnati bene, e nessun file da aggiungere. La
 * texture si sceglie dal ruolo, che arriva sincronizzato dall'entita': senza quel passaggio tutte
 * e nove le botteghe avrebbero la faccia della prima.
 *
 * <p>Il materiale e' <em>cutout</em> e non translucido, all'opposto dell'ombra: una skin ha
 * trasparenza solo nel secondo strato, e un materiale translucido la farebbe ordinare per
 * profondita' a ogni fotogramma per niente.
 */
public class ShopkeeperRenderer
		extends HumanoidMobRenderer<ShopkeeperEntity, ShopkeeperRenderer.State, HumanoidModel<ShopkeeperRenderer.State>> {

	/** Lo stato porta il ruolo: al momento del disegno l'entita' non e' piu' leggibile. */
	public static class State extends HumanoidRenderState {
		public Shopkeeper role = Shopkeeper.SMELTER;
	}

	private static final String SKIN_PATH = "textures/entity/player/wide/";

	public ShopkeeperRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModModelLayers.SHOPKEEPER),
				RenderTypes::entityCutout), 0.5F);
	}

	/**
	 * La mesh umanoide vanilla, senza ritocchi.
	 *
	 * <p>Un layer nostro invece di uno di Minecraft: quelli vanilla arrivano legati al materiale
	 * del mob a cui appartengono, ed e' la stessa trappola gia' annotata su {@code ShadowModel}.
	 */
	public static LayerDefinition createLayer() {
		return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ShopkeeperEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.role = entity.role();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return Identifier.withDefaultNamespace(SKIN_PATH + state.role.skin() + ".png");
	}
}
