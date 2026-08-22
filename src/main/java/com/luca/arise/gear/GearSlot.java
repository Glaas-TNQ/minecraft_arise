package com.luca.arise.gear;

import java.util.List;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Gli slot dell'equipaggiamento, e dove il pezzo si indossa davvero.
 *
 * <p>Si dividono in due famiglie, ed e' la divisione che regge tutto il sistema.
 *
 * <ul>
 *   <li><strong>Slot vanilla</strong> — testa, torso, gambe, piedi, mano. Il gioco ha gia' le sue
 *       caselle per queste cinque cose, con il drag&amp;drop, il modello che si vede addosso e la
 *       riparazione all'incudine. Riprodurle sarebbe stato costruire una copia peggiore
 *       dell'originale, quindi il pezzo ci finisce dentro e basta: e' un {@code ItemStack} vero.
 *   <li><strong>Slot del Cacciatore</strong> — guanti, cintura, spalline, mantello, collana,
 *       talismano, orecchini, anelli. Vanilla non ha un posto dove metterli, quindi il posto lo
 *       mettiamo noi, ed e' l'unica ragione per cui la nostra schermata esiste ancora.
 * </ul>
 *
 * <p>{@code unlocks} vale solo per la seconda famiglia e ha <strong>una voce per posizione</strong>:
 * la voce dice a che rango quella posizione si apre. Dieci anelli non compaiono tutti al livello 1
 * — se ne comincia con due e si arriva a dieci salendo di rango (design §8.4): venti caselle vuote
 * davanti a un principiante si leggono come una lista di faccende, mentre ogni sblocco successivo
 * e' un momento.
 *
 * <p>La tabella sta nel codice e non in config di proposito, contro la regola generale sui numeri
 * di bilanciamento: e' <em>struttura</em>, non bilanciamento. Se una config potesse ridurre le
 * posizioni disponibili, un giocatore con dieci anelli addosso si ritroverebbe pezzi equipaggiati
 * in caselle che non esistono piu', e servirebbe una migrazione per ogni modifica al file.
 */
public enum GearSlot implements StringRepresentable {

	// --- quello che vanilla sa gia' indossare -------------------------------------------------
	HEAD("head", EquipmentSlot.HEAD),
	CHEST("chest", EquipmentSlot.CHEST),
	LEGS("legs", EquipmentSlot.LEGS),
	FEET("feet", EquipmentSlot.FEET),
	WEAPON("weapon", EquipmentSlot.MAINHAND),

	// --- quello che vanilla non ha ------------------------------------------------------------
	HANDS("hands", Rank.E),
	BELT("belt", Rank.D),
	SHOULDERS("shoulders", Rank.C),
	CLOAK("cloak", Rank.B),
	NECKLACE("necklace", Rank.A),
	TALISMAN("talisman", Rank.S),
	EARRING("earring", Rank.C, Rank.B, Rank.A, Rank.S),
	RING("ring", Rank.E, Rank.E, Rank.D, Rank.C, Rank.B, Rank.B, Rank.A, Rank.A, Rank.S, Rank.S);

	public static final Codec<GearSlot> CODEC = StringRepresentable.fromEnum(GearSlot::values);

	/**
	 * Gli slot del Cacciatore, nell'ordine in cui la schermata li dispone.
	 *
	 * <p>Calcolata una volta: il menu la percorre per costruire le caselle, e l'ordine di questa
	 * lista <em>e'</em> l'ordine degli indici del contenitore. Cambiarlo sposta i pezzi gia'
	 * indossati, quindi non si cambia.
	 */
	public static final List<GearSlot> HUNTER =
			List.of(values()).stream().filter(slot -> slot.vanillaSlot == null).toList();

	/** Quante caselle ha in tutto il contenitore del Cacciatore. */
	public static final int HUNTER_POSITIONS = HUNTER.stream().mapToInt(GearSlot::positions).sum();

	private final String name;
	private final EquipmentSlot vanillaSlot;
	private final List<Rank> unlocks;

	GearSlot(String name, EquipmentSlot vanillaSlot) {
		this.name = name;
		this.vanillaSlot = vanillaSlot;
		this.unlocks = List.of();
	}

	GearSlot(String name, Rank... unlocks) {
		this.name = name;
		this.vanillaSlot = null;
		this.unlocks = List.of(unlocks);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** La casella vanilla in cui il pezzo si indossa, o {@code null} se il posto lo diamo noi. */
	public EquipmentSlot vanillaSlot() {
		return vanillaSlot;
	}

	/** Vero se il pezzo si indossa in una casella nostra. */
	public boolean hunterOwned() {
		return vanillaSlot == null;
	}

	/** L'indice della prima casella di questo slot nel contenitore del Cacciatore. */
	public int firstIndex() {
		int index = 0;

		for (GearSlot slot : HUNTER) {
			if (slot == this) {
				return index;
			}
			index += slot.positions();
		}

		// Uno slot vanilla non sta nel contenitore: chi chiama ha sbagliato strada.
		return -1;
	}

	/** Quante posizioni esistono in assoluto: 1 per quasi tutti, 4 orecchini, 10 anelli. */
	public int positions() {
		return vanillaSlot != null ? 1 : unlocks.size();
	}

	/** Quante posizioni sono aperte a questo rango di Cacciatore. */
	public int capacity(Rank hunterRank) {
		// Le caselle di vanilla sono sempre aperte: chiuderle vorrebbe dire impedire di indossare
		// un elmo di ferro qualunque, e non e' affare nostro.
		if (vanillaSlot != null) {
			return 1;
		}

		int open = 0;
		for (Rank required : unlocks) {
			if (hunterRank.ordinal() >= required.ordinal()) {
				open++;
			}
		}
		return open;
	}

	/** Il rango che apre la prossima posizione, o {@code null} se sono gia' tutte aperte. */
	public Rank nextUnlock(Rank hunterRank) {
		for (Rank required : unlocks) {
			if (hunterRank.ordinal() < required.ordinal()) {
				return required;
			}
		}
		return null;
	}

	/** Il rango che apre la posizione numero {@code position} di questo slot. */
	public Rank unlockOf(int position) {
		if (vanillaSlot != null || position < 0 || position >= unlocks.size()) {
			return Rank.E;
		}
		return unlocks.get(position);
	}

	/** Vero se lo slot e' ancora del tutto chiuso: la schermata lo disegna diverso. */
	public boolean locked(Rank hunterRank) {
		return capacity(hunterRank) == 0;
	}

	public Component label() {
		return Component.translatable("arise.gear.slot." + name);
	}
}
