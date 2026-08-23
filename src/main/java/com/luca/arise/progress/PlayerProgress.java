package com.luca.arise.progress;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import io.netty.buffer.ByteBuf;

/**
 * Lo stato del Sistema per un giocatore.
 *
 * <p>Immutabile per contratto dell'API delle Data Attachment: ogni modifica produce una nuova
 * istanza che va riassegnata con {@code setAttached}, mai mutata sul posto (vedi CLAUDE.md §5).
 */
public record PlayerProgress(int level, long xp, int unspentPoints, Map<Stat, Integer> stats, long souls) {

	public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("level").forGetter(PlayerProgress::level),
			Codec.LONG.fieldOf("xp").forGetter(PlayerProgress::xp),
			Codec.INT.fieldOf("unspent_points").forGetter(PlayerProgress::unspentPoints),
			Codec.unboundedMap(Stat.CODEC, Codec.INT).fieldOf("stats").forGetter(PlayerProgress::stats),
			// Opzionale: i salvataggi fatti prima dei soul coin ripartono da zero invece di
			// rendere illeggibile tutta la progressione.
			Codec.LONG.optionalFieldOf("souls", 0L).forGetter(PlayerProgress::souls)
	).apply(instance, PlayerProgress::new));

	public static final StreamCodec<ByteBuf, PlayerProgress> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final PlayerProgress INITIAL = new PlayerProgress(1, 0L, 0, Map.of(), 0L);

	public PlayerProgress {
		stats = Map.copyOf(stats);
	}

	public int stat(Stat stat) {
		return stats.getOrDefault(stat, 0);
	}

	public PlayerProgress withXp(long newXp) {
		return new PlayerProgress(level, newXp, unspentPoints, stats, souls);
	}

	public PlayerProgress withLevel(int newLevel, long newXp, int newUnspentPoints) {
		return new PlayerProgress(newLevel, newXp, newUnspentPoints, stats, souls);
	}

	public PlayerProgress withUnspentPoints(int newUnspentPoints) {
		return new PlayerProgress(level, xp, newUnspentPoints, stats, souls);
	}

	public PlayerProgress withSouls(long newSouls) {
		return new PlayerProgress(level, xp, unspentPoints, stats, Math.max(0L, newSouls));
	}

	/** Sposta {@code amount} punti dal non speso alla statistica indicata. */
	public PlayerProgress withStatIncreased(Stat stat, int amount) {
		Map<Stat, Integer> updated = new EnumMap<>(Stat.class);
		updated.putAll(stats);
		updated.merge(stat, amount, Integer::sum);
		return new PlayerProgress(level, xp, unspentPoints - amount, updated, souls);
	}

	/** Quanti punti sono stati spesi in tutto, su tutte le statistiche. */
	public int spentPoints() {
		int total = 0;
		for (int value : stats.values()) {
			total += value;
		}
		return total;
	}

	/**
	 * Svuota tutte le statistiche e restituisce i punti al non speso.
	 *
	 * <p>Il totale dei punti — spesi piu' non spesi — <strong>non cambia</strong>, ed e' l'unica
	 * proprieta' che questa operazione deve garantire: un respec che regalasse o mangiasse punti
	 * sarebbe un difetto silenzioso, perche' nessuno tiene il conto a mano di quanti ne ha avuti in
	 * cento livelli.
	 */
	public PlayerProgress withStatsReset() {
		return new PlayerProgress(level, xp, unspentPoints + spentPoints(), Map.of(), souls);
	}
}
