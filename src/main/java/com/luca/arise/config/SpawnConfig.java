package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Quando e dove i Gate si aprono da soli.
 *
 * <p>Finche' un varco esisteva solo scrivendo {@code /arise gate}, nel mondo non succedeva mai
 * niente da solo — ed e' la differenza fra una mod e un gioco. Questi numeri sono quella
 * differenza, quindi vale doppio che stiano in config e non nel codice.
 */
public record SpawnConfig(
		/** Se i varchi si aprono da soli. Spegnerlo lascia solo quelli evocati a comando. */
		boolean enabled,
		/** Ogni quanti tick si tira il dado, per giocatore. */
		int checkIntervalTicks,
		/** Probabilita' che il dado dia un varco. */
		double chancePerCheck,
		/** Quanti varchi possono coesistere nel raggio di un giocatore. */
		int maxNearby,
		/** Entro quale raggio si contano, e oltre il quale se ne puo' aprire un altro. */
		int nearbyRadius,
		/** Distanza minima dal giocatore. Sotto, sarebbe un agguato. */
		int minDistance,
		/** Distanza massima. Oltre, nessuno andrebbe a vedere. */
		int maxDistance,
		/** Quanto vive un varco spontaneo prima di richiudersi. 6000 = cinque minuti. */
		int lifetimeTicks,
		/** Probabilita' che il varco sia di un rango sopra quello del Cacciatore. */
		double rankUpChance,
		/** Probabilita' che sia di un rango sotto. */
		double rankDownChance,
		/** Quanti tentativi di posizionamento prima di rinunciare a questo giro. */
		int placementAttempts,
		/** Cosa succede a un varco che nessuno chiude, e quali varchi si sigillano. */
		Hazard hazard) {

	public static final Codec<SpawnConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("enabled").forGetter(SpawnConfig::enabled),
			Codec.INT.fieldOf("check_interval_ticks").forGetter(SpawnConfig::checkIntervalTicks),
			Codec.DOUBLE.fieldOf("chance_per_check").forGetter(SpawnConfig::chancePerCheck),
			Codec.INT.fieldOf("max_nearby").forGetter(SpawnConfig::maxNearby),
			Codec.INT.fieldOf("nearby_radius").forGetter(SpawnConfig::nearbyRadius),
			Codec.INT.fieldOf("min_distance").forGetter(SpawnConfig::minDistance),
			Codec.INT.fieldOf("max_distance").forGetter(SpawnConfig::maxDistance),
			Codec.INT.fieldOf("lifetime_ticks").forGetter(SpawnConfig::lifetimeTicks),
			Codec.DOUBLE.fieldOf("rank_up_chance").forGetter(SpawnConfig::rankUpChance),
			Codec.DOUBLE.fieldOf("rank_down_chance").forGetter(SpawnConfig::rankDownChance),
			Codec.INT.fieldOf("placement_attempts").forGetter(SpawnConfig::placementAttempts),
			Hazard.CODEC.fieldOf("hazard").forGetter(SpawnConfig::hazard)
	).apply(instance, SpawnConfig::new));

	/**
	 * Un varco ogni pochi minuti a chi gioca normalmente.
	 *
	 * <p>Il dado si tira ogni trenta secondi con una probabilita' del sei per cento: in media un
	 * varco ogni otto minuti e mezzo. Il tetto di due nel raggio di duecento blocchi impedisce che
	 * una sessione lunga in un posto solo diventi un campo di varchi.
	 */
	public static final SpawnConfig DEFAULT =
			new SpawnConfig(true, 600, 0.06, 2, 200, 48, 160, 6000, 0.15, 0.35, 12, Hazard.DEFAULT);

	/**
	 * Le due cose che possono andare storte con un varco: ignorarlo, e attraversarlo.
	 *
	 * <p>Annidate e non nella radice per una ragione meccanica: {@code RecordCodecBuilder} si ferma
	 * a sedici campi, e con questi otto la radice sarebbe arrivata a diciannove. La stessa cosa e'
	 * gia' successa ad {@code AriseConfig}, e la via e' stata la stessa — annidare per argomento,
	 * non spezzare a caso.
	 */
	public record Hazard(
			/** Probabilita' che un varco scaduto erutti invece di richiudersi. */
			double breakChance,
			/** Quante ondate di mob riversa, rispetto a una sua stanza. */
			int breakWaves,
			/** Entro quale raggio dal varco escono. */
			int breakRadius,
			/** Quanto prima della scadenza comincia a dare segni di cedimento. */
			int breakWarningTicks,
			/** Probabilita' che un varco spontaneo sia rosso. */
			double redGateChance,
			/** Ogni quanti tick il gelo di un varco rosso morde. */
			int frostIntervalTicks,
			/** Entro quale raggio una fonte di calore tiene lontano il gelo. */
			int frostHeatRadius,
			/** Quanta vita costa un morso di gelo. */
			double frostDamage) {

		public static final Codec<Hazard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("break_chance").forGetter(Hazard::breakChance),
				Codec.INT.fieldOf("break_waves").forGetter(Hazard::breakWaves),
				Codec.INT.fieldOf("break_radius").forGetter(Hazard::breakRadius),
				Codec.INT.fieldOf("break_warning_ticks").forGetter(Hazard::breakWarningTicks),
				Codec.DOUBLE.fieldOf("red_gate_chance").forGetter(Hazard::redGateChance),
				Codec.INT.fieldOf("frost_interval_ticks").forGetter(Hazard::frostIntervalTicks),
				Codec.INT.fieldOf("frost_heat_radius").forGetter(Hazard::frostHeatRadius),
				Codec.DOUBLE.fieldOf("frost_damage").forGetter(Hazard::frostDamage)
		).apply(instance, Hazard::new));

		/**
		 * Un varco su tre cede, uno su venti e' rosso.
		 *
		 * <p>Il cedimento deve capitare abbastanza spesso perche' ignorare un varco sia una
		 * decisione; il rosso abbastanza di rado perche' resti una cosa che non sai prima.
		 */
		public static final Hazard DEFAULT = new Hazard(0.35, 3, 40, 1200, 0.05, 40, 5, 1.0);
	}

	// Scorciatoie: il resto del codice chiede "quanto cede" a SpawnConfig, non a SpawnConfig.Hazard.

	public double breakChance() {
		return hazard.breakChance();
	}

	public int breakWaves() {
		return hazard.breakWaves();
	}

	public int breakRadius() {
		return hazard.breakRadius();
	}

	public int breakWarningTicks() {
		return hazard.breakWarningTicks();
	}

	public double redGateChance() {
		return hazard.redGateChance();
	}

	public int frostIntervalTicks() {
		return hazard.frostIntervalTicks();
	}

	public int frostHeatRadius() {
		return hazard.frostHeatRadius();
	}

	public double frostDamage() {
		return hazard.frostDamage();
	}
}
