package com.luca.arise.gear;

import java.util.List;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Gli slot dell'equipaggiamento del Cacciatore, e a che rango si aprono.
 *
 * <p>Non sono i quattro slot armatura di vanilla e non li toccano: qui l'equipaggiamento e' un
 * dato, non un {@code ItemStack} (design §8.1). Ventiquattro caselle non entrerebbero comunque in
 * un'armatura vanilla.
 *
 * <p>{@code unlocks} ha <strong>una voce per posizione</strong>, e la voce dice a che rango quella
 * posizione si apre. Dieci anelli non compaiono tutti al livello 1 — se ne comincia con due e si
 * arriva a dieci salendo di rango (design §8.4): venti caselle vuote davanti a un principiante si
 * leggono come una lista di faccende, mentre ogni sblocco successivo e' un momento.
 *
 * <p>La tabella sta nel codice e non in config di proposito, contro la regola generale sui numeri
 * di bilanciamento: e' <em>struttura</em>, non bilanciamento. Se una config potesse ridurre le
 * posizioni disponibili, un giocatore con dieci anelli addosso si ritroverebbe pezzi equipaggiati
 * in caselle che non esistono piu', e servirebbe una migrazione per ogni modifica al file.
 */
public enum GearSlot implements StringRepresentable {

	HEAD("head", Rank.E),
	CHEST("chest", Rank.E),
	LEGS("legs", Rank.E),
	FEET("feet", Rank.E),
	HANDS("hands", Rank.E),
	BELT("belt", Rank.D),
	SHOULDERS("shoulders", Rank.C),
	CLOAK("cloak", Rank.B),
	NECKLACE("necklace", Rank.A),
	TALISMAN("talisman", Rank.S),
	EARRING("earring", Rank.C, Rank.B, Rank.A, Rank.S),
	RING("ring", Rank.E, Rank.E, Rank.D, Rank.C, Rank.B, Rank.B, Rank.A, Rank.A, Rank.S, Rank.S);

	public static final Codec<GearSlot> CODEC = StringRepresentable.fromEnum(GearSlot::values);

	private final String name;
	private final List<Rank> unlocks;

	GearSlot(String name, Rank... unlocks) {
		this.name = name;
		this.unlocks = List.of(unlocks);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Quante posizioni esistono in assoluto: 1 per quasi tutti, 4 orecchini, 10 anelli. */
	public int positions() {
		return unlocks.size();
	}

	/** Quante posizioni sono aperte a questo rango di Cacciatore. */
	public int capacity(Rank hunterRank) {
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

	/** Vero se lo slot e' ancora del tutto chiuso: la schermata lo disegna diverso. */
	public boolean locked(Rank hunterRank) {
		return capacity(hunterRank) == 0;
	}

	public Component label() {
		return Component.translatable("arise.gear.slot." + name);
	}
}
