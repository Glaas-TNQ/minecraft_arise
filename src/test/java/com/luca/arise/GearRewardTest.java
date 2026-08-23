package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.luca.arise.config.GearConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gear.GearUnique;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.Quest;
import com.mojang.serialization.JsonOps;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * I premi degli incarichi, e la regola che li rende premi.
 *
 * <p>Questo file esiste per un difetto trovato giocando, non compilando: l'incarico che apre
 * l'equipaggiamento regalava un pezzo tirato a caso fra <em>tutti</em> gli slot, e gli slot del
 * Cacciatore si aprono col rango. Un Cacciatore di rango E riceveva un paio di orecchini — che si
 * indossano dal rango C — e l'incarico subito dopo gli chiedeva di indossare qualcosa.
 *
 * <p>E' esattamente il genere di cosa che una prova prende in un millesimo di secondo e una partita
 * prende in un'ora, perche' in partita succede solo la meta' delle volte.
 */
class GearRewardTest {

	/** Abbastanza tiri perche' uno slot chiuso, se puo' uscire, esca. */
	private static final int ROLLS = 500;

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	@Test
	@DisplayName("un premio finisce sempre in una casella che il Cacciatore ha gia' aperto")
	void rewardsAreWearable() {
		GearConfig config = GearConfig.DEFAULT;
		RandomSource random = RandomSource.create(20260823L);

		for (Rank hunter : Rank.values()) {
			for (int i = 0; i < ROLLS; i++) {
				GearPiece piece = GearRoll.rollUsable(config, Rank.E, hunter, random);

				assertTrue(piece.slot().capacity(hunter) > 0,
						"a rango " + hunter + " e' uscito un pezzo per " + piece.slot()
								+ ", che li' non ha nemmeno una casella");
			}
		}
	}

	@Test
	@DisplayName("il tiro libero puo' invece uscire chiuso: e' il bottino, ed e' voluto")
	void lootMayBeOutOfReach() {
		GearConfig config = GearConfig.DEFAULT;
		RandomSource random = RandomSource.create(1L);
		boolean sawLocked = false;

		for (int i = 0; i < ROLLS && !sawLocked; i++) {
			sawLocked = GearRoll.rollAny(config, Rank.E, random).slot().capacity(Rank.E) == 0;
		}

		// Se un giorno questa prova fallisse vorrebbe dire che rollAny ha smesso di pescare fra
		// tutti gli slot: non e' un difetto, ma e' un cambiamento che va visto invece che subito.
		assertTrue(sawLocked, "rollAny non pesca piu' fra gli slot chiusi: il bottino e' cambiato");
	}

	@Test
	@DisplayName("l'Occhio dell'Oscurita' si indossa dal primo minuto")
	void theFirstGiftFitsEveryone() {
		GearUnique eye = GearUnique.byName("eye_of_darkness");

		assertNotNull(eye, "il pezzo unico del primo regalo non esiste piu' con questo nome");
		assertNotNull(eye.base().slot().vanillaSlot(),
				"il primo regalo sta in uno slot del Cacciatore, che al rango piu' basso puo' "
						+ "essere chiuso");

		for (Rank hunter : Rank.values()) {
			assertTrue(eye.base().slot().capacity(hunter) > 0,
					"a rango " + hunter + " l'Occhio non ha una casella");
		}
	}

	@Test
	@DisplayName("ogni incarico che promette un pezzo unico ne nomina uno che esiste")
	void questsNameRealUniques() {
		for (Quest quest : Quest.values()) {
			if (quest.unique() == null) {
				continue;
			}

			assertNotNull(GearUnique.byName(quest.unique()),
					quest + " promette il pezzo unico '" + quest.unique()
							+ "', che non esiste: il premio sparirebbe in silenzio");
		}
	}

	@Test
	@DisplayName("il pezzo unico ha dei modificatori, e vengono dalla config")
	void theUniqueIsBuiltFromConfig() {
		GearUnique eye = GearUnique.byName("eye_of_darkness");
		GearPiece piece = eye.piece(GearConfig.DEFAULT);

		assertFalse(piece.stats().isEmpty(), "un premio senza modificatori e' un premio vuoto");
		assertEquals(eye.rank(), piece.rank());
		assertTrue(piece.unique().isPresent(), "il pezzo non si ricorda di essere unico");

		// Due consegne, due oggetti distinti: il resto del sistema riconosce i pezzi dall'UUID.
		assertFalse(piece.id().equals(eye.piece(GearConfig.DEFAULT).id()),
				"due Occhi consegnati hanno lo stesso identificativo");
	}

	@Test
	@DisplayName("un pezzo unico sopravvive al salvataggio, e uno normale resta senza")
	void persistence() {
		roundTrip(GearUnique.byName("eye_of_darkness").piece(GearConfig.DEFAULT), "il pezzo unico");
		roundTrip(GearRoll.roll(GearConfig.DEFAULT, GearSlot.HEAD, Rank.C, RandomSource.create(7L)),
				"il pezzo tirato");
	}

	private static void roundTrip(GearPiece value, String what) {
		JsonElement written = GearPiece.CODEC.encodeStart(JsonOps.INSTANCE, value)
				.getOrThrow(message -> new AssertionError(what + " non si scrive: " + message));

		GearPiece read = GearPiece.CODEC.parse(JsonOps.INSTANCE, written)
				.getOrThrow(message -> new AssertionError(what + " non si rilegge: " + message));

		assertEquals(value, read, what + ": il giro completo ha cambiato qualcosa");
	}
}
