package com.luca.arise.config;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.ability.Ability;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Il Mana: quanto se ne ha, quanto in fretta torna, quanto costa ogni cosa.
 *
 * <p>Il Mana esiste per una ragione sola, ed e' scritta qui perche' i numeri si tarino guardandola:
 * <strong>prima non c'era nessun prezzo per evocare</strong>. Il tetto delle evocazioni diceva
 * quante ombre potessero stare in campo insieme, non quante volte si potesse chiamarle — e le
 * ombre richiamate e rievocate all'infinito rendevano ogni altra decisione irrilevante. Il Mana e'
 * il costo che mancava: la riserva scende quando si chiama l'esercito, quando si lancia
 * un'abilita' e per tutto il tempo che si resta in volo, e risale da sola stando tranquilli.
 *
 * <p>La taratura di partenza: cento punti al primo livello e dieci per ogni livello dopo, sei al
 * secondo di rigenerazione, e un'ombra che ne costa venticinque. Un Cacciatore all'esordio ne
 * chiama quattro e poi aspetta una ventina di secondi; a livello venti ne chiama nove. Il tetto
 * delle evocazioni resta dov'e' — dice quante ne stanno in campo — ma adesso ha un prezzo davanti.
 */
public record ManaConfig(
		/** Mana al primo livello. */
		int base,
		/** Quanto se ne aggiunge a ogni livello oltre il primo. */
		int perLevel,
		/**
		 * Punti rigenerati al secondo.
		 *
		 * <p>Un decimale, e i decimi non si perdono: la rigenerazione si calcola sul tempo passato
		 * dall'ultimo conteggio, non sommando un pezzetto a ogni battito. Vedi {@code Mana}.
		 */
		double regenPerSecond,
		/**
		 * Quanti tick la rigenerazione resta ferma dopo una spesa.
		 *
		 * <p>E' cio' che rende il Mana una risorsa e non un ritardo: senza la pausa, spendere e
		 * riprendere fiato sono la stessa cosa, e chi evoca in continuazione paga solo qualche
		 * decimo di secondo. Due secondi.
		 */
		int pauseTicks,
		/** Quanto costa chiamare una singola ombra. */
		int summonCost,
		/**
		 * Il costo di ciascuna abilita'.
		 *
		 * <p>In una mappa e non in un campo per ciascuna, come i tempi di ricarica: un'abilita'
		 * nuova non tocca il codec, e chi bilancia vede la famiglia in fila.
		 */
		Map<Ability, Integer> abilityCosts,
		/** Quanto costa un secondo di volo. */
		int flightCostPerSecond,
		/** Sotto quanto Mana il volo non si accende nemmeno: partire per cadere subito e' peggio. */
		int flightFloor) {

	private static Map<Ability, Integer> defaultCosts() {
		Map<Ability, Integer> costs = new EnumMap<>(Ability.class);
		costs.put(Ability.SHADOW_STEP, 10);           // mobilita': deve restare quasi gratuita
		costs.put(Ability.SHADOW_EXCHANGE, 20);
		costs.put(Ability.MONARCH_DOMAIN, 45);
		costs.put(Ability.SOVEREIGN_AUTHORITY, 60);
		costs.put(Ability.SHADOW_FLIGHT, 20);         // solo per accendersi: il resto si paga in aria
		return costs;
	}

	public static final ManaConfig DEFAULT =
			new ManaConfig(100, 10, 6.0, 40, 25, defaultCosts(), 8, 15);

	public static final Codec<ManaConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("base").forGetter(ManaConfig::base),
			Codec.INT.fieldOf("per_level").forGetter(ManaConfig::perLevel),
			Codec.DOUBLE.fieldOf("regen_per_second").forGetter(ManaConfig::regenPerSecond),
			Codec.INT.fieldOf("pause_ticks").forGetter(ManaConfig::pauseTicks),
			Codec.INT.fieldOf("summon_cost").forGetter(ManaConfig::summonCost),
			Codec.unboundedMap(Ability.CODEC, Codec.INT).fieldOf("ability_costs")
					.forGetter(ManaConfig::abilityCosts),
			Codec.INT.fieldOf("flight_cost_per_second").forGetter(ManaConfig::flightCostPerSecond),
			Codec.INT.fieldOf("flight_floor").forGetter(ManaConfig::flightFloor)
	).apply(instance, ManaConfig::new));

	public ManaConfig {
		abilityCosts = Map.copyOf(abilityCosts);
	}

	/** Il Mana massimo a questo livello. */
	public int max(int level) {
		return Math.max(1, base + perLevel * Math.max(0, level - 1));
	}

	public int cost(Ability ability) {
		return abilityCosts.getOrDefault(ability, DEFAULT.abilityCosts().getOrDefault(ability, 0));
	}
}
