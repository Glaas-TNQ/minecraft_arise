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
		int placementAttempts) {

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
			Codec.INT.fieldOf("placement_attempts").forGetter(SpawnConfig::placementAttempts)
	).apply(instance, SpawnConfig::new));

	/**
	 * Un varco ogni pochi minuti a chi gioca normalmente.
	 *
	 * <p>Il dado si tira ogni trenta secondi con una probabilita' del sei per cento: in media un
	 * varco ogni otto minuti e mezzo. Il tetto di due nel raggio di duecento blocchi impedisce che
	 * una sessione lunga in un posto solo diventi un campo di varchi.
	 */
	public static final SpawnConfig DEFAULT =
			new SpawnConfig(true, 600, 0.06, 2, 200, 48, 160, 6000, 0.15, 0.35, 12);
}
