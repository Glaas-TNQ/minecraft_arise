package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.gem.Gem;
import com.luca.arise.network.GearActionPayload;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'equipaggiamento del Cacciatore.
 *
 * <p>Lista a sinistra, dettaglio a destra. Prima ogni riga doveva contenere nome, rango, slot,
 * quattro modificatori e due bottoni dentro trecentoquaranta pixel, e finiva troncata con dei
 * puntini. Adesso la riga dice quel poco che serve a scegliere — icona, nome, rango — e tutto il
 * resto sta nel pannello a fianco, che ha lo spazio per scriverlo per esteso.
 *
 * <p>I bottoni sono due in tutto e stanno sotto il dettaglio, invece di due per ogni riga: agiscono
 * sempre sul pezzo selezionato, che e' anche l'unico che si sta guardando.
 */
public class HunterScreen extends AriseScreen {

	private static final int PANEL_W = 440;
	private static final int PANEL_H = 230;
	private static final int LIST_W = 206;
	private static final int TAB_W = 74;
	private static final int TABS_H = 18;

	private enum Tab {
		WORN("worn"),
		STASH("stash");

		private final String key;

		Tab(String key) {
			this.key = key;
		}

		Component label() {
			return Component.translatable("arise.screen.hunter.tab." + key);
		}
	}

	/**
	 * Una riga della lista.
	 *
	 * <p>Un solo tipo per tre casi — un pezzo, delle posizioni ancora libere, uno slot chiuso —
	 * perche' devono potersi alternare mantenendo l'ordine degli slot.
	 */
	private record Row(GearSlot slot, GearPiece piece, int free, Rank unlock) {

		static Row holding(GearPiece piece) {
			return new Row(piece.slot(), piece, 0, null);
		}

		static Row free(GearSlot slot, int count) {
			return new Row(slot, null, count, null);
		}

		static Row locked(GearSlot slot, Rank unlock) {
			return new Row(slot, null, 0, unlock);
		}

		boolean isLocked() {
			return piece == null && unlock != null;
		}
	}

	private final ListPanel<Row> list = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private Tab tab = Tab.WORN;
	private UUID selectedId;
	private UUID pendingDiscard;

	private Button wornTab;
	private Button stashTab;
	private Button action;
	private Button discard;

	public HunterScreen() {
		super(Component.translatable("arise.screen.hunter.title"), PANEL_W, PANEL_H);
	}

	// ---------------------------------------------------------------- stato

	private PlayerGear gear() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	private Rank hunterRank() {
		return AriseConfig.get().hunterRank(progress().level());
	}

	private List<Row> rows() {
		PlayerGear gear = gear();

		if (tab == Tab.STASH) {
			return gear.sortedStash().stream().map(Row::holding).toList();
		}

		Rank rank = hunterRank();
		List<Row> rows = new ArrayList<>();

		for (GearSlot slot : GearSlot.values()) {
			int capacity = slot.capacity(rank);

			if (capacity == 0) {
				rows.add(Row.locked(slot, slot.nextUnlock(rank)));
				continue;
			}

			List<GearPiece> worn = gear.equippedIn(slot);
			worn.forEach(piece -> rows.add(Row.holding(piece)));

			int free = capacity - worn.size();
			if (free > 0) {
				rows.add(Row.free(slot, free));
			}
		}

		return rows;
	}

	private GearPiece selectedPiece() {
		Row row = list.selected();
		return row == null ? null : row.piece();
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void layout() {
		int left = bodyLeft();
		int top = bodyTop() + 6;

		wornTab = addRenderableWidget(Button.builder(Tab.WORN.label(), button -> switchTo(Tab.WORN))
				.bounds(left, top, TAB_W, TABS_H).build());
		stashTab = addRenderableWidget(Button.builder(Tab.STASH.label(), button -> switchTo(Tab.STASH))
				.bounds(left + TAB_W + 4, top, TAB_W, TABS_H).build());

		int listTop = top + TABS_H + 6;
		list.bounds(left, listTop, LIST_W, bodyBottom() - listTop - 4);

		int detailLeft = left + LIST_W + 12;
		int detailRight = bodyRight();
		int buttonsY = bodyBottom() - 24;
		int half = (detailRight - detailLeft - 4) / 2;

		action = addRenderableWidget(Button.builder(Component.empty(), button -> act())
				.bounds(detailLeft, buttonsY, half, 20).build());
		discard = addRenderableWidget(Button.builder(Component.empty(), button -> discard())
				.bounds(detailLeft + half + 4, buttonsY, half, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.hunter.gems"),
						button -> openGems())
				.bounds(detailRight - 74, top, 74, TABS_H).build());
	}

	private void switchTo(Tab target) {
		tab = target;
		selectedId = null;
		pendingDiscard = null;
		list.select(-1);
	}

	private void openGems() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new GemScreen(this));
		}
	}

	private void act() {
		GearPiece piece = selectedPiece();
		if (piece == null) {
			return;
		}

		pendingDiscard = null;
		ClientPlayNetworking.send(new GearActionPayload(piece.id(), tab == Tab.WORN
				? GearActionPayload.Action.UNEQUIP
				: GearActionPayload.Action.EQUIP));
	}

	/**
	 * Buttare via un pezzo chiede due click: il bottone si arma e cambia parola.
	 *
	 * <p>Nessuna finestra di conferma. Un gesto che si ripete decine di volte a ogni ritorno da un
	 * Gate non merita una finestra, ma un click solo su "Butta" prima o poi cancella il pezzo
	 * sbagliato.
	 */
	private void discard() {
		GearPiece piece = selectedPiece();
		if (piece == null) {
			return;
		}

		if (piece.id().equals(pendingDiscard)) {
			pendingDiscard = null;
			ClientPlayNetworking.send(new GearActionPayload(piece.id(), GearActionPayload.Action.DISCARD));
			return;
		}

		pendingDiscard = piece.id();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (list.mouseClicked(mouseX, mouseY)) {
			Row row = list.selected();
			selectedId = row == null || row.piece() == null ? null : row.piece().id();
			pendingDiscard = null;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (list.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---------------------------------------------------------------- disegno

	@Override
	protected Component status() {
		PlayerGear gear = gear();
		return Component.translatable("arise.screen.hunter.header", hunterRank().label(),
				gear.equipped().size(), gear.stash().size(), AriseConfig.get().gear().stashSize());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.hunter.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		wornTab.active = tab != Tab.WORN;
		stashTab.active = tab != Tab.STASH;

		// La lista si ricostruisce a ogni frame dai dati sincronizzati, e la selezione si ritrova
		// per identita': senza, indossare un pezzo lo farebbe cambiare sotto le dita.
		list.items(rows());
		if (selectedId != null) {
			list.selectFirst(row -> row.piece() != null && row.piece().id().equals(selectedId));
		}

		list.render(graphics, mouseX, mouseY, this::drawRow);
		drawDetail(graphics);
	}

	private void drawRow(GuiGraphicsExtractor graphics, Row row, int x, int y, int width,
			boolean selected, boolean hovered) {
		GearPiece piece = row.piece();
		boolean locked = row.isLocked();
		int tint = locked ? AriseTheme.DISABLED : piece == null ? AriseTheme.MUTED : piece.rank().color();

		Glyphs.slot(graphics, row.slot(), x + 7, y + 5, tint);

		if (piece == null) {
			graphics.text(font, row.slot().label(), x + 22, y + 4,
					locked ? AriseTheme.DISABLED : AriseTheme.MUTED);
			graphics.text(font, locked
							? Component.translatable("arise.screen.hunter.locked",
									row.unlock() == null ? Rank.S.label() : row.unlock().label())
							: Component.translatable("arise.screen.hunter.free", row.free()),
					x + 22, y + 14, AriseTheme.DISABLED);
			return;
		}

		graphics.text(font, piece.displayName(), x + 22, y + 4, AriseTheme.TEXT);
		graphics.text(font, piece.slot().label(), x + 22, y + 14, AriseTheme.MUTED);

		Component rank = piece.rank().label();
		graphics.text(font, rank, x + width - font.width(rank) - 6, y + 4, piece.rank().color());

		// I pallini delle incastonature: pieni quelle occupate, vuote le altre. Si vede a colpo
		// d'occhio quale pezzo ha ancora posto senza aprirlo.
		if (piece.sockets() > 0) {
			int dotX = x + width - 8 - piece.sockets() * 5;
			for (int i = 0; i < piece.sockets(); i++) {
				int color = i < piece.gems().size() ? AriseTheme.VIOLET : AriseTheme.LINE;
				graphics.fill(dotX + i * 5, y + 16, dotX + i * 5 + 3, y + 19, color);
			}
		}
	}

	private void drawDetail(GuiGraphicsExtractor graphics) {
		int left = bodyLeft() + LIST_W + 12;
		int right = bodyRight();
		int y = bodyTop() + 6 + TABS_H + 6;

		GearPiece piece = selectedPiece();

		action.visible = piece != null;
		discard.visible = piece != null;

		if (piece == null) {
			graphics.text(font, Component.translatable("arise.screen.hunter.pick"), left, y,
					AriseTheme.DISABLED);
			return;
		}

		action.setMessage(Component.translatable(tab == Tab.WORN
				? "arise.screen.hunter.unequip"
				: "arise.screen.hunter.equip"));
		discard.setMessage(Component.translatable(piece.id().equals(pendingDiscard)
				? "arise.screen.hunter.discard_confirm"
				: "arise.screen.hunter.discard"));

		graphics.text(font, piece.displayName(), left, y, AriseTheme.TEXT);
		y += 13;

		int chipWidth = chip(graphics, piece.rank().label(), left, y, piece.rank().color());
		graphics.text(font, piece.slot().label(), left + chipWidth + 6, y + 2, AriseTheme.MUTED);
		y += 20;

		divider(graphics, left, right, y);
		y += 6;

		for (Component line : piece.statLines()) {
			graphics.text(font, line, left, y, AriseTheme.MUTED);
			y += 11;
		}

		if (piece.sockets() <= 0) {
			return;
		}

		y += 4;
		divider(graphics, left, right, y);
		y += 6;

		sectionLabel(graphics, Component.translatable("arise.screen.hunter.sockets",
				piece.gems().size(), piece.sockets()), left, y);
		y += 12;

		for (Gem gem : piece.gems()) {
			Glyphs.gem(graphics, gem.type(), left, y, gem.rank().color());
			graphics.text(font, gem.displayName(), left + 12, y, AriseTheme.MUTED);
			y += 11;
		}
	}
}
