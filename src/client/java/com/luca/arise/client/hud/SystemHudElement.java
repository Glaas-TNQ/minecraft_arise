package com.luca.arise.client.hud;

import com.luca.arise.ability.Ability;
import com.luca.arise.ability.AbilityCooldowns;
import com.luca.arise.config.AbilityConfig;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.DailyConfig;
import com.luca.arise.daily.DailyQuest;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Quest;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowOrders;
import com.luca.arise.shadow.ShadowStance;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Il riquadro del Sistema in alto a sinistra: livello, barra XP e promemoria dei punti da spendere.
 *
 * <p>In 26.2 l'HUD non si disegna piu' con {@code GuiGraphics}: si implementa
 * {@link HudElement#extractRenderState} e si disegna su {@link GuiGraphicsExtractor}. Tutto qui e'
 * fatto con riempimenti e testo, senza texture: niente asset da mantenere e nessun problema di
 * scala.
 */
public class SystemHudElement implements HudElement {

	private static final int MARGIN = 8;
	private static final int PANEL_WIDTH = 124;
	private static final int BAR_HEIGHT = 4;
	private static final int PADDING = 4;

	private static final int COLOR_PANEL = 0xA0000000;
	private static final int COLOR_BORDER = 0xFF3C8BD9;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_XP_TRACK = 0xFF1B2838;
	private static final int COLOR_XP_FILL = 0xFF4FC3F7;
	private static final int COLOR_POINTS = 0xFFFFD54F;
	private static final int COLOR_SOULS = 0xFFFFD54F;
	private static final int COLOR_STANCE = 0xFF9BA8B8;

	/** L'ordine in corso: rosso caldo, perche' e' l'unica riga del pannello che e' temporanea. */
	private static final int COLOR_ORDER = 0xFFE86A6A;
	private static final int COLOR_LOCKED = 0xFF5A6B80;
	private static final int COLOR_QUEST = 0xFF7FD97F;
	private static final int COLOR_COOLDOWN = 0xB01B2838;

	private static final int SLOT_SIZE = 18;
	private static final int SLOT_GAP = 3;

	/** Quanto sopra il bordo inferiore: sopra la hotbar, senza coprire la barra dell'esperienza. */
	private static final int SLOT_BOTTOM_MARGIN = 62;

	/** Durata del lampo di salita di livello. */
	private static final long FLASH_MILLIS = 2500L;

	private int lastLevel = -1;
	private long flashUntil = 0L;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null) {
			lastLevel = -1;
			return;
		}

		// Sul client il dato arriva per sincronizzazione: finche' non e' arrivato non disegniamo
		// nulla, invece di inventare valori di default che poi "saltano".
		PlayerProgress progress = player.getAttached(ModAttachments.PROGRESS);
		if (progress == null) {
			return;
		}

		// Prima del risveglio non c'e' nessun Sistema da disegnare: l'HUD e' la prima cosa che
		// arriva, e vederlo comparire e' meta' della scena.
		PlayerQuests quests = player.getAttached(ModAttachments.QUESTS);
		if (quests == null || !quests.has(Unlock.SYSTEM)) {
			lastLevel = -1;
			return;
		}

		detectLevelUp(progress.level());
		drawPanel(graphics, minecraft.font, progress, player.getAttached(ModAttachments.STANCE),
				player.getAttached(ModAttachments.ORDERS), quests);

		if (quests.has(Unlock.ABILITIES)) {
			drawAbilities(graphics, minecraft, progress.level(),
					player.getAttached(ModAttachments.COOLDOWNS));
		}

		drawLevelUpFlash(graphics, minecraft, progress.level());
	}

	private void detectLevelUp(int level) {
		if (lastLevel != -1 && level > lastLevel) {
			flashUntil = System.currentTimeMillis() + FLASH_MILLIS;
		}
		lastLevel = level;
	}

	/**
	 * Quanti obiettivi della giornata sono ancora aperti. Zero se e' chiusa, o se non e' mai stata
	 * chiesta.
	 *
	 * <p>Il conto lo fa il client sui dati che gia' ha: la giornaliera e' sincronizzata verso il
	 * proprietario come tutto il resto, e mandare in rete un numero che si ricava da quattro numeri
	 * gia' presenti sarebbe un pacchetto per niente.
	 */
	private int openTasks() {
		LocalPlayer player = Minecraft.getInstance().player;
		DailyQuest daily = player == null ? null : player.getAttached(ModAttachments.DAILY);
		DailyConfig config = AriseConfig.get().daily();

		if (daily == null || !config.enabled() || daily.settled() || daily.day() < 0) {
			return 0;
		}

		return daily.remaining(config);
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Font font, PlayerProgress progress,
			ShadowStance stance, ShadowOrders orders, PlayerQuests quests) {
		long needed = AriseConfig.get().xpForNextLevel(progress.level());
		float fraction = needed <= 0 ? 1.0F : Math.min(1.0F, (float) progress.xp() / needed);

		Quest quest = quests.current();

		// L'ordine in corso e' l'unica riga che compare e sparisce da sola: un esercito che smette
		// di seguire, o che si accanisce su un bersaglio solo, e' un comportamento che va spiegato
		// mentre dura, o sembra un difetto.
		Component order = orders == null ? null : orders.label();

		// La giornaliera compare solo finche' e' aperta: chiusa, la riga sparisce da sola e il
		// pannello torna com'era. Una riga che dicesse «4/4» tutto il pomeriggio sarebbe un
		// promemoria di una cosa gia' fatta, che e' il modo piu' rapido di far smettere di leggere
		// il pannello.
		int daysOpen = openTasks();

		// Righe: livello, soul coin, postura, più i punti da spendere, l'ordine in corso,
		// l'incarico e la giornaliera, ciascuno solo quando c'è.
		int lines = 3 + (progress.unspentPoints() > 0 ? 1 : 0) + (order == null ? 0 : 1)
				+ (quest == null ? 0 : 1) + (daysOpen > 0 ? 1 : 0);
		int height = PADDING * 2 + font.lineHeight * lines + PADDING + BAR_HEIGHT;

		graphics.fill(MARGIN, MARGIN, MARGIN + PANEL_WIDTH, MARGIN + height, COLOR_PANEL);
		graphics.horizontalLine(MARGIN, MARGIN + PANEL_WIDTH - 1, MARGIN, COLOR_BORDER);

		int y = MARGIN + PADDING;
		graphics.text(font, Component.translatable("arise.hud.level", progress.level()),
				MARGIN + PADDING, y, COLOR_TEXT);
		y += font.lineHeight;

		graphics.text(font, Component.translatable("arise.hud.souls", progress.souls()),
				MARGIN + PADDING, y, COLOR_SOULS);
		y += font.lineHeight;

		graphics.text(font, Component.translatable("arise.hud.stance",
				(stance == null ? ShadowStance.DEFENSIVE : stance).label()),
				MARGIN + PADDING, y, COLOR_STANCE);
		y += font.lineHeight;

		if (progress.unspentPoints() > 0) {
			graphics.text(font, Component.translatable("arise.hud.points", progress.unspentPoints()),
					MARGIN + PADDING, y, COLOR_POINTS);
			y += font.lineHeight;
		}

		if (order != null) {
			graphics.text(font, order, MARGIN + PADDING, y, COLOR_ORDER);
			y += font.lineHeight;
		}

		// L'incarico in corso, col contatore: e' la riga che dice cosa fare adesso, e senza di lei
		// la catena esisterebbe solo dentro una schermata che nessuno tiene aperta.
		if (quest != null) {
			graphics.text(font, Component.translatable("arise.hud.quest", quest.title(),
					quests.progress(), quest.amount()), MARGIN + PADDING, y, COLOR_QUEST);
			y += font.lineHeight;
		}

		if (daysOpen > 0) {
			graphics.text(font, Component.translatable("arise.hud.daily", 4 - daysOpen),
					MARGIN + PADDING, y, COLOR_STANCE);
			y += font.lineHeight;
		}

		y += PADDING - 1;
		int barLeft = MARGIN + PADDING;
		int barRight = MARGIN + PANEL_WIDTH - PADDING;
		graphics.fill(barLeft, y, barRight, y + BAR_HEIGHT, COLOR_XP_TRACK);

		int filled = (int) ((barRight - barLeft) * fraction);
		if (filled > 0) {
			graphics.fill(barLeft, y, barLeft + filled, y + BAR_HEIGHT, COLOR_XP_FILL);
		}
	}

	/**
	 * La barra delle abilità, sopra la hotbar.
	 *
	 * <p>Le abilità non ancora sbloccate restano visibili ma spente: sapere che esistono e a che
	 * livello arrivano è metà del motivo per salire di livello.
	 */
	private void drawAbilities(GuiGraphicsExtractor graphics, Minecraft minecraft, int level,
			AbilityCooldowns cooldowns) {
		AbilityConfig config = AriseConfig.get().abilities();
		Ability[] abilities = Ability.values();
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();

		int totalWidth = abilities.length * SLOT_SIZE + (abilities.length - 1) * SLOT_GAP;
		int left = (graphics.guiWidth() - totalWidth) / 2;
		int top = graphics.guiHeight() - SLOT_BOTTOM_MARGIN;

		for (int i = 0; i < abilities.length; i++) {
			Ability ability = abilities[i];
			int x = left + i * (SLOT_SIZE + SLOT_GAP);
			boolean unlocked = level >= ability.requiredLevel();

			graphics.fill(x, top, x + SLOT_SIZE, top + SLOT_SIZE, COLOR_PANEL);

			if (!unlocked) {
				graphics.centeredText(minecraft.font, Component.literal(String.valueOf(ability.requiredLevel())),
						x + SLOT_SIZE / 2, top + 5, COLOR_LOCKED);
				continue;
			}

			// Il riempimento cala dall'alto verso il basso man mano che la ricarica scorre.
			float remaining = cooldowns == null ? 0.0F
					: cooldowns.remainingFraction(ability, now, config.cooldownTicks(ability));

			if (remaining > 0.0F) {
				int filled = (int) (SLOT_SIZE * remaining);
				graphics.fill(x, top, x + SLOT_SIZE, top + filled, COLOR_COOLDOWN);
			}

			graphics.horizontalLine(x, x + SLOT_SIZE - 1, top, remaining > 0 ? COLOR_LOCKED : COLOR_BORDER);
			graphics.centeredText(minecraft.font, Component.literal(ability.initial()),
					x + SLOT_SIZE / 2, top + 5, remaining > 0 ? COLOR_LOCKED : COLOR_TEXT);
		}
	}

	/** Il riquadro del "Sistema" che compare al centro quando si sale di livello, e sfuma. */
	private void drawLevelUpFlash(GuiGraphicsExtractor graphics, Minecraft minecraft, int level) {
		long remaining = flashUntil - System.currentTimeMillis();
		if (remaining <= 0) {
			return;
		}

		// Opaco per il primo terzo, poi dissolvenza: sparire subito lo renderebbe illeggibile.
		float progress = remaining / (float) FLASH_MILLIS;
		float opacity = Math.min(1.0F, progress * 1.5F);
		int alpha = (int) (opacity * 255) << 24;

		Font font = minecraft.font;
		Component text = Component.translatable("arise.hud.level_up", level);
		int width = font.width(text) + 24;
		int height = font.lineHeight + 12;
		int left = (graphics.guiWidth() - width) / 2;
		int top = graphics.guiHeight() / 4;

		graphics.fill(left, top, left + width, top + height, alpha & 0xC0000000);
		graphics.horizontalLine(left, left + width - 1, top, alpha | (COLOR_BORDER & 0x00FFFFFF));
		graphics.horizontalLine(left, left + width - 1, top + height - 1, alpha | (COLOR_BORDER & 0x00FFFFFF));
		graphics.centeredText(font, text, graphics.guiWidth() / 2, top + 6, alpha | (COLOR_TEXT & 0x00FFFFFF));
	}
}
