package com.luca.arise.client.ui;

/**
 * I colori e le misure di tutte le schermate della mod, in un posto solo.
 *
 * <p>Prima ogni schermata si dichiarava le sue costanti in cima, e il risultato erano cinque
 * grigi leggermente diversi e quattro idee di quanto sia alta una riga. Qui c'e' una tavolozza
 * sola, ed e' la stessa delle pagine di consegna del progetto: chi guarda una schermata e chi
 * legge la documentazione vedono lo stesso blu.
 *
 * <p>La tavolozza e' dichiaratamente scura e non segue il tema del gioco, perche' non e' una
 * finestra di Minecraft: e' una finestra del Sistema.
 */
public final class AriseTheme {

	// ---------------------------------------------------------------- colori

	/** Il velo sopra il gioco. Semitrasparente: il mondo resta visibile e la finestra galleggia. */
	public static final int SCRIM = 0xB0060810;

	public static final int PANEL = 0xF2121A2A;
	public static final int PANEL_SOFT = 0xF00E1522;
	public static final int ROW = 0x40000000;
	public static final int ROW_HOVER = 0x33FFFFFF;
	public static final int ROW_SELECTED = 0x304FC3F7;

	public static final int LINE = 0xFF1F2C42;
	public static final int LINE_SOFT = 0xFF172133;

	public static final int ACCENT = 0xFF4FC3F7;
	public static final int ACCENT_DEEP = 0xFF3C8BD9;
	public static final int GOLD = 0xFFFFD54F;
	public static final int VIOLET = 0xFF8E6BFF;
	public static final int GOOD = 0xFF7FD97F;
	public static final int WARN = 0xFFE8A64F;
	public static final int BAD = 0xFFE86A6A;

	public static final int TEXT = 0xFFE8F2FF;
	public static final int MUTED = 0xFF8FA0B5;
	public static final int DISABLED = 0xFF6B7684;

	// ---------------------------------------------------------------- misure

	/** Margine interno del pannello. */
	public static final int PAD = 14;
	/** Altezza della fascia del titolo. */
	public static final int HEADER = 30;
	/** Altezza della fascia in fondo, dove stanno i suggerimenti. */
	public static final int FOOTER = 20;
	/** Altezza di una riga di lista. Due righe di testo ci stanno comode. */
	public static final int ROW_HEIGHT = 26;
	/** Larghezza della barra di scorrimento. */
	public static final int SCROLLBAR = 4;
	/** Lato di un'icona. */
	public static final int ICON = 8;

	private AriseTheme() {
	}

	// ---------------------------------------------------------------- aiuti

	/** Lo stesso colore con un'altra opacita'. Serve per spegnere un'icona senza cambiarne il tono. */
	public static int alpha(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
	}

	/** Il colore di un testo che non si puo' usare adesso. */
	public static int dim(boolean enabled) {
		return enabled ? TEXT : DISABLED;
	}
}
