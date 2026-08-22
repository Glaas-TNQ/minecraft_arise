package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * L'Abyss Shop: quanto costa e quanto spesso cambia.
 *
 * <p>Il negozio non e' la fonte principale dell'equipaggiamento — quella sono i Gate (design
 * §8.5). E' la rete di sicurezza di chi non e' stato fortunato, e i prezzi sono tarati per essere
 * cari: comprare deve restare la seconda scelta, o i Gate diventano un bancomat.
 */
public record ShopConfig(
		/** Quante voci ha l'assortimento. */
		int offerCount,
		/** Quante di quelle voci sono sigillate: si compra il rango, non il pezzo. */
		int sealedCount,
		/** Ogni quanti tick l'assortimento cambia da solo. 24000 = un giorno di gioco. */
		int rotationTicks,
		/** Prezzo di un pezzo di rango E in uno slot da moltiplicatore 1. */
		double priceBase,
		/** Quanto costa un sigillato rispetto a un pezzo visibile dello stesso rango. */
		double sealedFactor,
		/** Di quanto puo' scostarsi il prezzo di una singola voce (0,15 = ±15%). */
		double priceJitter,
		/** Prezzo del primo ritiro dell'assortimento dentro la stessa rotazione. */
		double refreshBase,
		/** Di quanto si moltiplica il prezzo del ritiro a ogni ritiro successivo. */
		double refreshGrowth,
		/** Probabilita' che una voce salga di un rango sopra quello del Cacciatore. */
		double rankUpChance,
		/** Probabilita' che una voce scenda di un rango sotto quello del Cacciatore. */
		double rankDownChance) {

	public static final Codec<ShopConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("offer_count").forGetter(ShopConfig::offerCount),
			Codec.INT.fieldOf("sealed_count").forGetter(ShopConfig::sealedCount),
			Codec.INT.fieldOf("rotation_ticks").forGetter(ShopConfig::rotationTicks),
			Codec.DOUBLE.fieldOf("price_base").forGetter(ShopConfig::priceBase),
			Codec.DOUBLE.fieldOf("sealed_factor").forGetter(ShopConfig::sealedFactor),
			Codec.DOUBLE.fieldOf("price_jitter").forGetter(ShopConfig::priceJitter),
			Codec.DOUBLE.fieldOf("refresh_base").forGetter(ShopConfig::refreshBase),
			Codec.DOUBLE.fieldOf("refresh_growth").forGetter(ShopConfig::refreshGrowth),
			Codec.DOUBLE.fieldOf("rank_up_chance").forGetter(ShopConfig::rankUpChance),
			Codec.DOUBLE.fieldOf("rank_down_chance").forGetter(ShopConfig::rankDownChance)
	).apply(instance, ShopConfig::new));

	public static final ShopConfig DEFAULT =
			new ShopConfig(6, 2, 24000, 900.0, 0.8, 0.15, 400.0, 1.8, 0.18, 0.32);

	/** Il prezzo del prossimo ritiro, dato quanti se ne sono gia' fatti in questa rotazione. */
	public long refreshPrice(int refreshes) {
		return Math.max(1L, (long) (refreshBase * Math.pow(refreshGrowth, refreshes)));
	}
}
