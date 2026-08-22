package com.luca.arise.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.luca.arise.gem.GemType;
import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Quanto vale una gemma.
 *
 * <p>Il tetto per tipo e' la voce piu' importante del file. Dieci anelli di rango S, quattro
 * incastonature ciascuno, tutte di ematite: senza un tetto, il furto di vita arriverebbe al cento
 * per cento e il gioco finirebbe li'. Il tetto non e' bilanciamento fine, e' la garanzia che
 * nessuna combinazione possa rompere una regola.
 */
public record GemConfig(
		/** Potenza di una gemma, per rango. */
		List<Double> powerPerRank,
		/** Quanto vale un punto di potenza per ciascun effetto. */
		Map<GemType, Double> stepPerPower,
		/** Il massimo che un effetto puo' raggiungere sommando tutte le gemme indossate. */
		Map<GemType, Double> cap,
		/** Quanta della potenza di una gemma va in statistiche invece che nell'effetto. */
		double statShare,
		/** Quanti modificatori estrae una gemma. */
		int statCount,
		/** Quante gemme sciolte si possono tenere da parte. */
		int pouchSize,
		/** Soul coin per estrarre una gemma intatta al banco dell'Associazione. */
		double extractCost,
		/** Entro quanti blocchi dal segnaposto di una citta' il banco e' raggiungibile. */
		int benchRadius) {

	public static final Codec<GemConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.listOf().fieldOf("power_per_rank").forGetter(GemConfig::powerPerRank),
			Codec.unboundedMap(GemType.CODEC, Codec.DOUBLE).fieldOf("step_per_power")
					.forGetter(GemConfig::stepPerPower),
			Codec.unboundedMap(GemType.CODEC, Codec.DOUBLE).fieldOf("cap").forGetter(GemConfig::cap),
			Codec.DOUBLE.fieldOf("stat_share").forGetter(GemConfig::statShare),
			Codec.INT.fieldOf("stat_count").forGetter(GemConfig::statCount),
			Codec.INT.fieldOf("pouch_size").forGetter(GemConfig::pouchSize),
			Codec.DOUBLE.fieldOf("extract_cost").forGetter(GemConfig::extractCost),
			Codec.INT.fieldOf("bench_radius").forGetter(GemConfig::benchRadius)
	).apply(instance, GemConfig::new));

	public static final GemConfig DEFAULT = createDefault();

	public GemConfig {
		powerPerRank = List.copyOf(powerPerRank);
		stepPerPower = Map.copyOf(stepPerPower);
		cap = Map.copyOf(cap);
	}

	private static GemConfig createDefault() {
		Map<GemType, Double> step = new EnumMap<>(GemType.class);
		step.put(GemType.HEMATITE, 0.004);   // +0,4% di furto di vita per punto di potenza
		step.put(GemType.OBSIDIAN, 0.003);
		step.put(GemType.SAPPHIRE, 0.012);
		step.put(GemType.AMETHYST, 0.012);
		step.put(GemType.JASPER, 0.008);

		Map<GemType, Double> cap = new EnumMap<>(GemType.class);
		// Un quarto del danno che torna e' gia' tantissimo; oltre, non si muore piu'.
		cap.put(GemType.HEMATITE, 0.25);
		// L'estrazione automatica non deve mai sostituire il gesto di estrarre.
		cap.put(GemType.OBSIDIAN, 0.30);
		cap.put(GemType.SAPPHIRE, 1.50);
		cap.put(GemType.AMETHYST, 1.50);
		cap.put(GemType.JASPER, 0.60);

		return new GemConfig(List.of(1.0, 1.5, 2.2, 3.2, 4.6, 6.5), step, cap, 6.0, 1, 40, 750.0, 24);
	}

	/** La potenza grezza di una gemma di questo rango. */
	public double power(Rank rank) {
		if (powerPerRank.isEmpty()) {
			return 1.0;
		}

		return powerPerRank.get(Math.min(rank.ordinal(), powerPerRank.size() - 1));
	}

	/** Quanto vale l'effetto di <em>una</em> gemma. Il tetto si applica alla somma, non qui. */
	public double magnitude(GemType type, Rank rank) {
		return stepPerPower.getOrDefault(type, 0.0) * power(rank);
	}

	public double cap(GemType type) {
		return cap.getOrDefault(type, Double.MAX_VALUE);
	}
}
