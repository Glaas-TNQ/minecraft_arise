package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luca.arise.gem.Gem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Quello che il Sistema tiene per il giocatore, oltre a quello che ha addosso.
 *
 * <p>Due contenitori e una sacca.
 *
 * <ul>
 *   <li>{@code hunter} — le caselle che vanilla non ha: guanti, cintura, spalline, mantello,
 *       collana, talismano, quattro orecchini e dieci anelli. Elmo, corazza, gambali, stivali e
 *       arma <strong>non stanno qui</strong>: quelli si indossano nelle caselle del gioco, dove si
 *       vedono addosso al personaggio.
 *   <li>{@code dimensional} — lo spazio dimensionale, dove i pezzi trovati finiscono da soli. Non
 *       e' uno zaino generico: accetta solo equipaggiamento del Sistema, ed e' proprio il filtro a
 *       dargli un'identita' invece di renderlo una cassa in piu'.
 *   <li>{@code pouch} — le gemme, che restano un dato: si consumano incastonandole, e un oggetto
 *       che esiste solo per essere speso non guadagna niente a diventare un {@code ItemStack}.
 * </ul>
 *
 * <p>Le due liste hanno <strong>misura fissa</strong>, riempita di {@code ItemStack.EMPTY}: un
 * contenitore con dei buchi in mezzo e' la sola forma che un menu sappia disegnare, e la posizione
 * dentro la lista <em>e'</em> la casella. Gli indici li decide {@link GearSlot#HUNTER}, e per
 * questo quell'ordine non si tocca.
 *
 * <p>Il record e' immutabile ma gli {@code ItemStack} dentro non lo sono — e' il compromesso che
 * il gioco impone. Chi li modifica sul posto deve passare da {@link GearInventory}, che rifa'
 * l'attachment a ogni cambiamento: senza quel passaggio la persistenza e la sincronizzazione
 * fallirebbero in silenzio (CLAUDE.md §5).
 */
public record PlayerGear(List<ItemStack> hunter, List<ItemStack> dimensional, List<Gem> pouch,
		List<ItemStack> recovered) {

	/**
	 * Quanto e' grande lo spazio dimensionale: una cassa singola.
	 *
	 * <p>Numero nel codice e non in config, come le posizioni degli slot e per la stessa ragione:
	 * e' struttura. La misura decide quante righe disegna il menu, e una config che la cambiasse
	 * lascerebbe pezzi in caselle che non esistono piu'. Chi ne vuole di piu' ha i bauli — adesso
	 * che i pezzi sono oggetti veri, ci stanno dentro.
	 */
	public static final int DIMENSIONAL_SIZE = 27;

	public static final Codec<PlayerGear> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("hunter", List.of())
					.forGetter(PlayerGear::hunter),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("dimensional", List.of())
					.forGetter(PlayerGear::dimensional),
			Gem.CODEC.listOf().optionalFieldOf("pouch", List.of()).forGetter(PlayerGear::pouch),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("recovered", List.of())
					.forGetter(PlayerGear::recovered)
	).apply(instance, PlayerGear::new));

	/** Con i registri: dentro ci sono {@code ItemStack}, che senza registri non si sanno scrivere. */
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerGear> STREAM_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public static final PlayerGear EMPTY =
			new PlayerGear(List.of(), List.of(), List.of(), List.of());

	public PlayerGear {
		hunter = sized(hunter, GearSlot.HUNTER_POSITIONS);
		dimensional = sized(dimensional, DIMENSIONAL_SIZE);
		pouch = List.copyOf(pouch);
		recovered = List.copyOf(recovered);
	}

	/** Una lista della misura giusta: quel che c'e' si tiene, il resto sono caselle vuote. */
	private static List<ItemStack> sized(List<ItemStack> source, int size) {
		List<ItemStack> result = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			result.add(i < source.size() ? source.get(i) : ItemStack.EMPTY);
		}

		return Collections.unmodifiableList(result);
	}

	public boolean isEmpty() {
		return hunter.stream().allMatch(ItemStack::isEmpty)
				&& dimensional.stream().allMatch(ItemStack::isEmpty)
				&& pouch.isEmpty() && recovered.isEmpty();
	}

	public PlayerGear withHunter(List<ItemStack> items) {
		return new PlayerGear(items, dimensional, pouch, recovered);
	}

	public PlayerGear withDimensional(List<ItemStack> items) {
		return new PlayerGear(hunter, items, pouch, recovered);
	}

	/**
	 * Quello che il Sistema ha ritirato al momento della morte, in attesa di essere restituito.
	 *
	 * <p>Quasi sempre vuota, e per un solo respiro: si riempie quando il giocatore muore e si
	 * svuota quando riappare. Sta qui e non in un attachment a parte perche' deve sopravvivere
	 * alla morte esattamente come il resto, e questo attachment lo fa gia'.
	 */
	public PlayerGear withRecovered(List<ItemStack> items) {
		return new PlayerGear(hunter, dimensional, pouch, items);
	}

	/** I pezzi indossati nelle nostre caselle, in ordine di posizione. */
	public List<GearPiece> hunterPieces(GearSlot slot) {
		List<GearPiece> pieces = new ArrayList<>();
		int first = slot.firstIndex();

		for (int i = 0; i < slot.positions(); i++) {
			GearPiece piece = GearItems.piece(hunter.get(first + i));
			if (piece != null) {
				pieces.add(piece);
			}
		}

		return pieces;
	}

	/** Ogni oggetto conservato qui dentro, indossato o messo da parte. */
	public List<ItemStack> stacks() {
		List<ItemStack> all = new ArrayList<>(hunter.size() + dimensional.size());
		all.addAll(hunter);
		all.addAll(dimensional);
		return all;
	}

	public Optional<Gem> findGem(UUID id) {
		return pouch.stream().filter(gem -> gem.id().equals(id)).findFirst();
	}

	public PlayerGear withGem(Gem gem) {
		List<Gem> updated = new ArrayList<>(pouch);
		updated.add(gem);
		return new PlayerGear(hunter, dimensional, updated, recovered);
	}

	public PlayerGear withoutGem(UUID gemId) {
		List<Gem> updated = new ArrayList<>(pouch);
		updated.removeIf(gem -> gem.id().equals(gemId));
		return new PlayerGear(hunter, dimensional, updated, recovered);
	}
}
