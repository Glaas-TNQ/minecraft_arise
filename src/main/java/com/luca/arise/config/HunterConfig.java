package com.luca.arise.config;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Il Cacciatore: il suo rango e quello che si porta addosso.
 *
 * <p>E' un contenitore, e ha una ragione pratica di esistere oltre a quella di lettura: il codec
 * di un record si ferma a sedici campi, e {@link AriseConfig} e' gia' li'. Tutto quello che
 * riguarda il Cacciatore — oggi rango ed equipaggiamento, domani il negozio e le gemme — entra qui
 * dentro invece di allargare la radice.
 */
public record HunterConfig(
		/** Il livello a cui si entra in ciascun rango, da E a S. */
		List<Integer> rankLevels,
		/** Quanto vale un pezzo di equipaggiamento. */
		GearConfig gear,
		/** Prezzi e rotazione dell'Abyss Shop. */
		ShopConfig shop,
		/** Effetti e tetti delle gemme. */
		GemConfig gems) {

	public static final Codec<HunterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.listOf().fieldOf("rank_levels").forGetter(HunterConfig::rankLevels),
			GearConfig.CODEC.fieldOf("gear").forGetter(HunterConfig::gear),
			ShopConfig.CODEC.fieldOf("shop").forGetter(HunterConfig::shop),
			GemConfig.CODEC.fieldOf("gems").forGetter(HunterConfig::gems)
	).apply(instance, HunterConfig::new));

	public static final HunterConfig DEFAULT =
			new HunterConfig(List.of(1, 10, 20, 35, 55, 80),
					GearConfig.DEFAULT, ShopConfig.DEFAULT, GemConfig.DEFAULT);

	public HunterConfig {
		rankLevels = List.copyOf(rankLevels);
	}

	/**
	 * Il rango di un Cacciatore di questo livello.
	 *
	 * <p>Passa da {@link Rank#fromScore}, che e' gia' la regola usata per i ranghi delle ombre:
	 * una sola idea di "soglie ordinate" in tutta la mod, e tollerante se la lista in config e'
	 * corta o lunga.
	 */
	public Rank rank(int level) {
		List<Double> thresholds = new ArrayList<>(rankLevels.size());
		for (int required : rankLevels) {
			thresholds.add((double) required);
		}

		return Rank.fromScore(level, thresholds);
	}

	/** Il livello che serve per entrare in un rango, per i messaggi. */
	public int levelFor(Rank rank) {
		return rank.ordinal() < rankLevels.size() ? rankLevels.get(rank.ordinal()) : Integer.MAX_VALUE;
	}
}
