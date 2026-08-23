package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Stat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le regole del Sistema che sono aritmetica pura, e quindi si provano senza aprire il gioco.
 *
 * <p>La piu' importante e' la conservazione dei punti. Un giocatore accumula duecentonovantasette
 * punti in cento livelli e non ne tiene il conto a mano: se il respec ne regalasse uno o ne
 * mangiasse uno, nessuno se ne accorgerebbe mai, e la progressione andrebbe alla deriva in
 * silenzio. E' esattamente il genere di difetto che non da' nessun errore.
 */
class ProgressTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	@Test
	@DisplayName("il respec restituisce i punti spesi senza crearne ne' perderne")
	void respecConservesPoints() {
		PlayerProgress progress = PlayerProgress.INITIAL
				.withLevel(40, 0L, 120)
				.withStatIncreased(Stat.VITALITY, 40)
				.withStatIncreased(Stat.STRENGTH, 25)
				.withStatIncreased(Stat.AGILITY, 12);

		int before = progress.unspentPoints() + progress.spentPoints();
		assertEquals(120, before, "il totale di partenza deve restare quello del livello");
		assertEquals(77, progress.spentPoints());

		PlayerProgress after = progress.withStatsReset();

		assertEquals(before, after.unspentPoints() + after.spentPoints(),
				"il respec non deve creare ne' distruggere punti");
		assertEquals(0, after.spentPoints(), "dopo il respec non deve restare niente di speso");
		assertEquals(120, after.unspentPoints());
	}

	@Test
	@DisplayName("il respec non tocca livello, esperienza e soul coin")
	void respecKeepsEverythingElse() {
		PlayerProgress progress = PlayerProgress.INITIAL
				.withLevel(55, 4200L, 30)
				.withSouls(9000L)
				.withStatIncreased(Stat.ENDURANCE, 30);

		PlayerProgress after = progress.withStatsReset();

		// E' la meta' del contratto che l'oggetto promette nel tooltip, ed e' anche la meta' che
		// spaventa: chi legge "restituisce i punti" ha tutto il diritto di temere per il livello.
		assertEquals(progress.level(), after.level());
		assertEquals(progress.xp(), after.xp());
		assertEquals(progress.souls(), after.souls());
	}

	@Test
	@DisplayName("chi non ha speso niente non ha niente da rimpiangere")
	void respecOnAFreshHunterChangesNothing() {
		PlayerProgress fresh = PlayerProgress.INITIAL.withLevel(10, 0L, 27);

		assertEquals(0, fresh.spentPoints());
		assertEquals(fresh.unspentPoints(), fresh.withStatsReset().unspentPoints());
	}

	@Test
	@DisplayName("un progresso e' immutabile: spendere produce un'istanza nuova")
	void spendingDoesNotMutate() {
		PlayerProgress progress = PlayerProgress.INITIAL.withLevel(20, 0L, 57);
		PlayerProgress spent = progress.withStatIncreased(Stat.VITALITY, 10);

		// La regola §5 del progetto in una riga: le Data Attachment persistono e sincronizzano solo
		// se il valore e' immutabile e riassegnato. Una mutazione sul posto fallirebbe in silenzio.
		assertEquals(0, progress.stat(Stat.VITALITY), "l'istanza di partenza non deve cambiare");
		assertEquals(10, spent.stat(Stat.VITALITY));
		assertTrue(progress != spent);
		assertSame(PlayerProgress.INITIAL, PlayerProgress.INITIAL);
	}
}
