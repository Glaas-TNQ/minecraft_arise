package com.luca.arise.config;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.workshop.SoulTrait;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * L'Officina delle Anime: quanto vanno veloci i macchinari e quanto rendono.
 *
 * <p>Un solo numero governa tutte e quattro le macchine — {@code speedPerVigor} — e non e' una
 * semplificazione: e' la promessa che il gioco fa al giocatore. <em>Anime migliori dentro, lavoro
 * piu' veloce fuori</em>, sempre, con la stessa curva ovunque. Se ogni macchina avesse la sua
 * regola, capire dove conviene mettere l'anima di rango A diventerebbe un esercizio di lettura
 * della config invece che una decisione di gioco.
 *
 * @param speedPerVigor   quanta parte del tempo di lavoro toglie ogni punto di vigore installato
 * @param minSpeedFactor  il pavimento: sotto questa frazione del tempo base non si scende mai
 * @param traitPower      quanto vale ciascun tratto, in una mappa sola perche' si leggono insieme
 */
public record WorkshopConfig(
		/** L'interruttore generale: a falso i macchinari restano blocchi decorativi. */
		boolean enabled,
		/** Tick fra un'anima e la successiva nel Richiamo, prima del vigore. */
		int lureIntervalTicks,
		/** Tick di una fusione nel Crogiolo. */
		int crucibleIntervalTicks,
		/** Tick di una lavorazione nella Fucina. */
		int forgeIntervalTicks,
		/** Tick di un giro del Pozzo. */
		int wellIntervalTicks,
		double speedPerVigor,
		double minSpeedFactor,
		/** Soul coin per punto di vigore, a ogni giro del Pozzo. */
		double wellCoinsPerVigor,
		/** Probabilita' che un giro del Pozzo lasci anche un catalizzatore. */
		double wellCatalystChance,
		/** Quanta potenza dell'anima prodotta viene dal vigore installato nel Richiamo. */
		double lurePowerPerVigor,
		/** Se un'estrazione riuscita a esercito pieno lascia comunque l'anima come oggetto. */
		boolean overflowSoul,
		/** Se congedare un'ombra restituisce la sua anima, oltre ai soul coin. */
		boolean dismissReturnsSoul,
		Map<SoulTrait, Double> traitPower) {

	public static final Codec<WorkshopConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("enabled").forGetter(WorkshopConfig::enabled),
			Codec.INT.fieldOf("lure_interval_ticks").forGetter(WorkshopConfig::lureIntervalTicks),
			Codec.INT.fieldOf("crucible_interval_ticks").forGetter(WorkshopConfig::crucibleIntervalTicks),
			Codec.INT.fieldOf("forge_interval_ticks").forGetter(WorkshopConfig::forgeIntervalTicks),
			Codec.INT.fieldOf("well_interval_ticks").forGetter(WorkshopConfig::wellIntervalTicks),
			Codec.DOUBLE.fieldOf("speed_per_vigor").forGetter(WorkshopConfig::speedPerVigor),
			Codec.DOUBLE.fieldOf("min_speed_factor").forGetter(WorkshopConfig::minSpeedFactor),
			Codec.DOUBLE.fieldOf("well_coins_per_vigor").forGetter(WorkshopConfig::wellCoinsPerVigor),
			Codec.DOUBLE.fieldOf("well_catalyst_chance").forGetter(WorkshopConfig::wellCatalystChance),
			Codec.DOUBLE.fieldOf("lure_power_per_vigor").forGetter(WorkshopConfig::lurePowerPerVigor),
			Codec.BOOL.fieldOf("overflow_soul").forGetter(WorkshopConfig::overflowSoul),
			Codec.BOOL.fieldOf("dismiss_returns_soul").forGetter(WorkshopConfig::dismissReturnsSoul),
			Codec.unboundedMap(SoulTrait.CODEC, Codec.DOUBLE).fieldOf("trait_power")
					.forGetter(WorkshopConfig::traitPower)
	).apply(instance, WorkshopConfig::new));

	public static final WorkshopConfig DEFAULT = createDefault();

	public WorkshopConfig {
		traitPower = Map.copyOf(traitPower);
	}

	private static WorkshopConfig createDefault() {
		Map<SoulTrait, Double> traits = new EnumMap<>(SoulTrait.class);
		traits.put(SoulTrait.ARDORE, 0.25);      // -25% sul tempo di lavoro
		traits.put(SoulTrait.AVIDITA, 0.50);     // +50% soul coin dal Pozzo
		traits.put(SoulTrait.TENACIA, 0.25);     // 25% di raddoppiare l'uscita
		traits.put(SoulTrait.RISONANZA, 0.30);   // 30% di non consumare il catalizzatore
		traits.put(SoulTrait.FEROCIA, 0.20);     // +20% danno da ombra arruolata

		// Trenta secondi per un'anima nuova, dieci per una fusione, otto per una lavorazione,
		// venti per un giro di Pozzo. Sono tempi lunghi di proposito: un'officina che produce
		// piu' in fretta di quanto si combatte trasformerebbe i Gate in un passatempo.
		return new WorkshopConfig(true, 600, 200, 160, 400,
				0.010, 0.25, 0.5, 0.08, 0.35, true, true, traits);
	}

	public double traitPower(SoulTrait trait) {
		return traitPower.getOrDefault(trait, 0.0);
	}

	/**
	 * Quanto dura un giro di lavoro, dato il vigore installato e i tratti presenti.
	 *
	 * <p>Il vigore accorcia, l'Ardore accorcia ancora, il pavimento impedisce a entrambi di
	 * arrivare a zero. Senza il pavimento, un'officina abbastanza avanzata produrrebbe un oggetto
	 * per tick e il server passerebbe la giornata a svuotarla.
	 */
	public int workTicks(int baseTicks, double vigor, boolean ardore) {
		double factor = 1.0 - speedPerVigor * vigor;
		if (ardore) {
			factor -= traitPower(SoulTrait.ARDORE);
		}

		return Math.max(1, (int) (baseTicks * Math.max(minSpeedFactor, factor)));
	}
}
