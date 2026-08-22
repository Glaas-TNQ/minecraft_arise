package com.luca.arise.gem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.config.GemConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

/**
 * Una gemma: un dato, come tutto il resto dell'equipaggiamento.
 *
 * <p>Le statistiche sono congelate al momento del tiro, come per i pezzi. La <em>potenza</em>
 * dell'effetto passivo no: si ricava dal rango a ogni lettura, cosi' ritoccare il bilanciamento in
 * config riscala tutti gli effetti senza dover toccare le gemme gia' in giro. La differenza e'
 * voluta — le statistiche sono l'identita' della gemma, l'effetto e' una regola del gioco.
 */
public record Gem(UUID id, GemType type, Rank rank, Map<Stat, Double> stats) {

	public static final Codec<Gem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Gem::id),
			GemType.CODEC.fieldOf("type").forGetter(Gem::type),
			Rank.CODEC.fieldOf("rank").forGetter(Gem::rank),
			Codec.unboundedMap(Stat.CODEC, Codec.DOUBLE).fieldOf("stats").forGetter(Gem::stats)
	).apply(instance, Gem::new));

	public Gem {
		EnumMap<Stat, Double> ordered = new EnumMap<>(Stat.class);
		ordered.putAll(stats);
		stats = Collections.unmodifiableMap(ordered);
	}

	/** "Ematite di rango B". */
	public Component displayName() {
		return Component.translatable("arise.gem.name", type.label(), rank.label())
				.withStyle(rank.chatColor());
	}

	/** Quanto vale l'effetto passivo di questa gemma, da sola. */
	public double magnitude(GemConfig config) {
		return config.magnitude(type, rank);
	}

	public Component describe(GemConfig config) {
		return type.describe(magnitude(config));
	}
}
