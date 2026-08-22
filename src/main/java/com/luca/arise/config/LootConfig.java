package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Cosa lascia un Gate completato.
 *
 * <p>I Gate sono la fonte <em>principale</em> dell'equipaggiamento (design §8.5). L'Abyss Shop e'
 * la rete di sicurezza di chi non e' stato fortunato, e i suoi prezzi sono tarati per restare la
 * seconda scelta: se il bottino qui fosse avaro, comprare diventerebbe la prima, e i Gate un
 * bancomat.
 *
 * <p>I conteggi sono numeri con la virgola apposta: la parte intera e' garantita, la parte
 * decimale e' la probabilita' di un pezzo in piu'. Cosi' "1,5 pezzi" vuol dire davvero uno e mezzo
 * in media, e la curva fra un rango e il successivo resta liscia invece di saltare a scalini.
 */
public record LootConfig(
		/** Pezzi di equipaggiamento a un Gate di rango E. */
		double pieces,
		/** Pezzi in piu' per ogni rango sopra E. */
		double piecesPerRank,
		/** Probabilita' di una gemma a un Gate di rango E. */
		double gemChance,
		/** Quanto cresce quella probabilita' a ogni rango. */
		double gemChancePerRank,
		/** Punti statistica della pergamena a un Gate di rango E. */
		double scrollPoints,
		/** Punti in piu' per ogni rango sopra E. */
		double scrollPointsPerRank,
		/** Probabilita' che un singolo pezzo esca di un rango sopra quello del Gate. */
		double upgradeChance) {

	public static final Codec<LootConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("pieces").forGetter(LootConfig::pieces),
			Codec.DOUBLE.fieldOf("pieces_per_rank").forGetter(LootConfig::piecesPerRank),
			Codec.DOUBLE.fieldOf("gem_chance").forGetter(LootConfig::gemChance),
			Codec.DOUBLE.fieldOf("gem_chance_per_rank").forGetter(LootConfig::gemChancePerRank),
			Codec.DOUBLE.fieldOf("scroll_points").forGetter(LootConfig::scrollPoints),
			Codec.DOUBLE.fieldOf("scroll_points_per_rank").forGetter(LootConfig::scrollPointsPerRank),
			Codec.DOUBLE.fieldOf("upgrade_chance").forGetter(LootConfig::upgradeChance)
	).apply(instance, LootConfig::new));

	public static final LootConfig DEFAULT = new LootConfig(1.4, 0.5, 0.35, 0.12, 0.6, 0.35, 0.12);

	/** Quanti pezzi, dato il rango. La parte decimale resta come probabilita' del pezzo in piu'. */
	public double pieceCount(int rankOrdinal) {
		return Math.max(0.0, pieces + piecesPerRank * rankOrdinal);
	}

	public double gemChanceAt(int rankOrdinal) {
		return Math.clamp(gemChance + gemChancePerRank * rankOrdinal, 0.0, 1.0);
	}

	public double scrollPointsAt(int rankOrdinal) {
		return Math.max(0.0, scrollPoints + scrollPointsPerRank * rankOrdinal);
	}
}
