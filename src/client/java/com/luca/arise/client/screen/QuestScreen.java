package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.gear.GearUnique;
import com.luca.arise.quest.Quest;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Gli incarichi del Sistema.
 *
 * <p>Mostra <em>tutta</em> la catena, anche gli incarichi non ancora raggiunti. Nasconderli
 * sarebbe stato piu' misterioso e molto meno utile: vedere che l'Abyss Shop arriva al settimo
 * passo e' la risposta alla domanda che un giocatore si fa davvero, cioe' "quanto manca prima che
 * mi diano X".
 *
 * <p>Tre stati per riga, e si distinguono senza leggere: fatto, in corso, ancora lontano.
 */
public class QuestScreen extends AriseScreen {

	private static final int PANEL_W = 400;
	private static final int PANEL_H = 232;
	private static final int LIST_W = 190;

	private final ListPanel<Quest> list = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private int selectedIndex = -1;

	public QuestScreen() {
		super(Component.translatable("arise.screen.quest.title"), PANEL_W, PANEL_H);
	}

	private PlayerQuests quests() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerQuests quests = player == null ? null : player.getAttached(ModAttachments.QUESTS);
		return quests == null ? PlayerQuests.INITIAL : quests;
	}

	@Override
	protected void layout() {
		int top = bodyTop() + 16;
		list.bounds(bodyLeft(), top, LIST_W, bodyBottom() - top - 4);

		// La selezione parte dall'incarico corrente: e' quello che si sta aprendo la schermata per
		// guardare, nove volte su dieci.
		if (selectedIndex < 0) {
			PlayerQuests quests = quests();
			selectedIndex = quests.finished() ? Quest.count() - 1 : quests.index();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (list.mouseClicked(event.x(), event.y())) {
			selectedIndex = list.selectedIndex();
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

	@Override
	protected Component status() {
		PlayerQuests quests = quests();
		return Component.translatable("arise.screen.quest.header",
				Math.min(quests.index(), Quest.count()), Quest.count());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.quest.hint", key("quests"));
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		list.items(List.of(Quest.values()));
		list.select(selectedIndex);

		list.render(graphics, mouseX, mouseY, (g, quest, x, y, width, selected, hovered) ->
				drawQuest(g, quest, x, y, width));

		drawDetail(graphics);
	}

	private void drawQuest(GuiGraphicsExtractor graphics, Quest quest, int x, int y, int width) {
		PlayerQuests quests = quests();
		boolean done = quests.completed(quest);
		boolean now = quests.current() == quest;

		int color = done ? AriseTheme.GOOD : now ? AriseTheme.TEXT : AriseTheme.DISABLED;

		// Il segno di stato: pieno se fatto, contornato se in corso, un trattino se lontano.
		if (done) {
			Glyphs.rankPip(graphics, x + 6, y + 8, 9, AriseTheme.GOOD);
		} else if (now) {
			graphics.outline(x + 6, y + 8, 9, 9, AriseTheme.ACCENT);
		} else {
			graphics.fill(x + 7, y + 12, x + 14, y + 13, AriseTheme.LINE);
		}

		graphics.text(font, quest.title(), x + 20, y + 4, color);
		graphics.text(font, quest.grants().label(), x + 20, y + 14,
				done ? AriseTheme.MUTED : AriseTheme.DISABLED);

		if (now) {
			Component count = Component.literal(quests.progress() + "/" + quest.amount());
			graphics.text(font, count, x + width - font.width(count) - 6, y + 9, AriseTheme.ACCENT);
		}
	}

	/**
	 * Un paragrafo mandato a capo, se c'e' spazio.
	 *
	 * <p>Il controllo non e' pignoleria: il pannello e' alto quanto e' alto, e un incarico che
	 * regala anime, equipaggiamento <em>e</em> due paragrafi ci arriva vicino. Meglio un paragrafo
	 * che manca — sta comunque in chat quando l'incarico comincia — di un paragrafo che esce dal
	 * riquadro e finisce sopra ai bottoni.
	 */
	private int paragraph(GuiGraphicsExtractor graphics, Component text, int left, int right, int y,
			int color) {
		int lines = font.split(text, right - left).size();

		if (y + lines * 11 > bodyBottom() - 40) {
			return y;
		}

		graphics.textWithWordWrap(font, text, left, y, right - left, color);
		return y + lines * 11 + 6;
	}

	private void drawDetail(GuiGraphicsExtractor graphics) {
		int left = bodyLeft() + LIST_W + 12;
		int right = bodyRight();
		int y = bodyTop() + 16;

		Quest quest = list.selected();
		if (quest == null) {
			return;
		}

		PlayerQuests quests = quests();
		boolean done = quests.completed(quest);
		boolean now = quests.current() == quest;

		graphics.text(font, quest.title(), left, y, AriseTheme.TEXT);
		y += 15;

		chip(graphics, Component.translatable(done
						? "arise.screen.quest.done"
						: now ? "arise.screen.quest.current" : "arise.screen.quest.later"),
				left, y, done ? AriseTheme.GOOD : now ? AriseTheme.ACCENT : AriseTheme.DISABLED);
		y += 20;

		divider(graphics, left, right, y);
		y += 7;

		graphics.textWithWordWrap(font, quest.description(), left, y, right - left, AriseTheme.TEXT);
		y += 11 * font.split(quest.description(), right - left).size() + 6;

		// Il perche' e il come, sotto al cosa. In chat scorrono via; qui restano.
		y = paragraph(graphics, quest.lore(), left, right, y, AriseTheme.DISABLED);
		y = paragraph(graphics, quest.brief(), left, right, y, AriseTheme.ACCENT);

		if (now) {
			bar(graphics, left, y, right - left, 3,
					(double) quests.progress() / Math.max(1, quest.amount()), AriseTheme.ACCENT);
			y += 12;
		}

		sectionLabel(graphics, Component.translatable("arise.screen.quest.reward"), left, y);
		y += 12;

		keyValue(graphics, Component.translatable("arise.screen.quest.unlocks"),
				quest.grants().label(), left, right, y, AriseTheme.GOLD);
		y += 12;

		if (quest.souls() > 0) {
			keyValue(graphics, Component.translatable("arise.screen.quest.souls"),
					Component.literal(String.valueOf(quest.souls())), left, right, y, AriseTheme.GOLD);
			y += 12;
		}

		if (quest.gearRank() != null) {
			keyValue(graphics, Component.translatable("arise.screen.quest.gear"),
					Component.translatable("arise.rank." + quest.gearRank()), left, right, y,
					AriseTheme.GOLD);
			y += 12;
		}

		// Il pezzo unico ha una riga sua: senza, l'unico incarico che regala un oggetto con un nome
		// proprio era anche l'unico che non prometteva niente.
		GearUnique unique = quest.unique() == null ? null : GearUnique.byName(quest.unique());

		if (unique != null) {
			keyValue(graphics, Component.translatable("arise.screen.quest.gear"),
					unique.label(), left, right, y, AriseTheme.GOLD);
			y += 12;
		}

		// Come si usa quello che questo incarico ha dato, ma solo per gli incarichi gia' fatti: su
		// uno ancora da venire sarebbe la spiegazione di una cosa che non si ha.
		//
		// Il controllo sullo spazio non e' una precauzione teorica: il pannello dei dettagli e'
		// alto quanto e' alto, e un incarico che dia anime, equipaggiamento e una descrizione lunga
		// ci arriva vicino. Meglio una riga che manca di una riga che esce dal riquadro.
		if (done && y + 22 <= bodyBottom()) {
			Component hint = quest.grants().hint();
			graphics.textWithWordWrap(font, hint, left, y + 4, right - left, AriseTheme.MUTED);
		}
	}
}
