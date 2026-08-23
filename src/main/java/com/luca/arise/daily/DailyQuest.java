package com.luca.arise.daily;

import com.luca.arise.config.DailyConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * La giornata di un Cacciatore, secondo il Sistema.
 *
 * <p>Quattro contatori e il giorno a cui appartengono. Il giorno non e' una data: e' il numero di
 * giorni di Minecraft passati dall'inizio del mondo, che e' l'unico orologio che questa mod possa
 * usare senza chiedere niente al calendario di chi gioca. Chi non accende il gioco per una
 * settimana non trova sette penalita' ad aspettarlo.
 *
 * <p>{@code settled} e' la meta' che rende il record onesto: dice che di quel giorno il Sistema ha
 * gia' fatto i conti — premio dato, o penalita' pagata. Senza, riaprire il mondo il giorno dopo
 * potrebbe far scattare due volte la stessa penalita', o nessuna.
 *
 * @param day     il giorno di Minecraft a cui questi contatori appartengono
 * @param blocks  blocchi scavati
 * @param hits    colpi messi a segno
 * @param jumps   salti
 * @param sprint  blocchi percorsi di corsa
 * @param settled se di questo giorno il Sistema ha gia' fatto i conti
 */
public record DailyQuest(long day, int blocks, int hits, int jumps, int sprint, boolean settled) {

	public static final Codec<DailyQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("day").forGetter(DailyQuest::day),
			Codec.INT.fieldOf("blocks").forGetter(DailyQuest::blocks),
			Codec.INT.fieldOf("hits").forGetter(DailyQuest::hits),
			Codec.INT.fieldOf("jumps").forGetter(DailyQuest::jumps),
			Codec.INT.fieldOf("sprint").forGetter(DailyQuest::sprint),
			Codec.BOOL.fieldOf("settled").forGetter(DailyQuest::settled)
	).apply(instance, DailyQuest::new));

	public static final StreamCodec<ByteBuf, DailyQuest> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/**
	 * Il giorno meno uno: cosi' il primo battito dopo il login apre la giornata invece di trovarla
	 * gia' aperta e vuota. Un contatore che parte «gia' in corso» non annuncia mai niente.
	 */
	public static final DailyQuest NONE = new DailyQuest(-1L, 0, 0, 0, 0, false);

	/** Una giornata nuova, coi contatori azzerati. */
	public static DailyQuest forDay(long day) {
		return new DailyQuest(day, 0, 0, 0, 0, false);
	}

	public int progress(DailyTask task) {
		return switch (task) {
			case BLOCKS -> blocks;
			case HITS -> hits;
			case JUMPS -> jumps;
			case SPRINT -> sprint;
		};
	}

	/**
	 * Un contatore avanzato.
	 *
	 * <p>Si ferma al bersaglio invece di crescere all'infinito, ed e' voluto: il numero che il
	 * giocatore legge e' «cento su cento», non «quattrocentododici su cento». Il secondo direbbe
	 * qualcosa di vero e niente di utile.
	 */
	public DailyQuest with(DailyTask task, int amount, DailyConfig config) {
		int capped = Math.min(progress(task) + amount, task.target(config));

		return switch (task) {
			case BLOCKS -> new DailyQuest(day, capped, hits, jumps, sprint, settled);
			case HITS -> new DailyQuest(day, blocks, capped, jumps, sprint, settled);
			case JUMPS -> new DailyQuest(day, blocks, hits, capped, sprint, settled);
			case SPRINT -> new DailyQuest(day, blocks, hits, jumps, capped, settled);
		};
	}

	/**
	 * La giornata saldata.
	 *
	 * <p>Non si chiama {@code settled()}: quello e' gia' l'accessore del campo, e un record non
	 * accetta due metodi con quel nome. La differenza fra «com'e' andata» e «segnala che e' andata»
	 * merita comunque due nomi diversi.
	 */
	public DailyQuest withSettled() {
		return new DailyQuest(day, blocks, hits, jumps, sprint, true);
	}

	/** Vero se tutti e quattro sono al loro bersaglio. */
	public boolean complete(DailyConfig config) {
		for (DailyTask task : DailyTask.values()) {
			if (progress(task) < task.target(config)) {
				return false;
			}
		}

		return true;
	}

	/** Quanti obiettivi mancano. Serve solo a scrivere una riga sensata. */
	public int remaining(DailyConfig config) {
		int open = 0;

		for (DailyTask task : DailyTask.values()) {
			if (progress(task) < task.target(config)) {
				open++;
			}
		}

		return open;
	}
}
