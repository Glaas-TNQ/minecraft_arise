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
 * <p>È la scelta che rende la cosa possibile. Una città sono più di un milione di blocchi: tenerli
 * tutti in memoria come posizioni sarebbe assurdo, e piazzarli in un colpo bloccherebbe il server.
 * Un elenco di poche centinaia di parallelepipedi si genera in un istante, si conta in anticipo —
 * quindi c'è una barra di avanzamento vera — e si esegue un pezzo per volta.
 *
 * <p>L'ordine dei volumi conta: sono applicati in sequenza, e i successivi scavano nei precedenti.
 * È così che una porta si apre in un muro già costruito senza calcolare niente.
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

	/** Quanto si sgombra sopra: abbastanza per una montagna, non per l'intero mondo. */
	private static final int CLEARANCE = 120;

	/** Passo dei lampioni lungo le strade. */
	private static final int LAMP_SPACING = 14;

	/** Lato dell'Associazione dei Cacciatori. */
	private static final int HQ_SIZE = 21;

	private static final int HQ_FLOORS = 3;

	private CityPlan() {
	}

	/**
	 * Costruisce l'elenco dei volumi di una città.
	 *
	 * @param baseY la quota di calpestio: ci si cammina sopra, il terreno finisce a {@code baseY - 1}
	 */
	public static List<Fill> of(City city, CityConfig config, int baseY, RandomSource random) {
		List<Fill> fills = new ArrayList<>();

		int x0 = config.cityX(city);
		int z0 = config.cityZ(city);
		int x1 = x0 + config.size() - 1;
		int z1 = z0 + config.size() - 1;

		terrace(fills, city, x0, z0, x1, z1, baseY);
		roads(fills, city, config, x0, z0, x1, z1, baseY);

		int cells = config.size() / config.blockSize();
		int middle = cells / 2;

		for (int cx = 0; cx < cells; cx++) {
			for (int cz = 0; cz < cells; cz++) {
				// Le quattro celle centrali sono la piazza: l'Associazione ci sta in mezzo e
				// attorno resta lo spazio per vederla.
				if (cx == middle - 1 || cx == middle) {
					if (cz == middle - 1 || cz == middle) {
						continue;
					}
				}

				building(fills, city, config, x0, z0, cx, cz, baseY, random);
			}
		}

		headquarters(fills, city, config, baseY);

		return List.copyOf(fills);
	}

	/** Spiana: plinto sotto, aria sopra, e un suolo su cui posare tutto il resto. */
	private static void terrace(List<Fill> fills, City city, int x0, int z0, int x1, int z1, int baseY) {
		fills.add(new Fill(x0, baseY - FOUNDATION_DEPTH, z0, x1, baseY - 2, z1,
				Blocks.STONE.defaultBlockState()));
		fills.add(new Fill(x0, baseY, z0, x1, baseY + CLEARANCE, z1, Blocks.AIR.defaultBlockState()));
		fills.add(new Fill(x0, baseY - 1, z0, x1, baseY - 1, z1, city.sidewalk()));
	}

	/** La griglia stradale, con i lampioni ai bordi. */
	private static void roads(List<Fill> fills, City city, CityConfig config,
			int x0, int z0, int x1, int z1, int baseY) {
		int ground = baseY - 1;
		int half = config.roadWidth() / 2;

		// Le carreggiate ai due bordi cadrebbero metà fuori dalla città: si tagliano sul perimetro,
		// altrimenti finirebbero blocchi di asfalto sul terreno naturale mai spianato, sospesi o
		// sepolti a seconda di com'era fatta la collina lì accanto.
		for (int offset = 0; offset <= config.size(); offset += config.blockSize()) {
			int x = Math.clamp(x0 + offset, x0, x1);
			int z = Math.clamp(z0 + offset, z0, z1);

			fills.add(new Fill(Math.max(x0, x - half), ground, z0,
					Math.min(x1, x + half), ground, z1, city.road()));
			fills.add(new Fill(x0, ground, Math.max(z0, z - half),
					x1, ground, Math.min(z1, z + half), city.road()));
		}

		for (int offset = 0; offset <= config.size(); offset += config.blockSize()) {
			int x = Math.clamp(x0 + offset, x0, x1);

			for (int along = LAMP_SPACING / 2; along < config.size(); along += LAMP_SPACING) {
				lamp(fills, city, x - half - 1, baseY, z0 + along, x0, x1);
				lamp(fills, city, x + half + 1, baseY, z0 + along, x0, x1);
			}
		}
	}

	/** Un lampione, se cade dentro la città. Fuori dal perimetro sarebbe un palo in mezzo ai campi. */
	private static void lamp(List<Fill> fills, City city, int x, int baseY, int z, int x0, int x1) {
		if (x < x0 || x > x1) {
			return;
		}

		fills.add(new Fill(x, baseY, z, x, baseY + 3, z, city.accent()));
		fills.add(new Fill(x, baseY + 4, z, x, baseY + 4, z, Blocks.GLOWSTONE.defaultBlockState()));
	}

	/** Un edificio dentro il suo isolato: guscio, fasce di vetro, cornicione, porta. */
	private static void building(List<Fill> fills, City city, CityConfig config,
			int x0, int z0, int cx, int cz, int baseY, RandomSource random) {
		int lot = config.lotSize();
		int inset = 1 + random.nextInt(2);

		int minX = x0 + cx * config.blockSize() + config.roadWidth() / 2 + 1 + inset;
		int minZ = z0 + cz * config.blockSize() + config.roadWidth() / 2 + 1 + inset;
		int maxX = minX + lot - 2 - inset * 2;
		int maxZ = minZ + lot - 2 - inset * 2;

		if (maxX - minX < 4 || maxZ - minZ < 4) {
			return;
		}

		int floors = city.minFloors() + random.nextInt(city.maxFloors() - city.minFloors() + 1);
		int height = floors * City.FLOOR_HEIGHT;
		int top = baseY + height;

		// Guscio pieno e poi svuotato: due volumi invece di quattro muri, e le finestre si
		// ritagliano dopo senza dover ragionare su ogni parete.
		fills.add(new Fill(minX, baseY, minZ, maxX, top, maxZ, city.wall()));
		fills.add(new Fill(minX + 1, baseY, minZ + 1, maxX - 1, top - 1, maxZ - 1,
				Blocks.AIR.defaultBlockState()));

		for (int floor = 0; floor < floors; floor++) {
			int y = baseY + floor * City.FLOOR_HEIGHT + 1;

			fills.add(new Fill(minX, y, minZ + 1, minX, y + 1, maxZ - 1, city.glass()));
			fills.add(new Fill(maxX, y, minZ + 1, maxX, y + 1, maxZ - 1, city.glass()));
			fills.add(new Fill(minX + 1, y, minZ, maxX - 1, y + 1, minZ, city.glass()));
			fills.add(new Fill(minX + 1, y, maxZ, maxX - 1, y + 1, maxZ, city.glass()));
		}

		fills.add(new Fill(minX, top, minZ, maxX, top, maxZ, city.roof()));
		fills.add(new Fill(minX, top + 1, minZ, maxX, top + 1, maxZ, city.accent()));
		fills.add(new Fill(minX + 1, top + 1, minZ + 1, maxX - 1, top + 1, maxZ - 1,
				Blocks.AIR.defaultBlockState()));

		// Il pavimento interno, e solo quello: stenderlo su tutta l'impronta cancellerebbe i muri
		// al piano terra e l'edificio resterebbe aperto sui quattro lati.
		fills.add(new Fill(minX + 1, baseY, minZ + 1, maxX - 1, baseY, maxZ - 1, city.sidewalk()));

		// La porta guarda la strada a sud.
		int doorX = (minX + maxX) / 2;
		fills.add(new Fill(doorX - 1, baseY, maxZ, doorX + 1, baseY + 2, maxZ,
				Blocks.AIR.defaultBlockState()));
	}

	/**
	 * L'Associazione dei Cacciatori: l'unico edificio in cui si entra davvero.
	 *
	 * <p>Sta al centro della piazza, è più bassa dei grattacieli attorno ma più larga di tutto, e
	 * dentro è vuota e illuminata. Al centro c'è la pietra su cui poggia il terminale di viaggio —
	 * ed è anche il modo in cui la mod riconosce che questa città è già stata costruita.
	 */
	private static void headquarters(List<Fill> fills, City city, CityConfig config, int baseY) {
		int centreX = config.centreX(city);
		int centreZ = config.centreZ(city);
		int half = HQ_SIZE / 2;

		int minX = centreX - half;
		int maxX = centreX + half;
		int minZ = centreZ - half;
		int maxZ = centreZ + half;
		int height = HQ_FLOORS * City.FLOOR_HEIGHT;
		int top = baseY + height;

		// Il sagrato: si vede che lì attorno non ci si costruisce.
		fills.add(new Fill(minX - 4, baseY - 1, minZ - 4, maxX + 4, baseY - 1, maxZ + 4, city.sidewalk()));
		fills.add(new Fill(minX - 2, baseY - 1, minZ - 2, maxX + 2, baseY - 1, maxZ + 2, city.accent()));

		fills.add(new Fill(minX, baseY, minZ, maxX, top, maxZ, city.wall()));
		fills.add(new Fill(minX + 1, baseY, minZ + 1, maxX - 1, top - 1, maxZ - 1,
				Blocks.AIR.defaultBlockState()));

		// Vetrate alte due piani su tutti i lati: da fuori si vede che è un posto pubblico.
		fills.add(new Fill(minX, baseY + 1, minZ + 2, minX, baseY + 6, maxZ - 2, city.glass()));
		fills.add(new Fill(maxX, baseY + 1, minZ + 2, maxX, baseY + 6, maxZ - 2, city.glass()));
		fills.add(new Fill(minX + 2, baseY + 1, minZ, maxX - 2, baseY + 6, minZ, city.glass()));
		fills.add(new Fill(minX + 2, baseY + 1, maxZ, maxX - 2, baseY + 6, maxZ, city.glass()));

		fills.add(new Fill(minX, top, minZ, maxX, top, maxZ, city.roof()));
		fills.add(new Fill(minX - 1, top + 1, minZ - 1, maxX + 1, top + 1, maxZ + 1, city.accent()));
		fills.add(new Fill(minX, top + 1, minZ, maxX, top + 1, maxZ, Blocks.AIR.defaultBlockState()));

		// Ingresso a sud, largo cinque: ci si entra senza cercare la porta.
		fills.add(new Fill(centreX - 2, baseY, maxZ, centreX + 2, baseY + 3, maxZ,
				Blocks.AIR.defaultBlockState()));

		fills.add(new Fill(minX + 1, baseY, minZ + 1, maxX - 1, baseY, maxZ - 1, city.sidewalk()));
		fills.add(new Fill(minX + 2, top - 1, minZ + 2, maxX - 2, top - 1, maxZ - 2,
				Blocks.SEA_LANTERN.defaultBlockState()));

		// Quattro colonne, e in mezzo il piedistallo del terminale.
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				fills.add(new Fill(centreX + dx * 6, baseY, centreZ + dz * 6,
						centreX + dx * 6, top - 1, centreZ + dz * 6, city.accent()));
			}
		}

		fills.add(new Fill(centreX - 2, baseY, centreZ - 2, centreX + 2, baseY, centreZ + 2, city.accent()));
		fills.add(new Fill(centreX, baseY, centreZ, centreX, baseY, centreZ, marker()));
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
}
