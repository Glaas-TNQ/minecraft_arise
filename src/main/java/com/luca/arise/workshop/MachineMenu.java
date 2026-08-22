package com.luca.arise.workshop;

import com.luca.arise.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Il menu di un macchinario. Uno solo per tutti e quattro, come il blocco.
 *
 * <p>Le caselle si dispongono da sole a partire dal {@link MachineKind}: anime in fila in alto,
 * catalizzatore a destra se serve, ingresso e uscite in mezzo, inventario del giocatore sotto.
 * Cambiare quante caselle ha una macchina e' una riga nell'enum e nient'altro.
 *
 * <p><strong>L'ordine dei gruppi e' l'indirizzo delle caselle nei pacchetti</strong>: e' lo stesso
 * ordine di {@link MachineKind}, e non si tocca. Un giro di caselle spostate qui e non li'
 * significherebbe che uno shift-click sposta l'oggetto sbagliato.
 */
public class MachineMenu extends AbstractContainerMenu {

	public static final int PANEL_WIDTH = 176;
	public static final int PANEL_HEIGHT = 182;

	public static final int INVENTORY_Y = 100;

	/** La riga delle anime, e la riga del lavoro. Le usa anche la schermata per disegnare. */
	public static final int SOUL_Y = 26;
	public static final int WORK_Y = 58;

	public static final int CATALYST_X = 143;
	public static final int INPUT_X = 44;
	public static final int OUTPUT_CENTRE_X = 116;

	private final MachineKind kind;
	private final Container machine;
	private final ContainerData data;

	/** Costruttore del client: contenitore finto, che il server riempie a colpi di pacchetto. */
	public MachineMenu(int containerId, Inventory inventory, MachineKind kind) {
		this(containerId, inventory, kind, new SimpleContainer(kind.containerSize()),
				new SimpleContainerData(MachineBlockEntity.DATA_COUNT));
	}

	public MachineMenu(int containerId, Inventory inventory, MachineKind kind, Container machine,
			ContainerData data) {
		super(ModMenus.MACHINE, containerId);

		checkContainerDataCount(data, MachineBlockEntity.DATA_COUNT);

		this.kind = kind;
		this.machine = machine;
		this.data = data;

		addSoulSlots();
		addCatalystSlot();
		addWorkSlots();
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);

		addDataSlots(data);
	}

	public MachineKind kind() {
		return kind;
	}

	/** Da zero a uno: quanto e' avanzato il giro in corso. Serve solo alla barra. */
	public float progress() {
		int duration = data.get(MachineBlockEntity.DATA_DURATION);
		if (duration <= 0) {
			return 0.0F;
		}

		return Math.clamp(data.get(MachineBlockEntity.DATA_PROGRESS) / (float) duration, 0.0F, 1.0F);
	}

	public boolean working() {
		return data.get(MachineBlockEntity.DATA_DURATION) > 0;
	}

	private void addSoulSlots() {
		int startX = (PANEL_WIDTH - kind.souls() * 18) / 2;

		for (int i = 0; i < kind.souls(); i++) {
			addSlot(new SoulSlot(machine, i, startX + i * 18, SOUL_Y));
		}
	}

	private void addCatalystSlot() {
		if (kind.hasCatalyst()) {
			addSlot(new CatalystSlot(machine, kind.catalystSlot(), CATALYST_X, SOUL_Y));
		}
	}

	private void addWorkSlots() {
		for (int i = 0; i < kind.inputs(); i++) {
			addSlot(new Slot(machine, kind.firstInput() + i, INPUT_X + i * 18, WORK_Y));
		}

		int startX = OUTPUT_CENTRE_X - (kind.outputs() - 1) * 9;
		for (int i = 0; i < kind.outputs(); i++) {
			addSlot(new OutputSlot(machine, kind.firstOutput() + i, startX + i * 18, WORK_Y));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return machine.stillValid(player);
	}

	/**
	 * Lo shift-click.
	 *
	 * <p>Dalla macchina si esce sempre verso l'inventario. Dall'inventario si entra dove l'oggetto
	 * ha senso: un'anima fra le anime, un catalizzatore nella sua casella, tutto il resto
	 * nell'ingresso. Senza questo ordine, un'anima spinta col tasto shift finirebbe nella casella
	 * da fondere di una Fucina che non sa cosa farsene.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machineEnd = kind.containerSize();

		if (index < machineEnd) {
			if (!moveItemStackTo(stack, machineEnd, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveIntoMachine(stack)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	private boolean moveIntoMachine(ItemStack stack) {
		if (SoulItems.isSoul(stack)) {
			return moveItemStackTo(stack, kind.firstSoul(), kind.souls(), false);
		}

		if (SoulItems.isCatalyst(stack) && kind.hasCatalyst()) {
			return moveItemStackTo(stack, kind.catalystSlot(), kind.catalystSlot() + 1, false);
		}

		return kind.inputs() > 0
				&& moveItemStackTo(stack, kind.firstInput(), kind.firstInput() + kind.inputs(), false);
	}

	/**
	 * Una casella per un'anima e nient'altro.
	 *
	 * <p>Il filtro sta qui e non nella schermata perche' e' il server a decidere (CLAUDE.md §4):
	 * un client modificato che spedisse un click con una pietra in mano troverebbe comunque un no.
	 */
	public static class SoulSlot extends Slot {

		SoulSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return SoulItems.isSoul(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	public static class CatalystSlot extends Slot {

		CatalystSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return SoulItems.isCatalyst(stack);
		}
	}

	/** Da un'uscita si prende e basta: non ci si mette niente, nemmeno per sbaglio. */
	public static class OutputSlot extends Slot {

		OutputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
