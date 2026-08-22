package com.luca.arise.client.ui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Una lista che scorre davvero.
 *
 * <p>Le schermate della mod erano <em>paginate</em>, e non per scelta: con un bottone vero dentro
 * ogni riga, una lista scorrevole avrebbe voluto dire riposizionare i widget a ogni frame e
 * ritagliare l'area di disegno attorno a loro. Le pagine erano la via d'uscita.
 *
 * <p>Qui la via d'uscita e' un'altra: <strong>le righe non sono widget</strong>. Si disegnano e
 * basta, e il click si risolve calcolando su quale riga e' caduto. Da quel momento lo scorrimento
 * e' un numero, il ritaglio e' una {@code enableScissor}, e i bottoni restano solo dove servono
 * davvero — nel pannello dei dettagli, uno per azione invece di uno per riga.
 *
 * <p>Il prezzo e' che una riga non si raggiunge col tasto Tab. Per una lista di sessanta pezzi
 * d'equipaggiamento e' il verso giusto del compromesso.
 *
 * @param <T> il tipo degli elementi
 */
public final class ListPanel<T> {

	/** Come si disegna una riga. Le coordinate sono quelle dell'angolo in alto a sinistra. */
	@FunctionalInterface
	public interface RowRenderer<T> {
		void render(GuiGraphicsExtractor graphics, T item, int x, int y, int width, boolean selected,
				boolean hovered);
	}

	private final int rowHeight;

	private int x;
	private int y;
	private int listWidth;
	private int listHeight;

	private List<T> items = List.of();
	private int scroll;
	private int selected = -1;

	public ListPanel(int rowHeight) {
		this.rowHeight = rowHeight;
	}

	public void bounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.listWidth = width;
		this.listHeight = height;
	}

	/**
	 * Sostituisce gli elementi tenendo ferma la selezione.
	 *
	 * <p>La lista si rifa' a ogni frame a partire da dati che arrivano dal server, quindi ricade
	 * qui continuamente. Se la selezione fosse azzerata ogni volta, il pannello dei dettagli
	 * lampeggerebbe; se restasse un indice fuori dai limiti, il primo click dopo un acquisto
	 * agirebbe sul pezzo sbagliato.
	 */
	public void items(List<T> items) {
		this.items = items;
		this.selected = items.isEmpty() ? -1 : Math.min(selected, items.size() - 1);
		clampScroll();
	}

	public List<T> items() {
		return items;
	}

	public int selectedIndex() {
		return selected;
	}

	public T selected() {
		return selected >= 0 && selected < items.size() ? items.get(selected) : null;
	}

	public void select(int index) {
		selected = index < 0 || index >= items.size() ? -1 : index;
	}

	/** Sceglie il primo elemento che soddisfa la condizione. Serve a ritrovare la selezione. */
	public void selectFirst(java.util.function.Predicate<T> match) {
		for (int i = 0; i < items.size(); i++) {
			if (match.test(items.get(i))) {
				selected = i;
				return;
			}
		}
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	// ---------------------------------------------------------------- interazione

	private boolean inside(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + listHeight;
	}

	/** L'indice della riga sotto il puntatore, oppure -1. */
	public int rowAt(double mouseX, double mouseY) {
		if (!inside(mouseX, mouseY)) {
			return -1;
		}

		int index = (int) ((mouseY - y + scroll) / rowHeight);
		return index >= 0 && index < items.size() ? index : -1;
	}

	/** @return vero se il click e' stato consumato dalla lista */
	public boolean mouseClicked(double mouseX, double mouseY) {
		int row = rowAt(mouseX, mouseY);
		if (row < 0) {
			return false;
		}

		selected = row;
		return true;
	}

	/** @return vero se lo scorrimento e' stato consumato */
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!inside(mouseX, mouseY) || !scrollable()) {
			return false;
		}

		scroll -= (int) (amount * rowHeight);
		clampScroll();
		return true;
	}

	private boolean scrollable() {
		return contentHeight() > listHeight;
	}

	private int contentHeight() {
		return items.size() * rowHeight;
	}

	private void clampScroll() {
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - listHeight));
	}

	// ---------------------------------------------------------------- disegno

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, RowRenderer<T> renderer) {
		int hovered = rowAt(mouseX, mouseY);
		int usable = scrollable() ? listWidth - AriseTheme.SCROLLBAR - 2 : listWidth;

		graphics.enableScissor(x, y, x + listWidth, y + listHeight);

		// Solo le righe che si vedono: una sacca da ottanta gemme non ha motivo di disegnarne
		// ottanta per mostrarne sei.
		int first = Math.max(0, scroll / rowHeight);
		int last = Math.min(items.size(), (scroll + listHeight) / rowHeight + 1);

		for (int i = first; i < last; i++) {
			int rowY = y + i * rowHeight - scroll;

			if (i == selected) {
				graphics.fill(x, rowY, x + usable, rowY + rowHeight - 1, AriseTheme.ROW_SELECTED);
				graphics.fill(x, rowY, x + 2, rowY + rowHeight - 1, AriseTheme.ACCENT);
			} else {
				graphics.fill(x, rowY, x + usable, rowY + rowHeight - 1, AriseTheme.ROW);
				if (i == hovered) {
					graphics.fill(x, rowY, x + usable, rowY + rowHeight - 1, AriseTheme.ROW_HOVER);
				}
			}

			renderer.render(graphics, items.get(i), x, rowY, usable, i == selected, i == hovered);
		}

		graphics.disableScissor();

		if (scrollable()) {
			drawScrollbar(graphics);
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics) {
		int trackX = x + listWidth - AriseTheme.SCROLLBAR;
		graphics.fill(trackX, y, trackX + AriseTheme.SCROLLBAR, y + listHeight, AriseTheme.LINE_SOFT);

		int thumbHeight = Math.max(12, listHeight * listHeight / contentHeight());
		int travel = listHeight - thumbHeight;
		int thumbY = y + (travel <= 0 ? 0 : travel * scroll / (contentHeight() - listHeight));

		graphics.fill(trackX, thumbY, trackX + AriseTheme.SCROLLBAR, thumbY + thumbHeight,
				AriseTheme.ACCENT_DEEP);
	}
}
