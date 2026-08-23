package com.luca.arise.map;

/**
 * La geometria della mappa: da coordinate del mondo a pixel e ritorno.
 *
 * <p>Sta nel sorgente comune e non nel client perché è aritmetica pura, e l'aritmetica pura va
 * sul banco di prova. Il mondo è grande — le città stanno a duecentomila blocchi, i varchi a
 * cento — e una mappa che voglia mostrare entrambi deve saper cambiare scala di quattro ordini di
 * grandezza senza perdere il punto sotto il mouse. Tutto quello che può andare storto in quel
 * passaggio si prova qui con due numeri, non a occhio in gioco.
 *
 * <p>Le coordinate di schermo sono relative all'angolo in alto a sinistra della finestra della
 * mappa. La scala è in <em>blocchi per pixel</em>: piccola è vicino, grande è lontano.
 */
public final class MapProjection {

	/** Più vicino di così un blocco è un quadrato di quattro pixel: basta. */
	public static final double MIN_SCALE = 0.25;

	/** Più lontano di così l'intero mondo giocabile sta in una finestra da trecento pixel. */
	public static final double MAX_SCALE = 16384.0;

	/** I passi possibili del reticolo, in blocchi: potenze di due, leggibili come coordinate. */
	private static final int[] GRID_STEPS = {
			16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144};

	/** Sotto quanti pixel fra una linea e l'altra il reticolo diventa un tappeto. */
	private static final int MIN_GRID_PIXELS = 40;

	private final int viewWidth;
	private final int viewHeight;

	private double centreX;
	private double centreZ;
	private double scale;

	public MapProjection(int viewWidth, int viewHeight, double centreX, double centreZ, double scale) {
		this.viewWidth = Math.max(1, viewWidth);
		this.viewHeight = Math.max(1, viewHeight);
		this.centreX = centreX;
		this.centreZ = centreZ;
		this.scale = clampScale(scale);
	}

	public int viewWidth() {
		return viewWidth;
	}

	public int viewHeight() {
		return viewHeight;
	}

	public double centreX() {
		return centreX;
	}

	public double centreZ() {
		return centreZ;
	}

	public double scale() {
		return scale;
	}

	// ---------------------------------------------------------------- avanti e indietro

	public double toScreenX(double worldX) {
		return viewWidth / 2.0 + (worldX - centreX) / scale;
	}

	public double toScreenY(double worldZ) {
		return viewHeight / 2.0 + (worldZ - centreZ) / scale;
	}

	public double toWorldX(double screenX) {
		return centreX + (screenX - viewWidth / 2.0) * scale;
	}

	public double toWorldZ(double screenY) {
		return centreZ + (screenY - viewHeight / 2.0) * scale;
	}

	/** Vero se il punto di schermo cade dentro la finestra, con un margine in pixel. */
	public boolean onScreen(double screenX, double screenY, int margin) {
		return screenX >= margin && screenX <= viewWidth - margin
				&& screenY >= margin && screenY <= viewHeight - margin;
	}

	// ---------------------------------------------------------------- muoversi

	/** Trascina la mappa di tanti pixel: il mondo si sposta sotto il dito. */
	public void pan(double dxPixels, double dyPixels) {
		centreX -= dxPixels * scale;
		centreZ -= dyPixels * scale;
	}

	public void centreOn(double worldX, double worldZ) {
		this.centreX = worldX;
		this.centreZ = worldZ;
	}

	/**
	 * Cambia scala tenendo fermo il punto del mondo che sta sotto il cursore.
	 *
	 * <p>È la differenza fra una mappa che si lascia usare e una che scappa: con lo zoom attorno
	 * al centro, per avvicinarsi a un varco ai bordi bisogna prima trascinarlo in mezzo.
	 *
	 * @param factor maggiore di uno allontana, minore di uno avvicina
	 */
	public void zoomAt(double screenX, double screenY, double factor) {
		double anchorX = toWorldX(screenX);
		double anchorZ = toWorldZ(screenY);

		scale = clampScale(scale * factor);

		// Rimette l'ancora dov'era: il centro si sposta di quanto serve.
		centreX = anchorX - (screenX - viewWidth / 2.0) * scale;
		centreZ = anchorZ - (screenY - viewHeight / 2.0) * scale;
	}

	/**
	 * Inquadra un rettangolo del mondo per intero, con un margine in pixel tutt'attorno.
	 *
	 * <p>Un rettangolo degenere — un punto solo — non inquadra niente: si centra lì e si resta
	 * alla scala più vicina, che è l'unica scelta sensata.
	 */
	public void fit(double minX, double minZ, double maxX, double maxZ, int marginPixels) {
		double spanX = Math.max(0.0, maxX - minX);
		double spanZ = Math.max(0.0, maxZ - minZ);
		double usableW = Math.max(1, viewWidth - 2 * marginPixels);
		double usableH = Math.max(1, viewHeight - 2 * marginPixels);

		double needed = Math.max(spanX / usableW, spanZ / usableH);
		scale = clampScale(needed <= 0.0 ? MIN_SCALE : needed);
		centreX = (minX + maxX) / 2.0;
		centreZ = (minZ + maxZ) / 2.0;
	}

	// ---------------------------------------------------------------- disegnare

	/** Il passo del reticolo a questa scala: il più fitto che resti leggibile. */
	public int gridStep() {
		for (int step : GRID_STEPS) {
			if (step / scale >= MIN_GRID_PIXELS) {
				return step;
			}
		}

		return GRID_STEPS[GRID_STEPS.length - 1];
	}

	/**
	 * Dove disegnare un segno che sta fuori dalla finestra: sul bordo, lungo la retta che lo
	 * unisce al centro, rientrato di un margine.
	 *
	 * <p>È il modo in cui una città a duecentomila blocchi resta sulla mappa anche quando si
	 * guarda il prato attorno a sé: una freccia sul bordo che dice "di là". Se il punto è già
	 * dentro, torna com'è.
	 *
	 * @return {@code [x, y]} in pixel
	 */
	public double[] clampToEdge(double screenX, double screenY, int margin) {
		if (onScreen(screenX, screenY, margin)) {
			return new double[] {screenX, screenY};
		}

		double cx = viewWidth / 2.0;
		double cy = viewHeight / 2.0;
		double dx = screenX - cx;
		double dy = screenY - cy;

		double halfW = cx - margin;
		double halfH = cy - margin;

		// Quanto si puo' scorrere lungo (dx, dy) prima di toccare un bordo: il minore dei due.
		double tx = dx == 0.0 ? Double.POSITIVE_INFINITY : halfW / Math.abs(dx);
		double ty = dy == 0.0 ? Double.POSITIVE_INFINITY : halfH / Math.abs(dy);
		double t = Math.min(tx, ty);

		return new double[] {cx + dx * t, cy + dy * t};
	}

	/** Distanza in blocchi, sul piano, fra due punti del mondo. */
	public static double distance(double x0, double z0, double x1, double z1) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double clampScale(double wanted) {
		return Math.clamp(wanted, MIN_SCALE, MAX_SCALE);
	}
}
