package com.luca.arise.progress;

import com.luca.arise.AriseMod;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * I traguardi di Arise, agganciati al sistema che il gioco ha gia'.
 *
 * <p>Una mod con dodici sistemi ha un problema che non e' «manca roba»: e' che il giocatore non sa
 * <em>cosa esiste</em>. Gli advancement sono la risposta piu' economica possibile — sono gia' nel
 * gioco, hanno gia' il toast, il suono, la schermata a rami e la traduzione, e non chiedono una
 * tredicesima finestra a una mod che ne ha undici.
 *
 * <p><strong>Ogni traguardo e' un momento, non un contatore.</strong> Non c'e' «uccidi cento mob»:
 * ci sono la prima ombra, il primo varco, la prima nominata, il primo Gate Rosso, la discesa. Cose
 * che si ricordano, e che quando compaiono nella schermata dicono al giocatore «questo si puo'
 * fare» prima ancora che sappia come.
 *
 * <p>Tutti i criteri sono {@code minecraft:impossible}: il gioco non puo' accorgersi da solo che
 * un'ombra e' stata estratta, e inventare un trigger nuovo per ognuno vorrebbe dire un mixin per
 * ognuno. Li concede il codice, da dove la cosa succede davvero — che e' anche l'unico posto che
 * sa se e' successa.
 */
public final class AriseAdvancements {

	/** Il risveglio. E' la radice, e non ha toast: la scena la fa gia' l'Araldo. */
	public static final String AWAKENED = "awakened";

	public static final String FIRST_SHADOW = "first_shadow";
	public static final String NAMED_SHADOW = "named_shadow";
	public static final String GRAND_MARSHAL = "grand_marshal";

	public static final String FIRST_GATE = "first_gate";
	public static final String RED_GATE = "red_gate";
	public static final String GATE_BREAKER = "gate_breaker";

	public static final String ASSOCIATION = "association";
	public static final String WORKSHOP = "workshop";

	public static final String MASTERY = "mastery";
	public static final String ABYSS = "abyss";
	public static final String ABYSS_TEN = "abyss_ten";

	/** Il criterio, uguale per tutti: sono tutti {@code impossible}, e li concede il codice. */
	private static final String CRITERION = "granted";

	private AriseAdvancements() {
	}

	/**
	 * Concede un traguardo, se il giocatore non ce l'ha gia'.
	 *
	 * <p>Silenziosa in ogni caso in cui non si possa fare: un advancement mancante — perche' un pack
	 * ha rimosso il datapack, perche' il file ha un refuso — non deve impedire l'estrazione di
	 * un'ombra. E' decorazione sopra una cosa che funziona, e va trattata come tale.
	 */
	public static void award(ServerPlayer player, String name) {
		Identifier id = AriseMod.id(name);
		AdvancementHolder advancement = player.level().getServer()
				.getAdvancements().get(id);

		if (advancement == null) {
			return;
		}

		player.getAdvancements().award(advancement, CRITERION);
	}
}
