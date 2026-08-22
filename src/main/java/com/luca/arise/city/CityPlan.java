package com.luca.arise.city;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.CityConfig;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La pianta di una città, come <em>elenco di volumi da riempire</em> e non come blocchi.
 *
 * <p>È la scelta che rende la cosa possibile. Una città sono milioni di blocchi: tenerli tutti in
 * memoria come posizioni sarebbe assurdo, e piazzarli in un colpo bloccherebbe il server. Un
 * elenco di qualche migliaio di parallelepipedi si genera in un istante, si conta in anticipo —
 * quindi c'è una barra di avanzamento vera — e si esegue un pezzo per volta.
 *
 * <p>L'ordine dei volumi conta: sono applicati in sequenza, e i successivi scavano nei precedenti.
 * È così che una porta si apre in un muro già costruito senza calcolare niente, e che la piazza
 * del monumento cancella le strade che le passavano sopra.
 *
 * <p>La successione è sempre la stessa: si spiana, si tirano le strade, si riservano le tre zone
 * che non appartengono alla griglia (la piazza dell'Associazione, l'area del monumento, il viale
 * che le collega), si riempie tutto il resto isolato per isolato, e alla fine si posano i pezzi
 * unici. Il segnaposto dell'Associazione è l'ultimo volume dell'elenco: finché non compare, la
 * città non risulta costruita, e una costruzione interrotta a metà non inganna nessuno.
 */
public final class CityPlan {

	/** Un parallelepipedo pieno di un solo blocco. Estremi inclusi. */
	public record Fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {

		public long volume() {
			return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		}
	}

	/** Quanto scende il plinto sotto il livello di calpestio. */
	private static final int FOUNDATION_DEPTH = 20;

	/**
	 * Quanto si sgombra sopra.
	 *
	 * <p>Non è un numero libero: è anche il tetto degli edifici più alti che la griglia può
	 * produrre ({@code maxFloors} per {@link City#FLOOR_HEIGHT}). Sotto questa quota tutto è aria
	 * garantita, quindi un guscio di quattro pareti è vuoto dentro senza doverlo svuotare. Chi sale
	 * più in su — i monumenti — si sgombra il cielo per conto suo.
	 */
	private static final int CLEARANCE = 96;

	/** Passo dei lampioni lungo le strade. */
	private static final int LAMP_SPACING = 14;

	/** Lato dell'Associazione dei Cacciatori. */
	private static final int HQ_SIZE = 29;

	private static final int HQ_FLOORS = 4;

	/** Mezzo lato della piazza: l'Associazione più lo spazio per vederla. */
	private static final int PLAZA_HALF = 22;

	/** Mezza larghezza del viale che porta al monumento. */
	private static final int AVENUE_HALF = 5;

	private CityPlan() {
	}

	/**
	 * Costruisce l'elenco dei volumi di una città.
	 *
	 * @param baseY la quota di calpestio: ci si cammina sopra, il terreno finisce a {@code baseY - 1}
	 */
	public static List<Fill> of(City city, CityConfig config, int baseY, RandomSource random) {
		List<Fill> fills = new ArrayList<>();
		Grid grid = Grid.of(city, config);

		Rect plaza = Rect.around(grid.centreX(), grid.centreZ(), PLAZA_HALF);
		Rect monument = Rect.around(grid.centreX(), grid.centreZ() - landmarkOffset(city, grid),
				city.landmark().radius());
		Rect avenue = new Rect(grid.centreX() - AVENUE_HALF, monument.maxZ(),
				grid.centreX() + AVENUE_HALF, plaza.minZ());
		Rect boulevard = city.style().boulevard()
				? new Rect(grid.x0(), grid.centreZ() - 7, grid.x1(), grid.centreZ() + 7)
				: null;

		terrace(fills, city, grid, baseY);
		roads(fills, city, grid, baseY);

		if (boulevard != null) {
			avenue(fills, city, boulevard, baseY, false);
		}
		avenue(fills, city, avenue, baseY, true);
		districts(fills, city, grid, baseY, random, plaza, monument, avenue, boulevard);

		// La circonvallazione si posa dopo gli isolati, non prima: gli isolati che attraversa sono
		// diventati giardini, e cosi' il selciato ci passa in mezzo invece di finire sotto un muro.
		if (city.style().ringRoad()) {
			Shapes.ring(fills, grid.centreX(), grid.centreZ(), grid.ringRadius(), grid.ringRadius(),
					grid.road(), baseY - 1, baseY - 1, city.palette().road());
		}

		square(fills, city, monument, baseY, true);
		clearSky(fills, city, monument, baseY);
		CityLandmarks.build(fills, city, monument.centreX(), baseY, monument.centreZ());

		square(fills, city, plaza, baseY, false);
		headquarters(fills, city, grid, baseY);

		return List.copyOf(fills);
	}

	/** Quanto dista il monumento dalla piazza: tre isolati, o quanto ce ne sta prima del bordo. */
	private static int landmarkOffset(City city, Grid grid) {
		int radius = city.landmark().radius();
		int wanted = grid.block() * 3;
		int room = grid.size() / 2 - radius - 16;
		int least = PLAZA_HALF + radius + 8;

		return Math.max(least, Math.min(wanted, room));
	}

	// ---------------------------------------------------------------- suolo e strade

	/** Spiana: plinto sotto, aria sopra, e un suolo su cui posare tutto il resto. */
	private static void terrace(List<Fill> fills, City city, Grid grid, int baseY) {
		Rect all = new Rect(grid.x0(), grid.z0(), grid.x1(), grid.z1());

		Shapes.box(fills, all, baseY - FOUNDATION_DEPTH, baseY - 2, Blocks.STONE.defaultBlockState());
		Shapes.box(fills, all, baseY, baseY + CLEARANCE, CityPalette.air());
		Shapes.slab(fills, all, baseY - 1, city.palette().sidewalk());

		// La fascia verde fuori dalla griglia: quello che avanza non è marciapiede, è periferia.
		Rect built = new Rect(grid.gx0(), grid.gz0(),
				grid.gx0() + grid.cells() * grid.block(), grid.gz0() + grid.cells() * grid.block());

		if (built.minX() > all.minX()) {
			Shapes.box(fills, all.minX(), baseY - 1, all.minZ(), built.minX() - 1, baseY - 1,
					all.maxZ(), CityPalette.grass());
			Shapes.box(fills, built.maxX() + 1, baseY - 1, all.minZ(), all.maxX(), baseY - 1,
					all.maxZ(), CityPalette.grass());
			Shapes.box(fills, built.minX(), baseY - 1, all.minZ(), built.maxX(), baseY - 1,
					built.minZ() - 1, CityPalette.grass());
			Shapes.box(fills, built.minX(), baseY - 1, built.maxZ() + 1, built.maxX(), baseY - 1,
					all.maxZ(), CityPalette.grass());
		}
	}

	/** La maglia stradale, con le avenue più larghe e i lampioni ai bordi. */
	private static void roads(List<Fill> fills, City city, Grid grid, int baseY) {
		int ground = baseY - 1;
		BlockState road = city.palette().road();

		for (int i = 0; i <= grid.cells(); i++) {
			int half = grid.halfAt(i);
			int x = grid.gx0() + i * grid.block();
			int z = grid.gz0() + i * grid.block();

			Shapes.box(fills, Math.max(grid.x0(), x - half), ground, grid.z0(),
					Math.min(grid.x1(), x + half), ground, grid.z1(), road);
			Shapes.box(fills, grid.x0(), ground, Math.max(grid.z0(), z - half),
					grid.x1(), ground, Math.min(grid.z1(), z + half), road);
		}

		for (int i = 0; i <= grid.cells(); i++) {
			int x = grid.gx0() + i * grid.block();
			int half = grid.halfAt(i);

			for (int along = LAMP_SPACING / 2; along < grid.cells() * grid.block(); along += LAMP_SPACING) {
				lamp(fills, city, x - half - 1, baseY, grid.gz0() + along, grid);
				lamp(fills, city, x + half + 1, baseY, grid.gz0() + along, grid);
			}
		}
	}

	/** Un lampione, se cade dentro la città. Fuori dal perimetro sarebbe un palo in mezzo ai campi. */
	private static void lamp(List<Fill> fills, City city, int x, int baseY, int z, Grid grid) {
		if (x < grid.x0() || x > grid.x1() || z < grid.z0() || z > grid.z1()) {
			return;
		}

		Shapes.column(fills, x, z, baseY, baseY + 3, city.palette().accent());
		Shapes.column(fills, x, z, baseY + 4, baseY + 4, city.palette().lamp());
	}

	/**
	 * Un viale: carreggiata in mezzo, marciapiedi larghi ai lati, alberi e lampioni a passo fisso.
	 *
	 * <p>Serve a due cose diverse con lo stesso disegno: portare in fondo al monumento, e tagliare
	 * la città da parte a parte dove lo stile lo prevede. In entrambi i casi è la strada che si
	 * ricorda, perché è l'unica da cui si vede qualcosa in fondo.
	 */
	private static void avenue(List<Fill> fills, City city, Rect strip, int baseY, boolean northSouth) {
		int ground = baseY - 1;
		CityPalette palette = city.palette();

		Shapes.slab(fills, strip, ground, palette.sidewalk());
		Shapes.slab(fills, strip.shrink(2), ground, palette.road());

		if (northSouth) {
			for (int z = strip.minZ() + 6; z <= strip.maxZ() - 6; z += 10) {
				CityBuildings.tree(fills, palette, strip.minX() + 1, baseY, z, 4);
				CityBuildings.tree(fills, palette, strip.maxX() - 1, baseY, z, 4);
			}
		} else {
			for (int x = strip.minX() + 6; x <= strip.maxX() - 6; x += 10) {
				CityBuildings.tree(fills, palette, x, baseY, strip.minZ() + 1, 4);
				CityBuildings.tree(fills, palette, x, baseY, strip.maxZ() - 1, 4);
			}
		}
	}

	// ---------------------------------------------------------------- isolati

	/** Riempie la griglia: un giardino ogni tanto, un edificio in tutto il resto. */
	private static void districts(List<Fill> fills, City city, Grid grid, int baseY,
			RandomSource random, Rect plaza, Rect monument, Rect avenue, Rect boulevard) {
		for (int cx = 0; cx < grid.cells(); cx++) {
			for (int cz = 0; cz < grid.cells(); cz++) {
				Rect lot = subtract(subtract(subtract(grid.lot(cx, cz), plaza), monument), avenue);

				if (boulevard != null) {
					lot = subtract(lot, boulevard);
				}

				if (lot.shortSide() < CityBuildings.MIN_LOT) {
					continue;
				}

				// Sull'anello non si costruisce: resta il verde, e il selciato ci passera' dentro.
				if (grid.crossesRing(lot, city) || random.nextInt(100) < city.style().parkChance()) {
					CityBuildings.park(fills, city, lot, baseY, random);
				} else {
					CityBuildings.place(fills, city, lot, baseY, floors(city, grid, cx, cz, random),
							random);
				}
			}
		}
	}

	/**
	 * Toglie da un lotto la parte che finirebbe dentro una zona riservata, e tiene il pezzo piu'
	 * grande che resta.
	 *
	 * <p>Buttare via l'isolato intero sarebbe piu' semplice, ed e' il motivo per cui tante citta'
	 * generate hanno un buco attorno alla piazza: ogni isolato che la sfiora sparisce, e la piazza
	 * si ritrova in mezzo a un campo. Tenendo il pezzo piu' grande, invece, gli edifici arrivano
	 * fino al bordo della piazza e sono loro a darle una forma.
	 *
	 * <p>I quattro pezzi possibili sono le quattro fasce attorno alla zona; si sceglie quella con
	 * piu' superficie. Due blocchi di margine perche' il marciapiede si veda.
	 */
	private static Rect subtract(Rect lot, Rect zone) {
		if (!lot.overlaps(zone)) {
			return lot;
		}

		Rect[] pieces = {
				new Rect(lot.minX(), lot.minZ(), Math.min(lot.maxX(), zone.minX() - 2), lot.maxZ()),
				new Rect(Math.max(lot.minX(), zone.maxX() + 2), lot.minZ(), lot.maxX(), lot.maxZ()),
				new Rect(lot.minX(), lot.minZ(), lot.maxX(), Math.min(lot.maxZ(), zone.minZ() - 2)),
				new Rect(lot.minX(), Math.max(lot.minZ(), zone.maxZ() + 2), lot.maxX(), lot.maxZ())};

		Rect best = pieces[0];

		for (Rect piece : pieces) {
			if (area(piece) > area(best)) {
				best = piece;
			}
		}

		return best;
	}

	private static long area(Rect rect) {
		return rect.width() <= 0 || rect.depth() <= 0 ? 0 : (long) rect.width() * rect.depth();
	}

	/**
	 * Quanti piani ha un edificio in questo isolato.
	 *
	 * <p>Dove lo stile lo prevede l'altezza cresce verso il centro: è quello che dà uno skyline
	 * invece di un tappeto. Dove non lo prevede resta il sorteggio dentro una forbice stretta, che
	 * è come si comportano le città con il cornicione allineato.
	 */
	private static int floors(City city, Grid grid, int cx, int cz, RandomSource random) {
		CityStyle style = city.style();
		int span = style.maxFloors() - style.minFloors();

		if (!style.taper()) {
			return style.minFloors() + random.nextInt(span + 1);
		}

		int half = grid.cells() / 2;
		int distance = Math.max(Math.abs(cx - half), Math.abs(cz - half));
		int downtown = span * Math.max(0, half - distance) / Math.max(1, half);

		return Math.max(style.minFloors(), style.minFloors() + downtown - random.nextInt(3));
	}

	// ---------------------------------------------------------------- piazze e monumento

	/** Una piazza lastricata, con il riquadro interno di un altro colore e i lampioni agli angoli. */
	private static void square(List<Fill> fills, City city, Rect area, int baseY, boolean monument) {
		CityPalette palette = city.palette();

		Shapes.box(fills, area, baseY, baseY + 8, CityPalette.air());
		Shapes.slab(fills, area, baseY - 1, palette.sidewalk());
		Shapes.slab(fills, area.shrink(4), baseY - 1, palette.accent());

		if (monument) {
			Shapes.slab(fills, area.shrink(8), baseY - 1, palette.sidewalk());
		}

		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				int x = area.centreX() + dx * (area.width() / 2 - 2);
				int z = area.centreZ() + dz * (area.depth() / 2 - 2);

				Shapes.column(fills, x, z, baseY, baseY + 4, palette.accent());
				Shapes.column(fills, x, z, baseY + 5, baseY + 5, palette.lamp());
			}
		}
	}

	/**
	 * Sgombera il cielo sopra il monumento.
	 *
	 * <p>La spianata arriva a {@link #CLEARANCE}, che basta per il più alto degli edifici normali.
	 * Una guglia o un'antenna vanno molto più su, e lassù può esserci ancora il fianco di una
	 * montagna: senza questo, la torre ci finirebbe dentro.
	 */
	private static void clearSky(List<Fill> fills, City city, Rect area, int baseY) {
		int height = city.landmark().height();

		if (height <= CLEARANCE) {
			return;
		}

		Rect column = Rect.around(area.centreX(), area.centreZ(), Math.min(24, area.width() / 2));
		Shapes.box(fills, column, baseY + CLEARANCE + 1, baseY + height, CityPalette.air());
	}

	// ---------------------------------------------------------------- Associazione

	/**
	 * L'Associazione dei Cacciatori: l'unico edificio in cui si entra davvero.
	 *
	 * <p>Sta al centro della piazza, è più bassa dei grattacieli attorno ma più larga di tutto, e
	 * dentro è vuota e illuminata. Al centro c'è la pietra su cui poggia il terminale di viaggio —
	 * ed è anche il modo in cui la mod riconosce che questa città è già stata costruita, motivo per
	 * cui è l'ultimo volume di tutto l'elenco.
	 */
	private static void headquarters(List<Fill> fills, City city, Grid grid, int baseY) {
		CityPalette palette = city.palette();
		int centreX = grid.centreX();
		int centreZ = grid.centreZ();
		Rect walls = Rect.around(centreX, centreZ, HQ_SIZE / 2);
		int top = baseY + HQ_FLOORS * City.FLOOR_HEIGHT;

		// Il basamento: tre gradini che alzano l'edificio sopra la piazza. Sono anche la scala —
		// tre lastre che rientrano una sull'altra sono già dei gradini, su tutti e quattro i lati.
		for (int step = 0; step < 3; step++) {
			Shapes.slab(fills, walls.grow(4 - step), baseY - 1 + step, palette.wallAlt());
		}

		Shapes.walls(fills, walls, baseY + 2, top, palette.wall());

		// Vetrate alte due piani su tutti i lati: da fuori si vede che è un posto pubblico.
		Shapes.walls(fills, walls, baseY + 4, baseY + 9, palette.glass());
		Shapes.corners(fills, walls, baseY + 2, top, palette.trim());

		Shapes.slab(fills, walls, top, palette.roof());
		Shapes.walls(fills, walls.grow(1), top + 1, top + 1, palette.accent());
		Shapes.slab(fills, walls.shrink(3), top + 1, palette.roof());

		// Il portico: sette colonne davanti all'ingresso, sotto il timpano.
		for (int x = -9; x <= 9; x += 3) {
			Shapes.column(fills, centreX + x, walls.maxZ() + 2, baseY + 2, top - 1, palette.wallAlt());
		}

		Shapes.box(fills, centreX - 10, top, walls.maxZ() + 1, centreX + 10, top + 1,
				walls.maxZ() + 3, palette.trim());

		// Ingresso a sud, largo cinque: ci si entra senza cercare la porta.
		Shapes.box(fills, centreX - 2, baseY + 2, walls.maxZ(), centreX + 2, baseY + 6, walls.maxZ(),
				CityPalette.air());
		Shapes.box(fills, centreX - 2, baseY + 2, walls.maxZ() + 1, centreX + 2, baseY + 6,
				walls.maxZ() + 2, CityPalette.air());

		Shapes.box(fills, walls.shrink(2), top - 1, top - 1, Blocks.SEA_LANTERN.defaultBlockState());
		Shapes.slab(fills, Rect.around(centreX, centreZ, 3), baseY + 2, palette.accent());
		Shapes.box(fills, centreX, baseY + 2, centreZ, centreX, baseY + 2, centreZ, marker());
	}

	/**
	 * Quanto è distante dal segnaposto il punto in cui si arriva viaggiando.
	 *
	 * <p>Sta qui e non in chi teleporta perché dipende da com'è fatta l'Associazione: il giorno in
	 * cui diventa più larga, chi arriva non deve ritrovarsi dentro un muro.
	 */
	public static int entranceOffset() {
		return HQ_SIZE / 2 + 6;
	}

	/** La quota del segnaposto sopra il piano di calpestio: l'Associazione è su un basamento. */
	public static int markerHeight() {
		return 2;
	}

	/**
	 * Il blocco che dice "questa città esiste".
	 *
	 * <p>Non serve nessun file di salvataggio: la prova che una città è stata costruita è la città
	 * stessa. Si guarda se al centro dell'Associazione c'è questa pietra, e si ha la risposta —
	 * senza dati paralleli da tenere in sincronia con il mondo.
	 */
	public static BlockState marker() {
		return Blocks.LODESTONE.defaultBlockState();
	}

	// ---------------------------------------------------------------- la griglia

	/**
	 * La maglia di una città: dove cadono le strade e dove restano i lotti.
	 *
	 * <p>Il numero di celle è tenuto pari di proposito. Con un numero pari il centro della città
	 * cade esattamente su un incrocio, e la piazza dell'Associazione — che è un quadrato centrato
	 * lì — resta simmetrica rispetto alla griglia invece di tagliare due isolati a metà.
	 */
	private record Grid(int x0, int z0, int x1, int z1, int gx0, int gz0, int cells, int block,
			int road, int avenueEvery, int size) {

		static Grid of(City city, CityConfig config) {
			CityStyle style = city.style();
			int size = config.size();
			int block = style.blockSize(config.blockSize());
			int road = style.roadWidth(config.roadWidth());

			int cells = Math.max(2, size / block);
			if (cells % 2 != 0) {
				cells--;
			}

			int x0 = config.cityX(city);
			int z0 = config.cityZ(city);
			int margin = (size - cells * block) / 2;

			return new Grid(x0, z0, x0 + size - 1, z0 + size - 1, x0 + margin, z0 + margin,
					cells, block, road, style.avenueEvery(), size);
		}

		int centreX() {
			return x0 + size / 2;
		}

		int centreZ() {
			return z0 + size / 2;
		}

		/** Mezza larghezza della strada su questo incrocio: le avenue sono più larghe. */
		int halfAt(int boundary) {
			return road / 2 + (avenueEvery > 0 && boundary % avenueEvery == 0 ? 2 : 0);
		}

		/**
		 * Il rettangolo costruibile di una cella, strade escluse.
		 *
		 * <p>Due blocchi di stacco dalla carreggiata e non uno: sul primo ci stanno i lampioni, e un
		 * lampione murato dentro la facciata non lo vuole nessuno.
		 */
		Rect lot(int cx, int cz) {
			return new Rect(
					gx0 + cx * block + halfAt(cx) + 2,
					gz0 + cz * block + halfAt(cz) + 2,
					gx0 + (cx + 1) * block - halfAt(cx + 1) - 2,
					gz0 + (cz + 1) * block - halfAt(cz + 1) - 2);
		}

		int ringRadius() {
			return block * 3 / 2;
		}

		/** Vero se la circonvallazione passa dentro questo lotto: allora lì non si costruisce. */
		boolean crossesRing(Rect lot, City city) {
			if (!city.style().ringRoad()) {
				return false;
			}

			int radius = ringRadius();
			double near = distance(lot, true);
			double far = distance(lot, false);

			return near <= radius + road && far >= radius - road;
		}

		private double distance(Rect lot, boolean nearest) {
			int dx = nearest
					? Math.max(0, Math.max(lot.minX() - centreX(), centreX() - lot.maxX()))
					: Math.max(Math.abs(lot.minX() - centreX()), Math.abs(lot.maxX() - centreX()));
			int dz = nearest
					? Math.max(0, Math.max(lot.minZ() - centreZ(), centreZ() - lot.maxZ()))
					: Math.max(Math.abs(lot.minZ() - centreZ()), Math.abs(lot.maxZ() - centreZ()));

			return Math.sqrt((double) dx * dx + (double) dz * dz);
		}
	}
}
