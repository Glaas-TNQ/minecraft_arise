package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.MarketConfig;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La config: che si scriva e si rilegga identica, e che i numeri stiano in piedi.
 *
 * <p>Il giro completo — record → JSON → record — e' la prova piu' economica che esista per un
 * codec, e vale il doppio qui: la radice e' arrivata al limite dei sedici campi che
 * {@code RecordCodecBuilder} consente, e ogni sistema nuovo si annida da qualche parte. Un campo
 * dimenticato nel codec non da' errore: da' una voce che sparisce dal file al primo salvataggio,
 * e un giocatore che non capisce perche' le sue modifiche non durano.
 */
class ConfigTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	@Test
	@DisplayName("i valori di default si scrivono e si rileggono identici")
	void defaultsSurviveTheRoundTrip() {
		AriseConfig original = AriseConfig.createDefault();

		JsonElement written = AriseConfig.CODEC.encodeStart(JsonOps.INSTANCE, original)
				.getOrThrow(message -> new AssertionError("non si scrive: " + message));

		AriseConfig read = AriseConfig.CODEC.parse(JsonOps.INSTANCE, written)
				.getOrThrow(message -> new AssertionError("non si rilegge: " + message));

		assertEquals(original, read, "il giro completo ha cambiato qualcosa");
	}

	@Test
	@DisplayName("ogni sistema ha la sua voce nel file scritto")
	void everySystemHasItsSection() {
		JsonObject json = AriseConfig.CODEC
				.encodeStart(JsonOps.INSTANCE, AriseConfig.createDefault())
				.getOrThrow(message -> new AssertionError(message))
				.getAsJsonObject();

		for (String section : new String[] {"shadows", "gates", "abilities", "fx", "cities", "hunter"}) {
			assertTrue(json.has(section), "manca la sezione " + section);
		}

		JsonObject hunter = json.getAsJsonObject("hunter");
		for (String section : new String[] {"gear", "shop", "gems", "workshop"}) {
			assertTrue(hunter.has(section), "manca hunter." + section);
		}

		assertTrue(json.getAsJsonObject("cities").has("market"), "manca cities.market");
		assertTrue(json.getAsJsonObject("shadows").has("downtime_ticks"),
				"manca shadows.downtime_ticks");
	}

	@Test
	@DisplayName("le scorciatoie annidate puntano davvero alle voci annidate")
	void shortcutsPointWhereTheySay() {
		AriseConfig config = AriseConfig.createDefault();

		assertEquals(config.hunter().gear(), config.gear());
		assertEquals(config.hunter().shop(), config.shop());
		assertEquals(config.hunter().gems(), config.gems());
		assertEquals(config.hunter().workshop(), config.workshop());
		assertEquals(config.cities().market(), config.market());
	}

	@Test
	@DisplayName("cambiare la moneta al Banco costa sempre qualcosa")
	void theBankNeverPaysMoreThanItTakes() {
		MarketConfig market = MarketConfig.DEFAULT;

		assertTrue(market.redeemRate() > 0.0, "un ritiro che non rende niente e' una confisca");
		assertTrue(market.redeemRate() < 1.0,
				"un ritiro che rende tutto rende il Banco un parcheggio senza rischio");

		for (int coins = 1; coins <= 256; coins++) {
			long spent = (long) coins * market.coinValue();
			long back = market.redeemValue(coins);

			assertTrue(back >= 0, coins + " monete: il ritiro non puo' essere negativo");
			assertTrue(back < spent, coins + " monete: coniare e ritirare ha fatto guadagnare");
		}

		assertEquals(0L, market.redeemValue(0), "senza monete non si riceve niente");
	}

	@Test
	@DisplayName("il tetto dell'esercito cresce col livello e parte da qualcosa")
	void armyCapacityGrows() {
		var shadows = AriseConfig.createDefault().shadows();
		int previous = 0;

		assertTrue(shadows.capacityAt(1) > 0, "al livello uno si deve poter tenere almeno un'ombra");

		for (int level = 1; level <= 100; level++) {
			int capacity = shadows.capacityAt(level);
			assertTrue(capacity >= previous, "il tetto e' sceso al livello " + level);
			previous = capacity;
		}

		assertTrue(shadows.capacityAt(100) > shadows.capacityAt(1),
				"cento livelli devono valere piu' di uno");
	}

	@Test
	@DisplayName("la probabilita' di estrazione sale col livello e non arriva mai a uno")
	void extractionChanceStaysAChance() {
		var shadows = AriseConfig.createDefault().shadows();
		double previous = 0.0;

		for (int level = 1; level <= 200; level++) {
			double chance = shadows.extractionChanceAt(level);

			assertTrue(chance >= previous, "la probabilita' e' scesa al livello " + level);
			assertTrue(chance < 1.0, "livello " + level + ": l'estrazione non deve mai essere certa");
			previous = chance;
		}
	}

	@Test
	@DisplayName("al livello massimo il tetto dell'estrazione e' quasi raggiunto")
	void extractionCeilingIsReachable() {
		var config = AriseConfig.createDefault();
		var shadows = config.shadows();

		double atCap = shadows.extractionChanceAt(config.maxLevel());

		// Un tetto che nessuno puo' toccare non e' un tetto: con 0,005 a livello servivano
		// centoquarantuno livelli su cento disponibili, e la costante era codice morto. La prova
		// non fissa il numero — fissa il rapporto fra il tetto e il livello massimo, che e' la
		// cosa che si rompe se qualcuno ritocca la curva senza guardare.
		assertTrue(atCap > 0.90,
				"al livello massimo l'estrazione deve sfiorare il tetto, invece vale " + atCap);
		assertTrue(atCap <= 0.95,
				"il tetto resta un tetto: nessun livello puo' superarlo");
	}

	@Test
	@DisplayName("un varco che cede riversa quello che avresti dovuto affrontare comunque")
	void breachIsProportionate() {
		var gates = AriseConfig.createDefault().gates();
		var spawn = gates.spawn();

		assertTrue(spawn.breakChance() > 0.0 && spawn.breakChance() < 1.0,
				"a uno la rottura diventa un promemoria, a zero il mondo torna senza conseguenze");
		assertTrue(spawn.breakWarningTicks() > 0 && spawn.breakWarningTicks() < spawn.lifetimeTicks(),
				"il preavviso deve stare dentro la vita del varco, o non e' un preavviso");

		int previous = 0;
		for (com.luca.arise.progress.Rank rank : com.luca.arise.progress.Rank.values()) {
			int wave = gates.mobsPerRoom(rank) * spawn.breakWaves();

			assertTrue(wave >= previous, "un varco di rango piu' alto non puo' riversare di meno");
			assertTrue(wave > 0, "un varco che cede senza far uscire niente non e' ceduto");
			previous = wave;
		}
	}

	@Test
	@DisplayName("il cantiere delle citta' ha un tetto di tempo, non solo di blocchi")
	void cityBuildingIsBounded() {
		var cities = AriseConfig.createDefault().cities();

		assertTrue(cities.msPerTick() > 0, "senza scadenza il watchdog spegne il server");
		assertTrue(cities.msPerTick() < 50,
				"una fetta piu' lunga del battito lascerebbe il gioco senza tempo");
		assertTrue(cities.blocksPerTick() > 0, "un cantiere fermo non finisce mai");
		assertTrue(cities.size() > 0 && cities.blockSize() > cities.roadWidth(),
				"un isolato deve essere piu' largo della strada che lo circonda");
	}
}
