package com.luca.arise.city;

import java.util.List;

import com.luca.arise.city.CityPlan.Fill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * La costruzione di una città in corso: un po' per battito del server, finché non è finita.
 *
 * <p>Una città è quasi un milione di blocchi. Piazzarli tutti dentro un comando significherebbe
 * un server fermo per minuti, un client che dichiara la partita bloccata e, in multiplayer,
 * tutti espulsi per timeout. Qui si tiene un <em>cursore</em> dentro l'elenco dei volumi: ogni
 * battito se ne piazzano quanti dice la config, poi si riprende esattamente da dove si era.
 *
 * <p>Il prezzo è che la città cresce sotto gli occhi invece di apparire — che poi è anche meglio.
 */
public final class CityBuild {

	/** Come nei Gate: nessun aggiornamento ai vicini, o il costo si moltiplica. */
	private static final int FLAGS = 2;

	private final ServerLevel level;
	private final City city;
	private final List<Fill> fills;
	private final long total;
	private final int baseY;

	private int index;
	private boolean cursorReady;
	private int x;
	private int y;
	private int z;
	private long placed;

	public CityBuild(ServerLevel level, City city, List<Fill> fills, int baseY) {
		this.level = level;
		this.city = city;
		this.fills = fills;
		this.baseY = baseY;

		long sum = 0;
		for (Fill fill : fills) {
			sum += fill.volume();
		}
		this.total = Math.max(1, sum);
	}

	public City city() {
		return city;
	}

	public int baseY() {
		return baseY;
	}

	public ServerLevel level() {
		return level;
	}

	public int percent() {
		return (int) Math.min(100, placed * 100 / total);
	}

	public boolean done() {
		return index >= fills.size();
	}

	/**
	 * Piazza fino a {@code budget} blocchi e si ferma.
	 *
	 * @return vero quando non è rimasto più niente da costruire
	 */
	public boolean advance(int budget) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int remaining = budget;

		while (remaining > 0 && index < fills.size()) {
			Fill fill = fills.get(index);

			if (!cursorReady) {
				x = fill.minX();
				y = fill.minY();
				z = fill.minZ();
				cursorReady = true;
			}

			while (remaining > 0) {
				cursor.set(x, y, z);
				level.setBlock(cursor, fill.state(), FLAGS);
				remaining--;
				placed++;

				// Avanzamento del cursore: prima l'altezza, poi la profondità, poi la larghezza.
				// Salire per colonne tiene il lavoro dentro lo stesso chunk il più a lungo
				// possibile, e i chunk qui vanno generati da zero.
				if (++y > fill.maxY()) {
					y = fill.minY();
					if (++z > fill.maxZ()) {
						z = fill.minZ();
						if (++x > fill.maxX()) {
							index++;
							cursorReady = false;
							break;
						}
					}
				}
			}
		}

		return done();
	}
}
