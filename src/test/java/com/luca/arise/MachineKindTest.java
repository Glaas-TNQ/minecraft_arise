package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.workshop.MachineKind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gli indici delle caselle dei macchinari.
 *
 * <p>E' la cosa piu' pericolosa dell'Officina, e la meno visibile. Gli stessi numeri sono letti da
 * tre posti che non si parlano: la {@code MachineBlockEntity} per sapere dove pescare le anime, il
 * {@code MachineMenu} per disegnare le caselle, e le facce per le tramogge per decidere cosa si
 * puo' infilare da sopra e cosa si puo' portare via da sotto.
 *
 * <p>Un indice sbagliato non da' errore: da' un Crogiolo che fonde la casella sbagliata, o una
 * tramoggia che si porta via le operaie invece del prodotto. Si scoprirebbe dopo ore, e sembrerebbe
 * un problema di bilanciamento.
 */
class MachineKindTest {

	@Test
	@DisplayName("i gruppi di caselle sono contigui e coprono esattamente il contenitore")
	void slotGroupsTileTheContainer() {
		for (MachineKind kind : MachineKind.values()) {
			int expected = kind.souls() + (kind.hasCatalyst() ? 1 : 0)
					+ kind.inputs() + kind.outputs();

			assertEquals(expected, kind.containerSize(), kind + ": la dimensione non torna");

			assertEquals(0, kind.firstSoul(), kind + ": le anime devono venire per prime");

			if (kind.hasCatalyst()) {
				assertEquals(kind.souls(), kind.catalystSlot(),
						kind + ": il catalizzatore viene subito dopo le anime");
			} else {
				assertTrue(kind.catalystSlot() < 0,
						kind + ": non ha catalizzatore, e non deve dichiararne uno");
			}

			assertEquals(kind.souls() + (kind.hasCatalyst() ? 1 : 0), kind.firstInput(),
					kind + ": gli ingressi vengono dopo il catalizzatore");
			assertEquals(kind.firstInput() + kind.inputs(), kind.firstOutput(),
					kind + ": le uscite vengono dopo gli ingressi");
			assertEquals(kind.containerSize(), kind.firstOutput() + kind.outputs(),
					kind + ": le uscite devono arrivare in fondo");
		}
	}

	@Test
	@DisplayName("ogni casella appartiene a un gruppo solo")
	void everySlotBelongsToExactlyOneGroup() {
		for (MachineKind kind : MachineKind.values()) {
			for (int slot = 0; slot < kind.containerSize(); slot++) {
				int families = 0;

				if (kind.isSoulSlot(slot)) {
					families++;
				}
				if (slot == kind.catalystSlot()) {
					families++;
				}
				if (slot >= kind.firstInput() && slot < kind.firstInput() + kind.inputs()) {
					families++;
				}
				if (kind.isOutputSlot(slot)) {
					families++;
				}

				assertEquals(1, families,
						kind + ": la casella " + slot + " appartiene a " + families + " gruppi");
			}
		}
	}

	@Test
	@DisplayName("nessun indice cade fuori dal contenitore")
	void indicesStayInsideTheContainer() {
		for (MachineKind kind : MachineKind.values()) {
			assertFalse(kind.isSoulSlot(-1), kind + ": -1 non e' una casella");
			assertFalse(kind.isSoulSlot(kind.containerSize()),
					kind + ": oltre il fondo non c'e' niente");
			assertFalse(kind.isOutputSlot(kind.containerSize()),
					kind + ": oltre il fondo non c'e' niente");

			if (kind.hasCatalyst()) {
				assertTrue(kind.catalystSlot() < kind.containerSize(),
						kind + ": il catalizzatore cade fuori dal contenitore");
			}
		}
	}

	@Test
	@DisplayName("solo il Crogiolo consuma le anime che ospita")
	void onlyTheCrucibleEatsItsSouls() {
		for (MachineKind kind : MachineKind.values()) {
			assertEquals(kind == MachineKind.CRUCIBLE, kind.consumesSouls(),
					kind + ": chi consuma le anime e chi no e' la regola su cui regge l'Officina");
		}
	}

	@Test
	@DisplayName("ogni macchinario ha almeno un'uscita e almeno un posto per un'anima")
	void everyMachineCanHostASoulAndProduceSomething() {
		for (MachineKind kind : MachineKind.values()) {
			assertTrue(kind.souls() > 0, kind + ": senza anime non lavorerebbe mai");
			assertTrue(kind.outputs() > 0, kind + ": senza uscite il prodotto finirebbe per terra");
		}
	}
}
