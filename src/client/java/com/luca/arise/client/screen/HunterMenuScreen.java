package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.gear.HunterMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * La finestra del Cacciatore: accessori sopra, spazio dimensionale in mezzo, inventario sotto.
 *
 * <p>E' una schermata da contenitore e non una delle nostre: eredita da
 * {@link AbstractContainerScreen} tutto il comportamento delle caselle — trascinare, scambiare,
 * shift, tasti della barra rapida — e ci mette sopra la tavolozza del Sistema. Sembra una finestra
 * della mod, si comporta come una cassa.
 *
 * <p>Il fondo si dipinge in {@code extractBackground} e non nel disegno principale: li' finirebbe
 * <em>sopra</em> le caselle, nascondendo gli oggetti. E' la stessa trappola gia' annotata su
 * {@code AriseScreen}.
 */
public class HunterMenuScreen extends AbstractContainerScreen<HunterMenu> {

	private static final int SLOT = 18;

	public HunterMenuScreen(HunterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, HunterMenu.PANEL_WIDTH, HunterMenu.PANEL_HEIGHT);

		titleLabelX = 8;
		titleLabelY = 6;
		inventoryLabelX = 8;
		inventoryLabelY = HunterMenu.INVENTORY_Y - 11;
	}

	/**
	 * Il banco delle gemme resta una schermata a parte, e ci si arriva da qui.
	 *
	 * <p>Incastonare non e' un gesto da caselle: chiede di scegliere un pezzo, poi una gemma, poi
	 * di sapere se si e' dentro il perimetro di un'Associazione. Aprire quella finestra chiude
	 * questa, il che e' giusto — sono due lavori diversi.
	 */
	@Override
	protected void init() {
		super.init();

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.hunter.gems"),
						button -> openGems())
				.bounds(leftPos + imageWidth - 54, topPos + HunterMenu.DIMENSIONAL_Y - 14, 46, 12)
				.build());
	}

	private void openGems() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new GemScreen(null));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		graphics.fill(0, 0, width, height, AriseTheme.SCRIM);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, AriseTheme.PANEL);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, AriseTheme.LINE);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, AriseTheme.ACCENT);

		for (Slot slot : menu.slots) {
			extractSlotBox(graphics, slot);
		}
	}

	/**
	 * Il fondo di una casella.
	 *
	 * <p>Le caselle chiuse si disegnano lo stesso, spente e senza cornice: una casella che manca
	 * non racconta niente, una casella spenta dice che quel posto esiste e che si aprira'.
	 */
	private void extractSlotBox(GuiGraphicsExtractor graphics, Slot slot) {
		int x = leftPos + slot.x;
		int y = topPos + slot.y;
		boolean locked = slot instanceof HunterMenu.AccessorySlot accessory && accessory.locked();

		graphics.fill(x - 1, y - 1, x + 17, y + 17, locked ? AriseTheme.PANEL_SOFT : AriseTheme.ROW);
		graphics.outline(x - 1, y - 1, SLOT, SLOT, locked ? AriseTheme.LINE_SOFT : AriseTheme.LINE);

		if (slot instanceof HunterMenu.AccessorySlot accessory && !slot.hasItem()) {
			// L'icona fantasma dice a cosa serve la casella prima che ci sia dentro qualcosa.
			Glyphs.slot(graphics, accessory.type(), x + 4, y + 4,
					locked ? AriseTheme.alpha(AriseTheme.DISABLED, 90)
							: AriseTheme.alpha(AriseTheme.MUTED, 110));
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, AriseTheme.TEXT);
		graphics.text(font, Component.translatable("arise.screen.hunter.rank", menu.rank().label()),
				imageWidth - 8 - font.width(Component.translatable("arise.screen.hunter.rank",
						menu.rank().label())), titleLabelY, AriseTheme.MUTED);

		// L'etichetta dice cos'e' lo spazio dimensionale; la riga accanto dice come si tira fuori
		// quello che c'e' dentro. Senza, l'unico modo di scoprire lo shift+clic era provarlo.
		Component space = Component.translatable("arise.screen.hunter.dimensional");
		graphics.text(font, space, 8, HunterMenu.DIMENSIONAL_Y - 11, AriseTheme.MUTED);
		graphics.text(font, Component.translatable("arise.screen.hunter.equip_hint"),
				8 + font.width(space) + 8, HunterMenu.DIMENSIONAL_Y - 11, AriseTheme.DISABLED);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, AriseTheme.MUTED);
	}

	/**
	 * Il suggerimento su una casella chiusa.
	 *
	 * <p>Vanilla non disegna niente sulle caselle spente e non le considera nemmeno sotto il
	 * puntatore, quindi il controllo lo facciamo qui: senza, un giocatore vedrebbe otto caselle
	 * scure senza mai sapere cosa le apre.
	 */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);

		for (Slot slot : menu.slots) {
			if (!(slot instanceof HunterMenu.AccessorySlot accessory) || !accessory.locked()) {
				continue;
			}

			if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
				graphics.setComponentTooltipForNextFrame(font, List.of(
						accessory.type().label(),
						Component.translatable("arise.screen.hunter.locked",
								accessory.required().label())), mouseX, mouseY);
				return;
			}
		}
	}
}
