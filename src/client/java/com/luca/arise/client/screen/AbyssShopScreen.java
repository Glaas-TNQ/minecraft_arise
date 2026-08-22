package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.network.ShopActionPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shop.ShopOffer;
import com.luca.arise.shop.ShopStock;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'Abyss Shop.
 *
 * <p>Sei voci e basta: un negozio con un catalogo infinito e' un catalogo, non una scelta. Le
 * ultime sono <em>sigillate</em> — si compra il rango e il pezzo esce dopo — e costano meno perche'
 * il rischio lo prende chi paga.
 *
 * <p>Il prezzo del ritiro sale a ogni ritiro dentro la stessa rotazione: e' quello che impedisce di
 * trasformare i soul coin in un tiro a ripetizione finche' non esce il pezzo giusto.
 */
public class AbyssShopScreen extends Screen {

	private static final int ROW_HEIGHT = 30;
	private static final int PANEL_WIDTH = 340;
	private static final int BUY_WIDTH = 76;

	private static final int COLOR_TITLE = 0xFFC77FE8;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_SOULS = 0xFFFFD54F;
	private static final int COLOR_ROW = 0x40000000;
	private static final int COLOR_SEALED = 0xFF8E7CC3;

	private int lastFingerprint = Integer.MIN_VALUE;

	public AbyssShopScreen() {
		super(Component.translatable("arise.screen.shop.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- stato

	private ShopStock stock() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShopStock stock = player == null ? null : player.getAttached(ModAttachments.SHOP);
		return stock == null ? ShopStock.EMPTY : stock;
	}

	private long souls() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);
		return progress == null ? 0L : progress.souls();
	}

	private long refreshPrice() {
		return AriseConfig.get().shop().refreshPrice(stock().refreshes());
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void init() {
		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();
		List<ShopOffer> offers = stock().offers();
		long souls = souls();

		int index = 0;
		for (ShopOffer offer : offers) {
			Button buy = Button.builder(
							Component.translatable("arise.screen.shop.buy", offer.price()),
							button -> ClientPlayNetworking.send(
									new ShopActionPayload(offer.id(), ShopActionPayload.Action.BUY)))
					.bounds(left + PANEL_WIDTH - BUY_WIDTH, top + index * ROW_HEIGHT - 2, BUY_WIDTH, 20)
					.build();

			// Il bottone spento dice da solo perche': non serve un messaggio d'errore per una cosa
			// che si vede guardando il saldo.
			buy.active = souls >= offer.price();
			addRenderableWidget(buy);

			index++;
		}

		long price = refreshPrice();
		Button refresh = Button.builder(
						Component.translatable("arise.screen.shop.refresh", price),
						button -> ClientPlayNetworking.send(
								ShopActionPayload.of(ShopActionPayload.Action.REFRESH)))
				.bounds(left + PANEL_WIDTH - 130, top + Math.max(1, offers.size()) * ROW_HEIGHT + 6, 130, 20)
				.build();
		refresh.active = souls >= price;
		addRenderableWidget(refresh);
	}

	@Override
	public void tick() {
		super.tick();

		ShopStock stock = stock();
		int fingerprint = stock.offers().hashCode() * 31 + stock.refreshes() + Long.hashCode(souls());

		if (fingerprint != lastFingerprint) {
			lastFingerprint = fingerprint;
			rebuildWidgets();
		}
	}

	private int topOfRows() {
		int rows = Math.max(1, stock().offers().size());
		return height / 2 - (rows * ROW_HEIGHT) / 2 + 6;
	}

	// ---------------------------------------------------------------- disegno

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		ShopStock stock = stock();
		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfRows();

		graphics.centeredText(font, title, width / 2, top - 34, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.shop.balance", souls()),
				width / 2, top - 22, COLOR_SOULS);

		if (stock.offers().isEmpty()) {
			graphics.centeredText(font, Component.translatable("arise.screen.shop.empty"),
					width / 2, height / 2, COLOR_DIM);
			return;
		}

		int index = 0;
		for (ShopOffer offer : stock.offers()) {
			drawOffer(graphics, offer, left, top + index * ROW_HEIGHT);
			index++;
		}
	}

	private void drawOffer(GuiGraphicsExtractor graphics, ShopOffer offer, int left, int y) {
		graphics.fill(left, y - 3, left + PANEL_WIDTH, y + ROW_HEIGHT - 6, COLOR_ROW);
		graphics.fill(left + 2, y - 1, left + 5, y + 18, offer.rank().color());

		graphics.text(font, offer.rank().label(), left + 9, y, offer.rank().color());
		graphics.text(font, offer.label(), left + 25, y, offer.isSealed() ? COLOR_SEALED : COLOR_TEXT);

		GearPiece piece = offer.piece().orElse(null);

		if (piece == null) {
			graphics.text(font, Component.translatable("arise.screen.shop.sealed_hint"),
					left + 25, y + 11, COLOR_DIM);
			return;
		}

		graphics.text(font, piece.slot().label(), left + 25, y + 11, COLOR_DIM);

		int x = left + 25 + font.width(piece.slot().label()) + 10;
		int limit = left + PANEL_WIDTH - BUY_WIDTH - 8;

		for (Component line : piece.statLines()) {
			if (x + font.width(line) > limit) {
				graphics.text(font, Component.literal("…"), x, y + 11, COLOR_DIM);
				return;
			}

			graphics.text(font, line, x, y + 11, COLOR_DIM);
			x += font.width(line) + 8;
		}
	}
}
