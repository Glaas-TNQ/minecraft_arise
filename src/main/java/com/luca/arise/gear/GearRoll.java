package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.config.GearConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;

import net.minecraft.util.RandomSource;

/**
 * L'estrazione di un pezzo di equipaggiamento.
 *
 * <p>Un pezzo non viene disegnato, viene <em>tirato</em>: base × rango × slot danno un budget di
 * potenza, il budget si divide fra i modificatori che il rango concede, e ogni quota diventa un
 * valore vero passando per il cambio della statistica. Varieta' infinita a costo zero di asset
 * (design §8.1).
 *
 * <p>Il tiro accetta un {@link RandomSource} dall'esterno invece di crearselo: con un seed noto
 * l'assortimento di un negozio o il bottino di un Gate diventano riproducibili, esattamente come
 * gia' succede per {@code GateOffer}.
 */
public final class GearRoll {

	private GearRoll() {
	}

	/** Un pezzo per lo slot e il rango dati. */
	public static GearPiece roll(GearConfig config, GearSlot slot, Rank rank, RandomSource random) {
		List<GearBase> candidates = GearBase.of(slot);
		GearBase base = candidates.get(random.nextInt(candidates.size()));

		List<Stat> picked = pickStats(config, base, config.affixes(rank), random);
		List<Double> shares = shares(config, config.power(rank, slot), picked.size(), random);

		EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);
		for (int i = 0; i < picked.size(); i++) {
			Stat stat = picked.get(i);
			double value = round(shares.get(i) * config.step(stat));

			// Un modificatore che arrotonda a zero non va scritto: sarebbe una riga vuota nella
			// descrizione del pezzo, e il giocatore la leggerebbe come un difetto.
			if (value != 0.0) {
				stats.merge(stat, value, Double::sum);
			}
		}

		return new GearPiece(UUID.randomUUID(), base, rank, GearAffix.of(picked.getFirst()),
				stats, config.sockets(rank));
	}

	/** Un pezzo di rango dato in uno slot qualsiasi: e' la forma che serve al bottino. */
	public static GearPiece rollAny(GearConfig config, Rank rank, RandomSource random) {
		GearSlot[] slots = GearSlot.values();
		return roll(config, slots[random.nextInt(slots.length)], rank, random);
	}

	/**
	 * Sceglie le statistiche, senza ripetizioni.
	 *
	 * <p>La prima estratta diventa il titolo del pezzo e prende la quota piu' grossa: e' quello che
	 * rende "Anello del Titano" una promessa e non una decorazione.
	 */
	private static List<Stat> pickStats(GearConfig config, GearBase base, int count, RandomSource random) {
		List<Stat> pool = new ArrayList<>();
		List<Double> weights = new ArrayList<>();

		for (Stat stat : Stat.values()) {
			// Una statistica senza cambio non puo' diventare un valore: escluderla qui evita di
			// sprecare un modificatore su una riga che varrebbe zero.
			if (config.step(stat) <= 0.0) {
				continue;
			}

			pool.add(stat);
			weights.add(base.affinities().contains(stat) ? config.affinityWeight() : 1.0);
		}

		List<Stat> chosen = new ArrayList<>();
		int wanted = Math.min(count, pool.size());

		while (chosen.size() < wanted) {
			int index = weightedIndex(weights, random);
			chosen.add(pool.get(index));
			pool.remove(index);
			weights.remove(index);
		}

		return chosen;
	}

	private static int weightedIndex(List<Double> weights, RandomSource random) {
		double total = 0.0;
		for (double weight : weights) {
			total += weight;
		}

		double target = random.nextDouble() * total;
		for (int i = 0; i < weights.size(); i++) {
			target -= weights.get(i);
			if (target <= 0.0) {
				return i;
			}
		}

		return weights.size() - 1;
	}

	/**
	 * Divide il budget in quote, ordinate dalla piu' grossa alla piu' piccola.
	 *
	 * <p>Le quote sono perturbate e poi <em>rinormalizzate</em>: senza la rinormalizzazione due
	 * pezzi dello stesso rango potrebbero valere complessivamente il 30% l'uno piu' dell'altro, e
	 * il rango smetterebbe di voler dire qualcosa.
	 */
	private static List<Double> shares(GearConfig config, double budget, int count, RandomSource random) {
		List<Double> raw = new ArrayList<>(count);
		double sum = 0.0;

		for (int i = 0; i < count; i++) {
			double jitter = 1.0 + (random.nextDouble() * 2.0 - 1.0) * config.affixVariance();
			raw.add(jitter);
			sum += jitter;
		}

		List<Double> result = new ArrayList<>(count);
		for (double value : raw) {
			result.add(budget * value / sum);
		}

		result.sort((a, b) -> Double.compare(b, a));
		return result;
	}

	/** Tre decimali: abbastanza per le percentuali, non abbastanza per mostrare 0,30000000000004. */
	private static double round(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}
}
