package com.luca.arise.client.hud;

import java.util.List;

import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Quest;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Il tracciato dell'incarico in corso, sul bordo destro: cosa si sta facendo e <em>come si fa</em>.
 *
 * <h2>Perche' esiste</h2>
 *
 * <p>Il riquadro del Sistema, a sinistra, ha sempre avuto una riga per l'incarico: titolo e
 * contatore, {@code Uccidi creature 7/15}. Bastava per gli incarichi che si spiegano da soli, e non
 * bastava per nessuno degli altri. «Fai girare un macchinario 0/1» e' un compito che si legge in
 * due secondi e su cui ci si puo' fermare mezz'ora, perche' non dice che il macchinario va prima
 * costruito, che la ricetta e' arrivata nel libro e che il Progetto e' un ingrediente.
 *
 * <p>Quelle spiegazioni la mod le aveva gia' scritte — il {@code brief} di ogni incarico — e le
 * diceva <strong>una volta sola</strong>, in chat, nel momento in cui l'incarico veniva assegnato.
 * Chi tornava a giocare il giorno dopo non aveva nessun posto dove rileggerle, se non aprire una
 * schermata di cui non sapeva l'esistenza. Qui stanno, spezzate in passi numerati, sempre.
 *
 * <h2>I tre stati</h2>
 *
 * <p>Un pannello che non si puo' spegnere e' un pannello che a un certo punto da' fastidio, e uno
 * che si puo' solo spegnere e' un pannello che poi non si ritrova. Tre stati sullo stesso tasto:
 * <strong>disteso</strong> (titolo, barra, passi), <strong>stretto</strong> (titolo e barra) e
 * <strong>spento</strong>. Si parte disteso, perche' chi vede la mod per la prima volta e' esattamente
 * la persona per cui i passi sono stati scritti.
 */
public class QuestTrackerElement implements HudElement {

	/** Quanto e' largo il pannello. Abbastanza per una riga di passo in tre righe di testo. */
	private static final int WIDTH = 148;

	private static final int MARGIN = 8;
	private static final int PADDING = 5;
	private static final int BAR_HEIGHT = 3;
	private static final int LINE = 10;

	private static final int COLOR_PANEL = 0xA0000000;
	private static final int COLOR_BORDER = 0xFF8E7CFF;
	private static final int COLOR_TITLE = 0xFFE8F2FF;
	private static final int COLOR_HEADER = 0xFF9BA8B8;
	private static final int COLOR_STEP = 0xFFC9D4E4;
	private static final int COLOR_NUMBER = 0xFF8E7CFF;
	private static final int COLOR_TRACK = 0xFF1B2838;
	private static final int COLOR_FILL = 0xFF7FD97F;

	/** Come si mostra il tracciato. L'ordine e' quello in cui il tasto li fa girare. */
	public enum Mode {
		FULL,
		COMPACT,
		OFF;

		public Component label() {
			return Component.translatable("arise.msg.tracker." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/**
	 * Statico per la stessa ragione dell'interruttore dell'HUD: l'elemento e' registrato una volta
	 * all'avvio e nessuno ne tiene il riferimento, quindi il tasto non ha niente su cui chiamare un
	 * metodo. Non si salva — riaprire il gioco rimette il pannello disteso, che e' lo stato giusto
	 * per un interruttore premuto per sbaglio.
	 */
	private static Mode mode = Mode.FULL;

	/** Passa allo stato successivo e lo restituisce. */
	public static Mode cycle() {
		mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
		return mode;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (mode == Mode.OFF) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || !SystemHudElement.visible()) {
			return;
		}

		PlayerQuests quests = player.getAttached(ModAttachments.QUESTS);

		// Prima del risveglio non c'e' nessuna catena, e a catena finita non c'e' piu' niente da
		// tracciare: in tutti e due i casi il pannello non c'e', invece di esserci vuoto.
		if (quests == null || !quests.has(Unlock.SYSTEM)) {
			return;
		}

		Quest quest = quests.current();

		if (quest == null) {
			return;
		}

		draw(graphics, minecraft.font, quests, quest);
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, PlayerQuests quests, Quest quest) {
		int inner = WIDTH - PADDING * 2;
		List<Component> steps = mode == Mode.FULL ? quest.steps() : List.of();

		// L'altezza si calcola prima di disegnare qualunque cosa, perche' i passi vanno a capo e
		// quante righe occupino lo sa solo il font. Un pannello disegnato con un'altezza indovinata
		// e' un pannello che il giorno di una traduzione piu' lunga taglia l'ultima riga.
		int height = PADDING * 2 + LINE * 2 + BAR_HEIGHT + 3;

		for (Component step : steps) {
			height += 3 + LINE * font.split(step, inner - 9).size();
		}

		int left = graphics.guiWidth() - MARGIN - WIDTH;
		int top = MARGIN;

		graphics.fill(left, top, left + WIDTH, top + height, COLOR_PANEL);
		graphics.horizontalLine(left, left + WIDTH - 1, top, COLOR_BORDER);

		int x = left + PADDING;
		int y = top + PADDING;

		// L'intestazione dice a che punto della catena si e': senza, il pannello sa dire cosa fare
		// adesso ma non che esista un dopo.
		graphics.text(font, Component.translatable("arise.hud.tracker.header",
				quests.index() + 1, Quest.count()), x, y, COLOR_HEADER);
		y += LINE;

		graphics.text(font, quest.title(), x, y, COLOR_TITLE);

		Component count = Component.literal(quests.progress() + "/" + quest.amount());
		graphics.text(font, count, left + WIDTH - PADDING - font.width(count), y, COLOR_FILL);
		y += LINE;

		graphics.fill(x, y, x + inner, y + BAR_HEIGHT, COLOR_TRACK);
		int filled = (int) ((long) inner * Math.min(quests.progress(), quest.amount())
				/ Math.max(1, quest.amount()));

		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + BAR_HEIGHT, COLOR_FILL);
		}

		y += BAR_HEIGHT + 3;

		int number = 1;
		for (Component step : steps) {
			graphics.text(font, Component.literal(number + "."), x, y, COLOR_NUMBER);
			graphics.textWithWordWrap(font, step, x + 9, y, inner - 9, COLOR_STEP);

			y += 3 + LINE * font.split(step, inner - 9).size();
			number++;
		}
	}

	/** Il messaggio da mandare quando il tasto cambia stato. */
	public static Component announce(Mode next) {
		return Component.translatable("arise.msg.tracker.mode", next.label(),
						Component.keybind("key.arise.tracker"))
				.withStyle(ChatFormatting.GRAY);
	}
}
