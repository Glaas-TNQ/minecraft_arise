package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.progress.Rank;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.network.ShadowActionPayload;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.shadow.SummonedShadows;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'esercito: una pagina per volta, con evocazione e richiamo per singola ombra.
 *
 * <p>Paginata invece che scorrevole di proposito. Una lista con scorrimento richiederebbe
 * ritagliare l'area di disegno e riposizionare i bottoni a ogni frame; con le pagine i bottoni
 * sono widget veri in posizioni fisse, che si occupano da soli di click, hover e tastiera.
 *
 * <p>La schermata non conosce lo stato reale: legge l'esercito e l'elenco delle evocate dai due
 * attachment sincronizzati, e manda solo intenzioni.
 */
public class ArmyScreen extends Screen {

	private static final int ROWS_PER_PAGE = 6;
	private static final int ROW_HEIGHT = 26;
	private static final int PANEL_WIDTH = 300;
	private static final int BUTTON_WIDTH = 62;
	private static final int DETAIL_WIDTH = 20;

	private static final int COLOR_TITLE = 0xFF4FC3F7;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_XP_TRACK = 0xFF1B2838;
	private static final int COLOR_XP_FILL = 0xFF4FC3F7;
	private static final int COLOR_ROW = 0x40000000;

	private int page;
	private int lastFingerprint = Integer.MIN_VALUE;

	public ArmyScreen() {
		super(Component.translatable("arise.screen.army.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private ShadowArmy army() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShadowArmy army = player == null ? null : player.getAttached(ModAttachments.ARMY);
		return army == null ? ShadowArmy.EMPTY : army;
	}

	private SummonedShadows summoned() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		SummonedShadows summoned = player == null ? null : player.getAttached(ModAttachments.SUMMONED);
		return summoned == null ? SummonedShadows.EMPTY : summoned;
	}

	private int pageCount() {
		return Math.max(1, (army().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
	}

	private List<ShadowData> currentPage() {
		List<ShadowData> shadows = army().shadows();
		int from = Math.min(page * ROWS_PER_PAGE, shadows.size());
		int to = Math.min(from + ROWS_PER_PAGE, shadows.size());
		return shadows.subList(from, to);
	}

	@Override
	protected void init() {
		// La pagina potrebbe non esistere più: un'ombra persa mentre la schermata era aperta
		// lascerebbe una pagina vuota senza modo di tornare indietro.
		page = Math.clamp(page, 0, pageCount() - 1);

		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();
		SummonedShadows summoned = summoned();

		int index = 0;
		for (ShadowData shadow : currentPage()) {
			boolean isSummoned = summoned.contains(shadow.id());
			int y = top + index * ROW_HEIGHT;

			addRenderableWidget(Button.builder(
							Component.translatable(isSummoned
									? "arise.screen.army.recall"
									: "arise.screen.army.summon"),
							button -> toggle(shadow, isSummoned))
					.bounds(left + PANEL_WIDTH - BUTTON_WIDTH - DETAIL_WIDTH - 4, y - 2, BUTTON_WIDTH, 20)
					.build());

			addRenderableWidget(Button.builder(Component.literal("…"),
							button -> openDetail(shadow))
					.bounds(left + PANEL_WIDTH - DETAIL_WIDTH, y - 2, DETAIL_WIDTH, 20)
					.build());

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
	 * I bottoni dicono "Evoca" o "Richiama" in base a uno stato che arriva dal server con un tick
	 * di ritardo. Qui si controlla se quello stato è cambiato e si ricostruiscono i widget.
	 *
	 * <p>Va fatto nel tick e non nel disegno: ricostruire la lista dei widget mentre la si sta
	 * percorrendo per renderizzarla è il modo classico di prendersi una ConcurrentModification.
	 */
	@Override
	public void tick() {
		super.tick();

		int fingerprint = army().size() * 31 + summoned().ids().hashCode();
		if (fingerprint != lastFingerprint) {
			lastFingerprint = fingerprint;
			rebuildWidgets();
		}
	}

	private void turnTo(int newPage) {
		page = Math.clamp(newPage, 0, pageCount() - 1);
		rebuildWidgets();
	}

	private void openDetail(ShadowData shadow) {
		if (minecraft != null) {
			minecraft.setScreenAndShow(new ShadowDetailScreen(shadow.id(), this));
		}
	}

	private void toggle(ShadowData shadow, boolean isSummoned) {
		ClientPlayNetworking.send(ShadowActionPayload.of(shadow.id(), isSummoned
				? ShadowActionPayload.Action.RECALL
				: ShadowActionPayload.Action.SUMMON));
		// I bottoni si ricostruiscono al prossimo frame utile: lo stato vero arriva dal server,
		// qui non si anticipa nulla.
	}

	/** Scorre le pagine con la rotella, che è il gesto che tutti provano per primo. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && pageCount() > 1) {
			turnTo(page + (scrollY > 0 ? -1 : 1));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int topOfRows() {
		return height / 2 - (ROWS_PER_PAGE * ROW_HEIGHT) / 2 + 6;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army();
		SummonedShadows summoned = summoned();
		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();

		graphics.centeredText(font, title, width / 2, top - 34, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.army.header",
				army.size(), summoned.ids().size(), config.maxSummoned()),
				width / 2, top - 22, COLOR_DIM);

		if (army.isEmpty()) {
			graphics.centeredText(font, Component.translatable("arise.screen.army.empty"),
					width / 2, height / 2, COLOR_DIM);
			return;
		}

		int index = 0;
		for (ShadowData shadow : currentPage()) {
			drawRow(graphics, config, shadow, left, top + index * ROW_HEIGHT,
					summoned.contains(shadow.id()));
			index++;
		}

		if (pageCount() > 1) {
			graphics.text(font, Component.translatable("arise.screen.army.page",
					page + 1, pageCount()), left + 50, top + ROWS_PER_PAGE * ROW_HEIGHT + 12, COLOR_DIM);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, ShadowConfig config, ShadowData shadow,
			int left, int y, boolean isSummoned) {
		graphics.fill(left, y - 3, left + PANEL_WIDTH, y + ROW_HEIGHT - 6, COLOR_ROW);

		// Il rango è la prima cosa che si guarda in una lista lunga: colorato e in testa alla riga.
		graphics.fill(left + 2, y - 1, left + 5, y + 8, 0xFF000000 | shadow.color());
		graphics.text(font, shadow.rank(config).label(), left + 8, y, shadow.rank(config).color());
		graphics.text(font, shadow.displayName(), left + 24, y, isSummoned ? COLOR_XP_FILL : COLOR_TEXT);

		graphics.text(font, Component.translatable("arise.screen.army.stats",
				shadow.level(),
				String.format("%.0f", shadow.maxHealth(config)),
				String.format("%.1f", shadow.attackDamage(config))),
				left + 24, y + 10, COLOR_DIM);

		drawXpBar(graphics, config, shadow, left + PANEL_WIDTH - BUTTON_WIDTH - DETAIL_WIDTH - 84, y + 12);
	}

	private void drawXpBar(GuiGraphicsExtractor graphics, ShadowConfig config, ShadowData shadow,
			int x, int y) {
		int barWidth = 70;
		graphics.fill(x, y, x + barWidth, y + 3, COLOR_XP_TRACK);

		if (shadow.isMaxLevel(config)) {
			graphics.fill(x, y, x + barWidth, y + 3, COLOR_XP_FILL);
			return;
		}

		long needed = shadow.xpForNextLevel(config);
		int filled = needed <= 0 ? 0 : (int) (barWidth * Math.min(1.0, (double) shadow.xp() / needed));

		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + 3, COLOR_XP_FILL);
		}
	}
}
