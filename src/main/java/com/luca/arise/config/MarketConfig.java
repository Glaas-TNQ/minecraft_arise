package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Il Quartiere del Mercato: se c'e', e quanto vale una moneta.
 *
 * <p>{@code coinValue} e' il numero che tiene insieme le due economie della mod. I soul coin sono
 * un contatore dentro il Sistema, le Monete d'Anima sono oggetti dentro lo zaino, e questo e' il
 * cambio fra i due. Alzarlo rende le monete rare e il mercato caro; abbassarlo trasforma il Banco
 * in un bancomat.
 *
 * <p>Il <em>ritiro</em> rende meno del <em>conio</em>, e la differenza e' voluta: cambiare avanti e
 * indietro deve costare qualcosa, altrimenti il Banco e' un posto dove parcheggiare il denaro
 * senza rischio invece di una scelta fra tenerlo al sicuro e portarselo dietro.
 */
public record MarketConfig(
		/** Se le nove botteghe vengono popolate. A falso restano edifici vuoti. */
		boolean npcs,
		/** Quanti soul coin costa coniare una Moneta d'Anima. */
		long coinValue,
		/** Quante monete al massimo si coniano con un clic. */
		int mintBatch,
		/** Quanta parte del valore si riprende riportando indietro una moneta. */
		double redeemRate,
		/** Quanti soul coin chiede il Sensale per aprire un varco su ordinazione. */
		long gateFee) {

	public static final MarketConfig DEFAULT = new MarketConfig(true, 100L, 16, 0.8, 400L);

	public static final Codec<MarketConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("npcs").forGetter(MarketConfig::npcs),
			Codec.LONG.fieldOf("coin_value").forGetter(MarketConfig::coinValue),
			Codec.INT.fieldOf("mint_batch").forGetter(MarketConfig::mintBatch),
			Codec.DOUBLE.fieldOf("redeem_rate").forGetter(MarketConfig::redeemRate),
			Codec.LONG.fieldOf("gate_fee").forGetter(MarketConfig::gateFee)
	).apply(instance, MarketConfig::new));

	/** Quanti soul coin rende riportare indietro questo numero di monete. */
	public long redeemValue(int coins) {
		return Math.max(0L, (long) (coins * coinValue * redeemRate));
	}
}
