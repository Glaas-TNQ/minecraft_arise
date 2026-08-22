package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.network.GearActionPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'equipaggiamento del Cacciatore: quello che ha addosso e quello che tiene da parte.
 *
 * <p>Due schede invece di due pannelli affiancati. Ventiquattro posizioni piu' uno zaino non
 * entrano in una schermata leggibile a fianco l'una dell'altro, e due liste paginate in
 * contemporanea sarebbero il doppio dei widget per meta' dello spazio ciascuna.
 *
 * <p>Come le altre schermate della mod, non conosce lo stato vero: legge l'attachment
 * sincronizzato e manda intenzioni. Chi decide se un pezzo si puo' indossare e' il server.
 */
public class HunterScreen extends Screen {

	private static final int ROWS_PER_PAGE = 6;
	private static final int ROW_HEIGHT = 26;
	private static final int PANEL_WIDTH = 340;
	private static final int ACTION_WIDTH = 58;
	private static final int SMALL_WIDTH = 20;

	private static final int COLOR_TITLE = 0xFF4FC3F7;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_LOCKED = 0xFF6B7684;
	private static final int COLOR_ROW = 0x40000000;

	/** Che cosa si sta guardando. */
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
	 * <p>Un solo tipo di riga per tre casi diversi — un pezzo indossato, delle posizioni ancora
	 * libere, uno slot chiuso — perche' la lista deve poterli alternare mantenendo l'ordine degli
	 * slot. Tre liste separate avrebbero rimescolato tutto.
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

	private Tab tab = Tab.WORN;
	private int page;
	private UUID pendingDiscard;
	private int lastFingerprint = Integer.MIN_VALUE;

	public HunterScreen() {
		super(Component.translatable("arise.screen.hunter.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- stato

	private PlayerGear gear() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	/**
	 * Il rango del Cacciatore.
	 *
	 * <p>Ricavato dal livello con la config locale, come gia' fa {@code FxConfig} per l'aspetto
	 * delle ombre. Serve solo a disegnare: chi valida se uno slot e' aperto resta il server.
	 */
	private Rank hunterRank() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);
		return AriseConfig.get().hunterRank(progress == null ? 1 : progress.level());
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

	private int pageCount() {
		return Math.max(1, (rows().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
	}

	private List<Row> currentPage() {
		List<Row> rows = rows();
		int from = Math.min(page * ROWS_PER_PAGE, rows.size());
		int to = Math.min(from + ROWS_PER_PAGE, rows.size());
		return rows.subList(from, to);
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void init() {
		page = Math.clamp(page, 0, pageCount() - 1);

		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();

		addRenderableWidget(Button.builder(Tab.WORN.label(), button -> switchTo(Tab.WORN))
				.bounds(left, top - 26, 80, 20).build()).active = tab != Tab.WORN;
		addRenderableWidget(Button.builder(Tab.STASH.label(), button -> switchTo(Tab.STASH))
				.bounds(left + 84, top - 26, 80, 20).build()).active = tab != Tab.STASH;

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.hunter.gems"),
						button -> openGems())
				.bounds(left + PANEL_WIDTH - 80, top - 26, 80, 20).build());

		int index = 0;
		for (Row row : currentPage()) {
			int y = top + index * ROW_HEIGHT - 2;

			if (row.piece() != null) {
				boolean worn = tab == Tab.WORN;

				addRenderableWidget(Button.builder(
								Component.translatable(worn
										? "arise.screen.hunter.unequip"
										: "arise.screen.hunter.equip"),
								button -> send(row.piece().id(), worn
										? GearActionPayload.Action.UNEQUIP
										: GearActionPayload.Action.EQUIP))
						.bounds(left + PANEL_WIDTH - ACTION_WIDTH - SMALL_WIDTH - 4, y, ACTION_WIDTH, 20)
						.build());

				if (!worn) {
					boolean armed = row.piece().id().equals(pendingDiscard);

					addRenderableWidget(Button.builder(
									Component.literal(armed ? "!" : "×"),
									button -> discard(row.piece().id()))
							.bounds(left + PANEL_WIDTH - SMALL_WIDTH, y, SMALL_WIDTH, 20)
							.build());
				}
			}

			index++;
		}

		if (pageCount() > 1) {
			int navY = top + ROWS_PER_PAGE * ROW_HEIGHT + 6;

			Button previous = Button.builder(Component.literal("<"), button -> turnTo(page - 1))
					.bounds(left, navY, 20, 20).build();
			previous.active = page > 0;
			addRenderableWidget(previous);

			Button next = Button.builder(Component.literal(">"), button -> turnTo(page + 1))
					.bounds(left + 24, navY, 20, 20).build();
			next.active = page < pageCount() - 1;
			addRenderableWidget(next);
		}
	}

	/**
	 * Lo stato vero arriva dal server con un tick di ritardo: qui si guarda se e' cambiato e si
	 * ricostruiscono i widget. Nel tick e non nel disegno, o si rifa' la lista mentre la si sta
	 * percorrendo per renderizzarla.
	 */
	@Override
	public void tick() {
		super.tick();

		PlayerGear gear = gear();
		int fingerprint = gear.equipped().hashCode() * 31 + gear.stash().hashCode()
				+ tab.ordinal() * 7 + (pendingDiscard == null ? 0 : pendingDiscard.hashCode());

		if (fingerprint != lastFingerprint) {
			lastFingerprint = fingerprint;
			rebuildWidgets();
		}
	}

	private void openGems() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new GemScreen(this));
		}
	}

	private void switchTo(Tab target) {
		tab = target;
		page = 0;
		pendingDiscard = null;
		rebuildWidgets();
	}

	private void turnTo(int newPage) {
		page = Math.clamp(newPage, 0, pageCount() - 1);
		rebuildWidgets();
	}

	private void send(UUID id, GearActionPayload.Action action) {
		pendingDiscard = null;
		ClientPlayNetworking.send(new GearActionPayload(id, action));
	}

	/**
	 * Buttare via un pezzo chiede due click.
	 *
	 * <p>Nessuna finestra di conferma: il bottone si arma e basta. Una finestra per un gesto che si
	 * ripete decine di volte a ogni ritorno da un Gate diventa presto un fastidio, ma un click solo
	 * su una crocetta larga venti pixel prima o poi cancella il pezzo sbagliato.
	 */
	private void discard(UUID id) {
		if (id.equals(pendingDiscard)) {
			pendingDiscard = null;
			ClientPlayNetworking.send(new GearActionPayload(id, GearActionPayload.Action.DISCARD));
			return;
		}

		pendingDiscard = id;
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && pageCount() > 1) {
			turnTo(page + (scrollY > 0 ? -1 : 1));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int topOfRows() {
		return height / 2 - (ROWS_PER_PAGE * ROW_HEIGHT) / 2 + 10;
	}

	// ---------------------------------------------------------------- disegno

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		PlayerGear gear = gear();
		Rank rank = hunterRank();
		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();

		graphics.centeredText(font, title, width / 2, top - 52, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.hunter.header",
				rank.label(), gear.equipped().size(),
				gear.stash().size(), AriseConfig.get().gear().stashSize()),
				width / 2, top - 40, COLOR_DIM);

		List<Row> rows = currentPage();

		if (rows.isEmpty()) {
			graphics.centeredText(font, Component.translatable("arise.screen.hunter.empty"),
					width / 2, height / 2, COLOR_DIM);
			return;
		}

		int index = 0;
		for (Row row : rows) {
			drawRow(graphics, row, left, top + index * ROW_HEIGHT);
			index++;
		}

		if (pageCount() > 1) {
			graphics.text(font, Component.translatable("arise.screen.army.page",
					page + 1, pageCount()), left + 50, top + ROWS_PER_PAGE * ROW_HEIGHT + 12, COLOR_DIM);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, Row row, int left, int y) {
		graphics.fill(left, y - 3, left + PANEL_WIDTH, y + ROW_HEIGHT - 6, COLOR_ROW);

		GearPiece piece = row.piece();

		if (piece == null) {
			drawPlaceholder(graphics, row, left, y);
			return;
		}

		// Il rango e' la prima cosa che si guarda: una banda colorata prima del nome.
		graphics.fill(left + 2, y - 1, left + 5, y + 16, piece.rank().color());
		graphics.text(font, piece.rank().label(), left + 9, y, piece.rank().color());
		graphics.text(font, piece.displayName(), left + 25, y, COLOR_TEXT);
		graphics.text(font, piece.slot().label(), left + PANEL_WIDTH - ACTION_WIDTH - SMALL_WIDTH - 76,
				y, COLOR_DIM);

		// I modificatori su una riga sola, finche' c'e' spazio: un pezzo di rango S ne ha quattro
		// e il quarto finirebbe sotto i bottoni.
		int x = left + 25;
		int limit = left + PANEL_WIDTH - ACTION_WIDTH - SMALL_WIDTH - 12;

		for (Component line : piece.statLines()) {
			if (x + font.width(line) > limit) {
				graphics.text(font, Component.literal("…"), x, y + 11, COLOR_DIM);
				return;
			}

			graphics.text(font, line, x, y + 11, COLOR_DIM);
			x += font.width(line) + 8;
		}

		if (piece.sockets() > 0) {
			graphics.text(font, Component.translatable("arise.screen.hunter.sockets", piece.sockets()),
					x, y + 11, COLOR_LOCKED);
		}
	}

	/** Le righe senza pezzo: posizioni ancora libere, oppure uno slot che non si e' aperto. */
	private void drawPlaceholder(GuiGraphicsExtractor graphics, Row row, int left, int y) {
		graphics.text(font, row.slot().label(), left + 9, y, row.isLocked() ? COLOR_LOCKED : COLOR_DIM);

		Component detail = row.isLocked()
				? Component.translatable("arise.screen.hunter.locked",
						row.unlock() == null ? Rank.S.label() : row.unlock().label())
				: Component.translatable("arise.screen.hunter.free", row.free());

		graphics.text(font, detail, left + 9, y + 11, row.isLocked() ? COLOR_LOCKED : COLOR_DIM);
	}
}
