package com.luca.arise.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A che punto della catena e' un giocatore.
 *
 * <p>Due numeri, e sono davvero tutto: <em>quale</em> incarico ha davanti e <em>quanto</em> ha
 * fatto di quello. Gli incarichi completati sono quelli prima dell'indice, e i sistemi sbloccati
 * sono quelli che quegli incarichi concedono. Nessun elenco di cose fatte, nessun insieme di
 * permessi: un solo numero non puo' entrare in contraddizione con se stesso.
 *
 * @param index    l'incarico corrente; oltre l'ultimo significa catena finita
 * @param progress quanto e' stato fatto dell'incarico corrente
 */
public record PlayerQuests(int index, int progress) {

	public static final Codec<PlayerQuests> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("index").forGetter(PlayerQuests::index),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(PlayerQuests::progress)
	).apply(instance, PlayerQuests::new));

	public static final StreamCodec<ByteBuf, PlayerQuests> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Si comincia dal risveglio, con niente addosso. */
	public static final PlayerQuests INITIAL = new PlayerQuests(0, 0);

	public boolean finished() {
		return index >= Quest.count();
	}

	/** L'incarico corrente, o {@code null} se la catena e' finita. */
	public Quest current() {
		return finished() ? null : Quest.values()[index];
	}

	/** Vero se questo incarico e' gia' stato completato. */
	public boolean completed(Quest quest) {
		return quest.ordinal() < index;
	}

	/** Vero se il sistema e' stato concesso da un incarico gia' completato. */
	public boolean has(Unlock unlock) {
		for (int i = 0; i < index && i < Quest.count(); i++) {
			if (Quest.values()[i].grants() == unlock) {
				return true;
			}
		}

		return false;
	}

	public PlayerQuests withProgress(int newProgress) {
		return new PlayerQuests(index, Math.max(0, newProgress));
	}

	/** Avanza al prossimo incarico, azzerando il contatore. */
	public PlayerQuests next() {
		return new PlayerQuests(index + 1, 0);
	}
}
