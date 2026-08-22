package com.luca.arise.client.screen;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.network.SpendPointPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Stat;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * La schermata "Stato": statistiche, punti da spendere, valori reali degli attributi.
 *
 * <p>Non modifica nulla in locale. Ogni "+" manda un {@link SpendPointPayload} al server; quando il
 * server accetta, l'attachment sincronizzato torna indietro e la schermata si ridisegna da sola.
 * Se il server rifiuta, qui non cambia niente — che e' esattamente il comportamento voluto.
 */
public class StatusScreen extends Screen {

	private static final int ROW_HEIGHT = 24;
	private static final int PANEL_WIDTH = 240;

	private static final int COLOR_TITLE = 0xFF4FC3F7;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_POINTS = 0xFFFFD54F;

	private final Map<Stat, Button> buttons = new EnumMap<>(Stat.class);

	public StatusScreen() {
		super(Component.translatable("arise.screen.status.title"));
	}

	@Override
	protected void init() {
		buttons.clear();

		int left = (width - PANEL_WIDTH) / 2;
		int y = topOfRows();

		for (Stat stat : Stat.SPENDABLE) {
			Button button = Button.builder(Component.literal("+"), b -> spend(stat))
					.bounds(left + PANEL_WIDTH - 24, y - 6, 20, 20)
					.build();
			buttons.put(stat, addRenderableWidget(button));
			y += ROW_HEIGHT;
		}
	}

	/** Il gioco non si mette in pausa: e' una schermata da consultare al volo, come l'inventario. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void spend(Stat stat) {
		ClientPlayNetworking.send(new SpendPointPayload(stat, 1));
	}

	private int topOfRows() {
		return height / 2 - (Stat.SPENDABLE.size() * ROW_HEIGHT) / 2 + 8;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);

		if (progress == null) {
			graphics.centeredText(font, Component.translatable("arise.screen.status.loading"),
					width / 2, height / 2, COLOR_DIM);
			return;
		}

		AriseConfig config = AriseConfig.get();
		int left = (width - PANEL_WIDTH) / 2;

		graphics.centeredText(font, title, width / 2, topOfRows() - 44, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.status.level",
				progress.level(), progress.xp(), config.xpForNextLevel(progress.level())),
				width / 2, topOfRows() - 30, COLOR_TEXT);
		graphics.centeredText(font, Component.translatable("arise.screen.status.points",
				progress.unspentPoints()), width / 2, topOfRows() - 18,
				progress.unspentPoints() > 0 ? COLOR_POINTS : COLOR_DIM);

		int y = topOfRows();
		for (Stat stat : Stat.SPENDABLE) {
			int points = progress.stat(stat);
			int cap = config.cap(stat);
			boolean canSpend = progress.unspentPoints() > 0 && points < cap;

			// Lo stato dei bottoni si ricalcola a ogni frame dai dati sincronizzati: cosi' resta
			// coerente anche quando il server rifiuta o quando i punti arrivano da un level-up.
			Button button = buttons.get(stat);
			if (button != null) {
				button.active = canSpend;
			}

			graphics.text(font, Component.translatable(stat.translationKey()), left, y, COLOR_TEXT);
			graphics.text(font, Component.translatable("arise.screen.status.stat_value",
					points, cap, String.format("%.2f", player.getAttributeValue(stat.attribute()))),
					left + 88, y, COLOR_DIM);

			y += ROW_HEIGHT;
		}

		graphics.centeredText(font, Component.translatable("arise.screen.status.hint"),
				width / 2, y + 8, COLOR_DIM);
	}
}
