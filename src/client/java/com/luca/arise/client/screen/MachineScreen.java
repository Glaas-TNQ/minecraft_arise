package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.workshop.MachineKind;
import com.luca.arise.workshop.MachineMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * La finestra di un macchinario. Una sola, per tutti e quattro.
 *
 * <p>Disegna quello che il menu le passa e nient'altro: le caselle stanno gia' dove {@link
 * MachineMenu} le ha messe, e qui si aggiungono solo il fondo, le etichette e la barra. Se domani
 * un macchinario avesse sei caselle per le anime, questa classe non se ne accorgerebbe.
 *
 * <p>Come per la finestra del Cacciatore, il fondo si dipinge in {@code extractBackground}: nel
 * disegno principale finirebbe <em>sopra</em> le caselle, coprendo gli oggetti.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

	private static final int SLOT = 18;

	/** La barra di avanzamento: sotto la riga del lavoro, larga quanto il pannello meno i margini. */
	private static final int BAR_X = 30;
	private static final int BAR_Y = 82;
	private static final int BAR_WIDTH = MachineMenu.PANEL_WIDTH - BAR_X * 2;
	private static final int BAR_HEIGHT = 4;

	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, MachineMenu.PANEL_WIDTH, MachineMenu.PANEL_HEIGHT);

		titleLabelX = 8;
		titleLabelY = 6;
		inventoryLabelX = 8;
		inventoryLabelY = MachineMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		graphics.fill(0, 0, width, height, AriseTheme.SCRIM);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, AriseTheme.PANEL);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, AriseTheme.LINE);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, AriseTheme.ACCENT);

		// La fascia delle anime: dice a colpo d'occhio dove finiscono le operaie, anche quando le
		// caselle sono vuote e non c'e' nient'altro a distinguerle da quelle del lavoro.
		graphics.fill(leftPos + 6, topPos + MachineMenu.SOUL_Y - 6,
				leftPos + imageWidth - 6, topPos + MachineMenu.SOUL_Y + 24, AriseTheme.PANEL_SOFT);

		for (Slot slot : menu.slots) {
			extractSlotBox(graphics, slot);
		}

		extractBar(graphics);
	}

	private void extractSlotBox(GuiGraphicsExtractor graphics, Slot slot) {
		int x = leftPos + slot.x;
		int y = topPos + slot.y;

		boolean soul = slot instanceof MachineMenu.SoulSlot;
		boolean output = slot instanceof MachineMenu.OutputSlot;

		graphics.fill(x - 1, y - 1, x + 17, y + 17, AriseTheme.ROW);
		graphics.outline(x - 1, y - 1, SLOT, SLOT,
				soul ? AriseTheme.VIOLET : output ? AriseTheme.GOLD : AriseTheme.LINE);
	}

	/**
	 * La barra di avanzamento.
	 *
	 * <p>Non e' una barra dell'energia: e' il giro di lavoro in corso, e sparisce quando la
	 * macchina e' ferma. La differenza conta — una barra sempre presente racconterebbe una risorsa
	 * che in questa mod non esiste.
	 */
	private void extractBar(GuiGraphicsExtractor graphics) {
		int x = leftPos + BAR_X;
		int y = topPos + BAR_Y;

		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, AriseTheme.PANEL_SOFT);
		graphics.outline(x - 1, y - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, AriseTheme.LINE);

		if (!menu.working()) {
			return;
		}

		int filled = (int) (BAR_WIDTH * menu.progress());
		graphics.fill(x, y, x + filled, y + BAR_HEIGHT, AriseTheme.ACCENT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, AriseTheme.TEXT);

		Component state = menu.working()
				? Component.translatable("arise.screen.machine.working")
				: Component.translatable("arise.screen.machine.idle");

		graphics.text(font, state, imageWidth - 8 - font.width(state), titleLabelY,
				menu.working() ? AriseTheme.GOOD : AriseTheme.MUTED);

		graphics.text(font, Component.translatable("arise.screen.machine.souls"),
				8, MachineMenu.SOUL_Y - 12, AriseTheme.MUTED);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, AriseTheme.MUTED);
	}

	/**
	 * Il suggerimento sulla barra: cosa fa questa macchina, in una riga.
	 *
	 * <p>E' l'unico posto in cui il gioco lo spiega. Un macchinario che non dice cosa fa costringe
	 * a leggere la documentazione, e la documentazione non e' un'interfaccia.
	 */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		if (isHovering(BAR_X, BAR_Y - 2, BAR_WIDTH, BAR_HEIGHT + 4, mouseX, mouseY)) {
			MachineKind kind = menu.kind();
			graphics.setComponentTooltipForNextFrame(font,
					List.of(kind.label(), kind.hint()), mouseX, mouseY);
		}
	}
}
