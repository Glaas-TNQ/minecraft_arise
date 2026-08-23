package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Stat;
import com.luca.arise.progress.StatThreshold;

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

	// ---------------------------------------------------------------- le soglie

	@Test
	@DisplayName("ogni statistica spendibile ha tre soglie, e stanno tutte sotto il suo tetto")
	void everySpendableStatHasThresholds() {
		var config = AriseConfig.createDefault();

		for (Stat stat : Stat.SPENDABLE) {
			var thresholds = StatThreshold.of(stat);

			assertEquals(StatThreshold.STEPS.size(), thresholds.size(),
					stat + " deve avere una soglia per ogni gradino");

			int previous = 0;
			for (var threshold : thresholds) {
				assertTrue(threshold.points() > previous,
						stat + ": le soglie devono salire, " + threshold + " no");
				assertTrue(threshold.points() <= config.cap(stat),
						threshold + " sta sopra il tetto di " + stat + ": nessuno la vedrebbe mai");
				previous = threshold.points();
			}
		}
	}

	@Test
	@DisplayName("nessuna statistica non spendibile porta soglie che nessuno potrebbe raggiungere")
	void noThresholdsOnUnspendableStats() {
		for (Stat stat : Stat.values()) {
			if (Stat.SPENDABLE.contains(stat)) {
				continue;
			}

			// Le altre otto arrivano solo dall'equipaggiamento, e una soglia raggiungibile
			// indossando un anello sarebbe una soglia che si perde togliendolo.
			assertTrue(StatThreshold.of(stat).isEmpty(),
					stat + " non e' spendibile: non puo' avere soglie");
		}
	}

	@Test
	@DisplayName("la prossima soglia e' sempre quella giusta, e a un certo punto finiscono")
	void nextThresholdWalksForward() {
		assertEquals(StatThreshold.VITALITY_HUNGER, StatThreshold.next(Stat.VITALITY, 0));
		assertEquals(StatThreshold.VITALITY_HUNGER, StatThreshold.next(Stat.VITALITY, 24));
		assertEquals(StatThreshold.VITALITY_FALL, StatThreshold.next(Stat.VITALITY, 25));
		assertEquals(StatThreshold.VITALITY_LAST_STAND, StatThreshold.next(Stat.VITALITY, 99));
		assertEquals(null, StatThreshold.next(Stat.VITALITY, 100),
				"a soglie finite la barra deve tornare a misurare il tetto");
	}

	@Test
	@DisplayName("le soglie di Forza danno posti in campo, e solo quelle che li promettono")
	void strengthThresholdsGrantSlots() {
		int base = AriseConfig.createDefault().shadows().maxSummoned();
		PlayerProgress progress = PlayerProgress.INITIAL.withLevel(100, 0L, 297);

		assertEquals(base, StatThreshold.summonLimit(progress, base),
				"senza punti spesi il tetto resta quello di tutti");

		PlayerProgress first = progress.withStatIncreased(Stat.STRENGTH, 25);
		assertEquals(base + 1, StatThreshold.summonLimit(first, base));

		// La soglia di mezzo fa piu' forte l'esercito, non piu' grande: se desse un posto anche
		// lei, i tre gradini di Forza direbbero tutti la stessa cosa.
		PlayerProgress middle = progress.withStatIncreased(Stat.STRENGTH, 50);
		assertEquals(base + 1, StatThreshold.summonLimit(middle, base),
				"la soglia di mezzo non concede posti");

		PlayerProgress full = progress.withStatIncreased(Stat.STRENGTH, 100);
		assertEquals(base + 2, StatThreshold.summonLimit(full, base));
	}

	@Test
	@DisplayName("il respec toglie anche le soglie: cio' che si e' scelto si puo' disfare")
	void respecTakesTheThresholdsBack() {
		int base = AriseConfig.createDefault().shadows().maxSummoned();
		PlayerProgress full = PlayerProgress.INITIAL.withLevel(100, 0L, 297)
				.withStatIncreased(Stat.STRENGTH, 100);

		assertEquals(base + 2, StatThreshold.summonLimit(full, base));
		assertEquals(base, StatThreshold.summonLimit(full.withStatsReset(), base),
				"dopo il respec le soglie devono cadere insieme ai punti che le reggevano");
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
