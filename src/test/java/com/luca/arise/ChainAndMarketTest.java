package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.city.CityMarket;
import com.luca.arise.npc.Shopkeeper;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Quest;
import com.luca.arise.quest.Unlock;
import com.luca.arise.shadow.ShadowDowntime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La catena degli incarichi, il recupero delle ombre e la pianta del mercato. */
class ChainAndMarketTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	// ---------------------------------------------------------------- la catena

	@Test
	@DisplayName("seguendo la catena si aprono tutti i sistemi, uno alla volta")
	void walkingTheChainOpensEverything() {
		PlayerQuests state = PlayerQuests.INITIAL;
		Set<Unlock> opened = EnumSet.noneOf(Unlock.class);

		assertNotNull(state.current(), "all'inizio ci deve essere un compito");

		for (int step = 0; step < Quest.count(); step++) {
			Quest quest = state.current();
			assertNotNull(quest, "la catena si e' fermata al passo " + step);

			// Prima di completarlo, il sistema che concede non deve essere gia' aperto.
			assertFalse(state.has(quest.grants()),
					quest + " concede " + quest.grants() + ", che risulta gia' aperto");

			state = state.next();
			opened.add(quest.grants());

			assertTrue(state.has(quest.grants()),
					quest + ": il sistema concesso non risulta aperto subito dopo");
		}

		assertTrue(state.finished(), "dopo tutti gli incarichi la catena deve dirsi finita");
		assertNull(state.current(), "a catena finita non c'e' piu' un compito");

		for (Unlock unlock : Unlock.values()) {
			assertTrue(opened.contains(unlock),
					unlock + " non viene aperto da nessun incarico: resta chiuso per sempre");
		}
	}

	@Test
	@DisplayName("un sistema non risulta mai aperto prima del suo incarico")
	void nothingOpensEarly() {
		for (int index = 0; index <= Quest.count(); index++) {
			PlayerQuests state = new PlayerQuests(index, 0);

			for (int i = 0; i < Quest.count(); i++) {
				Quest quest = Quest.values()[i];
				boolean shouldBeOpen = i < index;

				assertEquals(shouldBeOpen, state.has(quest.grants()),
						"a incarico " + index + ", " + quest.grants()
								+ " dovrebbe essere " + (shouldBeOpen ? "aperto" : "chiuso"));
			}
		}
	}

	@Test
	@DisplayName("nessun incarico chiede qualcosa che non si puo' fare")
	void everyQuestAsksForSomethingPossible() {
		for (Quest quest : Quest.values()) {
			assertTrue(quest.amount() > 0, quest + ": un compito da zero volte e' gia' finito");
			assertNotNull(quest.objective(), quest + ": senza obiettivo non si completa mai");
			assertNotNull(quest.grants(), quest + ": ogni incarico deve aprire qualcosa");
		}
	}

	// ---------------------------------------------------------------- il recupero

	@Test
	@DisplayName("un'ombra caduta torna disponibile quando scade il suo tempo, non prima")
	void downtimeExpiresOnTime() {
		UUID shadow = UUID.randomUUID();
		long now = 1000L;

		ShadowDowntime empty = ShadowDowntime.EMPTY;
		assertFalse(empty.isDown(shadow, now), "senza voci nessuno e' a terra");
		assertEquals(0L, empty.remaining(shadow, now));

		ShadowDowntime down = empty.with(shadow, now + 1200L, now);
		assertTrue(down.isDown(shadow, now), "appena caduta deve risultare a terra");
		assertEquals(1200L, down.remaining(shadow, now));
		assertEquals(1L, down.remaining(shadow, now + 1199L), "un tick prima e' ancora a terra");
		assertEquals(0L, down.remaining(shadow, now + 1200L), "allo scadere e' pronta");
		assertFalse(down.isDown(shadow, now + 1200L));
		assertFalse(down.isDown(shadow, now + 5000L), "molto dopo, a maggior ragione");
	}

	@Test
	@DisplayName("le voci scadute spariscono, quelle vive restano")
	void pruningKeepsOnlyTheLiving() {
		UUID older = UUID.randomUUID();
		UUID newer = UUID.randomUUID();

		ShadowDowntime state = ShadowDowntime.EMPTY
				.with(older, 500L, 0L)
				.with(newer, 2000L, 0L);

		assertEquals(2, state.entries().size());

		ShadowDowntime pruned = state.pruned(1000L);
		assertEquals(1, pruned.entries().size(), "la voce scaduta doveva sparire");
		assertTrue(pruned.isDown(newer, 1000L), "quella viva doveva restare");
		assertFalse(pruned.isDown(older, 1000L));

		assertEquals(pruned, pruned.pruned(1000L), "ripulire due volte non cambia niente");
	}

	@Test
	@DisplayName("la stessa ombra non compare due volte fra quelle a terra")
	void aShadowFallsOnlyOnce() {
		UUID shadow = UUID.randomUUID();

		ShadowDowntime state = ShadowDowntime.EMPTY
				.with(shadow, 1000L, 0L)
				.with(shadow, 3000L, 0L);

		assertEquals(1, state.entries().size(), "due cadute, una voce sola");
		assertEquals(3000L, state.remaining(shadow, 0L), "vale l'ultima caduta");
	}

	// ---------------------------------------------------------------- il mercato

	@Test
	@DisplayName("le nove botteghe ci sono tutte, una per mestiere, e non si sovrappongono")
	void everyShopHasItsPlaceAndKeepsIt() {
		assertEquals(Shopkeeper.values().length, CityMarket.STALLS.size(),
				"ogni mestiere deve avere la sua bottega");

		Set<Shopkeeper> roles = new HashSet<>();
		for (CityMarket.Stall stall : CityMarket.STALLS) {
			assertTrue(roles.add(stall.role()), stall.role() + " ha due botteghe");
		}

		// Sette blocchi di lato: due botteghe piu' vicine di sette si compenetrano.
		for (int i = 0; i < CityMarket.STALLS.size(); i++) {
			for (int j = i + 1; j < CityMarket.STALLS.size(); j++) {
				CityMarket.Stall a = CityMarket.STALLS.get(i);
				CityMarket.Stall b = CityMarket.STALLS.get(j);

				boolean apart = Math.abs(a.dx() - b.dx()) >= 7 || Math.abs(a.dz() - b.dz()) >= 7;
				assertTrue(apart, a.role() + " e " + b.role() + " si sovrappongono");
			}
		}
	}

	@Test
	@DisplayName("chi vende sta dentro la sua bottega, dietro il bancone e non dentro un muro")
	void theShopkeeperStandsBehindTheCounter() {
		int centreX = 0;
		int centreZ = 0;
		int floor = 64;

		for (CityMarket.Stall stall : CityMarket.STALLS) {
			double[] spot = CityMarket.standing(stall, centreX, floor, centreZ);

			double dx = spot[0] - (centreX + stall.dx() + 0.5);
			double dz = spot[2] - (centreZ + stall.dz() + 0.5);

			// L'interno utile e' il 5x5 dentro il 7x7: oltre due blocchi dal centro c'e' il muro.
			assertTrue(Math.abs(dx) <= 2.0 && Math.abs(dz) <= 2.0,
					stall.role() + ": sta dentro un muro (" + dx + ", " + dz + ")");

			// E deve stare dal lato opposto alla porta, cioe' controvento rispetto allo sguardo.
			if (stall.facing() == 0.0F) {
				assertEquals(-2.0, dz, 1.0E-9, stall.role() + ": guarda a sud, deve stare a nord");
			} else if (stall.facing() == 180.0F) {
				assertEquals(2.0, dz, 1.0E-9, stall.role() + ": guarda a nord, deve stare a sud");
			} else if (stall.facing() == 90.0F) {
				assertEquals(2.0, dx, 1.0E-9, stall.role() + ": guarda a ovest, deve stare a est");
			} else {
				assertEquals(-2.0, dx, 1.0E-9, stall.role() + ": guarda a est, deve stare a ovest");
			}

			assertEquals(floor, spot[1], 1.0E-9, stall.role() + ": deve stare sul pavimento");
		}
	}

	@Test
	@DisplayName("la piazza e' abbastanza larga da contenere il quartiere")
	void thePlazaFitsTheMarket() {
		int half = CityMarket.plazaHalf();

		for (CityMarket.Stall stall : CityMarket.STALLS) {
			// Il bordo esterno della bottega, piu' i tre blocchi di mezzo lato.
			assertTrue(Math.abs(stall.dx()) + 3 <= half,
					stall.role() + ": sborda dalla piazza lungo X");
			assertTrue(Math.abs(stall.dz()) + 3 <= half,
					stall.role() + ": sborda dalla piazza lungo Z");
		}
	}

	@Test
	@DisplayName("nessuna bottega finisce sopra l'Associazione")
	void noShopSitsOnTheHeadquarters() {
		// L'Associazione e' 29x29 al centro della piazza: mezzo lato quattordici, piu' i tre di
		// mezza bottega, fa diciassette blocchi da tenere liberi.
		for (CityMarket.Stall stall : CityMarket.STALLS) {
			boolean clear = Math.abs(stall.dx()) >= 18 || Math.abs(stall.dz()) >= 18;
			assertTrue(clear, stall.role() + " e' costruita addosso all'Associazione");
		}
	}
}
