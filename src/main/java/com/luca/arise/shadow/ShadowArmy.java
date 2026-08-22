package com.luca.arise.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** L'esercito conservato da un giocatore. Immutabile, come ogni valore in un attachment. */
public record ShadowArmy(List<ShadowData> shadows) {

	public static final Codec<ShadowArmy> CODEC =
			ShadowData.CODEC.listOf().xmap(ShadowArmy::new, ShadowArmy::shadows);

	public static final StreamCodec<ByteBuf, ShadowArmy> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final ShadowArmy EMPTY = new ShadowArmy(List.of());

	public ShadowArmy {
		shadows = List.copyOf(shadows);
	}

	public int size() {
		return shadows.size();
	}

	public boolean isEmpty() {
		return shadows.isEmpty();
	}

	public Optional<ShadowData> find(UUID id) {
		return shadows.stream().filter(shadow -> shadow.id().equals(id)).findFirst();
	}

	public ShadowArmy with(ShadowData shadow) {
		List<ShadowData> updated = new ArrayList<>(shadows);
		updated.add(shadow);
		return new ShadowArmy(updated);
	}

	public ShadowArmy without(UUID id) {
		List<ShadowData> updated = new ArrayList<>(shadows);
		updated.removeIf(shadow -> shadow.id().equals(id));
		return new ShadowArmy(updated);
	}

	/** Sostituisce un'ombra con la sua versione aggiornata, mantenendone la posizione in lista. */
	public ShadowArmy replace(ShadowData shadow) {
		List<ShadowData> updated = new ArrayList<>(shadows);

		for (int i = 0; i < updated.size(); i++) {
			if (updated.get(i).id().equals(shadow.id())) {
				updated.set(i, shadow);
				return new ShadowArmy(updated);
			}
		}

		return this;
	}
}
