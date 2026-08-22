package com.luca.arise.config;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Bilanciamento dell'esercito d'ombra.
 *
 * <p>Sotto-oggetto {@code "shadows"} di {@code config/arise.json}. È un blocco a sé perché il codec
 * di un record ha un tetto di 16 campi; per la stessa ragione il livellamento è annidato ancora
 * più sotto, in {@link Leveling}.
 *
 * <p>I campi sono <strong>obbligatori</strong> di proposito: le chiavi mancanti in un file vecchio
 * vengono riempite dai default prima del parsing, in {@code AriseConfig.withDefaults}. Con
 * {@code optionalFieldOf} il codec le accetterebbe in lettura ma le ometterebbe in scrittura
 * quando coincidono col default, e resterebbero invisibili nel file — quindi non modificabili.
 */
public record ShadowConfig(
		/** Probabilità base di estrarre l'ombra da un nemico ucciso. */
		double extractionChanceBase,
		/** Quanto la probabilità cresce per ogni livello del giocatore. */
		double extractionChancePerLevel,
		/** Per quanti tick un cadavere resta estraibile. */
		int extractionWindowTicks,
		/** Entro quanti blocchi si può estrarre un'ombra. */
		double extractionRange,
		/** Ombre conservabili al livello 1. */
		int baseCapacity,
		/** Capienza aggiuntiva per livello. */
		double capacityPerLevel,
		/** Quante ombre possono stare evocate insieme. Limite tecnico oltre che di design. */
		int maxSummoned,
		/** Vita dell'ombra = vita massima del mob d'origine × questo. */
		double healthFactor,
		/** Danno dell'ombra = danno stimato del mob d'origine × questo. */
		double damageFactor,
		/** Velocità di movimento dell'ombra evocata. */
		double movementSpeed,
		/** Raggio entro cui l'ombra cerca bersagli. */
		double followRange,
		/** Punteggio minimo per ciascun rango, da E a S. */
		List<Double> rankThresholds,
		/** Crescita delle ombre con l'esperienza. */
		Leveling leveling,
		/** Quanto costano in soul coin le operazioni sull'esercito. */
		Costs costs) {

	/** Prezzi in soul coin. Annidati per non sfondare il tetto di 16 campi del codec. */
	public record Costs(
			/** Costo base per far salire un'ombra di un livello. */
			long upgradeBase,
			/** Quanto il costo cresce per ogni livello già raggiunto. */
			long upgradePerLevel,
			/** Costo di un rinominare. */
			long rename,
			/** Costo di un cambio colore. */
			long recolor,
			/** Percentuale del valore restituita quando si congeda un'ombra. */
			double dismissRefund) {

		public static final Costs DEFAULT = new Costs(50L, 25L, 20L, 30L, 0.25);

		public static final Codec<Costs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("upgrade_base").forGetter(Costs::upgradeBase),
				Codec.LONG.fieldOf("upgrade_per_level").forGetter(Costs::upgradePerLevel),
				Codec.LONG.fieldOf("rename").forGetter(Costs::rename),
				Codec.LONG.fieldOf("recolor").forGetter(Costs::recolor),
				Codec.DOUBLE.fieldOf("dismiss_refund").forGetter(Costs::dismissRefund)
		).apply(instance, Costs::new));

		/** Costo per portare un'ombra dal livello indicato a quello successivo. */
		public long upgradeCost(int level) {
			return upgradeBase + upgradePerLevel * (level - 1);
		}
	}

	/** Come crescono le ombre combattendo. */
	public record Leveling(
			/** XP necessaria a un'ombra per passare dal livello 1 al 2. */
			double xpBase,
			/** Esponente della curva. */
			double xpExponent,
			/** Quota dell'XP di un'uccisione che va alle ombre evocate che non hanno colpito. */
			double xpShare,
			/** Crescita percentuale delle statistiche per ogni livello dell'ombra. */
			double statGrowthPerLevel,
			/** Livello massimo di un'ombra. */
			int maxLevel) {

		public static final Leveling DEFAULT = new Leveling(30.0, 1.4, 0.5, 0.08, 50);

		public static final Codec<Leveling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("xp_base").forGetter(Leveling::xpBase),
				Codec.DOUBLE.fieldOf("xp_exponent").forGetter(Leveling::xpExponent),
				Codec.DOUBLE.fieldOf("xp_share").forGetter(Leveling::xpShare),
				Codec.DOUBLE.fieldOf("stat_growth_per_level")
						.forGetter(Leveling::statGrowthPerLevel),
				Codec.INT.fieldOf("max_level").forGetter(Leveling::maxLevel)
		).apply(instance, Leveling::new));

		/** XP necessaria per passare da questo livello al successivo. */
		public long xpForNextLevel(int level) {
			return Math.max(1L, (long) (xpBase * Math.pow(level, xpExponent)));
		}

		/** Moltiplicatore delle statistiche a questo livello. */
		public double statMultiplier(int level) {
			return 1.0 + statGrowthPerLevel * (level - 1);
		}
	}

	public static final ShadowConfig DEFAULT = new ShadowConfig(
			0.25, 0.005, 300, 8.0, 6, 0.25, 4, 1.5, 1.2, 0.32, 32.0,
			List.of(0.0, 30.0, 60.0, 110.0, 180.0, 280.0), Leveling.DEFAULT, Costs.DEFAULT);

	public static final Codec<ShadowConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("extraction_chance_base")
					.forGetter(ShadowConfig::extractionChanceBase),
			Codec.DOUBLE.fieldOf("extraction_chance_per_level")
					.forGetter(ShadowConfig::extractionChancePerLevel),
			Codec.INT.fieldOf("extraction_window_ticks")
					.forGetter(ShadowConfig::extractionWindowTicks),
			Codec.DOUBLE.fieldOf("extraction_range")
					.forGetter(ShadowConfig::extractionRange),
			Codec.INT.fieldOf("base_capacity")
					.forGetter(ShadowConfig::baseCapacity),
			Codec.DOUBLE.fieldOf("capacity_per_level")
					.forGetter(ShadowConfig::capacityPerLevel),
			Codec.INT.fieldOf("max_summoned")
					.forGetter(ShadowConfig::maxSummoned),
			Codec.DOUBLE.fieldOf("health_factor")
					.forGetter(ShadowConfig::healthFactor),
			Codec.DOUBLE.fieldOf("damage_factor")
					.forGetter(ShadowConfig::damageFactor),
			Codec.DOUBLE.fieldOf("movement_speed")
					.forGetter(ShadowConfig::movementSpeed),
			Codec.DOUBLE.fieldOf("follow_range")
					.forGetter(ShadowConfig::followRange),
			Codec.DOUBLE.listOf().fieldOf("rank_thresholds")
					.forGetter(ShadowConfig::rankThresholds),
			Leveling.CODEC.fieldOf("leveling")
					.forGetter(ShadowConfig::leveling),
			Costs.CODEC.fieldOf("costs")
					.forGetter(ShadowConfig::costs)
	).apply(instance, ShadowConfig::new));

	public ShadowConfig {
		rankThresholds = List.copyOf(rankThresholds);
	}

	/** Quante ombre può conservare un giocatore di questo livello. */
	public int capacityAt(int level) {
		return baseCapacity + (int) (capacityPerLevel * (level - 1));
	}

	/** Probabilità di estrazione per un giocatore di questo livello, limitata al 95%. */
	public double extractionChanceAt(int level) {
		return Math.min(0.95, extractionChanceBase + extractionChancePerLevel * (level - 1));
	}
}
