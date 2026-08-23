package com.luca.arise.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * La squadra: quali ombre escono, e in che ordine.
 *
 * <p>E' la correzione del difetto piu' grosso che l'esercito aveva. {@code summon()} scorreva la
 * lista dall'inizio e mandava fuori le prime quattro disponibili — cioe' le prime quattro
 * <em>estratte</em>, che dopo dieci ore di gioco sono le quattro piu' deboli che si possiedono.
 * Per mandare fuori quelle giuste bisognava aprire la schermata e evocarle una per una, ogni
 * volta. Con una squadra si sceglie una volta e il tasto fa la cosa giusta per sempre.
 *
 * <p>L'ordine conta: se la squadra e' piu' numerosa dei posti disponibili — perche' il tetto e'
 * sceso, o perche' qualcuna e' gia' fuori — escono le prime.
 *
 * <p>Persistente e {@code copyOnDeath} come l'esercito: una squadra che si azzera morendo
 * costringerebbe a ricomporla ogni volta, che e' esattamente il fastidio che questa classe esiste
 * per togliere.
 */
public record ShadowSquad(List<UUID> ids) {

	public static final Codec<ShadowSquad> CODEC =
			UUIDUtil.CODEC.listOf().xmap(ShadowSquad::new, ShadowSquad::ids);

	public static final StreamCodec<ByteBuf, ShadowSquad> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final ShadowSquad EMPTY = new ShadowSquad(List.of());

	public ShadowSquad {
		ids = List.copyOf(ids);
	}

	public boolean contains(UUID id) {
		return ids.contains(id);
	}

	public int size() {
		return ids.size();
	}

	public boolean isEmpty() {
		return ids.isEmpty();
	}

	/** Posizione in squadra a partire da uno, per la schermata. Zero se non ne fa parte. */
	public int slotOf(UUID id) {
		return ids.indexOf(id) + 1;
	}

	/**
	 * Aggiunge o toglie, entro il tetto.
	 *
	 * <p>Restituisce {@code this} quando non c'e' posto: chi chiama se ne accorge dall'identita' e
	 * risponde "squadra piena" invece di far finta di aver fatto qualcosa.
	 */
	public ShadowSquad toggled(UUID id, int capacity) {
		if (contains(id)) {
			List<UUID> updated = new ArrayList<>(ids);
			updated.remove(id);
			return new ShadowSquad(updated);
		}

		if (ids.size() >= capacity) {
			return this;
		}

		List<UUID> updated = new ArrayList<>(ids);
		updated.add(id);
		return new ShadowSquad(updated);
	}

	/**
	 * La stessa squadra senza le ombre che non sono piu' nell'esercito.
	 *
	 * <p>Serve perche' congedare un'ombra non passa da qui: la pulizia si fa alla lettura, come per
	 * il tempo di recupero, cosi' non c'e' un secondo posto da ricordarsi di aggiornare.
	 */
	public ShadowSquad pruned(ShadowArmy army) {
		List<UUID> updated = new ArrayList<>(ids.size());

		for (UUID id : ids) {
			if (army.find(id).isPresent()) {
				updated.add(id);
			}
		}

		return updated.size() == ids.size() ? this : new ShadowSquad(updated);
	}
}
