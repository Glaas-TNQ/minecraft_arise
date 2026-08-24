package com.luca.arise.map;

/**
 * La geometria dei riquadri di terreno: quanto e' grande un campione, dove cade un riquadro, quale
 * livello di dettaglio serve a una certa scala.
 *
 * <p>Aritmetica pura, e sta nel sorgente comune per la stessa ragione di {@link MapProjection}: la
 * calcolano il server (che i riquadri li produce) e il client (che li chiede e li disegna), e se i
 * due la calcolassero in modo anche solo leggermente diverso il client chiederebbe riquadri che il
 * server considera altri riquadri. Un difetto del genere non da' nessun errore: da' una mappa con
 * dei buchi che si spostano quando ci si muove.
 *
 * <h2>I cinque livelli, e perche' cinque</h2>
 *
 * <p>Un riquadro e' sempre {@value #TILE} campioni per lato; a cambiare e' la <em>distanza fra due
 * campioni</em>, che quadruplica a ogni livello: quattro blocchi, sedici, sessantaquattro,
 * duecentocinquantasei, mille e ventiquattro. Un riquadro copre quindi da 256 blocchi a 65 536, e i
 * cinque livelli insieme coprono dal prato attorno ai piedi al continente intero.
 *
 * <p>Oltre l'ultimo livello il terreno non si disegna, e non e' una rinuncia: alla scala in cui un
 * riquadro da 65 536 blocchi sta in quattro pixel, disegnare il terreno vorrebbe dire produrre
 * migliaia di riquadri per ottenere una tinta uniforme. La' restano il reticolo e i segni, che sono
 * l'unica cosa che a quella distanza vuol dire qualcosa.
 */
public final class MapTiles {

	/** Campioni per lato di un riquadro. */
	public static final int TILE = 64;

	/** Il livello piu' grossolano. Oltre, il terreno non si disegna. */
	public static final int MAX_LOD = 4;

	private MapTiles() {
	}

	/** Distanza in blocchi fra due campioni a questo livello: 4, 16, 64, 256, 1024. */
	public static int step(int lod) {
		return 4 << (2 * clampLod(lod));
	}

	/** Lato in blocchi di un riquadro a questo livello. */
	public static int span(int lod) {
		return TILE * step(lod);
	}

	/**
	 * Il livello che serve a questa scala.
	 *
	 * <p>Il piu' fine in cui un campione copra almeno un pixel. Piu' fine sarebbe lavoro buttato —
	 * si calcolerebbero campioni che finiscono nello stesso pixel — e piu' grossolano si vedrebbe,
	 * perche' ogni campione diventerebbe un quadrato.
	 *
	 * @param scale blocchi per pixel
	 * @return il livello, oppure {@code -1} se a questa scala il terreno non si disegna
	 */
	public static int lodFor(double scale) {
		for (int lod = 0; lod <= MAX_LOD; lod++) {
			if (step(lod) >= scale) {
				return lod;
			}
		}

		return -1;
	}

	/** L'indice del riquadro che contiene questa coordinata. */
	public static int tileOf(double world, int lod) {
		return (int) Math.floor(world / span(lod));
	}

	/** La coordinata del suo angolo. */
	public static int originOf(int tile, int lod) {
		return tile * span(lod);
	}

	/**
	 * I tre numeri di un riquadro in uno solo, per poterlo usare come chiave.
	 *
	 * <p>Ventiquattro bit per indice: a livello zero un riquadro copre 256 blocchi, quindi
	 * ottomilioni di riquadri sono due miliardi di blocchi per lato — quattro volte il bordo del
	 * mondo. Non ci si arriva nemmeno barando.
	 */
	public static long key(int lod, int tileX, int tileZ) {
		return ((long) (clampLod(lod) & 0xF) << 56)
				| ((long) (tileX & 0xFFFFFF) << 24)
				| (tileZ & 0xFFFFFF);
	}

	private static int clampLod(int lod) {
		return Math.clamp(lod, 0, MAX_LOD);
	}
}
