package com.luca.arise.city;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.city.CityPlan.Fill;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Che cosa c'è dentro un isolato, a seconda della città.
 *
 * <p>Cinque forme, non cinque colori. Una torre che rientra a scalini, un grappolo di palazzine
 * addossate con i vicoli in mezzo, un'insula bassa con il cortile, una manzana chiusa con il patio,
 * un caseggiato con la mansarda: sono cinque modi diversi di stare in un rettangolo, ed è la
 * ragione per cui camminare a Tokyo non è come camminare a Berlino.
 *
 * <p>Il piano di calpestio della strada è {@code baseY - 1}, posato dalla spianata: gli edifici non
 * hanno bisogno di un pavimento al piano terra, ce l'hanno già. I muri partono da {@code baseY}.
 */
public final class CityBuildings {

	/** Sotto questo lato non ci sta niente che valga la pena di guardare. */
	public static final int MIN_LOT = 7;

	/** Passo delle finestre punzonate, quelle non a fascia continua. */
	private static final int WINDOW_STEP = 3;

	private CityBuildings() {
	}

	/** Costruisce quello che questa città mette in un lotto. */
	public static void place(List<Fill> fills, City city, Rect lot, int baseY, int floors,
			RandomSource random) {
		if (lot.shortSide() < MIN_LOT) {
			return;
		}

		switch (city.style()) {
			case GRIDIRON -> tower(fills, city.palette(), lot, baseY, floors);
			case SHITAMACHI -> cluster(fills, city.palette(), lot, baseY, floors, random);
			case ANTICA -> perimeter(fills, city.palette(), lot, baseY, floors, 5, false, true);
			case MANZANA -> perimeter(fills, city.palette(), lot, baseY, floors, 6, false, false);
			case BOULEVARD -> perimeter(fills, city.palette(), lot, baseY, floors, 7, true, false);
		}
	}

	// ---------------------------------------------------------------- le cinque forme

	/**
	 * La torre a scalini: tre corpi sovrapposti che rientrano di due blocchi per volta.
	 *
	 * <p>È la sola ragione per cui uno skyline si legge. Un grattacielo dritto è un parallelepipedo
	 * e da lontano si conta male; tre corpi che rientrano danno un profilo, e cinquanta profili
	 * diversi danno una città.
	 */
	private static void tower(List<Fill> fills, CityPalette palette, Rect lot, int baseY, int floors) {
		int height = floors * City.FLOOR_HEIGHT;

		if (height <= 28 || lot.shortSide() < 11) {
			body(fills, palette, lot, baseY, floors, true);
			parapet(fills, palette, lot, baseY + height);
			door(fills, palette, lot, baseY);
			return;
		}

		int[] share = {45, 33, 22};
		Rect rect = lot;
		int y = baseY;

		for (int stage = 0; stage < share.length; stage++) {
			int part = stage == share.length - 1
					? baseY + height - y
					: Math.max(City.FLOOR_HEIGHT, height * share[stage] / 100);
			int stageFloors = Math.max(1, part / City.FLOOR_HEIGHT);

			body(fills, palette, rect, y, stageFloors, true);

			int top = y + stageFloors * City.FLOOR_HEIGHT;
			Shapes.slab(fills, rect, top, palette.roof());
			Shapes.walls(fills, rect.grow(1), top + 1, top + 1, palette.accent());

			y = top;
			rect = rect.shrink(2);

			if (rect.shortSide() < 5) {
				break;
			}
		}

		// La guglia: un'antenna e una luce in punta, come si usava nel '31.
		int spireX = rect.centreX();
		int spireZ = rect.centreZ();
		Shapes.box(fills, spireX - 1, y, spireZ - 1, spireX + 1, y + 5, spireZ + 1, palette.wallAlt());
		Shapes.column(fills, spireX, spireZ, y + 6, y + 13, palette.accent());
		Shapes.column(fills, spireX, spireZ, y + 14, y + 14, palette.lamp());

		door(fills, palette, lot, baseY);
	}

	/**
	 * Il grappolo: due o quattro palazzine dentro lo stesso lotto, separate da un vicolo di un
	 * blocco e alte in modo diverso.
	 *
	 * <p>Un solo edificio per isolato dà una città ordinata. Tokyo non è ordinata: è la somma di
	 * cose costruite in momenti diversi, e il vicolo che resta in mezzo è metà del carattere.
	 */
	private static void cluster(List<Fill> fills, CityPalette palette, Rect lot, int baseY,
			int floors, RandomSource random) {
		for (Rect part : split(lot, random)) {
			if (part.shortSide() < 5) {
				continue;
			}

			int own = Math.max(2, floors + random.nextInt(5) - 2);
			int top = baseY + own * City.FLOOR_HEIGHT;

			body(fills, palette, part, baseY, own, true);
			Shapes.slab(fills, part, top, palette.roof());

			// Il vano scala sul tetto, e l'insegna accesa sull'angolo verso strada.
			Shapes.box(fills, part.centreX() - 1, top + 1, part.centreZ() - 1,
					part.centreX() + 1, top + 2, part.centreZ() + 1, palette.trim());
			Shapes.column(fills, part.maxX(), part.maxZ(), baseY + 2, top - 2, palette.accent());
			Shapes.column(fills, part.maxX(), part.maxZ(), baseY + 1, baseY + 1, palette.lamp());

			door(fills, palette, part, baseY);
		}
	}

	/** Taglia il lotto in due o quattro, lasciando un vicolo di un blocco fra i pezzi. */
	private static List<Rect> split(Rect lot, RandomSource random) {
		List<Rect> parts = new ArrayList<>();
		boolean cutX = lot.width() >= 13 && random.nextInt(4) > 0;
		boolean cutZ = lot.depth() >= 13 && random.nextInt(4) > 0;

		int midX = cutX ? lot.minX() + lot.width() / 2 + random.nextInt(3) - 1 : lot.maxX();
		int midZ = cutZ ? lot.minZ() + lot.depth() / 2 + random.nextInt(3) - 1 : lot.maxZ();

		parts.add(new Rect(lot.minX(), lot.minZ(), midX, midZ));

		if (cutX) {
			parts.add(new Rect(midX + 2, lot.minZ(), lot.maxX(), midZ));
		}
		if (cutZ) {
			parts.add(new Rect(lot.minX(), midZ + 2, midX, lot.maxZ()));
		}
		if (cutX && cutZ) {
			parts.add(new Rect(midX + 2, midZ + 2, lot.maxX(), lot.maxZ()));
		}

		return parts;
	}

	/**
	 * L'isolato costruito sul perimetro, con il cortile nel mezzo.
	 *
	 * <p>Una forma sola per tre città, perché la differenza sta nei numeri: Roma la vuole profonda
	 * cinque e con le arcate al piano terra, Madrid sei e chiusa, Berlino sette con la mansarda. È
	 * anche la forma che regge meglio l'ingrandimento: più il lotto è grande, più il cortile è
	 * bello, mentre un parallelepipedo grande resta solo un parallelepipedo grande.
	 */
	private static void perimeter(List<Fill> fills, CityPalette palette, Rect lot, int baseY,
			int floors, int depth, boolean mansard, boolean arcade) {
		int top = baseY + floors * City.FLOOR_HEIGHT;
		boolean courtyard = lot.shortSide() - depth * 2 >= 5;

		body(fills, palette, lot, baseY, floors, false);

		if (courtyard) {
			Rect inner = lot.shrink(depth);

			Shapes.walls(fills, inner, baseY, top - 1, palette.wall());
			Shapes.slab(fills, inner.shrink(1), baseY - 1, CityPalette.grass());

			// Il passaggio che porta dentro: senza, il cortile è un vano tecnico invisibile.
			Shapes.box(fills, lot.centreX() - 1, baseY, lot.minZ(), lot.centreX() + 1, baseY + 3,
					lot.minZ() + depth, CityPalette.air());
			tree(fills, palette, inner.centreX(), baseY, inner.centreZ(), 5);
		}

		// Il tetto, e subito dopo il buco sopra il cortile: un tetto pieno lo cancellerebbe.
		Shapes.slab(fills, lot, top, palette.roof());

		if (mansard) {
			Shapes.slab(fills, lot.shrink(1), top + 1, palette.roof());
			Shapes.walls(fills, lot.shrink(2), top + 2, top + 2, palette.trim());
		}

		if (courtyard) {
			Shapes.box(fills, lot.shrink(depth + 1), top, top + 2, CityPalette.air());
		}

		if (arcade) {
			for (int x = lot.minX() + 2; x <= lot.maxX() - 2; x += 4) {
				Shapes.column(fills, x, lot.maxZ(), baseY, baseY + 2, CityPalette.air());
				Shapes.column(fills, x, lot.minZ(), baseY, baseY + 2, CityPalette.air());
			}
		}

		door(fills, palette, lot, baseY);
	}

	// ---------------------------------------------------------------- pezzi comuni

	/** Muri, fasce marcapiano e finestre: il guscio, senza tetto. */
	private static void body(List<Fill> fills, CityPalette palette, Rect rect, int baseY, int floors,
			boolean curtain) {
		int top = baseY + floors * City.FLOOR_HEIGHT;

		Shapes.walls(fills, rect, baseY, top - 1, palette.wall());
		Shapes.corners(fills, rect, baseY, top - 1, palette.trim());

		for (int floor = 0; floor < floors; floor++) {
			int y = baseY + floor * City.FLOOR_HEIGHT + 1;

			if (curtain) {
				// Fascia continua di vetro: è la facciata a nastro dei palazzi moderni.
				Shapes.walls(fills, rect, y, y + 1, palette.glass());
				Shapes.corners(fills, rect, y, y + 1, palette.wall());
			} else {
				windows(fills, palette, rect, y);
				Shapes.walls(fills, rect, y + 2, y + 2, palette.trim());
			}
		}
	}

	/** Finestre punzonate: buchi di vetro a passo fisso, non una vetrata continua. */
	private static void windows(List<Fill> fills, CityPalette palette, Rect rect, int y) {
		for (int x = rect.minX() + 2; x <= rect.maxX() - 2; x += WINDOW_STEP) {
			Shapes.box(fills, x, y, rect.minZ(), x, y + 1, rect.minZ(), palette.glass());
			Shapes.box(fills, x, y, rect.maxZ(), x, y + 1, rect.maxZ(), palette.glass());
		}

		for (int z = rect.minZ() + 2; z <= rect.maxZ() - 2; z += WINDOW_STEP) {
			Shapes.box(fills, rect.minX(), y, z, rect.minX(), y + 1, z, palette.glass());
			Shapes.box(fills, rect.maxX(), y, z, rect.maxX(), y + 1, z, palette.glass());
		}
	}

	/** Il parapetto del tetto piano, con dentro l'aria: da sotto si vede il bordo. */
	private static void parapet(List<Fill> fills, CityPalette palette, Rect rect, int top) {
		Shapes.slab(fills, rect, top, palette.roof());
		Shapes.walls(fills, rect, top + 1, top + 1, palette.accent());
	}

	/** La porta verso la strada a sud, con l'architrave e la luce sopra. */
	private static void door(List<Fill> fills, CityPalette palette, Rect rect, int baseY) {
		int x = rect.centreX();

		Shapes.box(fills, x - 1, baseY, rect.maxZ(), x + 1, baseY + 2, rect.maxZ(), CityPalette.air());
		Shapes.box(fills, x - 2, baseY + 3, rect.maxZ(), x + 2, baseY + 3, rect.maxZ(), palette.trim());
		Shapes.column(fills, x, rect.maxZ() + 1, baseY + 3, baseY + 3, palette.lamp());
	}

	// ---------------------------------------------------------------- verde

	/**
	 * Un giardino invece di un edificio.
	 *
	 * <p>Un isolato su dieci vuoto cambia la città più di quanto sembri: dà respiro, rompe la
	 * griglia, e soprattutto dà un posto da cui guardare in alto i palazzi attorno.
	 */
	public static void park(List<Fill> fills, City city, Rect lot, int baseY, RandomSource random) {
		CityPalette palette = city.palette();

		Shapes.slab(fills, lot, baseY - 1, CityPalette.grass());

		// Due viali in croce, e la fontana all'incrocio.
		Shapes.box(fills, lot.centreX() - 1, baseY - 1, lot.minZ(), lot.centreX() + 1, baseY - 1,
				lot.maxZ(), palette.sidewalk());
		Shapes.box(fills, lot.minX(), baseY - 1, lot.centreZ() - 1, lot.maxX(), baseY - 1,
				lot.centreZ() + 1, palette.sidewalk());

		if (lot.shortSide() >= 16) {
			basin(fills, palette, lot.centreX(), baseY, lot.centreZ(), 3);
		}

		for (int corner = 0; corner < 4; corner++) {
			int x = corner % 2 == 0 ? lot.minX() + 4 : lot.maxX() - 4;
			int z = corner < 2 ? lot.minZ() + 4 : lot.maxZ() - 4;

			tree(fills, palette, x, baseY, z, 4 + random.nextInt(3));
		}

		Shapes.column(fills, lot.minX() + 1, lot.minZ() + 1, baseY, baseY + 3, palette.accent());
		Shapes.column(fills, lot.minX() + 1, lot.minZ() + 1, baseY + 4, baseY + 4, palette.lamp());
	}

	/** Un albero: tronco e chioma della specie che la città si porta dietro. */
	public static void tree(List<Fill> fills, CityPalette palette, int x, int baseY, int z, int height) {
		Shapes.column(fills, x, z, baseY, baseY + height, palette.wood());
		Shapes.box(fills, x - 2, baseY + height - 1, z - 2, x + 2, baseY + height, z + 2, palette.foliage());
		Shapes.box(fills, x - 1, baseY + height + 1, z - 1, x + 1, baseY + height + 1, z + 1,
				palette.foliage());
		Shapes.column(fills, x, z, baseY + height - 1, baseY + height, palette.wood());
	}

	/** Una vasca d'acqua con il bordo rialzato e lo zampillo in mezzo. */
	public static void basin(List<Fill> fills, CityPalette palette, int centreX, int baseY,
			int centreZ, int radius) {
		BlockState rim = palette.wallAlt();

		Shapes.disc(fills, centreX, centreZ, radius + 1, radius + 1, baseY - 1, baseY, rim);
		Shapes.disc(fills, centreX, centreZ, radius, radius, baseY, baseY, CityPalette.water());
		Shapes.column(fills, centreX, centreZ, baseY, baseY + 2, rim);
		Shapes.column(fills, centreX, centreZ, baseY + 3, baseY + 3, palette.lamp());
	}
}
