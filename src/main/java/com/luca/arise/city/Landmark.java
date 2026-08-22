package com.luca.arise.city;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il monumento di una città: la cosa che si riconosce da lontano e per cui vale la pena andarci.
 *
 * <p>Una città generata è un posto dove si passa; una città con dentro il Colosseo è un posto dove
 * si <em>torna</em>. È l'unico pezzo di città costruito a mano invece che da una regola, ed è
 * esattamente per questo che funziona: tutto il resto è variazione su un tema, questo no.
 *
 * <p>{@code radius} è mezzo lato dell'area che il monumento si tiene per sé — dentro non si
 * costruisce e le strade vengono cancellate. {@code height} è quanto sale, e serve a sgomberare il
 * cielo sopra prima di posarlo: la spianata iniziale non arriva così in alto.
 */
public enum Landmark implements StringRepresentable {

	/** New York: la torre a scalini del '31, con la guglia. */
	EMPIRE_STATE("empire_state", 24, 128),

	/** Tokyo: il traliccio rosso e bianco, con le due terrazze. */
	TOKYO_TOWER("tokyo_tower", 22, 100),

	/** Roma: l'anfiteatro ellittico, tre ordini di arcate, un pezzo dell'anello caduto. */
	COLOSSEUM("colosseum", 40, 40),

	/** Madrid: la porta neoclassica a cinque fornici, e la fontana davanti. */
	ALCALA("alcala", 26, 32),

	/** Berlino: la porta con la quadriga, e dietro la torre della televisione con la sfera. */
	BRANDENBURG("brandenburg", 34, 144);

	private final String name;
	private final int radius;
	private final int height;

	Landmark(String name, int radius, int height) {
		this.name = name;
		this.radius = radius;
		this.height = height;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Mezzo lato dell'area riservata. */
	public int radius() {
		return radius;
	}

	/** Quanto sale sopra il piano di calpestio. */
	public int height() {
		return height;
	}

	public Component label() {
		return Component.translatable("arise.landmark." + name);
	}
}
