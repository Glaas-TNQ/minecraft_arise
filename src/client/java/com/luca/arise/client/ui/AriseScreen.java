package com.luca.arise.client.ui;

import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * La finestra del Sistema: il telaio comune a tutte le schermate della mod.
 *
 * <p>Prima ogni schermata si disegnava il proprio pannello centrato a mano, con le sue costanti e
 * le sue misure. Il risultato era che aprirne due di seguito si vedeva: titoli a quote diverse,
 * righe di altezza diversa, e nessun posto dove leggere il saldo senza chiuderla.
 *
 * <p>Qui il telaio e' uno solo — velo, pannello, riga d'accento in alto, titolo a sinistra, stato
 * a destra, suggerimento in fondo — e le sottoclassi riempiono il corpo. La riga d'accento in alto
 * e' la stessa convenzione delle pagine di consegna del progetto.
 */
public abstract class AriseScreen extends Screen {

	private final int wantedWidth;
	private final int wantedHeight;

	/** Il rettangolo del pannello, ricalcolato a ogni {@code init}. */
	protected int panelX;
	protected int panelY;
	protected int panelWidth;
	protected int panelHeight;

	protected AriseScreen(Component title, int wantedWidth, int wantedHeight) {
		super(title);
		this.wantedWidth = wantedWidth;
		this.wantedHeight = wantedHeight;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// Il pannello non e' mai piu' grande della finestra: a scala GUI 4 su uno schermo piccolo
		// lo spazio virtuale scende sotto i 300 pixel, e un pannello fisso finirebbe fuori dai
		// bordi con i bottoni irraggiungibili.
		panelWidth = Math.min(wantedWidth, width - 20);
		panelHeight = Math.min(wantedHeight, height - 20);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		layout();
	}

	/** Dove le sottoclassi creano i loro widget. Il pannello e' gia' misurato. */
	protected abstract void layout();

	/** Dove le sottoclassi disegnano il corpo. */
	protected abstract void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	/** La riga a destra del titolo: rango, saldo, quello che conta sempre. Puo' mancare. */
	protected Component status() {
		return null;
	}

	/** Il suggerimento in fondo. Puo' mancare. */
	protected Component hint() {
		return null;
	}

	/** Il colore della riga d'accento: distingue una schermata dall'altra a colpo d'occhio. */
	protected int accent() {
		return AriseTheme.ACCENT;
	}

	// ---------------------------------------------------------------- riquadri

	/** Il primo pixel utile sotto la fascia del titolo. */
	protected int bodyTop() {
		return panelY + AriseTheme.HEADER;
	}

	/** Il primo pixel occupato dalla fascia in fondo. */
	protected int bodyBottom() {
		return panelY + panelHeight - AriseTheme.FOOTER;
	}

	protected int bodyLeft() {
		return panelX + AriseTheme.PAD;
	}

	protected int bodyRight() {
		return panelX + panelWidth - AriseTheme.PAD;
	}

	// ---------------------------------------------------------------- disegno

	/**
	 * Il telaio e il corpo si disegnano nello <em>sfondo</em>, non nel disegno principale.
	 *
	 * <p>E' l'ordine che conta: {@code extractRenderState} disegna prima lo sfondo e poi i widget.
	 * Un pannello dipinto li' dentro finisce <strong>sopra i bottoni</strong> e li nasconde — i
	 * bottoni restano cliccabili, ma invisibili, che e' il modo peggiore di rompere una schermata.
	 * Disegnando qui, l'ordine diventa quello giusto: sfondo, telaio, corpo, e i widget sopra tutto.
	 */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		graphics.fill(0, 0, width, height, AriseTheme.SCRIM);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, AriseTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, AriseTheme.LINE);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, accent());

		graphics.text(font, title, bodyLeft(), panelY + 11, AriseTheme.TEXT);

		Component status = status();
		if (status != null) {
			graphics.text(font, status, bodyRight() - font.width(status), panelY + 11, AriseTheme.MUTED);
		}

		graphics.fill(panelX + 1, bodyTop() - 1, panelX + panelWidth - 1, bodyTop(), AriseTheme.LINE_SOFT);

		content(graphics, mouseX, mouseY);

		Component hint = hint();
		if (hint != null) {
			graphics.fill(panelX + 1, bodyBottom(), panelX + panelWidth - 1, bodyBottom() + 1,
					AriseTheme.LINE_SOFT);
			graphics.text(font, hint, bodyLeft(), bodyBottom() + 7, AriseTheme.DISABLED);
		}
	}

	// ---------------------------------------------------------------- pezzi riusabili

	/** Un'etichetta di sezione: maiuscoletto spento sopra un gruppo. */
	protected void sectionLabel(GuiGraphicsExtractor graphics, Component text, int x, int y) {
		graphics.text(font, text, x, y, AriseTheme.DISABLED);
	}

	/** Una riga chiave/valore, col valore allineato a destra. */
	protected void keyValue(GuiGraphicsExtractor graphics, Component key, Component value,
			int left, int right, int y, int valueColor) {
		graphics.text(font, key, left, y, AriseTheme.MUTED);
		graphics.text(font, value, right - font.width(value), y, valueColor);
	}

	/** Una barra piena a una frazione, col fondo sempre visibile. */
	protected void bar(GuiGraphicsExtractor graphics, int x, int y, int barWidth, int barHeight,
			double fraction, int color) {
		graphics.fill(x, y, x + barWidth, y + barHeight, AriseTheme.LINE_SOFT);

		int filled = (int) Math.round(barWidth * Math.clamp(fraction, 0.0, 1.0));
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + barHeight, color);
		}
	}

	/** Una targhetta con bordo: rango, stato, conteggio. Restituisce quanto e' larga. */
	protected int chip(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
		int chipWidth = font.width(text) + 8;

		graphics.fill(x, y, x + chipWidth, y + 12, AriseTheme.alpha(color, 0x22));
		graphics.outline(x, y, chipWidth, 12, AriseTheme.alpha(color, 0x88));
		graphics.text(font, text, x + 4, y + 2, color);

		return chipWidth;
	}

	protected void divider(GuiGraphicsExtractor graphics, int left, int right, int y) {
		graphics.fill(left, y, right, y + 1, AriseTheme.LINE_SOFT);
	}

	// ---------------------------------------------------------------- stato del giocatore

	protected PlayerProgress progress() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);
		return progress == null ? PlayerProgress.INITIAL : progress;
	}

	protected long souls() {
		return progress().souls();
	}
}
