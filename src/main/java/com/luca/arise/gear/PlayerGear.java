package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luca.arise.gem.Gem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * L'equipaggiamento di un giocatore: quello indossato e quello tenuto da parte.
 *
 * <p>Immutabile come ogni valore in un attachment (CLAUDE.md §5): ogni operazione restituisce una
 * nuova istanza.
 *
 * <p>Lo slot di un pezzo non e' scritto qui, e' nel pezzo. Una mappa slot → pezzi sarebbe
 * ridondante e potrebbe entrare in contraddizione con se stessa; una lista non puo'. La posizione
 * dentro lo slot e' l'ordine nella lista, ed e' cio' che rende "il terzo anello" una cosa stabile.
 */
public record PlayerGear(List<GearPiece> equipped, List<GearPiece> stash, List<Gem> pouch) {

	public static final Codec<PlayerGear> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			GearPiece.CODEC.listOf().fieldOf("equipped").forGetter(PlayerGear::equipped),
			GearPiece.CODEC.listOf().fieldOf("stash").forGetter(PlayerGear::stash),
			// Opzionale: i salvataggi fatti prima delle gemme si caricano con la sacca vuota.
			Gem.CODEC.listOf().optionalFieldOf("pouch", List.of()).forGetter(PlayerGear::pouch)
	).apply(instance, PlayerGear::new));

	public static final StreamCodec<ByteBuf, PlayerGear> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final PlayerGear EMPTY = new PlayerGear(List.of(), List.of(), List.of());

	public PlayerGear {
		equipped = List.copyOf(equipped);
		stash = List.copyOf(stash);
		pouch = List.copyOf(pouch);
	}

	public boolean isEmpty() {
		return equipped.isEmpty() && stash.isEmpty() && pouch.isEmpty();
	}

	/** I pezzi indossati in questo slot, nell'ordine delle posizioni. */
	public List<GearPiece> equippedIn(GearSlot slot) {
		return equipped.stream().filter(piece -> piece.slot() == slot).toList();
	}

	public Optional<GearPiece> find(UUID id) {
		return java.util.stream.Stream.concat(equipped.stream(), stash.stream())
				.filter(piece -> piece.id().equals(id))
				.findFirst();
	}

	public boolean isEquipped(UUID id) {
		return equipped.stream().anyMatch(piece -> piece.id().equals(id));
	}

	/** Lo zaino ordinato dal pezzo piu' forte in giu', a parita' di rango. */
	public List<GearPiece> sortedStash() {
		return stash.stream()
				.sorted(Comparator.comparingInt((GearPiece piece) -> piece.rank().ordinal()).reversed()
						.thenComparing(Comparator.comparingDouble(GearPiece::weight).reversed()))
				.toList();
	}

	public Optional<Gem> findGem(UUID id) {
		return pouch.stream().filter(gem -> gem.id().equals(id)).findFirst();
	}

	/** Tutti i pezzi che hanno almeno un'incastonatura libera, indossati o no. */
	public List<GearPiece> socketable() {
		return java.util.stream.Stream.concat(equipped.stream(), stash.stream())
				.filter(piece -> piece.freeSockets() > 0)
				.toList();
	}

	/**
	 * Rimpiazza un pezzo con una sua versione modificata, ovunque si trovi.
	 *
	 * <p>Serve all'incastonatura: un pezzo con una gemma in piu' e' un record nuovo, e va messo
	 * esattamente dov'era il vecchio — spostarlo fra indossato e zaino cambierebbe in silenzio
	 * quello che il giocatore ha addosso.
	 */
	public PlayerGear withReplaced(GearPiece piece) {
		List<GearPiece> worn = replace(equipped, piece);
		List<GearPiece> stashed = replace(stash, piece);
		return new PlayerGear(worn, stashed, pouch);
	}

	private static List<GearPiece> replace(List<GearPiece> source, GearPiece piece) {
		List<GearPiece> updated = new ArrayList<>(source);

		for (int i = 0; i < updated.size(); i++) {
			if (updated.get(i).id().equals(piece.id())) {
				updated.set(i, piece);
			}
		}

		return updated;
	}

	public PlayerGear withGem(Gem gem) {
		List<Gem> updated = new ArrayList<>(pouch);
		updated.add(gem);
		return new PlayerGear(equipped, stash, updated);
	}

	public PlayerGear withoutGem(UUID gemId) {
		List<Gem> updated = new ArrayList<>(pouch);
		updated.removeIf(gem -> gem.id().equals(gemId));
		return new PlayerGear(equipped, stash, updated);
	}

	public PlayerGear withStashed(GearPiece piece) {
		List<GearPiece> updated = new ArrayList<>(stash);
		updated.add(piece);
		return new PlayerGear(equipped, updated, pouch);
	}

	/** Sposta un pezzo dallo zaino agli slot indossati. Nessun controllo: li fa il gestore. */
	public PlayerGear withEquipped(GearPiece piece) {
		List<GearPiece> stashed = new ArrayList<>(stash);
		stashed.removeIf(other -> other.id().equals(piece.id()));

		List<GearPiece> worn = new ArrayList<>(equipped);
		worn.add(piece);

		return new PlayerGear(worn, stashed, pouch);
	}

	/** Il contrario. */
	public PlayerGear withUnequipped(GearPiece piece) {
		List<GearPiece> worn = new ArrayList<>(equipped);
		worn.removeIf(other -> other.id().equals(piece.id()));

		List<GearPiece> stashed = new ArrayList<>(stash);
		stashed.add(piece);

		return new PlayerGear(worn, stashed, pouch);
	}

	/** Toglie di mezzo un pezzo, ovunque si trovi. */
	public PlayerGear without(UUID id) {
		List<GearPiece> worn = new ArrayList<>(equipped);
		worn.removeIf(piece -> piece.id().equals(id));

		List<GearPiece> stashed = new ArrayList<>(stash);
		stashed.removeIf(piece -> piece.id().equals(id));

		return new PlayerGear(worn, stashed, pouch);
	}
}
