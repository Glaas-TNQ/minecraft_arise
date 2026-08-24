package com.luca.arise.client.map;

import com.luca.arise.AriseMod;
import com.luca.arise.map.MapProjection;
import com.luca.arise.map.MapTiles;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * La tela su cui il terreno diventa pixel.
 *
 * <h2>Perche' una texture e non dei rettangoli</h2>
 *
 * <p>Una schermata di mappa e' centomila pixel, ognuno con il suo colore. Disegnarli con
 * {@code fill} vorrebbe dire centomila rettangoli per fotogramma, sessanta volte al secondo: sei
 * milioni di rettangoli al secondo per disegnare un prato. Non e' lento, e' fermo.
 *
 * <p>Qui i pixel si scrivono <strong>una volta sola</strong>, dentro un'immagine, e l'immagine
 * diventa una texture che la scheda video disegna in un colpo. Si riscrive solo quando cambia
 * qualcosa: quando la mappa si sposta, quando cambia scala, o quando arriva un riquadro nuovo. Se
 * si sta fermi a guardare, non si ridisegna niente.
 *
 * <h2>Perche' si campiona per pixel e non per riquadro</h2>
 *
 * <p>La strada ovvia sarebbe disegnare ogni riquadro al suo posto, scalato. Ma i riquadri finiscono
 * a cavallo del bordo della finestra e i loro campioni non cadono mai esattamente su un pixel: ne
 * verrebbe una matematica piena di casi al contorno, e ogni caso al contorno di questo genere si
 * manifesta come una riga di pixel sbagliata che compare e sparisce mentre si trascina.
 *
 * <p>Il verso opposto non ha casi al contorno: per ogni pixel si chiede a che punto del mondo
 * corrisponde, e si prende il campione che ci sta sopra. Un riquadro che manca e' semplicemente un
 * pixel di fondo, senza bisogno di saperlo in anticipo.
 */
public final class MapCanvas implements AutoCloseable {

	private static final Identifier ID = AriseMod.id("map_canvas");

	/** Il colore dei pixel per cui il riquadro non e' ancora arrivato. */
	private final int background;

	private DynamicTexture texture;
	private NativeImage image;

	private int width;
	private int height;

	// Cio' che descrive l'ultima immagine costruita. Se non e' cambiato niente, non si ricostruisce.
	private double lastCentreX = Double.NaN;
	private double lastCentreZ = Double.NaN;
	private double lastScale = Double.NaN;
	private int lastVersion = -1;
	private int lastLod = -2;

	public MapCanvas(int background) {
		this.background = background;
	}

	/**
	 * Ridisegna se serve, poi disegna. Da chiamare a ogni fotogramma: decide da sola se c'e' lavoro.
	 *
	 * @param lod il livello di dettaglio, o negativo se a questa scala il terreno non si disegna
	 */
	public void draw(GuiGraphicsExtractor graphics, MapProjection projection, int lod,
			int x, int y, int viewWidth, int viewHeight) {
		if (lod < 0 || viewWidth <= 0 || viewHeight <= 0) {
			return;
		}

		resize(viewWidth, viewHeight);

		if (image == null) {
			return;
		}

		if (stale(projection, lod)) {
			repaint(projection, lod);
		}

		// Angoli, non larghezza e altezza: questa sovrapposizione di `blit` vuole
		// {@code (x0, y0, x1, y1, u0, u1, v0, v1)}. Le altre ne vogliono quattro con la misura, e
		// scambiarle non da' nessun errore — da' una texture disegnata dall'angolo dello schermo
		// fin dove capita. E' l'unica cosa di questo file che si e' dovuta leggere nel bytecode.
		graphics.blit(ID, x, y, x + viewWidth, y + viewHeight, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private boolean stale(MapProjection projection, int lod) {
		return lod != lastLod
				|| projection.centreX() != lastCentreX
				|| projection.centreZ() != lastCentreZ
				|| projection.scale() != lastScale
				|| TerrainTiles.version() != lastVersion;
	}

	/**
	 * Riempie l'immagine, un pixel per volta.
	 *
	 * <p>L'ultimo riquadro letto si tiene da parte, e non e' una micro-ottimizzazione: si scorre
	 * per righe, e una riga di pixel attraversa pochissimi riquadri. Senza questa scorciatoia si
	 * farebbe una ricerca in una mappa per ognuno dei centomila pixel, e ricostruire la tela — che
	 * succede a ogni pixel di trascinamento — costerebbe abbastanza da farlo sentire.
	 */
	private void repaint(MapProjection projection, int lod) {
		int span = MapTiles.span(lod);
		int step = MapTiles.step(lod);

		int heldX = Integer.MIN_VALUE;
		int heldZ = Integer.MIN_VALUE;
		int[] held = null;

		for (int py = 0; py < height; py++) {
			double worldZ = projection.toWorldZ(py + 0.5);
			int tileZ = (int) Math.floor(worldZ / span);
			int inZ = (int) Math.floor((worldZ - (double) tileZ * span) / step);

			for (int px = 0; px < width; px++) {
				double worldX = projection.toWorldX(px + 0.5);
				int tileX = (int) Math.floor(worldX / span);

				if (tileX != heldX || tileZ != heldZ) {
					heldX = tileX;
					heldZ = tileZ;
					held = TerrainTiles.get(lod, tileX, tileZ);
				}

				if (held == null) {
					image.setPixelABGR(px, py, abgr(background));
					continue;
				}

				int inX = (int) Math.floor((worldX - (double) tileX * span) / step);
				int colour = held[Math.clamp(inZ, 0, MapTiles.TILE - 1) * MapTiles.TILE
						+ Math.clamp(inX, 0, MapTiles.TILE - 1)];

				image.setPixelABGR(px, py, abgr(colour));
			}
		}

		texture.upload();

		lastCentreX = projection.centreX();
		lastCentreZ = projection.centreZ();
		lastScale = projection.scale();
		lastVersion = TerrainTiles.version();
		lastLod = lod;
	}

	/** Da ARGB, che e' come ragiona tutto il resto della mod, ad ABGR, che e' come vuole la GPU. */
	private static int abgr(int argb) {
		return (argb & 0xFF00FF00)
				| ((argb & 0x00FF0000) >> 16)
				| ((argb & 0x000000FF) << 16);
	}

	private void resize(int wanted, int wantedHeight) {
		if (image != null && width == wanted && height == wantedHeight) {
			return;
		}

		close();

		width = wanted;
		height = wantedHeight;
		image = new NativeImage(width, height, false);
		texture = new DynamicTexture(() -> "arise map", image);

		Minecraft.getInstance().getTextureManager().register(ID, texture);

		// La misura e' cambiata: quello che era disegnato prima non vale piu' niente.
		lastVersion = -1;
		lastLod = -2;
	}

	/**
	 * Libera la texture. Da chiamare quando la schermata si chiude.
	 *
	 * <p>Senza, ogni apertura della mappa lascerebbe una texture sulla scheda video: pochi megabyte
	 * per volta, che dopo un pomeriggio non sono pochi. E' l'unica risorsa di tutta la mod che si
	 * alloca fuori dalla memoria di Java.
	 *
	 * <p><strong>Solo {@code release}, e nessun {@code close} nostro.</strong> Il gestore delle
	 * texture chiude gia' quella che gli si toglie, e chiuderla una seconda volta libererebbe due
	 * volte lo stesso puntatore nativo: non un'eccezione di Java, un processo che se ne va. La
	 * differenza sta dentro {@code TextureManager.release}, e si vede solo leggendola.
	 */
	@Override
	public void close() {
		if (texture == null) {
			return;
		}

		Minecraft.getInstance().getTextureManager().release(ID);

		texture = null;
		image = null;
	}
}
