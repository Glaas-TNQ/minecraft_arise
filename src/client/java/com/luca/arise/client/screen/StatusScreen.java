package com.luca.arise.client.screen;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.gem.Gem;
import com.luca.arise.network.SpendPointPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Stat;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Lo stato del Sistema: livello, punti, statistiche.
 *
 * <p>Non modifica nulla in locale. Ogni "+" manda un {@link SpendPointPayload} al server; quando il
 * server accetta, l'attachment sincronizzato torna indietro e la schermata si ridisegna da sola.
 * Se il server rifiuta, qui non cambia niente — che e' esattamente il comportamento voluto.
 *
 * <p>Due colonne: a sinistra le quattro statistiche spendibili, con la barra che dice quanto manca
 * al tetto; a destra tutto quello che arriva da fuori i punti — equipaggiamento e gemme — perche'
 * la domanda vera davanti a questa schermata non e' "quanto ho" ma "da dove viene".
 */
public class StatusScreen extends AriseScreen {

	private static final int PANEL_W = 400;
	private static final int PANEL_H = 190;
	private static final int LEFT_W = 220;
	private static final int ROW = 26;

	private final Map<Stat, Button> buttons = new EnumMap<>(Stat.class);

	public StatusScreen() {
		super(Component.translatable("arise.screen.status.title"), PANEL_W, PANEL_H);
	}

	private PlayerGear gear() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	/** Quanto una statistica riceve da equipaggiamento e gemme indossate. */
	private double external(Stat stat) {
		double total = 0.0;

		for (GearPiece piece : gear().equipped()) {
			total += piece.stats().getOrDefault(stat, 0.0);

			for (Gem gem : piece.gems()) {
				total += gem.stats().getOrDefault(stat, 0.0);
			}
		}

		return total;
	}

	@Override
	protected void layout() {
		buttons.clear();

		int y = bodyTop() + 22;
		for (Stat stat : Stat.SPENDABLE) {
			buttons.put(stat, addRenderableWidget(Button.builder(Component.literal("+"),
							button -> ClientPlayNetworking.send(new SpendPointPayload(stat, 1)))
					.bounds(bodyLeft() + LEFT_W - 34, y - 4, 18, 18).build()));
			y += ROW;
		}
	}

	@Override
	protected Component status() {
		PlayerProgress progress = progress();
		return Component.translatable("arise.screen.status.level", progress.level(),
				progress.xp(), AriseConfig.get().xpForNextLevel(progress.level()));
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.status.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		if (player == null) {
			return;
		}

		AriseConfig config = AriseConfig.get();
		PlayerProgress progress = progress();
		int left = bodyLeft();
		int valueRight = left + LEFT_W - 40;

		graphics.text(font, Component.translatable("arise.screen.status.points",
				progress.unspentPoints()), left, bodyTop() + 6,
				progress.unspentPoints() > 0 ? AriseTheme.GOLD : AriseTheme.DISABLED);

		int y = bodyTop() + 22;
		for (Stat stat : Stat.SPENDABLE) {
			int points = progress.stat(stat);
			int cap = config.cap(stat);

			// Lo stato del bottone si ricalcola a ogni frame dai dati sincronizzati: resta coerente
			// anche quando il server rifiuta o quando i punti arrivano da un livello.
			Button button = buttons.get(stat);
			if (button != null) {
				button.active = progress.unspentPoints() > 0 && points < cap;
			}

			graphics.text(font, Component.translatable(stat.translationKey()), left, y, AriseTheme.TEXT);

			Component amount = Component.literal(points + "/" + cap);
			graphics.text(font, amount, valueRight - font.width(amount), y, AriseTheme.MUTED);

			bar(graphics, left, y + 11, LEFT_W - 40, 2, cap <= 0 ? 0.0 : (double) points / cap,
					AriseTheme.ACCENT_DEEP);

			y += ROW;
		}

		drawSources(graphics, player);
	}

	private void drawSources(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int left = bodyLeft() + LEFT_W + 10;
		int right = bodyRight();
		int y = bodyTop() + 6;

		sectionLabel(graphics, Component.translatable("arise.screen.status.sources"), left, y);
		y += 13;

		// Tutte e dodici, non solo le spendibili: le altre otto esistono solo grazie
		// all'equipaggiamento, e questo e' l'unico posto dove si vedono.
		for (Stat stat : Stat.values()) {
			double bonus = external(stat);
			double total = player.getAttributeValue(stat.attribute());

			if (bonus == 0.0 && !stat.spendable()) {
				continue;
			}

			keyValue(graphics, Component.translatable(stat.translationKey()),
					Component.literal(String.format("%.2f", total)), left, right, y, AriseTheme.TEXT);

			if (bonus != 0.0) {
				Component from = Component.literal(stat.format(bonus));
				graphics.text(font, from, right - font.width(from) - 44, y, AriseTheme.GOOD);
			}

			y += 11;
		}
	}
}
