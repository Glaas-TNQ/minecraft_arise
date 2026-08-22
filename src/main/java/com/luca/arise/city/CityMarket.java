package com.luca.arise.city;

import java.util.List;

import com.luca.arise.city.CityPlan.Fill;
import com.luca.arise.npc.Shopkeeper;

/**
 * Il Quartiere del Mercato: nove botteghe attorno alla piazza dell'Associazione.
 *
 * <p>Le citta' erano facciate. Nove edifici piccoli, con dentro qualcuno, cambiano quello che una
 * citta' <em>e'</em>: si smette di guardarla dall'alto e si comincia a entrarci. Il quartiere sta
 * sulla piazza e non in un isolato lontano proprio per questo — la prima cosa che si vede
 * arrivando dev'essere una porta aperta.
 *
 * <p><strong>Le posizioni sono la fonte di verita' condivisa.</strong> Questa classe dice dove
 * stanno le botteghe, e sia il costruttore della citta' sia chi ci mette dentro le persone leggono
 * la stessa lista. Due elenchi paralleli — uno per i muri, uno per gli NPC — sarebbero andati in
 * disaccordo alla prima bottega spostata di due blocchi.
 */
public final class CityMarket {

	/** Mezzo lato di una bottega: 7x7 esterni. */
	private static final int HALF = 3;

	/** Altezza interna. Bassa: sono botteghe, non palazzi. */
	private static final int HEIGHT = 4;

	/** Quanto dista dal centro della piazza la fila delle botteghe. */
	private static final int RING = 30;

	/** Quanto stanno distanti fra loro le botteghe di uno stesso lato. */
	private static final int SPREAD = 16;

	/**
	 * Una bottega: chi ci sta, dove sta, e da che parte guarda.
	 *
	 * <p>{@code facing} e' il verso in cui guarda chi ci sta dietro il bancone, cioe' verso la
	 * porta e quindi verso la piazza. E' un'imbardata di Minecraft: <strong>0 e' sud</strong>, 90
	 * ovest, 180 nord. Lo stesso numero decide da che parte si apre il muro e da che parte si gira
	 * l'NPC, ed e' il motivo per cui un venditore non finisce mai a fissare un muro.
	 */
	public record Stall(Shopkeeper role, int dx, int dz, float facing) {
	}

	/**
	 * Le nove botteghe, in coordinate relative al centro della piazza.
	 *
	 * <p>Quattro a nord, quattro a sud, una a est. Il lato ovest resta libero perche' e' da li'
	 * che si arriva viaggiando, e trovarsi addosso una parete appena materializzati sarebbe un
	 * benvenuto pessimo. Le colonne a x = ±5 sono lasciate stare: e' dove passa il viale del
	 * monumento.
	 */
	public static final List<Stall> STALLS = List.of(
			new Stall(Shopkeeper.SMELTER, -SPREAD - 8, -RING, 0.0F),
			new Stall(Shopkeeper.QUARRIER, -SPREAD + 8, -RING, 0.0F),
			new Stall(Shopkeeper.SOUL_TRADER, SPREAD - 8, -RING, 0.0F),
			new Stall(Shopkeeper.DRAFTSMAN, SPREAD + 8, -RING, 0.0F),

			new Stall(Shopkeeper.HERBALIST, -SPREAD - 8, RING, 180.0F),
			new Stall(Shopkeeper.BANKER, -SPREAD + 8, RING, 180.0F),
			new Stall(Shopkeeper.GATE_BROKER, SPREAD - 8, RING, 180.0F),
			new Stall(Shopkeeper.JEWELLER, SPREAD + 8, RING, 180.0F),

			new Stall(Shopkeeper.CLERK, RING, 0, 90.0F));

	private CityMarket() {
	}

	/** Mezzo lato della piazza che serve a contenere il quartiere, piu' il respiro attorno. */
	public static int plazaHalf() {
		return RING + HALF + 5;
	}

	/**
	 * Alza le nove botteghe.
	 *
	 * <p>Vengono dopo la piazza e prima dell'Associazione: la piazza ha appena spianato tutto, e
	 * costruirci sopra e' l'unico modo di non dover sottrarre niente a nessuno.
	 */
	public static void build(List<Fill> fills, City city, int centreX, int baseY, int centreZ) {
		for (Stall stall : STALLS) {
			shop(fills, city, centreX + stall.dx(), baseY, centreZ + stall.dz(), stall.facing());
		}
	}

	/**
	 * Una bottega: quattro muri, un tetto, una porta e un bancone.
	 *
	 * <p>Il bancone e' la meta' del lavoro. Senza, il venditore sarebbe una persona in piedi in
	 * una stanza vuota; con il bancone davanti, la stanza diventa un negozio senza che nessuno
	 * debba scriverlo da nessuna parte.
	 */
	private static void shop(List<Fill> fills, City city, int x, int baseY, int z, float facing) {
		CityPalette palette = city.palette();
		Rect outer = Rect.around(x, z, HALF);

		// Prima si sgombra: la piazza ha spianato il suolo, ma non l'aria.
		Shapes.box(fills, outer, baseY, baseY + HEIGHT + 2, CityPalette.air());

		Shapes.slab(fills, outer, baseY - 1, palette.sidewalk());
		Shapes.walls(fills, outer, baseY, baseY + HEIGHT - 1, palette.wallAlt());

		// La fascia di vetro all'altezza degli occhi: da fuori si vede che dentro c'e' qualcuno.
		Shapes.walls(fills, outer, baseY + 2, baseY + 2, palette.glass());

		Shapes.slab(fills, outer, baseY + HEIGHT, palette.roof());
		Shapes.slab(fills, outer.grow(1), baseY + HEIGHT + 1, palette.trim());

		// Le due lanterne ai lati della porta.
		door(fills, city, outer, baseY, facing);
		counter(fills, city, outer, baseY, facing);
	}

	/** L'apertura nel muro, alta due e larga tre, sul lato verso la piazza. */
	private static void door(List<Fill> fills, City city, Rect outer, int baseY, float facing) {
		CityPalette palette = city.palette();
		int y = baseY;

		if (facing == 0.0F || facing == 180.0F) {
			// Guarda a sud (0) o a nord (180): l'apertura sta sul lato opposto al muro di fondo.
			int z = facing == 0.0F ? outer.maxZ() : outer.minZ();
			Shapes.box(fills, outer.centreX() - 1, y, z, outer.centreX() + 1, y + 1, z,
					CityPalette.air());
			Shapes.column(fills, outer.centreX() - 2, z, y + 2, y + 2, palette.lamp());
			Shapes.column(fills, outer.centreX() + 2, z, y + 2, y + 2, palette.lamp());
		} else {
			// Guarda a ovest (90) o a est (270): l'apertura sta sul muro verso cui guarda.
			int x = facing == 90.0F ? outer.minX() : outer.maxX();
			Shapes.box(fills, x, y, outer.centreZ() - 1, x, y + 1, outer.centreZ() + 1,
					CityPalette.air());
			Shapes.column(fills, x, outer.centreZ() - 2, y + 2, y + 2, palette.lamp());
			Shapes.column(fills, x, outer.centreZ() + 2, y + 2, y + 2, palette.lamp());
		}
	}

	/** Il bancone: una fila di blocchi fra la porta e chi vende. */
	private static void counter(List<Fill> fills, City city, Rect outer, int baseY, float facing) {
		CityPalette palette = city.palette();
		Rect inner = outer.shrink(1);

		if (facing == 0.0F) {
			Shapes.box(fills, inner.minX(), baseY, inner.centreZ(), inner.maxX(), baseY,
					inner.centreZ(), palette.trim());
		} else if (facing == 180.0F) {
			Shapes.box(fills, inner.minX(), baseY, inner.centreZ(), inner.maxX(), baseY,
					inner.centreZ(), palette.trim());
		} else {
			Shapes.box(fills, inner.centreX(), baseY, inner.minZ(), inner.centreX(), baseY,
					inner.maxZ(), palette.trim());
		}
	}

	/**
	 * Dove sta in piedi chi vende: un blocco dietro il bancone, verso il muro di fondo.
	 *
	 * <p>Si ricava dalla stessa {@link Stall} che ha costruito l'edificio, quindi non puo' finire
	 * fuori posto: spostare una bottega significa spostare la sua persona, e non serve ricordarsi
	 * di farlo.
	 */
	public static double[] standing(Stall stall, int centreX, int baseY, int centreZ) {
		double x = centreX + stall.dx() + 0.5;
		double z = centreZ + stall.dz() + 0.5;

		if (stall.facing() == 0.0F) {
			z -= 2.0;
		} else if (stall.facing() == 180.0F) {
			z += 2.0;
		} else if (stall.facing() == 90.0F) {
			x += 2.0;
		} else {
			x -= 2.0;
		}

		return new double[] {x, baseY, z};
	}
}
