package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luca.arise.map.MapProjection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La geometria della mappa del mondo.
 *
 * <p>È aritmetica pura, e la cosa che la mappa deve fare bene è cambiare scala di quattro ordini
 * di grandezza — dai cento blocchi di un varco ai duecentomila di una città — senza perdere il
 * punto sotto il mouse e senza mandare una città fuori dal bordo. Si prova qui, con due numeri,
 * invece che a occhio in gioco.
 */
class MapProjectionTest {

	private static final double EPS = 1e-6;

	@Test
	@DisplayName("avanti e indietro: un punto del mondo torna dov'era")
	void roundTrip() {
		MapProjection map = new MapProjection(300, 200, 1000.0, -500.0, 4.0);

		double sx = map.toScreenX(1234.5);
		double sy = map.toScreenY(-432.25);

		assertEquals(1234.5, map.toWorldX(sx), EPS);
		assertEquals(-432.25, map.toWorldZ(sy), EPS);

		// Il centro del mondo inquadrato sta al centro della finestra.
		assertEquals(150.0, map.toScreenX(1000.0), EPS);
		assertEquals(100.0, map.toScreenY(-500.0), EPS);
	}

	@Test
	@DisplayName("lo zoom tiene fermo il punto sotto il cursore")
	void zoomKeepsAnchor() {
		MapProjection map = new MapProjection(300, 200, 0.0, 0.0, 2.0);
		double anchorX = map.toWorldX(40.0);
		double anchorZ = map.toWorldZ(170.0);

		map.zoomAt(40.0, 170.0, 1.25);
		assertEquals(2.5, map.scale(), EPS);
		assertEquals(anchorX, map.toWorldX(40.0), EPS);
		assertEquals(anchorZ, map.toWorldZ(170.0), EPS);

		map.zoomAt(40.0, 170.0, 1.0 / 1.25);
		assertEquals(2.0, map.scale(), EPS);
		assertEquals(anchorX, map.toWorldX(40.0), EPS);
		assertEquals(anchorZ, map.toWorldZ(170.0), EPS);
	}

	@Test
	@DisplayName("la scala resta fra i suoi limiti")
	void scaleIsClamped() {
		MapProjection map = new MapProjection(300, 200, 0.0, 0.0, 1.0);

		for (int i = 0; i < 200; i++) {
			map.zoomAt(150, 100, 0.5);
		}
		assertEquals(MapProjection.MIN_SCALE, map.scale(), EPS);

		for (int i = 0; i < 200; i++) {
			map.zoomAt(150, 100, 2.0);
		}
		assertEquals(MapProjection.MAX_SCALE, map.scale(), EPS);
	}

	@Test
	@DisplayName("trascinare sposta il mondo sotto il dito, non al contrario")
	void panFollowsTheFinger() {
		MapProjection map = new MapProjection(300, 200, 0.0, 0.0, 2.0);
		double worldUnderFinger = map.toWorldX(100.0);

		// Il dito va a destra di 30 pixel: il punto che stava sotto deve stare a 130.
		map.pan(30.0, 0.0);
		assertEquals(worldUnderFinger, map.toWorldX(130.0), EPS);
	}

	@Test
	@DisplayName("inquadrare le città e il giocatore li mette tutti dentro")
	void fitCoversEverything() {
		MapProjection map = new MapProjection(380, 220, 0.0, 0.0, 2.0);

		// Il giocatore allo spawn, le cinque città a duecentomila blocchi in fila.
		double minX = 0.0;
		double maxX = 200000.0 + 4 * 15000.0 + 256.0;
		double minZ = 0.0;
		double maxZ = 200000.0 + 256.0;

		map.fit(minX, minZ, maxX, maxZ, 30);

		assertTrue(map.onScreen(map.toScreenX(minX), map.toScreenY(minZ), 29));
		assertTrue(map.onScreen(map.toScreenX(maxX), map.toScreenY(maxZ), 29));
		assertTrue(map.onScreen(map.toScreenX(maxX), map.toScreenY(minZ), 29));
		assertEquals((minX + maxX) / 2.0, map.centreX(), EPS);
	}

	@Test
	@DisplayName("inquadrare un punto solo non divide per zero")
	void fitDegenerate() {
		MapProjection map = new MapProjection(300, 200, 0.0, 0.0, 64.0);
		map.fit(10.0, 20.0, 10.0, 20.0, 30);

		assertEquals(MapProjection.MIN_SCALE, map.scale(), EPS);
		assertEquals(10.0, map.centreX(), EPS);
		assertEquals(20.0, map.centreZ(), EPS);
	}

	@Test
	@DisplayName("il reticolo si infittisce avvicinandosi e si dirada allontanandosi")
	void gridStepFollowsScale() {
		MapProjection near = new MapProjection(300, 200, 0.0, 0.0, 0.5);
		MapProjection far = new MapProjection(300, 200, 0.0, 0.0, 1000.0);

		assertEquals(32, near.gridStep(), "a mezzo blocco per pixel 32 blocchi sono 64 pixel");
		assertEquals(65536, far.gridStep(), "a mille blocchi per pixel servono 65536 blocchi per 65 pixel");
		assertTrue(near.gridStep() / near.scale() >= 40);
		assertTrue(far.gridStep() / far.scale() >= 40);
	}

	@Test
	@DisplayName("un segno fuori dalla finestra finisce sul bordo, nella direzione giusta")
	void clampToEdge() {
		MapProjection map = new MapProjection(300, 200, 0.0, 0.0, 2.0);

		// Una citta' lontanissima a est e un po' a sud: deve stare sul bordo destro.
		double[] east = map.clampToEdge(map.toScreenX(200000.0), map.toScreenY(20000.0), 6);
		assertEquals(294.0, east[0], EPS);
		assertTrue(east[1] > 100.0 && east[1] < 194.0, "a sud del centro, ma dentro il margine");

		// A nord, in verticale: sul bordo in alto, al centro.
		double[] north = map.clampToEdge(map.toScreenX(0.0), map.toScreenY(-100000.0), 6);
		assertEquals(150.0, north[0], EPS);
		assertEquals(6.0, north[1], EPS);

		// Un punto gia' dentro non si muove.
		double[] inside = map.clampToEdge(120.0, 80.0, 6);
		assertEquals(120.0, inside[0], EPS);
		assertEquals(80.0, inside[1], EPS);
		assertFalse(map.onScreen(map.toScreenX(200000.0), 100.0, 6));
	}

	@Test
	@DisplayName("la distanza sul piano")
	void distance() {
		assertEquals(5.0, MapProjection.distance(0, 0, 3, 4), EPS);
		assertEquals(0.0, MapProjection.distance(7, -2, 7, -2), EPS);
	}
}
