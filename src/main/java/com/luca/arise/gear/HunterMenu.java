package com.luca.arise.gear;

import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModMenus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Il menu del Cacciatore: gli accessori e lo spazio dimensionale, accanto all'inventario vero.
 *
 * <p>E' un {@link AbstractContainerMenu} e non una schermata disegnata a mano, e la differenza non
 * e' estetica. Un menu vero significa trascinare, cliccare con lo shift, doppio click che
 * raccoglie, tasti 1-9 per scambiare con la barra rapida: comportamenti che nessuno ha voglia di
 * riscrivere e che tutti si aspettano gia' di trovare. La versione precedente era una lista con
 * due bottoni, e ogni gesto che un giocatore prova d'istinto li' dentro non funzionava.
 *
 * <p>Le caselle sono tre gruppi, in quest'ordine: venti accessori, ventisette di spazio
 * dimensionale, e l'inventario del giocatore. L'ordine <em>e'</em> l'indirizzo delle caselle nei
 * pacchetti, quindi non si tocca.
 */
public class HunterMenu extends AbstractContainerMenu {

	/** Cinque colonne: cinque righe da quattro non entrerebbero in larghezza, quattro da cinque si. */
	public static final int ACCESSORY_COLUMNS = 5;

	public static final int DIMENSIONAL_COLUMNS = 9;

	/** Misure del pannello, condivise con la schermata: qui stanno le caselle, li' il disegno. */
	public static final int PANEL_WIDTH = 176;
	public static final int PANEL_HEIGHT = 244;

	public static final int ACCESSORY_X = 8;
	public static final int ACCESSORY_Y = 18;
	public static final int DIMENSIONAL_X = 8;
	public static final int DIMENSIONAL_Y = 94;
	public static final int INVENTORY_Y = 162;

	private static final int ACCESSORY_END = GearSlot.HUNTER_POSITIONS;
	private static final int DIMENSIONAL_END = ACCESSORY_END + PlayerGear.DIMENSIONAL_SIZE;

	private final Container accessories;
	private final Container dimensional;
	private final Rank rank;

	/** Costruttore del client: contenitori finti, che il server riempie a colpi di pacchetto. */
	public HunterMenu(int containerId, Inventory inventory, Rank rank) {
		this(containerId, inventory, new SimpleContainer(GearSlot.HUNTER_POSITIONS),
				new SimpleContainer(PlayerGear.DIMENSIONAL_SIZE), rank);
	}

	/** Costruttore del server: i contenitori veri, appoggiati all'attachment. */
	public static HunterMenu forPlayer(int containerId, Inventory inventory, ServerPlayer player) {
		return new HunterMenu(containerId, inventory, GearInventory.hunter(player),
				GearInventory.dimensional(player), GearManager.hunterRank(player));
	}

	private HunterMenu(int containerId, Inventory inventory, Container accessories,
			Container dimensional, Rank rank) {
		super(ModMenus.HUNTER, containerId);

		this.accessories = accessories;
		this.dimensional = dimensional;
		this.rank = rank;

		addAccessorySlots();
		addDimensionalSlots();
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	private void addAccessorySlots() {
		int index = 0;

		for (GearSlot slot : GearSlot.HUNTER) {
			for (int position = 0; position < slot.positions(); position++) {
				int column = index % ACCESSORY_COLUMNS;
				int row = index / ACCESSORY_COLUMNS;

				addSlot(new AccessorySlot(accessories, index, ACCESSORY_X + column * 18,
						ACCESSORY_Y + row * 18, slot, position, rank));
				index++;
			}
		}
	}

	private void addDimensionalSlots() {
		for (int i = 0; i < PlayerGear.DIMENSIONAL_SIZE; i++) {
			int column = i % DIMENSIONAL_COLUMNS;
			int row = i / DIMENSIONAL_COLUMNS;

			addSlot(new DimensionalSlot(dimensional, i, DIMENSIONAL_X + column * 18,
					DIMENSIONAL_Y + row * 18));
		}
	}

	public Rank rank() {
		return rank;
	}

	@Override
	public boolean stillValid(Player player) {
		return accessories.stillValid(player) && dimensional.stillValid(player);
	}

	/**
	 * Il click con lo shift.
	 *
	 * <p>Dalle nostre caselle si esce sempre verso l'inventario. Dall'inventario si entra prima
	 * nella casella che <em>indossa</em> il pezzo e solo dopo nello spazio dimensionale: chi tiene
	 * premuto shift su un anello vuole indossarlo, non metterlo via.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < DIMENSIONAL_END) {
			if (!moveItemStackTo(stack, DIMENSIONAL_END, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, ACCESSORY_END, false)
				&& !moveItemStackTo(stack, ACCESSORY_END, DIMENSIONAL_END, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
	}

	/**
	 * Una casella di accessorio: accetta un tipo solo, e solo se il rango l'ha aperta.
	 *
	 * <p>Il controllo sta qui e non nella schermata perche' e' il server a decidere (CLAUDE.md §4):
	 * un client modificato che spedisse un click su una casella chiusa troverebbe comunque un no.
	 */
	public static class AccessorySlot extends Slot {

		private final GearSlot type;
		private final int position;
		private final Rank rank;

		AccessorySlot(Container container, int index, int x, int y, GearSlot type, int position,
				Rank rank) {
			super(container, index, x, y);
			this.type = type;
			this.position = position;
			this.rank = rank;
		}

		public GearSlot type() {
			return type;
		}

		/** Il rango che apre questa casella, da mostrare a chi la trova chiusa. */
		public Rank required() {
			return type.unlockOf(position);
		}

		public boolean locked() {
			return position >= type.capacity(rank);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			GearPiece piece = GearItems.piece(stack);
			return !locked() && piece != null && piece.slot() == type;
		}

		@Override
		public boolean isActive() {
			return !locked();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	/**
	 * Una casella dello spazio dimensionale: accetta equipaggiamento del Sistema e nient'altro.
	 *
	 * <p>Il filtro e' cio' che gli da' un'identita'. Senza, sarebbe una cassa portatile da
	 * ventisette posti — un oggetto di gioco potentissimo e completamente estraneo alla mod.
	 */
	public static class DimensionalSlot extends Slot {

		DimensionalSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return GearItems.isGear(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
