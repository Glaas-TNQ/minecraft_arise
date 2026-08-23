package com.luca.arise.gate;

import net.minecraft.network.chat.Component;

/**
 * Le regole della discesa: una nuova ogni cinque gradini.
 *
 * <p>E' il cuore dell'Abisso, e la ragione per cui non e' semplicemente un varco con i numeri piu'
 * grossi. Lo scaling infinito e' documentato come «la rovina degli ARPG moderni» proprio perche'
 * oltre una certa soglia il livello novecento e' il livello dieci con piu' vita: l'antidoto e' che
 * ogni fascia aggiunga <strong>una regola</strong>, non un moltiplicatore.
 *
 * <p>Il criterio con cui sono state scelte queste quattro: ognuna deve avere un nome che il
 * giocatore possa pronunciare mentre perde. «Mi ha ucciso perche' il Sovrano non aveva la prima
 * fase» e' una frase che si puo' dire; «mi ha ucciso perche' aveva il quaranta per cento di vita in
 * piu'» non lo e'.
 *
 * <p>Si sommano. Al venticinquesimo gradino ci sono tutte e quattro, e a quel punto non e' un varco
 * piu' grosso: e' un altro gioco, giocato con lo stesso esercito.
 */
public enum AbyssRule {

	/** Ogni stanza ha un mob con un affisso, a qualunque rango. */
	AFFIXED("affixed", 5),

	/** Il Sovrano parte gia' in seconda fase: spazza le ombre dal primo secondo. */
	RELENTLESS("relentless", 10),

	/** L'esercito scende con meta' dei posti in campo. */
	THINNED("thinned", 15),

	/** Le ombre cadute non tornano fino all'uscita. */
	UNFORGIVING("unforgiving", 20);

	private final String name;
	private final int depth;

	AbyssRule(String name, int depth) {
		this.name = name;
		this.depth = depth;
	}

	public String getSerializedName() {
		return name;
	}

	/** Da quale gradino in giu' questa regola e' in vigore. */
	public int depth() {
		return depth;
	}

	public Component label() {
		return Component.translatable("arise.abyss.rule." + name);
	}

	public Component description() {
		return Component.translatable("arise.abyss.rule." + name + ".desc");
	}
}
