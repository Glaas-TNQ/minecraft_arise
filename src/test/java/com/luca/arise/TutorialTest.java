package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.AwakeningConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.tutorial.HeraldPage;
import com.luca.arise.tutorial.PlayerTutorial;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La prima ora: il discorso, il dato che lo ricorda e il posto in cui succede.
 *
 * <p>Le tre cose hanno in comune il fatto di essere aritmetica pura, e quindi di poter fallire in
 * silenzio. Un discorso che non finisce lascia un giocatore chiuso in una stanza; un dato che non
 * si rilegge glielo fa rifare da capo a ogni riavvio; una Sala nel posto sbagliato la costruisce
 * dentro un dungeon di qualcun altro, e quello si scopre soltanto quando succede.
 */
class TutorialTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	// ---------------------------------------------------------------- il discorso

	@Test
	@DisplayName("il discorso comincia dall'inizio, finisce, e finito resta finito")
	void chain() {
		PlayerTutorial tutorial = PlayerTutorial.INITIAL;

		assertEquals(HeraldPage.GREETING, tutorial.current(), "il discorso non comincia dal saluto");
		assertTrue(!tutorial.done(), "il discorso comincia gia' finito");

		for (int i = 0; i < HeraldPage.count(); i++) {
			assertNotNull(tutorial.current(), "pagina " + i + " mancante: il discorso si interrompe");
			tutorial = tutorial.next();
		}

		assertTrue(tutorial.done(), "dopo l'ultima pagina il discorso non risulta finito");
		assertNull(tutorial.current(), "finito il discorso, l'Araldo ha ancora qualcosa da dire");

		// Un clic in piu' non deve tornare indietro all'inizio: chi ha finito e riclicca esce.
		assertTrue(tutorial.next().done(), "un clic di troppo riapre il discorso");
	}

	@Test
	@DisplayName("ogni pagina ha una chiave sua")
	void pagesAreDistinct() {
		Set<String> names = new HashSet<>();

		for (HeraldPage page : HeraldPage.values()) {
			assertTrue(names.add(page.getSerializedName()),
					"due pagine con lo stesso nome: " + page.getSerializedName());
			assertNotNull(page.line(), page + " non ha una battuta");
		}
	}

	@Test
	@DisplayName("il saluto sopravvive al riavvio")
	void persistence() {
		PlayerTutorial value = new PlayerTutorial(3, true);

		JsonElement written = PlayerTutorial.CODEC.encodeStart(JsonOps.INSTANCE, value)
				.getOrThrow(message -> new AssertionError("non si scrive: " + message));
		PlayerTutorial read = PlayerTutorial.CODEC.parse(JsonOps.INSTANCE, written)
				.getOrThrow(message -> new AssertionError("non si rilegge: " + message));

		assertEquals(value, read, "il giro completo ha cambiato qualcosa");
	}

	// ---------------------------------------------------------------- la Sala

	@Test
	@DisplayName("chi arriva e chi aspetta stanno dentro la Sala, ai due capi")
	void geometry() {
		AwakeningConfig config = AwakeningConfig.DEFAULT;

		double arrivalZ = config.sanctumZ() - config.radius() + 2 + 0.5;
		double heraldZ = config.sanctumZ() + config.radius() - 2 + 0.5;

		assertTrue(arrivalZ > config.sanctumZ() - config.radius(),
				"chi si risveglia compare dentro il muro");
		assertTrue(heraldZ < config.sanctumZ() + config.radius(),
				"l'Araldo sta dentro il muro");
		assertTrue(heraldZ - arrivalZ > 6.0,
				"i due capi della Sala sono troppo vicini: non c'e' nessuna scena da attraversare");

		// L'imbardata zero guarda a sud, cioe' verso Z crescenti (CLAUDE.md). Se un giorno la Sala
		// venisse girata, questo e' cio' che si rompe per primo — e senza dare nessun errore.
		assertTrue(heraldZ > arrivalZ, "con imbardata zero si arriva dando le spalle all'Araldo");
	}

	@Test
	@DisplayName("la Sala non finisce dentro un varco di qualcun altro")
	void awayFromGates() {
		AwakeningConfig config = AriseConfig.createDefault().awakening();
		GateConfig gates = AriseConfig.createDefault().gates();

		// Le istanze dei Gate stanno a indice * regionSpacing con l'indice da zero in su: tutto il
		// semiasse negativo e' libero. Serve pero' che la Sala non tocchi l'istanza numero zero,
		// che sta sull'origine e si allarga al massimo di questo tanto.
		int worstCase = (gates.maxRooms() + gates.maxBranches()) * gates.cellSize();
		int nearestEdge = Math.abs(config.sanctumX()) - config.radius() - 1;

		assertTrue(config.sanctumX() < 0, "la Sala sta dalla parte dei varchi");
		assertTrue(nearestEdge > worstCase,
				"la Sala e' a " + nearestEdge + " blocchi dall'origine, e un varco puo' arrivare a "
						+ worstCase);
	}

	@Test
	@DisplayName("la Sala ha una porta, cioe' un lato dispari con un centro esatto")
	void side() {
		AwakeningConfig config = AwakeningConfig.DEFAULT;

		assertEquals(config.radius() * 2 + 1, config.side(), "il lato non torna col raggio");
		assertTrue(config.side() % 2 == 1, "un lato pari non ha un blocco centrale");
		assertTrue(config.height() >= 4, "un soffitto sotto i quattro blocchi si sente addosso");
	}
}
