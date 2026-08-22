package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.luca.arise.config.GateConfig;

import net.minecraft.util.RandomSource;

/**
 * La pianta di un Gate: quali celle della griglia sono stanze, di che tipo, e come si collegano.
 *
 * <p>Solo geometria astratta, nessun blocco. Tenerla separata dalla costruzione significa poterla
 * generare, ispezionare e ripulire senza toccare il mondo — ed è anche l'unica parte davvero
 * "procedurale", quindi merita di stare da sola.
 *
 * <p>La pianta è una <strong>spina dorsale con diramazioni</strong>: prima un percorso principale
 * che porta al boss, poi qualche stanza laterale senza uscita innestata sul percorso. La catena
 * pura di prima produceva dungeon corretti ma tutti uguali, e soprattutto senza scelte: si
 * andava avanti e basta.
 */
public record GateLayout(Map<Cell, Kind> rooms, List<Link> links, Cell entrance, Cell bossRoom) {

	/** Una cella della griglia. Le coordinate sono in celle, non in blocchi. */
	public record Cell(int x, int z) {
		Cell offset(int dx, int dz) {
			return new Cell(x + dx, z + dz);
		}
	}

	/** Un corridoio fra due celle adiacenti. */
	public record Link(Cell from, Cell to) {
	}

	/** Che stanza è. Determina dimensione, luci e cosa ci si trova dentro. */
	public enum Kind {
		/** Dove si arriva. Sempre vuota. */
		ENTRANCE,
		/** Stanza di passaggio, popolata. */
		NORMAL,
		/** Sala ampia con pilastri: più nemici e copertura per combattere. */
		HALL,
		/** Stanza laterale senza uscita: pochi nemici, ma è una scelta se andarci. */
		SIDE,
		/** Dove sta il sovrano. Ampia, con luci diverse. */
		BOSS,
	}

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	public GateLayout {
		rooms = Map.copyOf(rooms);
		links = List.copyOf(links);
	}

	public Kind kindOf(Cell cell) {
		return rooms.getOrDefault(cell, Kind.NORMAL);
	}

	public static GateLayout generate(GateConfig config, RandomSource random) {
		int span = config.maxRooms() - config.minRooms() + 1;
		int mainLength = config.minRooms() + random.nextInt(Math.max(1, span));

		Map<Cell, Kind> rooms = new LinkedHashMap<>();
		List<Link> links = new ArrayList<>();
		List<Cell> spine = new ArrayList<>();

		Cell entrance = new Cell(0, 0);
		rooms.put(entrance, Kind.ENTRANCE);
		spine.add(entrance);

		// Spina dorsale: si cresce sempre dall'ultima piazzata, così il percorso resta lungo e il
		// boss finisce davvero in fondo invece che accanto all'ingresso.
		while (spine.size() < mainLength) {
			Cell from = spine.getLast();
			Cell next = freeNeighbour(from, rooms.keySet(), random);

			if (next == null) {
				break;
			}

			// Una stanza su tre è una sala ampia: spezza il ritmo e dà spazio a un vero scontro.
			rooms.put(next, random.nextInt(3) == 0 ? Kind.HALL : Kind.NORMAL);
			spine.add(next);
			links.add(new Link(from, next));
		}

		Cell boss = spine.getLast();
		rooms.put(boss, Kind.BOSS);

		addBranches(config, random, rooms, links, spine);

		return new GateLayout(rooms, links, entrance, boss);
	}

	/**
	 * Innesta stanze laterali sul percorso principale.
	 *
	 * <p>Mai sull'ingresso e mai sulla stanza del boss: la prima deve restare un punto di calma,
	 * e accanto alla seconda una deviazione verrebbe esplorata dopo la fine, quando non serve più.
	 */
	private static void addBranches(GateConfig config, RandomSource random, Map<Cell, Kind> rooms,
			List<Link> links, List<Cell> spine) {
		int branches = config.minBranches()
				+ random.nextInt(Math.max(1, config.maxBranches() - config.minBranches() + 1));

		for (int i = 0; i < branches && spine.size() > 2; i++) {
			Cell from = spine.get(1 + random.nextInt(spine.size() - 2));
			Cell next = freeNeighbour(from, rooms.keySet(), random);

			if (next != null) {
				rooms.put(next, Kind.SIDE);
				links.add(new Link(from, next));
			}
		}
	}

	private static Cell freeNeighbour(Cell from, java.util.Set<Cell> occupied, RandomSource random) {
		List<Cell> candidates = new ArrayList<>();

		for (int[] direction : DIRECTIONS) {
			Cell candidate = from.offset(direction[0], direction[1]);
			if (!occupied.contains(candidate)) {
				candidates.add(candidate);
			}
		}

		return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
	}

	/** Estensione della pianta in celle, per calcolare l'area da ripulire all'uscita. */
	public int[] boundsInCells() {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (Cell cell : rooms.keySet()) {
			minX = Math.min(minX, cell.x());
			maxX = Math.max(maxX, cell.x());
			minZ = Math.min(minZ, cell.z());
			maxZ = Math.max(maxZ, cell.z());
		}

		return new int[] {minX, maxX, minZ, maxZ};
	}
}
