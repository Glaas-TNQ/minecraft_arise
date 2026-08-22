package com.luca.arise.client.screen;

import java.util.UUID;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.network.ShopActionPayload;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shop.ShopOffer;
import com.luca.arise.shop.ShopStock;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'Abyss Shop.
 *
 * <p>Assortimento a sinistra, dettaglio a destra. Le voci sigillate mostrano solo il rango, ed e'
 * il punto: si compra la promessa, non il pezzo, e per questo costano meno.
 *
 * <p>Il bottone del ritiro sta in basso a sinistra col suo prezzo scritto sopra, perche' quel
 * prezzo raddoppia quasi a ogni pressione e va guardato prima, non dopo.
 */
public class AbyssShopScreen extends AriseScreen {

	private static final int PANEL_W = 420;
	private static final int PANEL_H = 210;
	private static final int LIST_W = 200;

	private final ListPanel<ShopOffer> list = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private UUID selectedId;
	private Button buy;
	private Button refresh;

	public AbyssShopScreen() {
		super(Component.translatable("arise.screen.shop.title"), PANEL_W, PANEL_H);
	}

	private ShopStock stock() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShopStock stock = player == null ? null : player.getAttached(ModAttachments.SHOP);
		return stock == null ? ShopStock.EMPTY : stock;
	}

	private long refreshPrice() {
		return AriseConfig.get().shop().refreshPrice(stock().refreshes());
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void layout() {
		int left = bodyLeft();
		int top = bodyTop() + 16;
		int listHeight = bodyBottom() - top - 28;

		list.bounds(left, top, LIST_W, listHeight);

		refresh = addRenderableWidget(Button.builder(Component.empty(), button ->
						ClientPlayNetworking.send(ShopActionPayload.of(ShopActionPayload.Action.REFRESH)))
				.bounds(left, bodyBottom() - 24, LIST_W, 20).build());

		int detailLeft = left + LIST_W + 12;
		buy = addRenderableWidget(Button.builder(Component.empty(), button -> buy())
				.bounds(detailLeft, bodyBottom() - 24, bodyRight() - detailLeft, 20).build());
	}

	private void buy() {
		ShopOffer offer = list.selected();
		if (offer != null) {
			ClientPlayNetworking.send(new ShopActionPayload(offer.id(), ShopActionPayload.Action.BUY));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (list.mouseClicked(mouseX, mouseY)) {
			ShopOffer offer = list.selected();
			selectedId = offer == null ? null : offer.id();
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
	protected int accent() {
		return AriseTheme.VIOLET;
	}

	@Override
	protected Component status() {
		return Component.translatable("arise.screen.shop.balance", souls());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.shop.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ShopStock stock = stock();

		list.items(stock.offers());
		if (selectedId != null) {
			list.selectFirst(offer -> offer.id().equals(selectedId));
		}

		long price = refreshPrice();
		refresh.setMessage(Component.translatable("arise.screen.shop.refresh", price));
		refresh.active = souls() >= price;

		sectionLabel(graphics, Component.translatable("arise.screen.shop.stock",
				stock.offers().size()), bodyLeft(), bodyTop() + 5);

		if (list.isEmpty()) {
			graphics.text(font, Component.translatable("arise.screen.shop.empty"),
					bodyLeft(), bodyTop() + 22, AriseTheme.DISABLED);
		} else {
			list.render(graphics, mouseX, mouseY, this::drawOffer);
		}

		drawDetail(graphics);
	}

	private void drawOffer(GuiGraphicsExtractor graphics, ShopOffer offer, int x, int y, int width,
			boolean selected, boolean hovered) {
		GearPiece piece = offer.piece().orElse(null);

		if (piece == null) {
			Glyphs.rankPip(graphics, x + 7, y + 6, 9, offer.rank().color());
		} else {
			Glyphs.slot(graphics, piece.slot(), x + 7, y + 5, offer.rank().color());
		}

		graphics.text(font, offer.label(), x + 22, y + 4,
				offer.isSealed() ? AriseTheme.VIOLET : AriseTheme.TEXT);

		Component price = Component.translatable("arise.screen.shop.price", offer.price());
		int color = souls() >= offer.price() ? AriseTheme.GOLD : AriseTheme.DISABLED;
		graphics.text(font, price, x + 22, y + 14, color);

		Component rank = offer.rank().label();
		graphics.text(font, rank, x + width - font.width(rank) - 6, y + 9, offer.rank().color());
	}

	private void drawDetail(GuiGraphicsExtractor graphics) {
		int left = bodyLeft() + LIST_W + 12;
		int right = bodyRight();
		int y = bodyTop() + 16;

		ShopOffer offer = list.selected();
		buy.visible = offer != null;

		if (offer == null) {
			graphics.text(font, Component.translatable("arise.screen.shop.pick"), left, y,
					AriseTheme.DISABLED);
			return;
		}

		buy.setMessage(Component.translatable("arise.screen.shop.buy", offer.price()));
		buy.active = souls() >= offer.price();

		graphics.text(font, offer.label(), left, y,
				offer.isSealed() ? AriseTheme.VIOLET : AriseTheme.TEXT);
		y += 15;

		chip(graphics, offer.rank().label(), left, y, offer.rank().color());
		y += 20;

		divider(graphics, left, right, y);
		y += 6;

		GearPiece piece = offer.piece().orElse(null);

		if (piece == null) {
			graphics.textWithWordWrap(font, Component.translatable("arise.screen.shop.sealed_hint"),
					left, y, right - left, AriseTheme.MUTED);
			return;
		}

		graphics.text(font, piece.slot().label(), left, y, AriseTheme.MUTED);
		y += 13;

		for (Component line : piece.statLines()) {
			graphics.text(font, line, left, y, AriseTheme.MUTED);
			y += 11;
		}

		if (piece.sockets() > 0) {
			y += 4;
			graphics.text(font, Component.translatable("arise.screen.hunter.sockets", 0,
					piece.sockets()), left, y, AriseTheme.DISABLED);
		}
	}
}
