package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luca.arise.config.ShadowConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.workshop.Catalyst;
import com.luca.arise.workshop.LooseSoul;
import com.luca.arise.workshop.SoulTrait;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Le anime: quanto valgono, quanto crescono, e il patto con le ombre. */
class SoulTest {

	private static final List<Double> THRESHOLDS = ShadowConfig.DEFAULT.rankThresholds();

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	private static LooseSoul soul(double health, double damage) {
		return LooseSoul.of(Identifier.withDefaultNamespace("zombie"), health, damage);
	}

	@Test
	@DisplayName("un'anima e l'ombra che ne nasce dicono lo stesso rango")
	void soulAndShadowAgreeOnRank() {
		// E' scritto nei commenti di mezza Officina, ed e' l'unica cosa che rende credibile
		// l'arruolamento: se il tooltip dice rango C e l'esercito rango B, uno dei due mente.
		for (double health = 8.0; health <= 320.0; health += 13.0) {
			for (double damage = 1.0; damage <= 40.0; damage += 3.0) {
				LooseSoul loose = soul(health, damage);

				ShadowData shadow = new ShadowData(UUID.randomUUID(), loose.sourceType(),
						loose.level(), 0L, loose.health(), loose.damage(), Optional.empty(),
						ShadowData.DEFAULT_COLOR);

				assertEquals(loose.power(), shadow.powerScore(), 1.0E-9,
						"il metro della potenza deve essere lo stesso");
				assertEquals(loose.rank(THRESHOLDS), shadow.rank(ShadowConfig.DEFAULT),
						"vita " + health + ", danno " + damage);
			}
		}
	}

	@Test
	@DisplayName("il vigore cresce col livello e non torna mai indietro")
	void vigorGrowsWithLevel() {
		LooseSoul base = soul(20.0, 3.0);
		double previous = base.vigor();

		assertEquals(base.power(), previous, 1.0E-9, "al livello uno vigore e potenza coincidono");

		for (int level = 2; level <= 30; level++) {
			double vigor = base.withLevel(level).vigor();
			assertTrue(vigor > previous, "il livello " + level + " deve valere piu' del precedente");
			previous = vigor;
		}
	}

	@Test
	@DisplayName("un'anima non regge piu' tratti di quanti il catalizzatore ne consenta")
	void traitsRespectTheCatalyst() {
		for (Rank grade : Rank.values()) {
			Catalyst catalyst = new Catalyst(grade);
			LooseSoul result = soul(20.0, 3.0);

			for (SoulTrait trait : SoulTrait.values()) {
				result = result.with(trait, catalyst.traitCapacity());
			}

			assertTrue(result.traits().size() <= catalyst.traitCapacity(),
					grade + ": ne ha presi " + result.traits().size()
							+ " con capienza " + catalyst.traitCapacity());
			assertTrue(result.traits().size() <= LooseSoul.MAX_TRAITS,
					grade + ": oltre tre tratti il tooltip diventa una tabella");
		}
	}

	@Test
	@DisplayName("lo stesso tratto non si prende due volte")
	void traitsAreUnique() {
		LooseSoul result = soul(20.0, 3.0)
				.with(SoulTrait.ARDORE, 3)
				.with(SoulTrait.ARDORE, 3)
				.with(SoulTrait.ARDORE, 3);

		assertEquals(1, result.traits().size(), "l'Ardore e' uno solo");
	}

	@Test
	@DisplayName("aggiungere un tratto quando non c'e' piu' posto non cambia l'anima")
	void fullSoulIsUnchanged() {
		LooseSoul full = soul(20.0, 3.0).with(SoulTrait.ARDORE, 1);
		assertSame(full, full.with(SoulTrait.TENACIA, 1),
				"senza posto deve tornare esattamente la stessa istanza");
	}

	@Test
	@DisplayName("il catalizzatore migliore regge piu' tratti e conserva piu' livelli")
	void betterCatalystsAreStrictlyBetter() {
		int capacity = 0;
		double yield = 0.0;

		for (Rank grade : Rank.values()) {
			Catalyst catalyst = new Catalyst(grade);

			assertTrue(catalyst.traitCapacity() >= capacity, grade + ": la capienza e' scesa");
			assertTrue(catalyst.levelYield() > yield, grade + ": la resa e' scesa");
			assertTrue(catalyst.levelYield() < 1.0,
					grade + ": una resa piena renderebbe la fusione una moltiplicazione gratuita");

			capacity = catalyst.traitCapacity();
			yield = catalyst.levelYield();
		}

		assertEquals(LooseSoul.MAX_TRAITS, capacity, "il grado piu' alto deve arrivare al massimo");
	}

	@Test
	@DisplayName("la fusione fa sempre salire di livello, anche col catalizzatore peggiore")
	void fusionAlwaysImproves() {
		// E' la regola che il Crogiolo promette. Con quattro anime di livello uno e il
		// catalizzatore piu' scarso la somma per la resa fa meno di due: senza il pavimento a
		// "il migliore piu' uno" la fusione restituirebbe un'anima piu' debole di quelle entrate.
		for (Rank grade : Rank.values()) {
			Catalyst catalyst = new Catalyst(grade);

			for (int level = 1; level <= 20; level++) {
				int sum = level * 4;
				int fused = Math.max(level + 1, (int) Math.round(sum * catalyst.levelYield()));

				assertTrue(fused > level,
						grade + ", quattro anime di livello " + level + " → " + fused);
			}
		}
	}

	@Test
	@DisplayName("le soglie dei ranghi salgono, e la piu' bassa parte da zero")
	void rankThresholdsAreSane() {
		assertEquals(Rank.values().length, THRESHOLDS.size(),
				"una soglia per rango, o Rank.fromScore legge un rango che non esiste");
		assertEquals(0.0, THRESHOLDS.get(0), 1.0E-9, "il rango piu' basso deve accogliere tutto");

		for (int i = 1; i < THRESHOLDS.size(); i++) {
			assertTrue(THRESHOLDS.get(i) > THRESHOLDS.get(i - 1),
					"la soglia " + i + " non e' sopra la precedente");
		}

		assertSame(Rank.E, Rank.fromScore(0.0, THRESHOLDS));
		assertSame(Rank.S, Rank.fromScore(THRESHOLDS.get(THRESHOLDS.size() - 1) + 1, THRESHOLDS));
	}

	@Test
	@DisplayName("il tempo di lavoro scende col vigore ma non sfonda il pavimento")
	void workTicksHaveAFloor() {
		var workshop = com.luca.arise.config.WorkshopConfig.DEFAULT;
		int base = workshop.lureIntervalTicks();
		int previous = Integer.MAX_VALUE;

		for (double vigor = 0.0; vigor <= 5000.0; vigor += 25.0) {
			int ticks = workshop.workTicks(base, vigor, false);

			assertTrue(ticks >= 1, "un giro non puo' durare zero tick");
			assertTrue(ticks >= (int) (base * workshop.minSpeedFactor()),
					"vigore " + vigor + ": sfondato il pavimento");
			assertTrue(ticks <= previous, "vigore " + vigor + ": piu' vigore ha rallentato");

			previous = ticks;
		}

		assertTrue(workshop.workTicks(base, 0.0, true) < workshop.workTicks(base, 0.0, false),
				"l'Ardore deve accorciare il lavoro");
	}
}
