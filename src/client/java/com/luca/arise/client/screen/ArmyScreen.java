package com.luca.arise.client.screen;

import java.util.UUID;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'esercito d'ombra.
 *
 * <p>Era paginato, sei ombre per volta, con due bottoni per riga. Un esercito da cinquanta
 * significava nove pagine da sfogliare per trovare quella giusta. Ora scorre, e i due bottoni
 * agiscono sull'ombra selezionata — che e' anche quella di cui il pannello a destra sta mostrando
 * vita, danno e progresso.
 *
 * <p>Le ombre evocate hanno un puntino acceso nella riga: e' l'informazione che si cerca piu'
 * spesso, e prima costava leggere la parola sul bottone.
 */
public class ArmyScreen extends AriseScreen {

	private static final int PANEL_W = 430;
	private static final int PANEL_H = 220;
	private static final int LIST_W = 220;

	private final ListPanel<ShadowData> list = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private UUID selectedId;
	private Button toggle;
	private Button details;

	public ArmyScreen() {
		super(Component.translatable("arise.screen.army.title"), PANEL_W, PANEL_H);
	}

	// ---------------------------------------------------------------- stato

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

	// ---------------------------------------------------------------- widget

	@Override
	protected void layout() {
		int left = bodyLeft();
		int top = bodyTop() + 16;
		int listHeight = bodyBottom() - top - 28;

		list.bounds(left, top, LIST_W, listHeight);

		int detailLeft = left + LIST_W + 12;
		int detailRight = bodyRight();
		int buttonsY = bodyBottom() - 24;
		int half = (detailRight - detailLeft - 4) / 2;

		toggle = addRenderableWidget(Button.builder(Component.empty(), button -> toggle())
				.bounds(detailLeft, buttonsY, half, 20).build());
		details = addRenderableWidget(Button.builder(
						Component.translatable("arise.screen.army.details"), button -> openDetails())
				.bounds(detailLeft + half + 4, buttonsY, half, 20).build());
	}

	private void toggle() {
		ShadowData shadow = list.selected();
		if (shadow == null) {
			return;
		}

		ClientPlayNetworking.send(ShadowActionPayload.of(shadow.id(),
				summoned().contains(shadow.id())
						? ShadowActionPayload.Action.RECALL
						: ShadowActionPayload.Action.SUMMON));
	}

	private void openDetails() {
		ShadowData shadow = list.selected();
		if (shadow != null && minecraft != null) {
			minecraft.setScreenAndShow(new ShadowDetailScreen(shadow.id(), this));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (list.mouseClicked(event.x(), event.y())) {
			ShadowData shadow = list.selected();
			selectedId = shadow == null ? null : shadow.id();
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
		return Component.translatable("arise.screen.army.header", army().size(),
				summoned().ids().size(), AriseConfig.get().shadows().maxSummoned());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.army.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ShadowConfig config = AriseConfig.get().shadows();

		list.items(army().shadows());
		if (selectedId != null) {
			list.selectFirst(shadow -> shadow.id().equals(selectedId));
		}

		sectionLabel(graphics, Component.translatable("arise.screen.army.roster"),
				bodyLeft(), bodyTop() + 5);

		if (list.isEmpty()) {
			graphics.text(font, Component.translatable("arise.screen.army.empty"),
					bodyLeft(), bodyTop() + 22, AriseTheme.DISABLED);
		} else {
			list.render(graphics, mouseX, mouseY, (g, shadow, x, y, width, selected, hovered) ->
					drawShadow(g, config, shadow, x, y, width));
		}

		drawDetail(graphics, config);
	}

	private void drawShadow(GuiGraphicsExtractor graphics, ShadowConfig config, ShadowData shadow,
			int x, int y, int width) {
		boolean out = summoned().contains(shadow.id());

		Glyphs.rankPip(graphics, x + 6, y + 8, 9, 0xFF000000 | shadow.color());
		graphics.text(font, shadow.displayName(), x + 20, y + 4,
				out ? AriseTheme.ACCENT : AriseTheme.TEXT);
		graphics.text(font, Component.translatable("arise.screen.army.line", shadow.level(),
				String.format("%.0f", shadow.maxHealth(config)),
				String.format("%.1f", shadow.attackDamage(config))),
				x + 20, y + 14, AriseTheme.MUTED);

		Component rank = shadow.rank(config).label();
		graphics.text(font, rank, x + width - font.width(rank) - 6, y + 4, shadow.rank(config).color());

		// Il puntino dell'ombra fuori: si cerca a colpo d'occhio, e prima bisognava leggere il
		// bottone per saperlo.
		if (out) {
			graphics.fill(x + width - 9, y + 15, x + width - 5, y + 19, AriseTheme.ACCENT);
		}
	}

	private void drawDetail(GuiGraphicsExtractor graphics, ShadowConfig config) {
		int left = bodyLeft() + LIST_W + 12;
		int right = bodyRight();
		int y = bodyTop() + 16;

		ShadowData shadow = list.selected();

		toggle.visible = shadow != null;
		details.visible = shadow != null;

		if (shadow == null) {
			graphics.text(font, Component.translatable("arise.screen.army.pick"), left, y,
					AriseTheme.DISABLED);
			return;
		}

		boolean out = summoned().contains(shadow.id());
		toggle.setMessage(Component.translatable(out
				? "arise.screen.army.recall"
				: "arise.screen.army.summon"));

		graphics.text(font, shadow.displayName(), left, y, AriseTheme.TEXT);
		y += 15;

		chip(graphics, shadow.rank(config).label(), left, y, shadow.rank(config).color());
		y += 20;

		divider(graphics, left, right, y);
		y += 6;

		keyValue(graphics, Component.translatable("arise.screen.army.level"),
				Component.literal(String.valueOf(shadow.level())), left, right, y, AriseTheme.TEXT);
		y += 12;
		keyValue(graphics, Component.translatable("arise.screen.army.health"),
				Component.literal(String.format("%.0f", shadow.maxHealth(config))),
				left, right, y, AriseTheme.TEXT);
		y += 12;
		keyValue(graphics, Component.translatable("arise.screen.army.damage"),
				Component.literal(String.format("%.1f", shadow.attackDamage(config))),
				left, right, y, AriseTheme.TEXT);
		y += 16;

		sectionLabel(graphics, Component.translatable(shadow.isMaxLevel(config)
				? "arise.screen.army.maxed"
				: "arise.screen.army.progress"), left, y);
		y += 11;

		long needed = shadow.xpForNextLevel(config);
		double fraction = shadow.isMaxLevel(config) || needed <= 0
				? 1.0
				: (double) shadow.xp() / needed;
		bar(graphics, left, y, right - left, 3, fraction, AriseTheme.ACCENT_DEEP);
	}
}
