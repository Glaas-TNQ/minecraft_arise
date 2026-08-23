package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.shadow.NamedShadow;
import com.luca.arise.shadow.ShadowArchetype;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.shadow.ShadowGrade;
import com.luca.arise.shadow.ShadowManager;
import com.luca.arise.shadow.ShadowOrders;
import com.luca.arise.shadow.ShadowSquad;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L'esercito come formazione: archetipi, gradi, squadra e ordini.
 *
 * <p>Tutto quello che qui si prova e' aritmetica pura, ed e' aritmetica che sbagliata non da'
 * nessun errore. Un ordine di chiamata invertito manda in campo le quattro ombre piu' deboli e
 * sembra che il gioco funzioni; una soglia dei gradi fuori posto fa comandare una recluta; un
 * codec dimentico cancella la squadra al primo salvataggio. Sono esattamente i difetti che in
 * gioco costano un'ora a trovare e qui costano due secondi.
 */
class ShadowLegionTest {

	private static final ShadowConfig CONFIG = ShadowConfig.DEFAULT;

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	private static ShadowData shadow(String mob, ShadowArchetype archetype, int level,
			double health, double damage) {
		return new ShadowData(UUID.randomUUID(), Identifier.withDefaultNamespace(mob), archetype,
				level, 0L, health, damage, Optional.empty(), ShadowData.DEFAULT_COLOR);
	}

	private static <T> void roundTrip(Codec<T> codec, T value, String what) {
		JsonElement written = codec.encodeStart(JsonOps.INSTANCE, value)
				.getOrThrow(message -> new AssertionError(what + " non si scrive: " + message));

		T read = codec.parse(JsonOps.INSTANCE, written)
				.getOrThrow(message -> new AssertionError(what + " non si rilegge: " + message));

		assertEquals(value, read, what + ": il giro completo ha cambiato qualcosa");
	}

	// ---------------------------------------------------------------- archetipi

	@Test
	@DisplayName("l'archetipo cambia davvero le statistiche, e sopravvive al salvataggio")
	void archetypesMatter() {
		for (ShadowArchetype archetype : ShadowArchetype.values()) {
			roundTrip(ShadowData.CODEC, shadow("zombie", archetype, 3, 30.0, 4.0),
					"un'ombra " + archetype.getSerializedName());
		}

		ShadowData guard = shadow("zombie", ShadowArchetype.GUARD, 1, 30.0, 4.0);
		ShadowData tank = shadow("zombie", ShadowArchetype.TANK, 1, 30.0, 4.0);
		ShadowData mage = shadow("zombie", ShadowArchetype.MAGE, 1, 30.0, 4.0);
		ShadowData beast = shadow("zombie", ShadowArchetype.BEAST, 1, 30.0, 4.0);

		assertTrue(tank.maxHealth(CONFIG) > guard.maxHealth(CONFIG),
				"un Colosso deve avere piu' vita di una Guardia");
		assertTrue(mage.maxHealth(CONFIG) < guard.maxHealth(CONFIG),
				"un Mago deve incassare peggio di una Guardia");
		assertTrue(beast.attackDamage(CONFIG) > guard.attackDamage(CONFIG),
				"una Bestia deve colpire piu' forte di una Guardia");
		assertTrue(tank.attackDamage(CONFIG) < guard.attackDamage(CONFIG),
				"un Colosso paga la vita col danno");
	}

	@Test
	@DisplayName("il rango guarda il mob d'origine, il grado guarda l'ombra di adesso")
	void rankIsNotGrade() {
		ShadowData fresh = shadow("zombie", ShadowArchetype.GUARD, 1, 30.0, 4.0);
		ShadowData veteran = shadow("zombie", ShadowArchetype.GUARD, 40, 30.0, 4.0);

		assertEquals(fresh.rank(CONFIG), veteran.rank(CONFIG),
				"il rango viene dal mob e non deve muoversi con i livelli");
		assertEquals(fresh.powerScore(), veteran.powerScore(), 1.0E-9,
				"nemmeno il punteggio di base deve muoversi");

		assertTrue(veteran.effectivePower(CONFIG) > fresh.effectivePower(CONFIG),
				"la potenza effettiva invece deve salire coi livelli");
		assertTrue(veteran.grade(CONFIG).ordinal() > fresh.grade(CONFIG).ordinal(),
				"e con lei il grado");
	}

	@Test
	@DisplayName("l'archetipo di un'ombra vecchia, che non ce l'aveva, e' la Guardia")
	void oldShadowsBecomeGuards() {
		// La forma esatta di un'ombra salvata prima di questo blocco: nessun campo "archetype".
		String legacy = """
				{"id":[1,2,3,4],"source_type":"minecraft:zombie","level":5,"xp":12,
				 "max_health":30.0,"attack_damage":4.0}
				""";

		ShadowData read = ShadowData.CODEC
				.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(legacy))
				.getOrThrow(message -> new AssertionError("un'ombra vecchia non si rilegge: " + message));

		assertEquals(ShadowArchetype.GUARD, read.archetype(),
				"senza il campo, l'ombra deve comportarsi come si comportava prima");
		assertEquals(5, read.level(), "il resto deve restare intatto");
	}

	// ---------------------------------------------------------------- gradi

	@Test
	@DisplayName("le soglie dei gradi salgono, e ogni grado e' raggiungibile")
	void gradesClimb() {
		List<Double> thresholds = CONFIG.legion().gradeThresholds();

		assertEquals(ShadowGrade.values().length, thresholds.size(),
				"una soglia per grado, o gli ultimi non si raggiungono mai");

		for (int i = 1; i < thresholds.size(); i++) {
			assertTrue(thresholds.get(i) > thresholds.get(i - 1),
					"la soglia " + i + " deve stare sopra la precedente");
		}

		for (int i = 0; i < thresholds.size(); i++) {
			assertEquals(ShadowGrade.values()[i], ShadowGrade.fromPower(thresholds.get(i), thresholds),
					"la soglia esatta deve dare il suo grado");
		}

		assertEquals(ShadowGrade.NORMAL, ShadowGrade.fromPower(-1.0, thresholds),
				"sotto la prima soglia si resta Normale");
	}

	@Test
	@DisplayName("il nome e l'aura arrivano col grado, non prima")
	void gradeGrantsPrivileges() {
		assertFalse(ShadowGrade.NORMAL.canBeNamed(), "una recluta non si battezza");
		assertFalse(ShadowGrade.ELITE.canBeNamed(), "nemmeno un'elite");
		assertTrue(ShadowGrade.KNIGHT.canBeNamed(), "da Cavaliere in su si'");
		assertTrue(ShadowGrade.GRAND_MARSHAL.canBeNamed(), "e a maggior ragione in cima");

		assertEquals(0, ShadowGrade.ELITE_KNIGHT.commandSteps(), "sotto Comandante non si comanda");
		assertEquals(1, ShadowGrade.COMMANDER.commandSteps(), "il Comandante e' il primo gradino");
		assertEquals(4, ShadowGrade.GRAND_MARSHAL.commandSteps(), "e il Gran Maresciallo il quarto");

		ShadowData recruit = shadow("zombie", ShadowArchetype.GUARD, 1, 30.0, 4.0);
		assertEquals(0.0, recruit.auraDamage(CONFIG), 1.0E-9,
				"una recluta non deve regalare niente a nessuno");

		// Un Colosso da ravager, maxato: e' il caso in cui l'aura deve esserci.
		ShadowData marshal = shadow("ravager", ShadowArchetype.TANK,
				CONFIG.leveling().maxLevel(), 150.0, 14.0);
		assertTrue(marshal.grade(CONFIG).commands(),
				"un Colosso da ravager al livello massimo deve comandare");
		assertTrue(marshal.auraDamage(CONFIG) > 0.0, "e la sua aura deve valere qualcosa");
	}

	// ---------------------------------------------------------------- squadra

	@Test
	@DisplayName("la squadra sopravvive al salvataggio, rispetta il tetto e si ripulisce")
	void squadHolds() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();

		ShadowSquad squad = ShadowSquad.EMPTY.toggled(first, 2).toggled(second, 2);
		roundTrip(ShadowSquad.CODEC, squad, "una squadra di due");

		assertEquals(1, squad.slotOf(first), "il primo entrato e' il primo a uscire");
		assertEquals(2, squad.slotOf(second));
		assertEquals(0, squad.slotOf(third), "chi non c'e' non ha posto");

		assertSame(squad, squad.toggled(third, 2),
				"a squadra piena non si aggiunge, e si deve poter distinguere dal non far niente");

		ShadowSquad without = squad.toggled(first, 2);
		assertFalse(without.contains(first), "ripremuto, esce");
		assertEquals(1, without.size());

		// Un'ombra congedata sparisce dall'esercito e non da qui: la pulizia e' alla lettura.
		ShadowArmy army = ShadowArmy.EMPTY.with(new ShadowData(second,
				Identifier.withDefaultNamespace("zombie"), ShadowArchetype.GUARD, 1, 0L,
				30.0, 4.0, Optional.empty(), ShadowData.DEFAULT_COLOR));

		assertEquals(List.of(second), squad.pruned(army).ids(),
				"la squadra ripulita tiene solo chi e' ancora nell'esercito");
		assertSame(without, without.pruned(army),
				"e se non c'e' niente da togliere non deve nemmeno costruire una lista nuova");
	}

	@Test
	@DisplayName("chi esce col tasto: prima la squadra nel suo ordine, poi i piu' forti")
	void callUpOrder() {
		ShadowData weak = shadow("zombie", ShadowArchetype.GUARD, 1, 20.0, 2.0);
		ShadowData middle = shadow("husk", ShadowArchetype.GUARD, 10, 30.0, 5.0);
		ShadowData strong = shadow("ravager", ShadowArchetype.TANK, 20, 150.0, 14.0);
		ShadowData chosen = shadow("skeleton", ShadowArchetype.MAGE, 2, 30.0, 3.0);

		ShadowArmy army = ShadowArmy.EMPTY.with(weak).with(middle).with(strong).with(chosen);

		// Senza squadra: dalla piu' forte alla piu' debole, non nell'ordine di estrazione.
		List<ShadowData> free = ShadowManager.callUpOrder(army, ShadowSquad.EMPTY, CONFIG);
		assertEquals(strong.id(), free.get(0).id(), "senza squadra esce prima la piu' forte");
		assertEquals(weak.id(), free.get(free.size() - 1).id(), "e per ultima la piu' debole");

		// Con la squadra: prima lei, nell'ordine in cui e' stata composta, poi le altre.
		ShadowSquad squad = ShadowSquad.EMPTY.toggled(chosen.id(), 4).toggled(weak.id(), 4);
		List<ShadowData> ordered = ShadowManager.callUpOrder(army, squad, CONFIG);

		assertEquals(chosen.id(), ordered.get(0).id(), "il primo posto in squadra e' il primo");
		assertEquals(weak.id(), ordered.get(1).id(),
				"anche se e' la piu' debole dell'esercito: la squadra e' una scelta, non un consiglio");
		assertEquals(strong.id(), ordered.get(2).id(), "poi le altre, dalla piu' forte");
		assertEquals(army.size(), ordered.size(), "e non se ne deve perdere nessuna");
	}

	// ---------------------------------------------------------------- ordini

	@Test
	@DisplayName("gli ordini si scrivono, si combinano e si revocano uno alla volta")
	void ordersCompose() {
		UUID target = UUID.randomUUID();
		BlockPos post = new BlockPos(120, 64, -40);

		roundTrip(ShadowOrders.CODEC, ShadowOrders.NONE, "nessun ordine");
		roundTrip(ShadowOrders.CODEC, ShadowOrders.NONE.withFocus(target), "un bersaglio");
		roundTrip(ShadowOrders.CODEC, ShadowOrders.NONE.withHold(post), "una posizione");

		ShadowOrders both = ShadowOrders.NONE.withFocus(target).withHold(post);
		roundTrip(ShadowOrders.CODEC, both, "bersaglio e posizione insieme");

		assertTrue(both.hasFocus() && both.isHolding(), "i due ordini si combinano");
		assertFalse(both.isIdle());

		assertTrue(both.withoutFocus().isHolding(),
				"revocare il bersaglio non deve far tornare a seguire");
		assertTrue(both.withoutHold().hasFocus(),
				"e liberare la posizione non deve far mollare il bersaglio");

		assertTrue(ShadowOrders.NONE.isIdle(), "senza ordini non c'e' niente da dire");
		assertEquals(null, ShadowOrders.NONE.label(), "e infatti l'HUD non scrive niente");
		assertNotEquals(null, both.label(), "con due ordini invece deve dirlo");
	}

	// ---------------------------------------------------------------- config

	@Test
	@DisplayName("la config della legione fa il giro completo su disco")
	void legionConfigSurvives() {
		roundTrip(ShadowConfig.CODEC, ShadowConfig.DEFAULT, "il bilanciamento dell'esercito");
		roundTrip(ShadowConfig.Legion.CODEC, ShadowConfig.Legion.DEFAULT, "la legione");
		roundTrip(ShadowConfig.Behaviour.CODEC, ShadowConfig.Behaviour.DEFAULT, "i comportamenti");

		for (ShadowArchetype archetype : ShadowArchetype.values()) {
			ShadowConfig.Archetype tuning = archetype.tuning(ShadowConfig.Legion.DEFAULT);
			roundTrip(ShadowConfig.Archetype.CODEC, tuning, "i numeri di " + archetype);

			assertTrue(tuning.health() > 0.0 && tuning.damage() > 0.0 && tuning.speed() > 0.0,
					archetype + " non puo' avere moltiplicatori nulli: sarebbe un'ombra inerte");
		}

		assertFalse(ShadowConfig.Behaviour.DEFAULT.casterSet().isEmpty(),
				"senza lanciatori in elenco non nascerebbe mai un Mago");
	}

	// ---------------------------------------------------------------- le nominate

	@Test
	@DisplayName("ogni ombra nominata nasce coerente con se stessa")
	void namedShadowsAreWellFormed() {
		for (NamedShadow which : NamedShadow.values()) {
			ShadowData shadow = which.create();

			assertEquals(which, shadow.named().orElse(null),
					which + " deve sapere di essere se stessa");
			assertEquals(which.archetype(), shadow.archetype(),
					which + " deve nascere con l'archetipo dichiarato");
			assertEquals(which.color(), shadow.color());
			assertTrue(shadow.level() > 1,
					which + " non e' un'ombra qualunque: non puo' nascere al livello uno");
			assertTrue(shadow.baseMaxHealth() > 0.0 && shadow.baseAttackDamage() > 0.0);

			// Il nome e' fisso e non passa da customName: chi lo cercasse li' lo troverebbe vuoto e
			// concluderebbe, sbagliando, che l'ombra non ha nome.
			assertTrue(shadow.customName().isEmpty(),
					which + " porta il nome dell'enum, non un nome scritto addosso");
		}
	}

	@Test
	@DisplayName("due nominate diverse non si confondono, e la stessa non entra due volte")
	void namedShadowsAreUnique() {
		ShadowArmy army = ShadowArmy.EMPTY
				.with(NamedShadow.IGRIS.create())
				.with(NamedShadow.BERU.create());

		assertTrue(army.hasNamed(NamedShadow.IGRIS));
		assertTrue(army.hasNamed(NamedShadow.BERU));
		assertFalse(army.hasNamed(NamedShadow.BELLION),
				"un esercito non deve credere di avere un'ombra che non ha");

		// E' il controllo che impedisce a un varco Sculk di rango S rifatto dieci volte di produrre
		// dieci Beru. La condizione che le concede e' ripetibile; l'ombra no.
		assertTrue(ShadowArmy.EMPTY.with(NamedShadow.IGRIS.create()).hasNamed(NamedShadow.IGRIS));
	}

	@Test
	@DisplayName("una nominata sopravvive al salvataggio, e una qualunque resta senza nome")
	void namedSurvivesDisk() {
		ShadowData igris = NamedShadow.IGRIS.create();
		ShadowData plain = new ShadowData(UUID.randomUUID(),
				Identifier.withDefaultNamespace("zombie"), ShadowArchetype.GUARD, 3, 0L,
				30.0, 6.0, Optional.empty(), ShadowData.DEFAULT_COLOR);

		roundTrip(ShadowData.CODEC, igris, "Igris");
		roundTrip(ShadowData.CODEC, plain, "un'ombra qualunque");

		// L'asimmetria e' il punto: il campo e' opzionale, quindi un esercito salvato prima che le
		// nominate esistessero si rilegge intero e nessuna delle sue ombre e' nominata.
		assertTrue(plain.named().isEmpty(),
				"un'ombra estratta da un cadavere non deve risultare nominata");
	}

	@Test
	@DisplayName("il grado e il livello di una nominata restano quelli di un'ombra normale")
	void namedShadowsPlayByTheSameRules() {
		ShadowConfig config = ShadowConfig.DEFAULT;
		ShadowData beru = NamedShadow.BERU.create();

		// La regola che tiene in piedi tutto il blocco: una nominata e' unica in cio' che fa, non
		// migliore nei numeri. Se un giorno saltasse le regole del grado, sarebbe solo la piu'
		// forte — e la collezione tornerebbe a essere una lista ordinata per potenza.
		assertEquals(beru.grade(config), ShadowGrade.fromPower(beru.effectivePower(config),
				config.legion().gradeThresholds()));

		ShadowData levelled = beru.withLevelUp();
		assertEquals(beru.level() + 1, levelled.level());
		assertEquals(NamedShadow.BERU, levelled.named().orElse(null),
				"salire di livello non deve far perdere il nome");
	}
}
