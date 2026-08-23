package com.luca.arise.client.screen;

import java.util.UUID;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.network.ShadowActionPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowData;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Dettaglio di una singola ombra: nome, colore, potenziamento, congedo.
 *
 * <p>Schermata separata invece di altri quattro bottoni per riga nell'esercito: una riga con sei
 * comandi diventa illeggibile appena le ombre sono più di tre, e il rinominare ha comunque bisogno
 * di un campo di testo che in una lista non ci sta.
 */
public class ShadowDetailScreen extends AriseScreen {

	/** Tavolozza fissa: un selettore libero sarebbe più codice e peggiori risultati a schermo. */
	private static final int[] PALETTE = {
			0xFFFFFF, 0x4FC3F7, 0x7FD97F, 0xC77FE8,
			0xFFD54F, 0xE86A6A, 0x6A7BE8, 0x9BA8B8,
	};

	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 210;
	private static final int SWATCH = 20;

	private static final int COLOR_TITLE = AriseTheme.ACCENT;
	private static final int COLOR_TEXT = AriseTheme.TEXT;
	private static final int COLOR_DIM = AriseTheme.MUTED;
	private static final int COLOR_SOULS = AriseTheme.GOLD;

	private final UUID shadowId;
	private final Screen parent;

	private EditBox nameBox;
	private int lastFingerprint = Integer.MIN_VALUE;

	/** Dove comincia la fila delle pastiglie: la calcola {@code layout}, la usano disegno e click. */
	private int swatchLeft;
	private int swatchTop;

	public ShadowDetailScreen(UUID shadowId, Screen parent) {
		super(Component.translatable("arise.screen.detail.title"), PANEL_WIDTH, PANEL_HEIGHT);
		this.shadowId = shadowId;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private ShadowData shadow() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		if (player == null) {
			return null;
		}

		var army = player.getAttached(ModAttachments.ARMY);
		return army == null ? null : army.find(shadowId).orElse(null);
	}

	@Override
	protected void layout() {
		ShadowData shadow = shadow();
		if (shadow == null) {
			// L'ombra è stata congedata mentre eravamo qui: non c'è più niente da mostrare.
			onClose();
			return;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		int left = bodyLeft();
		int top = bodyTop() + 2;

		nameBox = new EditBox(font, left, top + 28, PANEL_WIDTH - 70, 20,
				Component.translatable("arise.screen.detail.name"));
		nameBox.setMaxLength(ShadowActionPayload.MAX_NAME_LENGTH);
		nameBox.setValue(shadow.displayName().getString());
		addRenderableWidget(nameBox);

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.detail.rename",
						config.costs().rename()), button -> send(ShadowActionPayload.Action.RENAME))
				.bounds(left + PANEL_WIDTH - 66, top + 28, 66, 20)
				.build());

		// Le pastiglie non sono widget, ed e' la stessa decisione presa per le righe delle liste:
		// il corpo della schermata si disegna nello *sfondo* (vedi AriseScreen), quindi qualunque
		// bottone messo qui finisce sopra al colore e lo copre. Erano otto quadrati grigi
		// identici, con l'unico segno visibile il trattino della selezione, che cade appena sotto.
		swatchLeft = left;
		swatchTop = top + 74;

		int actionsTop = top + 120;
		boolean maxLevel = shadow.isMaxLevel(config);
		long upgradeCost = config.costs().upgradeCost(shadow.level());

		Button upgrade = Button.builder(
						maxLevel
								? Component.translatable("arise.screen.detail.max_level")
								: Component.translatable("arise.screen.detail.upgrade", upgradeCost),
						button -> send(ShadowActionPayload.Action.UPGRADE))
				.bounds(left, actionsTop, 124, 20)
				.build();
		upgrade.active = !maxLevel && souls() >= upgradeCost;
		addRenderableWidget(upgrade);

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.detail.dismiss"),
						button -> send(ShadowActionPayload.Action.DISMISS))
				.bounds(left + PANEL_WIDTH - 124, actionsTop, 124, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.detail.back"),
						button -> onClose())
				.bounds(left + PANEL_WIDTH / 2 - 50, actionsTop + 26, 100, 20)
				.build());
	}

	private void send(ShadowActionPayload.Action action) {
		String name = action == ShadowActionPayload.Action.RENAME && nameBox != null
				? nameBox.getValue()
				: "";
		ClientPlayNetworking.send(new ShadowActionPayload(shadowId, action, name, 0));
	}

	private void sendColor(int color) {
		ClientPlayNetworking.send(new ShadowActionPayload(shadowId,
				ShadowActionPayload.Action.RECOLOR, "", color));
	}

	/**
	 * Su quale pastiglia cade questo punto, o {@code -1}.
	 *
	 * <p>Una riga di aritmetica al posto di otto widget: la stessa scelta delle liste, e con lo
	 * stesso vantaggio — quello che si vede e quello che si clicca sono calcolati dallo stesso
	 * numero, quindi non possono scollarsi.
	 */
	private int hitSwatch(double mouseX, double mouseY) {
		if (mouseY < swatchTop || mouseY >= swatchTop + SWATCH) {
			return -1;
		}

		int offset = (int) (mouseX - swatchLeft);
		int stride = SWATCH + 4;
		int index = offset / stride;

		// Lo spazio fra due pastiglie non e' nessuna delle due.
		if (offset < 0 || index >= PALETTE.length || offset % stride >= SWATCH) {
			return -1;
		}

		return index;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = hitSwatch(event.x(), event.y());

		if (index >= 0) {
			sendColor(PALETTE[index]);
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	/** Come nell'esercito: lo stato vero arriva dal server, i widget lo inseguono. */
	@Override
	public void tick() {
		super.tick();

		ShadowData shadow = shadow();
		if (shadow == null) {
			onClose();
			return;
		}

		int fingerprint = shadow.level() * 31 + shadow.color() * 7 + Long.hashCode(souls())
				+ shadow.displayName().getString().hashCode();

		if (fingerprint != lastFingerprint) {
			lastFingerprint = fingerprint;
			// Il campo di testo è l'unica cosa che non va sovrascritta: il giocatore potrebbe
			// starci scrivendo dentro proprio ora.
			String typed = nameBox == null ? null : nameBox.getValue();
			rebuildWidgets();
			if (typed != null && nameBox != null) {
				nameBox.setValue(typed);
			}
		}
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {

		ShadowData shadow = shadow();
		if (shadow == null) {
			return;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		int left = bodyLeft();
		int top = bodyTop() + 2;

		graphics.text(font, shadow.rank(config).label(), left, top + 4, shadow.rank(config).color());
		graphics.text(font, Component.translatable("arise.screen.army.stats",
				shadow.level(),
				String.format("%.0f", shadow.maxHealth(config)),
				String.format("%.1f", shadow.attackDamage(config))),
				left + 20, top + 4, COLOR_TEXT);

		graphics.text(font, Component.translatable("arise.screen.detail.color",
				config.costs().recolor()), left, top + 60, COLOR_DIM);

		// La pastiglia di colore va disegnata sopra il bottone: il bottone è solo la zona
		// cliccabile, il colore è l'informazione.
		for (int i = 0; i < PALETTE.length; i++) {
			int x = swatchLeft + i * (SWATCH + 4);
			boolean chosen = PALETTE[i] == shadow.color();
			boolean hovered = hitSwatch(mouseX, mouseY) == i;

			graphics.fill(x, swatchTop, x + SWATCH, swatchTop + SWATCH, 0xFF000000 | PALETTE[i]);
			graphics.outline(x, swatchTop, SWATCH, SWATCH,
					chosen ? COLOR_TEXT : hovered ? COLOR_TITLE : AriseTheme.LINE);

			if (chosen) {
				graphics.horizontalLine(x + 2, x + SWATCH - 3, swatchTop + SWATCH + 2, COLOR_TEXT);
			}
		}

		graphics.centeredText(font, Component.translatable("arise.screen.detail.souls", souls()),
				width / 2, top + 102, COLOR_SOULS);
	}
}
