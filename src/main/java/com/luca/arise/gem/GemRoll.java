package com.luca.arise.gem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import com.luca.arise.config.GearConfig;
import com.luca.arise.config.GemConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;

import net.minecraft.util.RandomSource;

/**
 * L'estrazione di una gemma.
 *
 * <p>Piu' semplice di quella di un pezzo, e di proposito: una gemma si sceglie per il suo effetto,
 * non per i suoi numeri. Le statistiche che porta sono un contorno, quindi ne esce una sola, presa
 * fra le affinita' del tipo.
 */
public final class GemRoll {

	private GemRoll() {
	}

	public static Gem roll(GemConfig gems, GearConfig gear, GemType type, Rank rank,
			RandomSource random) {
		double budget = gems.power(rank) * gems.statShare();

		List<Stat> pool = new ArrayList<>(type.affinities());
		EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);

		int wanted = Math.min(Math.max(1, gems.statCount()), pool.size());
		double share = budget / wanted;

		for (int i = 0; i < wanted; i++) {
			Stat stat = pool.remove(random.nextInt(pool.size()));
			double value = Math.round(share * gear.step(stat) * 1000.0) / 1000.0;

			if (value != 0.0) {
				stats.put(stat, value);
			}
		}

		return new Gem(UUID.randomUUID(), type, rank, stats);
	}

	/** Una gemma di tipo qualsiasi: e' la forma che serve al bottino dei boss. */
	public static Gem rollAny(GemConfig gems, GearConfig gear, Rank rank, RandomSource random) {
		GemType[] types = GemType.values();
		return roll(gems, gear, types[random.nextInt(types.length)], rank, random);
	}
}
