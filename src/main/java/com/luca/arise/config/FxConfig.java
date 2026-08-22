package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Aspetto ed effetti: quanto è trasparente un'ombra, quanti particellari escono, quanto spesso.
 *
 * <p>Sono le uniche voci della config che valgono <em>sul client</em> e non sul server. È una
 * scelta: sono impostazioni di gusto e di prestazioni, e ognuno deve poterle abbassare sulla
 * propria macchina senza chiedere niente a chi ospita la partita. Niente qui influenza il gioco.
 */
public record FxConfig(
		/** Opacità dell'ombra, da 0 (invisibile) a 1 (solida). */
		double shadowOpacity,
		/** Quanto è più alta e affusolata l'ombra rispetto a un umanoide normale. */
		double shadowStretch,
		/** Se le ombre evocate emettono l'aura del loro colore. */
		boolean shadowAura,
		/** Un particellare d'aura ogni quanti tick. Alzalo se il gioco arranca con l'esercito fuori. */
		int auraIntervalTicks,
		/** Moltiplicatore sul numero di particellari degli effetti a botta singola. */
		double effectScale) {

	public static final FxConfig DEFAULT = new FxConfig(0.72, 1.08, true, 3, 1.0);

	public static final Codec<FxConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("shadow_opacity").forGetter(FxConfig::shadowOpacity),
			Codec.DOUBLE.fieldOf("shadow_stretch").forGetter(FxConfig::shadowStretch),
			Codec.BOOL.fieldOf("shadow_aura").forGetter(FxConfig::shadowAura),
			Codec.INT.fieldOf("aura_interval_ticks").forGetter(FxConfig::auraIntervalTicks),
			Codec.DOUBLE.fieldOf("effect_scale").forGetter(FxConfig::effectScale)
	).apply(instance, FxConfig::new));

	/** Il canale alfa da usare nella tinta del modello. */
	public int alpha() {
		return (int) (Math.clamp(shadowOpacity, 0.0, 1.0) * 255.0) << 24;
	}

	/** Il conteggio di particellari richiesto, scalato dall'impostazione, mai sotto uno. */
	public int scaled(int count) {
		return Math.max(1, (int) Math.round(count * Math.clamp(effectScale, 0.0, 4.0)));
	}
}
