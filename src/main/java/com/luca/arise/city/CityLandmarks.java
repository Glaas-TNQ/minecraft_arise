package com.luca.arise.city;

import java.util.List;

import com.luca.arise.city.CityPlan.Fill;

import net.minecraft.world.level.block.state.BlockState;

/**
 * I cinque monumenti, uno per città, costruiti a mano.
 *
 * <p>Tutto il resto della città è una regola applicata duecento volte: cambia la tavolozza, cambia
 * la maglia, ma resta una regola. Questi cinque pezzi no — sono scritti uno per uno, e sono la
 * ragione per cui una città si riconosce da lontano e si ricorda dopo. Il costo è che sono
 * duecento righe di coordinate; il ricavo è che il giocatore, arrivando, dice "quello è il
 * Colosseo" invece di "un altro isolato".
 *
 * <p>Ognuno riceve il centro della sua area riservata: dentro quel quadrato non passa nessuna
 * strada e non si costruisce nient'altro, ci pensa {@link CityPlan}.
 */
public final class CityLandmarks {

	private CityLandmarks() {
	}

	/** Costruisce il monumento di questa città attorno al punto dato. */
	public static void build(List<Fill> fills, City city, int centreX, int baseY, int centreZ) {
		CityPalette palette = city.palette();

		switch (city.landmark()) {
			case EMPIRE_STATE -> empireState(fills, palette, centreX, baseY, centreZ);
			case TOKYO_TOWER -> tokyoTower(fills, palette, centreX, baseY, centreZ);
			case COLOSSEUM -> colosseum(fills, palette, centreX, baseY, centreZ);
			case ALCALA -> alcala(fills, palette, centreX, baseY, centreZ);
			case BRANDENBURG -> brandenburg(fills, palette, centreX, baseY, centreZ);
		}
	}

	// ---------------------------------------------------------------- New York

	/**
	 * La torre a scalini del '31: basamento a tutto lotto, due rientri, la corona e la guglia.
	 *
	 * <p>Le lesene verticali contano quanto i rientri. Sono loro a dare alla facciata quel disegno
	 * a righe che si legge anche da mille blocchi, quando le finestre sono ormai un pixel.
	 */
	private static void empireState(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		int[] halves = {13, 9, 6};
		int[] heights = {24, 36, 30};
		int y = baseY;

		for (int stage = 0; stage < halves.length; stage++) {
			Rect rect = Rect.around(cx, cz, halves[stage]);
			int top = y + heights[stage];

			Shapes.walls(fills, rect, y, top, palette.wallAlt());

			for (int band = y + 2; band < top - 2; band += 4) {
				Shapes.walls(fills, rect, band, band + 1, palette.glass());
			}

			pilasters(fills, rect, y, top, palette.accent());
			Shapes.walls(fills, rect.grow(1), top + 1, top + 1, palette.accent());
			Shapes.slab(fills, rect, top + 1, palette.roof());

			y = top + 2;
		}

		// La corona: tre anelli che rientrano, poi l'antenna con la luce in cima.
		for (int step = 0; step < 3; step++) {
			Rect crown = Rect.around(cx, cz, 5 - step);
			Shapes.walls(fills, crown, y + step * 3, y + step * 3 + 2, palette.wallAlt());
		}

		Shapes.box(fills, cx - 1, y + 9, cz - 1, cx + 1, y + 22, cz + 1, palette.accent());
		Shapes.column(fills, cx, cz, y + 23, y + 30, palette.trim());
		Shapes.column(fills, cx, cz, y + 31, y + 31, palette.lamp());

		// L'atrio: cinque blocchi di ingresso, e sopra la fascia illuminata.
		Shapes.box(fills, cx - 2, baseY, cz + 13, cx + 2, baseY + 4, cz + 13, CityPalette.air());
		Shapes.box(fills, cx - 3, baseY + 5, cz + 13, cx + 3, baseY + 5, cz + 13, palette.lamp());
	}

	/** Colonnine verticali a passo fisso sulle quattro facce. */
	private static void pilasters(List<Fill> fills, Rect rect, int minY, int maxY, BlockState state) {
		for (int x = rect.minX() + 2; x <= rect.maxX() - 2; x += 4) {
			Shapes.column(fills, x, rect.minZ(), minY, maxY, state);
			Shapes.column(fills, x, rect.maxZ(), minY, maxY, state);
		}

		for (int z = rect.minZ() + 2; z <= rect.maxZ() - 2; z += 4) {
			Shapes.column(fills, rect.minX(), z, minY, maxY, state);
			Shapes.column(fills, rect.maxX(), z, minY, maxY, state);
		}
	}

	// ---------------------------------------------------------------- Tokyo

	/**
	 * Il traliccio: quattro gambe che si stringono salendo, due terrazze e l'antenna.
	 *
	 * <p>Le gambe sono costruite un livello per volta con il rientro calcolato sulla quota: è
	 * l'unico modo di ottenere una diagonale con delle scatole, e da terra la scala sfugge.
	 */
	private static void tokyoTower(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		int legHeight = 44;
		int baseHalf = 12;
		int shaftHalf = 3;

		for (int h = 0; h <= legHeight; h++) {
			int half = baseHalf - (baseHalf - shaftHalf) * h / legHeight;
			int y = baseY + h;
			BlockState state = (h / 6) % 2 == 0 ? palette.accent() : palette.wallAlt();

			leg(fills, cx - half, y, cz - half, state);
			leg(fills, cx + half - 1, y, cz - half, state);
			leg(fills, cx - half, y, cz + half - 1, state);
			leg(fills, cx + half - 1, y, cz + half - 1, state);

			// Le controventature: una cornice ogni otto blocchi tiene insieme le quattro gambe.
			if (h % 8 == 0 && h > 0) {
				Shapes.walls(fills, Rect.around(cx, cz, half), y, y, palette.accent());
			}
		}

		int deck = baseY + legHeight;
		Rect platform = Rect.around(cx, cz, 10);

		Shapes.slab(fills, platform, deck, palette.wallAlt());
		Shapes.walls(fills, platform, deck + 1, deck + 3, palette.glass());
		Shapes.walls(fills, platform, deck + 4, deck + 4, palette.accent());
		Shapes.slab(fills, platform, deck + 4, palette.accent());

		int shaftTop = baseY + 78;
		Rect shaft = Rect.around(cx, cz, shaftHalf);

		for (int y = deck + 5; y <= shaftTop; y++) {
			Shapes.walls(fills, shaft, y, y,
					(y / 6) % 2 == 0 ? palette.accent() : palette.wallAlt());
		}

		Rect upper = Rect.around(cx, cz, 5);
		Shapes.slab(fills, upper, baseY + 66, palette.wallAlt());
		Shapes.walls(fills, upper, baseY + 67, baseY + 69, palette.glass());
		Shapes.slab(fills, upper, baseY + 70, palette.accent());

		Shapes.box(fills, cx - 1, shaftTop, cz - 1, cx + 1, baseY + 92, cz + 1, palette.accent());
		Shapes.column(fills, cx, cz, baseY + 93, baseY + 96, palette.trim());
		Shapes.column(fills, cx, cz, baseY + 97, baseY + 97, palette.lamp());
	}

	/** Una gamba del traliccio: due blocchi per due, a una quota. */
	private static void leg(List<Fill> fills, int x, int y, int z, BlockState state) {
		Shapes.box(fills, x, y, z, x + 1, y, z + 1, state);
	}

	// ---------------------------------------------------------------- Roma

	/**
	 * L'anfiteatro: tre ordini di arcate, le gradinate che digradano verso l'arena, e un pezzo
	 * dell'anello superiore mancante.
	 *
	 * <p>La rovina non è un dettaglio estetico: un anfiteatro intero sembra costruito ieri, e la
	 * città attorno è di travertino nuovo. Il pezzo caduto è quello che dice che è antico.
	 */
	private static void colosseum(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		int radiusX = 34;
		int radiusZ = 28;
		int thickness = 4;

		// L'arena e le gradinate, prima: l'anello esterno le chiuderà dentro.
		Shapes.disc(fills, cx, cz, radiusX - 20, radiusZ - 16, baseY - 1, baseY - 1, palette.trim());

		for (int step = 0; step < 5; step++) {
			int rx = radiusX - 6 - step * 3;
			int rz = radiusZ - 6 - step * 3;

			if (rx < 10 || rz < 8) {
				break;
			}

			Shapes.ring(fills, cx, cz, rx, rz, 3, baseY - 1, baseY + 14 - step * 3, palette.wallAlt());
		}

		// I tre ordini. Il terzo è tagliato a metà: è l'anello caduto.
		Shapes.ring(fills, cx, cz, radiusX, radiusZ, thickness, baseY, baseY + 11, palette.wall());
		Shapes.ring(fills, cx, cz, radiusX + 1, radiusZ + 1, thickness + 1, baseY + 12, baseY + 12,
				palette.accent());
		Shapes.ring(fills, cx, cz, radiusX, radiusZ, thickness, baseY + 13, baseY + 23, palette.wall());
		Shapes.ring(fills, cx, cz, radiusX + 1, radiusZ + 1, thickness + 1, baseY + 24, baseY + 24,
				palette.accent());
		Shapes.ring(fills, cx, cz, radiusX, radiusZ, thickness, baseY + 25, baseY + 33, palette.wall(),
				cx - radiusX, cx + 8);

		arches(fills, cx, cz, radiusX, radiusZ, baseY + 1, baseY + 8);
		arches(fills, cx, cz, radiusX, radiusZ, baseY + 14, baseY + 20);
		arches(fills, cx, cz, radiusX, radiusZ, baseY + 26, baseY + 30);

		// I due ingressi sull'asse maggiore, quelli da cui si entra davvero.
		Shapes.box(fills, cx - radiusX - 1, baseY, cz - 2, cx - radiusX + thickness, baseY + 5, cz + 2,
				CityPalette.air());
		Shapes.box(fills, cx + radiusX - thickness, baseY, cz - 2, cx + radiusX + 1, baseY + 5, cz + 2,
				CityPalette.air());

		for (int torch = -1; torch <= 1; torch += 2) {
			Shapes.column(fills, cx + torch * (radiusX + 2), cz, baseY, baseY + 3, palette.accent());
			Shapes.column(fills, cx + torch * (radiusX + 2), cz, baseY + 4, baseY + 4, palette.lamp());
		}
	}

	/** Le arcate di un ordine: ventiquattro vuoti scavati tutt'attorno all'anello. */
	private static void arches(List<Fill> fills, int cx, int cz, int radiusX, int radiusZ,
			int minY, int maxY) {
		int count = 24;

		for (int k = 0; k < count; k++) {
			double angle = Math.PI * 2 * k / count;
			int x = cx + (int) Math.round(Math.cos(angle) * (radiusX - 2));
			int z = cz + (int) Math.round(Math.sin(angle) * (radiusZ - 2));

			Shapes.box(fills, x - 1, minY, z - 1, x + 1, maxY - 1, z + 1, CityPalette.air());
			Shapes.box(fills, x, minY, z, x, maxY, z, CityPalette.air());
		}
	}

	// ---------------------------------------------------------------- Madrid

	/**
	 * La porta neoclassica: cinque fornici, tre ad arco e due rettangolari, e la fontana davanti.
	 *
	 * <p>Una porta in mezzo a una piazza non chiude niente, ed è giusto così: serve a dare alla
	 * piazza un centro e un davanti, che è quello che le piazze generate di solito non hanno.
	 */
	private static void alcala(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		int gz = cz + 6;
		Rect body = new Rect(cx - 13, gz - 3, cx + 13, gz + 3);

		Shapes.box(fills, body, baseY, baseY + 15, palette.wallAlt());

		// Il fornice centrale, alto e a tutta altezza; i due laterali più bassi.
		arch(fills, cx, baseY, gz, 3, 10, palette);
		arch(fills, cx - 8, baseY, gz, 2, 7, palette);
		arch(fills, cx + 8, baseY, gz, 2, 7, palette);

		// I due passaggi rettangolari agli estremi.
		for (int side = -1; side <= 1; side += 2) {
			Shapes.box(fills, cx + side * 12 - 1, baseY, gz - 3, cx + side * 12 + 1, baseY + 5, gz + 3,
					CityPalette.air());
		}

		// Lesene fra un fornice e l'altro, poi cornicione e attico.
		for (int x = -11; x <= 11; x += 4) {
			Shapes.column(fills, cx + x, gz - 3, baseY, baseY + 14, palette.trim());
			Shapes.column(fills, cx + x, gz + 3, baseY, baseY + 14, palette.trim());
		}

		Shapes.box(fills, cx - 14, baseY + 16, gz - 4, cx + 14, baseY + 17, gz + 4, palette.trim());
		Shapes.box(fills, cx - 7, baseY + 18, gz - 2, cx + 7, baseY + 21, gz + 2, palette.wallAlt());
		Shapes.box(fills, cx - 5, baseY + 22, gz - 1, cx + 5, baseY + 22, gz + 1, palette.trim());

		for (int statue = -1; statue <= 1; statue++) {
			Shapes.column(fills, cx + statue * 4, gz, baseY + 23, baseY + 25, palette.accent());
		}

		// La fontana davanti, sull'asse: la porta da sola resterebbe un muro con dei buchi.
		CityBuildings.basin(fills, palette, cx, baseY, cz - 12, 6);
		Shapes.column(fills, cx, cz - 12, baseY + 3, baseY + 6, palette.wallAlt());
		Shapes.column(fills, cx, cz - 12, baseY + 7, baseY + 7, palette.accent());

		for (int corner = -1; corner <= 1; corner += 2) {
			for (int side = -1; side <= 1; side += 2) {
				Shapes.column(fills, cx + corner * 18, cz + side * 18, baseY, baseY + 4,
						palette.accent());
				Shapes.column(fills, cx + corner * 18, cz + side * 18, baseY + 5, baseY + 5,
						palette.lamp());
			}
		}
	}

	/** Un fornice ad arco scavato in un muro pieno: i lati dritti e la ghiera che si stringe. */
	private static void arch(List<Fill> fills, int cx, int baseY, int cz, int half, int height,
			CityPalette palette) {
		Shapes.box(fills, cx - half, baseY, cz - 4, cx + half, baseY + height - half, cz + 4,
				CityPalette.air());

		for (int step = 1; step <= half; step++) {
			Shapes.box(fills, cx - half + step, baseY + height - half + step, cz - 4,
					cx + half - step, baseY + height - half + step, cz + 4, CityPalette.air());
		}

		Shapes.box(fills, cx - half - 1, baseY + height + 1, cz - 4, cx + half + 1,
				baseY + height + 1, cz + 4, palette.trim());
	}

	// ---------------------------------------------------------------- Berlino

	/**
	 * La porta con la quadriga, e dietro la torre della televisione con la sfera.
	 *
	 * <p>Due monumenti in un'area sola perché Berlino è due città: quella neoclassica del viale e
	 * quella che le è cresciuta accanto. La sfera in cima all'asta è anche il pezzo che dimostra
	 * che con delle scatole si fa una palla — basta affettarla.
	 */
	private static void brandenburg(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		int gz = cz + 20;

		// Lo stilobate, e le dodici colonne su due file.
		Shapes.box(fills, cx - 14, baseY - 1, gz - 6, cx + 14, baseY, gz + 6, palette.wallAlt());

		for (int x = -10; x <= 10; x += 4) {
			for (int row = -1; row <= 1; row += 2) {
				Shapes.box(fills, cx + x - 1, baseY + 1, gz + row * 3 - 1, cx + x, baseY + 11,
						gz + row * 3, palette.wallAlt());
			}
		}

		Shapes.box(fills, cx - 13, baseY + 12, gz - 4, cx + 13, baseY + 14, gz + 4, palette.trim());
		Shapes.box(fills, cx - 8, baseY + 15, gz - 3, cx + 8, baseY + 17, gz + 3, palette.wallAlt());

		// La quadriga: il carro e i quattro cavalli, accennati.
		Shapes.box(fills, cx - 1, baseY + 18, gz - 1, cx + 1, baseY + 20, gz + 1, palette.accent());

		for (int horse = -4; horse <= 4; horse += 2) {
			Shapes.box(fills, cx + horse, baseY + 18, gz - 3, cx + horse, baseY + 19, gz - 2,
					palette.accent());
		}

		fernsehturm(fills, palette, cx, baseY, cz - 16);
	}

	/** L'asta con la sfera: basamento, fusto che si stringe, sfera vetrata, antenna. */
	private static void fernsehturm(List<Fill> fills, CityPalette palette, int cx, int baseY, int cz) {
		Shapes.disc(fills, cx, cz, 8, 8, baseY - 1, baseY + 1, palette.wallAlt());
		Shapes.ring(fills, cx, cz, 8, 8, 1, baseY + 2, baseY + 4, palette.trim());
		Shapes.disc(fills, cx, cz, 4, 4, baseY, baseY + 16, palette.wallAlt());
		Shapes.disc(fills, cx, cz, 3, 3, baseY + 17, baseY + 90, palette.wallAlt());

		int centreY = baseY + 100;
		Shapes.ball(fills, cx, centreY, cz, 8, palette.wallAlt());

		// La fascia vetrata all'equatore: è il piano panoramico, e da sotto si vede accesa.
		for (int dy = -2; dy <= 2; dy++) {
			int half = (int) Math.round(8 * Math.sqrt(Math.max(0.0, 1.0 - dy * dy / 64.0)));
			Shapes.ring(fills, cx, cz, half, half, 1, centreY + dy, centreY + dy, palette.glass());
		}

		Shapes.disc(fills, cx, cz, 2, 2, centreY + 9, baseY + 132, palette.trim());
		Shapes.column(fills, cx, cz, baseY + 133, baseY + 140, palette.accent());
		Shapes.column(fills, cx, cz, baseY + 141, baseY + 141, palette.lamp());
	}
}
