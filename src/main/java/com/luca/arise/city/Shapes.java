package com.luca.arise.city;

import java.util.List;

import com.luca.arise.city.CityPlan.Fill;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Le forme che un elenco di parallelepipedi non ha: gusci vuoti, anelli ellittici, sfere.
 *
 * <p>Un {@link Fill} è una scatola, e una città fatta di sole scatole si vede che è fatta di sole
 * scatole. Qui una curva viene <em>affettata</em>: per ogni colonna di X si calcola quanto è larga
 * la figura in Z e si emette una scatola sottile. Cento scatole da un blocco di larghezza
 * disegnano un'ellisse che a occhio è un'ellisse — e restano scatole, quindi il costruttore a
 * battiti non cambia di una riga.
 *
 * <p>L'altra ragione di questo file è il costo. Un edificio costruito come blocco pieno e poi
 * svuotato costa <em>due volte</em> il suo volume in posizionamenti: su una città di trecento
 * blocchi di lato sono milioni di operazioni buttate. {@link #walls} ne piazza quattro pareti
 * sottili: il costo scende dal volume alla superficie.
 */
public final class Shapes {

	private Shapes() {
	}

	// ---------------------------------------------------------------- scatole

	public static void box(List<Fill> fills, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, BlockState state) {
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			return;
		}

		fills.add(new Fill(minX, minY, minZ, maxX, maxY, maxZ, state));
	}

	public static void box(List<Fill> fills, Rect rect, int minY, int maxY, BlockState state) {
		box(fills, rect.minX(), minY, rect.minZ(), rect.maxX(), maxY, rect.maxZ(), state);
	}

	/** Un piano orizzontale spesso un blocco. */
	public static void slab(List<Fill> fills, Rect rect, int y, BlockState state) {
		box(fills, rect, y, y, state);
	}

	/** Le quattro pareti di un guscio, senza toccare quello che c'è dentro. */
	public static void walls(List<Fill> fills, Rect rect, int minY, int maxY, BlockState state) {
		if (rect.shortSide() < 2 || minY > maxY) {
			return;
		}

		box(fills, rect.minX(), minY, rect.minZ(), rect.minX(), maxY, rect.maxZ(), state);
		box(fills, rect.maxX(), minY, rect.minZ(), rect.maxX(), maxY, rect.maxZ(), state);
		box(fills, rect.minX() + 1, minY, rect.minZ(), rect.maxX() - 1, maxY, rect.minZ(), state);
		box(fills, rect.minX() + 1, minY, rect.maxZ(), rect.maxX() - 1, maxY, rect.maxZ(), state);
	}

	/** Le quattro colonne agli angoli di un rettangolo. */
	public static void corners(List<Fill> fills, Rect rect, int minY, int maxY, BlockState state) {
		box(fills, rect.minX(), minY, rect.minZ(), rect.minX(), maxY, rect.minZ(), state);
		box(fills, rect.maxX(), minY, rect.minZ(), rect.maxX(), maxY, rect.minZ(), state);
		box(fills, rect.minX(), minY, rect.maxZ(), rect.minX(), maxY, rect.maxZ(), state);
		box(fills, rect.maxX(), minY, rect.maxZ(), rect.maxX(), maxY, rect.maxZ(), state);
	}

	public static void column(List<Fill> fills, int x, int z, int minY, int maxY, BlockState state) {
		box(fills, x, minY, z, x, maxY, z, state);
	}

	// ---------------------------------------------------------------- curve

	/** Un disco ellittico pieno, esteso in altezza: un cilindro a base ovale. */
	public static void disc(List<Fill> fills, int centreX, int centreZ, int radiusX, int radiusZ,
			int minY, int maxY, BlockState state) {
		for (int x = centreX - radiusX; x <= centreX + radiusX; x++) {
			int half = halfSpan(x - centreX, radiusX, radiusZ);

			if (half >= 0) {
				box(fills, x, minY, centreZ - half, x, maxY, centreZ + half, state);
			}
		}
	}

	/** Un anello ellittico: il disco meno il disco più piccolo dentro. */
	public static void ring(List<Fill> fills, int centreX, int centreZ, int radiusX, int radiusZ,
			int thickness, int minY, int maxY, BlockState state) {
		ring(fills, centreX, centreZ, radiusX, radiusZ, thickness, minY, maxY, state,
				centreX - radiusX, centreX + radiusX);
	}

	/**
	 * Un anello ellittico limitato a una fascia di X.
	 *
	 * <p>Il ritaglio serve alle rovine: un anfiteatro a cui manca un pezzo dell'anello esterno è
	 * riconoscibile, uno intero sembra costruito ieri.
	 */
	public static void ring(List<Fill> fills, int centreX, int centreZ, int radiusX, int radiusZ,
			int thickness, int minY, int maxY, BlockState state, int clipMinX, int clipMaxX) {
		int innerX = radiusX - thickness;
		int innerZ = radiusZ - thickness;

		for (int x = Math.max(centreX - radiusX, clipMinX);
				x <= Math.min(centreX + radiusX, clipMaxX); x++) {
			int outer = halfSpan(x - centreX, radiusX, radiusZ);

			if (outer < 0) {
				continue;
			}

			int inner = innerX <= 0 ? -1 : halfSpan(x - centreX, innerX, innerZ);

			if (inner < 0) {
				box(fills, x, minY, centreZ - outer, x, maxY, centreZ + outer, state);
			} else {
				box(fills, x, minY, centreZ - outer, x, maxY, centreZ - inner - 1, state);
				box(fills, x, minY, centreZ + inner + 1, x, maxY, centreZ + outer, state);
			}
		}
	}

	/** Una palla piena, affettata in dischi orizzontali. */
	public static void ball(List<Fill> fills, int centreX, int centreY, int centreZ, int radius,
			BlockState state) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = halfSpan(dy, radius, radius);

			if (half >= 0) {
				disc(fills, centreX, centreZ, half, half, centreY + dy, centreY + dy, state);
			}
		}
	}

	/** Mezza larghezza dell'ellisse alla distanza {@code offset} dal centro, o -1 se è fuori. */
	private static int halfSpan(int offset, int radiusX, int radiusZ) {
		if (radiusX <= 0 || Math.abs(offset) > radiusX) {
			return -1;
		}

		double t = (double) offset / radiusX;
		return (int) Math.round(radiusZ * Math.sqrt(Math.max(0.0, 1.0 - t * t)));
	}
}
