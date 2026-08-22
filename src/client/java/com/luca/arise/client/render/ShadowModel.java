package com.luca.arise.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * La sagoma di un'ombra: umanoide, ma non umana.
 *
 * <p>Parte dalla mesh umanoide di Minecraft e la assottiglia di un paio di millimetri per lato
 * ({@link CubeDeformation} negativa), poi la incurva in avanti sulle spalle. È poco, ed è
 * voluto: le proporzioni umanoidi restano perché la texture è una skin 64×64 standard — chi
 * volesse dare un aspetto diverso al proprio esercito può disegnarla come qualunque altra skin,
 * senza sapere niente di modelli.
 *
 * <p>Il resto dell'effetto non sta nella geometria ma nel materiale: il tipo di render è
 * <em>translucido</em> invece che opaco, così l'alfa scelto in config diventa trasparenza vera e
 * l'ombra si vede attraverso. È il motivo per cui questo modello non può usare il layer dello
 * zombie: quello arriva già legato al suo materiale opaco.
 */
public class ShadowModel extends HumanoidModel<ShadowRenderer.State> {

	/** Quanto l'ombra è curva in avanti, in radianti. Poco: di più sembra ferita, non minacciosa. */
	private static final float HUNCH = 0.12F;

	/** Il respiro: ampiezza e velocità dell'oscillazione a riposo. */
	private static final float BREATH_AMPLITUDE = 0.04F;
	private static final float BREATH_SPEED = 0.06F;

	public ShadowModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);

		// Lo strato del cappello è il secondo strato di una skin: su una silhouette translucida
		// raddoppierebbe i contorni invece di aggiungere dettaglio.
		this.hat.visible = false;
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(-0.12F), 0.0F), 64, 64);
	}

	@Override
	public void setupAnim(ShadowRenderer.State state) {
		super.setupAnim(state);

		// Le parti dell'umanoide sono tutte figlie della radice, quindi la curvatura va data a
		// ognuna: busto e braccia in avanti, testa all'indietro della stessa quantità perché
		// continui a guardare dritto chi ha davanti. Le gambe restano ferme, o sembrerebbe cadere.
		float breath = Mth.sin(state.ageInTicks * BREATH_SPEED) * BREATH_AMPLITUDE;
		float lean = HUNCH + breath;

		this.body.xRot += lean;
		this.rightArm.xRot += lean;
		this.leftArm.xRot += lean;
		this.head.xRot -= lean * 0.8F;
	}
}
