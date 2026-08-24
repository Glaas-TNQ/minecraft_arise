package com.luca.arise.config;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.ability.Ability;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Tempi di ricarica e portata delle abilità.
 *
 * <p>I tempi stanno in una mappa per abilità invece che in un campo ciascuno: aggiungerne una
 * nuova non tocca il codec, e chi bilancia vede tutti i numeri della stessa famiglia in fila.
 */
public record AbilityConfig(
		/** Tick di ricarica per ciascuna abilità. */
		Map<Ability, Integer> cooldowns,
		/** Quanti blocchi copre lo scatto d'ombra. */
		double dashDistance,
		/** Durata del Dominio del Monarca, in tick. */
		int domainDurationTicks,
		/** Raggio dell'Autorità del Sovrano, in blocchi. */
		double authorityRadius,
		/** Durata dell'Autorità del Sovrano, in tick. */
		int authorityDurationTicks,
		/**
		 * Il Mana: quanto se ne ha e quanto costa ogni cosa.
		 *
		 * <p>Annidato qui e non sulla radice della config perche' la radice e' <strong>gia' a
		 * sedici campi</strong>, che e' il tetto di {@code RecordCodecBuilder}. E questa e' comunque
		 * la sua casa giusta: il Mana e' il prezzo delle azioni attive, e le azioni attive stanno
		 * qui. La scorciatoia {@code AriseConfig.mana()} evita a chi legge di doverlo sapere.
		 */
		ManaConfig mana) {

	private static Map<Ability, Integer> defaultCooldowns() {
		Map<Ability, Integer> cooldowns = new EnumMap<>(Ability.class);
		cooldowns.put(Ability.SHADOW_STEP, 60);            // 3 s: è mobilità, deve restare fluida
		cooldowns.put(Ability.SHADOW_EXCHANGE, 200);       // 10 s
		// Il volo si accende e si spegne a piacere: la ricarica serve solo a impedire che lo si
		// faccia venti volte al secondo, non a razionarlo. Quello lo fa il Mana.
		cooldowns.put(Ability.SHADOW_FLIGHT, 20);          // 1 s
		cooldowns.put(Ability.MONARCH_DOMAIN, 600);        // 30 s
		cooldowns.put(Ability.SOVEREIGN_AUTHORITY, 900);   // 45 s
		return cooldowns;
	}

	public static final AbilityConfig DEFAULT = new AbilityConfig(
			defaultCooldowns(), 8.0, 400, 12.0, 200, ManaConfig.DEFAULT);

	public static final Codec<AbilityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Ability.CODEC, Codec.INT).fieldOf("cooldowns")
					.forGetter(AbilityConfig::cooldowns),
			Codec.DOUBLE.fieldOf("dash_distance").forGetter(AbilityConfig::dashDistance),
			Codec.INT.fieldOf("domain_duration_ticks").forGetter(AbilityConfig::domainDurationTicks),
			Codec.DOUBLE.fieldOf("authority_radius").forGetter(AbilityConfig::authorityRadius),
			Codec.INT.fieldOf("authority_duration_ticks").forGetter(AbilityConfig::authorityDurationTicks),
			ManaConfig.CODEC.fieldOf("mana").forGetter(AbilityConfig::mana)
	).apply(instance, AbilityConfig::new));

	public AbilityConfig {
		cooldowns = Map.copyOf(cooldowns);
	}

	public int cooldownTicks(Ability ability) {
		return cooldowns.getOrDefault(ability, DEFAULT.cooldowns().get(ability));
	}
}
