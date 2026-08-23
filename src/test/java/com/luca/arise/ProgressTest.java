package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.HunterConfig;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Rank;
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

	// ---------------------------------------------------------------- l'Abisso

	@Test
	@DisplayName("l'Abisso si scende un gradino per volta, e il record non peggiora mai")
	void abyssRecordOnlyImproves() {
		var record = com.luca.arise.gate.AbyssRecord.NONE;

		assertEquals(1, record.next(), "chi non e' mai sceso comincia dal primo");

		record = record.with(1, 900L);
		assertEquals(1, record.deepest());
		assertEquals(900L, record.bestTicks());
		assertEquals(2, record.next());

        // Una discesa piu' profonda ma piu' lenta alza la profondita' e lascia stare il tempo.
		record = record.with(2, 1500L);
		assertEquals(2, record.deepest());
		assertEquals(900L, record.bestTicks(), "un tempo peggiore non deve sovrascrivere il record");

		// E una piu' veloce ma meno profonda fa l'opposto.
		record = record.with(1, 400L);
		assertEquals(2, record.deepest(), "richiudere un gradino gia' fatto non abbassa la profondita'");
		assertEquals(400L, record.bestTicks());
	}

	@Test
	@DisplayName("il rango della discesa sale e si ferma a S, e le regole si accumulano")
	void abyssScalesByRules() {
		var rankAt = (java.util.function.IntFunction<com.luca.arise.progress.Rank>)
				com.luca.arise.gate.Abyss::rankAt;

		assertEquals(com.luca.arise.progress.Rank.E, rankAt.apply(1));
		assertEquals(com.luca.arise.progress.Rank.S, rankAt.apply(16));

		// Oltre il rango S non si inventa una tabella di mob peggiori: da li' in giu' a crescere
		// sono soltanto le regole, che e' esattamente il punto del blocco.
		assertEquals(com.luca.arise.progress.Rank.S, rankAt.apply(100));

		assertTrue(com.luca.arise.gate.Abyss.rulesAt(1).isEmpty(),
				"il primo gradino deve essere un varco normale");

		int previous = 0;
		for (int depth = 1; depth <= 30; depth++) {
			int rules = com.luca.arise.gate.Abyss.rulesAt(depth).size();

			assertTrue(rules >= previous, "gradino " + depth + ": una regola e' sparita");
			previous = rules;
		}

		assertEquals(com.luca.arise.gate.AbyssRule.values().length,
				com.luca.arise.gate.Abyss.rulesAt(25).size(),
				"al venticinquesimo devono esserci tutte");
	}

	@Test
	@DisplayName("la stessa profondita' da' sempre la stessa pianta: il record confronta qualcosa")
	void abyssIsTheSameDescentForEveryone() {
		for (int depth = 1; depth <= 12; depth++) {
			assertEquals(com.luca.arise.gate.Abyss.seedFor(depth),
					com.luca.arise.gate.Abyss.seedFor(depth),
					"il seme di un gradino deve essere fisso");
		}

		// E diverso da quello di ogni altro gradino, o due profondita' sarebbero lo stesso varco.
		var seeds = new java.util.HashSet<Long>();
		for (int depth = 1; depth <= 50; depth++) {
			assertTrue(seeds.add(com.luca.arise.gate.Abyss.seedFor(depth)),
					"il gradino " + depth + " ripete il seme di un altro");
		}
	}

	// ---------------------------------------------------------------- la giornaliera

	@Test
	@DisplayName("i contatori si fermano al bersaglio, e la giornata si chiude solo con tutti e quattro")
	void dailyCountersStopAtTheTarget() {
		var config = com.luca.arise.config.DailyConfig.DEFAULT;
		var daily = com.luca.arise.daily.DailyQuest.forDay(3L);

		assertEquals(4, daily.remaining(config), "all'alba sono tutti e quattro aperti");
		assertFalse(daily.complete(config));

		for (var task : com.luca.arise.daily.DailyTask.values()) {
			// Il doppio del bersaglio: il contatore deve fermarsi, perche' il numero che il
			// giocatore legge e' "cento su cento", non "duecento su cento".
			daily = daily.with(task, task.target(config) * 2, config);
			assertEquals(task.target(config), daily.progress(task),
					task + ": il contatore deve fermarsi al bersaglio");
		}

		assertEquals(0, daily.remaining(config));
		assertTrue(daily.complete(config), "con tutti e quattro pieni la giornata e' chiusa");
	}

	@Test
	@DisplayName("chi non ha mai visto un'alba non ha una giornata da saldare")
	void aFreshHunterOwesNothing() {
		var none = com.luca.arise.daily.DailyQuest.NONE;

		// Il giorno negativo e' il segnaposto di chi non ha mai giocato: mandarlo nella Zona di
		// Penalita' al primo login sarebbe punirlo per una giornata che nessuno gli ha chiesto.
		assertTrue(none.day() < 0, "il segnaposto deve essere riconoscibile");
		assertFalse(none.settled());
		assertEquals(0, none.progress(com.luca.arise.daily.DailyTask.BLOCKS));
	}

	@Test
	@DisplayName("la giornata saldata resta saldata, e i contatori non si perdono")
	void settlingKeepsTheCounters() {
		var config = com.luca.arise.config.DailyConfig.DEFAULT;
		var daily = com.luca.arise.daily.DailyQuest.forDay(7L)
				.with(com.luca.arise.daily.DailyTask.BLOCKS, 40, config)
				.withSettled();

		assertTrue(daily.settled());
		assertEquals(40, daily.progress(com.luca.arise.daily.DailyTask.BLOCKS),
				"saldare non deve azzerare quello che era stato fatto");
		assertEquals(7L, daily.day());
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

	@Test
	@DisplayName("ogni promozione di rango apre almeno una casella: il messaggio non deve mentire")
	void everyRankOpensSomething() {
		HunterConfig hunter = AriseConfig.createDefault().hunter();

		// Il messaggio di promozione elenca cosa si e' aperto. Se un rango non aprisse niente,
		// l'elenco sarebbe vuoto e la promozione suonerebbe come una scena senza contenuto.
		for (Rank rank : Rank.values()) {
			if (rank.ordinal() == 0) {
				continue;
			}

			Rank previous = Rank.values()[rank.ordinal() - 1];
			int opened = 0;

			for (GearSlot slot : GearSlot.values()) {
				opened += Math.max(0, slot.capacity(rank) - slot.capacity(previous));
			}

			assertTrue(opened > 0, "il rango " + rank + " non apre nessuna casella");
		}

		// E la scala dev'essere raggiungibile: il rango piu' alto entro il livello massimo.
		assertEquals(Rank.values()[Rank.values().length - 1],
				hunter.rank(AriseConfig.createDefault().maxLevel()),
				"il rango piu' alto deve essere raggiungibile prima del livello massimo");
	}

	@Test
	@DisplayName("il rango sale col livello e non scende mai")
	void rankIsMonotonic() {
		HunterConfig hunter = AriseConfig.createDefault().hunter();
		int highest = 0;
		int promotions = 0;

		for (int level = 1; level <= AriseConfig.createDefault().maxLevel(); level++) {
			int here = hunter.rank(level).ordinal();
			assertTrue(here >= highest, "il rango e' sceso al livello " + level);

			if (here > highest) {
				promotions++;
				highest = here;
			}
		}

		// Sei promozioni: E parte gia' addosso, e da li' si sale fino a S.
		assertEquals(Rank.values().length - 1, promotions);
	}
}
