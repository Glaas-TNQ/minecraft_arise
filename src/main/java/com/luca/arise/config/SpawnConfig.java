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
		/**
		 * Probabilita' che un varco scaduto <em>erutti</em> invece di richiudersi.
		 *
		 * <p>Un terzo, e il numero e' scelto per una ragione precisa: deve essere abbastanza alto
		 * perche' ignorare un varco sia una decisione, e abbastanza basso perche' non lo sia
		 * sempre. A uno, i varchi diventerebbero un promemoria; a zero, il mondo tornerebbe a non
		 * avere nessuna conseguenza per chi non fa niente.
		 */
		double breakChance,
		/** Quante ondate di mob riversa un varco che cede, rispetto a una sua stanza. */
		int breakWaves,
		/** Entro quale raggio dal varco escono i mob. */
		int breakRadius,
		/**
		 * Quanto prima della scadenza il varco comincia a dare segni di cedimento.
		 *
		 * <p>Senza preavviso una rottura e' una tassa; con il preavviso e' una decisione — e il
		 * giocatore che sente il varco cedere puo' scegliere di correre a chiuderlo.
		 */
		int breakWarningTicks) {

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
			Codec.DOUBLE.fieldOf("break_chance").forGetter(SpawnConfig::breakChance),
			Codec.INT.fieldOf("break_waves").forGetter(SpawnConfig::breakWaves),
			Codec.INT.fieldOf("break_radius").forGetter(SpawnConfig::breakRadius),
			Codec.INT.fieldOf("break_warning_ticks").forGetter(SpawnConfig::breakWarningTicks)
	).apply(instance, SpawnConfig::new));

	/**
	 * Un varco ogni pochi minuti a chi gioca normalmente.
	 *
	 * <p>Il dado si tira ogni trenta secondi con una probabilita' del sei per cento: in media un
	 * varco ogni otto minuti e mezzo. Il tetto di due nel raggio di duecento blocchi impedisce che
	 * una sessione lunga in un posto solo diventi un campo di varchi.
	 */
	public static final SpawnConfig DEFAULT =
			new SpawnConfig(true, 600, 0.06, 2, 200, 48, 160, 6000, 0.15, 0.35, 12,
					0.35, 3, 40, 1200);
}
