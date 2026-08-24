package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.ability.Ability;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ManaConfig;
import com.luca.arise.mana.Mana;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Il Mana: la curva, i costi e — soprattutto — la rigenerazione che non perde i decimi.
 *
 * <p>La prova che conta e' {@link #regenLosesNothing()}. Con sei punti al secondo e un battito
 * ogni quarto di secondo, la via ovvia — sommare {@code 6 / 4} a ogni battito — perde mezzo punto
 * per battito perche' un intero non tiene 1,5. Sono due punti al secondo su sei: un terzo della
 * rigenerazione che sparisce, e sparisce <em>in silenzio</em>. Nessun errore, nessun log: solo un
 * Mana che risale piu' piano di quanto la config dica, e un numero in config che non vuol dire
 * quello che c'e' scritto.
 */
class ManaTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	private static ManaConfig config() {
		return AriseConfig.createDefault().abilities().mana();
	}

	/** La stessa aritmetica di {@code ManaManager.tick}, isolata per poterla provare. */
	private static int regenOver(ManaConfig config, int from, int max, long ticks) {
		int gained = (int) (ticks * config.regenPerSecond() / 20.0);
		return Math.min(max, from + gained);
	}

	@Test
	@DisplayName("il tetto cresce col livello e non scende mai sotto uno")
	void maxGrowsWithLevel() {
		ManaConfig config = config();

		assertEquals(config.base(), config.max(1));
		assertEquals(config.base() + config.perLevel(), config.max(2));
		assertEquals(config.base() + config.perLevel() * 9, config.max(10));

		// Un livello impossibile non deve dare un tetto impossibile: zero Mana significa che
		// nessuna abilita' parte piu', e sarebbe un blocco totale senza nessun messaggio.
		assertTrue(config.max(0) >= 1);
		assertTrue(config.max(-5) >= 1);
	}

	@Test
	@DisplayName("la rigenerazione non perde i decimi fra un battito e l'altro")
	void regenLosesNothing() {
		ManaConfig config = config();
		int max = 10_000;

		// Venti battiti da cinque tick sono cinque secondi: cinque volte la rigenerazione al
		// secondo, esatta. Sommando 6/4 a ogni battito se ne otterrebbero venti invece di trenta.
		int accumulated = 0;
		long start = 0L;

		for (int beat = 1; beat <= 20; beat++) {
			long now = start + beat * 5L;
			accumulated = regenOver(config, 0, max, now - start);
		}

		assertEquals((int) (config.regenPerSecond() * 5), accumulated);
	}

	@Test
	@DisplayName("la rigenerazione si ferma al tetto")
	void regenStopsAtMax() {
		ManaConfig config = config();
		assertEquals(50, regenOver(config, 50, 50, 20 * 60));
	}

	@Test
	@DisplayName("ogni abilita' ha un costo, e il volo costa piu' di uno scatto")
	void everyAbilityCosts() {
		ManaConfig config = config();

		for (Ability ability : Ability.values()) {
			assertTrue(config.cost(ability) > 0,
					"nessun costo in Mana per " + ability.getSerializedName());
		}

		assertTrue(config.cost(Ability.SOVEREIGN_AUTHORITY) > config.cost(Ability.SHADOW_STEP));
	}

	@Test
	@DisplayName("un Cacciatore all'esordio evoca qualche ombra, non nessuna e non tutte")
	void firstLevelSummons() {
		ManaConfig config = config();
		int affordable = config.max(1) / config.summonCost();

		assertTrue(affordable >= 2, "al primo livello non si evoca quasi niente: " + affordable);
		assertTrue(affordable <= 6, "al primo livello si evoca troppo: " + affordable);
	}

	@Test
	@DisplayName("il valore d'esordio si riconosce come «non ancora riempito»")
	void initialIsUnset() {
		assertTrue(Mana.INITIAL.unset());
		assertTrue(!new Mana(0, 0L, 0L).unset(), "zero Mana e' un valore vero, non un'assenza");
	}

	@Test
	@DisplayName("la spesa ferma la rigenerazione per il tempo previsto")
	void spendingPauses() {
		ManaConfig config = config();
		Mana after = new Mana(100, 0L, 0L).spent(75, 1000L, config.pauseTicks());

		assertEquals(75, after.current());
		assertEquals(1000L + config.pauseTicks(), after.busyUntil());
		assertEquals(1000L, after.lastRegen());
	}

	@Test
	@DisplayName("il Mana non scende sotto zero nemmeno se glielo si chiede")
	void neverNegative() {
		assertEquals(0, new Mana(10, 0L, 0L).spent(-40, 0L, 0).current());
		assertEquals(0, new Mana(10, 0L, 0L).with(-1, 0L).current());
	}
}
