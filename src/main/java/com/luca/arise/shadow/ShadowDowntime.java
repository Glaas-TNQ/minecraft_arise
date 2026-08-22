package com.luca.arise.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Le ombre a terra: quali sono, e da quale tick tornano disponibili.
 *
 * <p>Non e' persistente, ed e' una scelta obbligata: {@code readyAtTick} e' un tick di gioco
 * assoluto, e dopo un riavvio quel numero non vuole piu' dire la stessa cosa. E' lo stesso motivo
 * per cui non lo sono i tempi di ricarica delle abilita'. Un riavvio rimette in piedi tutti:
 * meglio questo che un esercito bloccato per sempre da un contatore che nessuno sa piu' leggere.
 *
 * <p>Sincronizzato invece si', perche' e' informazione che il client deve poter disegnare: la
 * schermata dell'esercito mostra quanti secondi mancano, e senza si vedrebbe solo un bottone che
 * rifiuta senza spiegare.
 */
public record ShadowDowntime(List<Entry> entries) {

	/** Un'ombra a terra e il tick in cui si rialza. */
	public record Entry(UUID id, long readyAtTick) {

		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
				Codec.LONG.fieldOf("ready_at").forGetter(Entry::readyAtTick)
		).apply(instance, Entry::new));
	}

	public static final Codec<ShadowDowntime> CODEC =
			Entry.CODEC.listOf().xmap(ShadowDowntime::new, ShadowDowntime::entries);

	public static final StreamCodec<ByteBuf, ShadowDowntime> STREAM_CODEC =
			ByteBufCodecs.fromCodec(CODEC);

	public static final ShadowDowntime EMPTY = new ShadowDowntime(List.of());

	public ShadowDowntime {
		entries = List.copyOf(entries);
	}

	/** Quanti tick mancano perche' quest'ombra sia di nuovo evocabile. Zero se e' pronta. */
	public long remaining(UUID id, long now) {
		for (Entry entry : entries) {
			if (entry.id().equals(id)) {
				return Math.max(0L, entry.readyAtTick() - now);
			}
		}

		return 0L;
	}

	public boolean isDown(UUID id, long now) {
		return remaining(id, now) > 0L;
	}

	/**
	 * Mette a terra un'ombra e ripulisce dalle voci scadute nello stesso passaggio.
	 *
	 * <p>La pulizia sta qui e non in un tick apposta: l'unico momento in cui questa lista cresce
	 * e' una morte, e una lista che si ripulisce quando cresce non ha bisogno di un guardiano.
	 */
	public ShadowDowntime with(UUID id, long readyAtTick, long now) {
		List<Entry> updated = new ArrayList<>(entries.size() + 1);

		for (Entry entry : entries) {
			if (!entry.id().equals(id) && entry.readyAtTick() > now) {
				updated.add(entry);
			}
		}

		updated.add(new Entry(id, readyAtTick));
		return new ShadowDowntime(updated);
	}

	/** La stessa lista senza le voci scadute, o {@code this} se non e' cambiato niente. */
	public ShadowDowntime pruned(long now) {
		List<Entry> updated = new ArrayList<>(entries.size());

		for (Entry entry : entries) {
			if (entry.readyAtTick() > now) {
				updated.add(entry);
			}
		}

		return updated.size() == entries.size() ? this : new ShadowDowntime(updated);
	}
}
