package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.luca.arise.city.City;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.map.MapProjection;
import com.luca.arise.map.MapTiles;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La geometria della mappa: i riquadri di terreno e la costellazione delle citta'.
 *
 * <p>I riquadri vanno provati qui perche' la stessa aritmetica la fanno <strong>due macchine
 * diverse</strong>: il server, che decide quale pezzo di mondo dipingere, e il client, che decide
 * quali pezzi chiedere e dove metterli. Se le due risposte divergessero anche di un riquadro,
 * nessuno riceverebbe un errore: si vedrebbe una mappa con dei buchi che si spostano quando ci si
 * muove, e sarebbe impossibile capire da dove viene.
 *
 * <p>Le citta' vanno provate perche' il difetto che l'anello corregge era invisibile finche' non
 * c'e' stata una mappa da guardare — cinque punti in fila. Una prova che dica «non sono in fila» e'
 * l'unica cosa che impedisce a un giorno di rifattorizzazione di rimetterle li'.
 */
class MapTilesTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	// ---------------------------------------------------------------- i riquadri

	@Test
	@DisplayName("il passo quadruplica a ogni livello, e il riquadro con lui")
	void stepsQuadruple() {
		assertEquals(4, MapTiles.step(0));
		assertEquals(16, MapTiles.step(1));
		assertEquals(64, MapTiles.step(2));
		assertEquals(256, MapTiles.step(3));
		assertEquals(1024, MapTiles.step(4));

		for (int lod = 0; lod <= MapTiles.MAX_LOD; lod++) {
			assertEquals(MapTiles.TILE * MapTiles.step(lod), MapTiles.span(lod));
		}
	}

	@Test
	@DisplayName("un livello fuori scala non fa esplodere niente")
	void lodIsClamped() {
		assertEquals(MapTiles.step(0), MapTiles.step(-3));
		assertEquals(MapTiles.step(MapTiles.MAX_LOD), MapTiles.step(99));
	}

	@Test
	@DisplayName("il livello scelto ha sempre un campione grande almeno un pixel")
	void lodCoversAPixel() {
		for (double scale = MapProjection.MIN_SCALE; scale <= 1024.0; scale *= 1.37) {
			int lod = MapTiles.lodFor(scale);

			assertTrue(lod >= 0, "nessun livello per la scala " + scale);
			assertTrue(MapTiles.step(lod) >= scale,
					"alla scala " + scale + " il livello " + lod + " e' troppo fine");

			// E non troppo grossolano: il livello sotto non doveva bastare.
			if (lod > 0) {
				assertTrue(MapTiles.step(lod - 1) < scale,
						"alla scala " + scale + " bastava il livello " + (lod - 1));
			}
		}
	}

	@Test
	@DisplayName("oltre l'ultimo livello il terreno si dichiara non disegnabile")
	void beyondTheLastLevel() {
		assertEquals(MapTiles.MAX_LOD, MapTiles.lodFor(MapTiles.step(MapTiles.MAX_LOD)));
		assertEquals(-1, MapTiles.lodFor(MapTiles.step(MapTiles.MAX_LOD) + 1));
		assertEquals(-1, MapTiles.lodFor(MapProjection.MAX_SCALE));
	}

	@Test
	@DisplayName("ogni coordinata cade in un riquadro, anche in negativo")
	void tilesCoverEverything() {
		for (int lod = 0; lod <= MapTiles.MAX_LOD; lod++) {
			int span = MapTiles.span(lod);

			for (int world : new int[] {-3_000_000, -span - 1, -span, -1, 0, 1, span, 200_000}) {
				int tile = MapTiles.tileOf(world, lod);
				int origin = MapTiles.originOf(tile, lod);

				assertTrue(origin <= world && world < origin + span,
						"il blocco " + world + " non sta nel riquadro " + tile
								+ " (livello " + lod + ")");
			}
		}
	}

	@Test
	@DisplayName("due riquadri diversi non hanno mai la stessa chiave")
	void keysDoNotCollide() {
		Set<Long> keys = new HashSet<>();

		for (int lod = 0; lod <= MapTiles.MAX_LOD; lod++) {
			for (int x = -40; x <= 40; x += 7) {
				for (int z = -40; z <= 40; z += 7) {
					assertTrue(keys.add(MapTiles.key(lod, x, z)),
							"chiave ripetuta per " + lod + " " + x + " " + z);
				}
			}
		}

		// Il caso che una chiave scritta male sbaglierebbe di sicuro: stessi indici, segno opposto.
		assertNotEquals(MapTiles.key(0, 1, -1), MapTiles.key(0, -1, 1));
	}

	// ---------------------------------------------------------------- le citta'

	@Test
	@DisplayName("le cinque citta' non stanno piu' in fila")
	void citiesAreNotInARow() {
		CityConfig config = AriseConfig.createDefault().cities();

		Set<Integer> xs = new HashSet<>();
		Set<Integer> zs = new HashSet<>();

		for (City city : City.values()) {
			xs.add(config.centreX(city));
			zs.add(config.centreZ(city));
		}

		// Il difetto vero era questo: cinque Z identiche. Bastava guardare la mappa per vederlo, e
		// non bastava leggere il codice — la riga diceva `return originZ` e sembrava intenzionale.
		assertTrue(zs.size() >= 4, "le citta' condividono la stessa Z: " + zs);
		assertTrue(xs.size() >= 4, "le citta' condividono la stessa X: " + xs);
	}

	@Test
	@DisplayName("stanno tutte sull'anello, alla distanza che dice la config")
	void citiesSitOnTheRing() {
		CityConfig config = AriseConfig.createDefault().cities();

		for (City city : City.values()) {
			double distance = MapProjection.distance(config.originX(), config.originZ(),
					config.centreX(city), config.centreZ(city));

			assertEquals(config.ringRadius(), distance, 1.5,
					city.getSerializedName() + " non sta sull'anello");
		}
	}

	@Test
	@DisplayName("nessuna citta' cade esattamente su un asse della costellazione")
	void noCityOnAnAxis() {
		CityConfig config = AriseConfig.createDefault().cities();

		for (City city : City.values()) {
			assertNotEquals(config.originX(), config.centreX(city),
					city.getSerializedName() + " e' allineata in verticale col centro");
			assertNotEquals(config.originZ(), config.centreZ(city),
					city.getSerializedName() + " e' allineata in orizzontale col centro");
		}
	}

	@Test
	@DisplayName("l'angolo nord-ovest resta mezzo lato prima del centro")
	void cornerMatchesCentre() {
		CityConfig config = AriseConfig.createDefault().cities();

		for (City city : City.values()) {
			assertEquals(config.centreX(city) - config.size() / 2, config.cityX(city));
			assertEquals(config.centreZ(city) - config.size() / 2, config.cityZ(city));
		}
	}

	@Test
	@DisplayName("due citta' non si sovrappongono nemmeno di un blocco")
	void citiesDoNotOverlap() {
		CityConfig config = AriseConfig.createDefault().cities();

		for (City one : City.values()) {
			for (City other : City.values()) {
				if (one == other) {
					continue;
				}

				double distance = MapProjection.distance(config.centreX(one), config.centreZ(one),
						config.centreX(other), config.centreZ(other));

				assertTrue(distance > config.size() * 2.0,
						one.getSerializedName() + " e " + other.getSerializedName()
								+ " sono a " + (int) distance + " blocchi");
			}
		}
	}
}
