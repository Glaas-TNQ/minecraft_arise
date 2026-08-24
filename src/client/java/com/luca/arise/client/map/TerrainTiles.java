package com.luca.arise.client.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.luca.arise.map.MapTiles;
import com.luca.arise.network.MapTileRequestPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * I riquadri di terreno che il client ha gia' ricevuto, e quelli che ha gia' chiesto.
 *
 * <p>Due insiemi e nient'altro, ma il secondo e' quello che conta. Senza l'insieme delle richieste
 * in volo, ogni fotogramma della mappa rifarebbe la stessa domanda: sessanta volte al secondo, per
 * ognuno dei venti riquadri che mancano, finche' non arriva la risposta. Sarebbero milleduecento
 * pacchetti al secondo per disegnare un prato.
 *
 * <h2>Perche' e' statica, e perche' non si svuota da sola</h2>
 *
 * <p>Vive fuori dalla schermata: chiudere la mappa e riaprirla non deve voler dire riscaricare il
 * terreno che si stava guardando un secondo fa. Si svuota quando si esce dal mondo — un altro mondo
 * e' un altro terreno, e un riquadro tenuto attraverso quel confine mostrerebbe il posto sbagliato
 * senza dirlo.
 */
public final class TerrainTiles {

	/**
	 * Quanti riquadri si tengono qui.
	 *
	 * <p>Meno che sul server, e va bene: il client ne guarda una schermata per volta, e sedici
	 * kilobyte l'uno su un processo che ha gia' il mondo in memoria non e' il posto dove essere
	 * generosi.
	 */
	private static final int CAPACITY = 384;

	/** Ordine di accesso: cosi' cio' che si e' guardato per ultimo e' l'ultimo a essere buttato. */
	private static final Map<Long, int[]> TILES = new LinkedHashMap<>(128, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
			return size() > CAPACITY;
		}
	};

	private static final Set<Long> ASKED = new HashSet<>();

	/**
	 * Quante volte e' arrivato un riquadro.
	 *
	 * <p>Serve alla tela per sapere che c'e' qualcosa di nuovo da dipingere. Confrontare il numero
	 * di riquadri non basterebbe: la cache ha un tetto, quindi arrivarne uno nuovo mentre se ne
	 * butta uno vecchio lascia il conto identico — e la mappa resterebbe con il buco.
	 */
	private static int version;

	private TerrainTiles() {
	}

	/** I colori del riquadro, o {@code null} se non e' ancora arrivato. */
	public static int[] get(int lod, int tileX, int tileZ) {
		return TILES.get(MapTiles.key(lod, tileX, tileZ));
	}

	/** Un riquadro e' arrivato. */
	public static void put(int lod, int tileX, int tileZ, int[] colours) {
		long key = MapTiles.key(lod, tileX, tileZ);
		TILES.put(key, colours);
		ASKED.remove(key);
		version++;
	}

	/** Cambia ogni volta che arriva un riquadro. Vedi {@code MapCanvas}. */
	public static int version() {
		return version;
	}

	/**
	 * Chiede i riquadri che mancano, saltando quelli gia' chiesti.
	 *
	 * <p>Ne parte al massimo una manciata per volta ({@link MapTileRequestPayload#MAX_TILES}): il
	 * server ne dipinge due alla volta, quindi chiederne quaranta insieme non li farebbe arrivare
	 * prima — farebbe solo arrivare per ultimi quelli sotto il mouse, perche' la coda non ha nessun
	 * modo di sapere che nel frattempo la mappa si e' spostata.
	 *
	 * @param wanted gli indici dei riquadri, a coppie {@code x, z}, in ordine di importanza
	 * @return quanti ne sono stati chiesti
	 */
	public static int askFor(int lod, List<Integer> wanted) {
		List<Integer> batch = new ArrayList<>();

		for (int i = 0; i + 1 < wanted.size() && batch.size() < MapTileRequestPayload.MAX_TILES * 2; i += 2) {
			int tileX = wanted.get(i);
			int tileZ = wanted.get(i + 1);
			long key = MapTiles.key(lod, tileX, tileZ);

			if (TILES.containsKey(key) || !ASKED.add(key)) {
				continue;
			}

			batch.add(tileX);
			batch.add(tileZ);
		}

		if (batch.isEmpty()) {
			return 0;
		}

		ClientPlayNetworking.send(new MapTileRequestPayload(lod, batch));
		return batch.size() / 2;
	}

	/**
	 * Dimentica le richieste in volo.
	 *
	 * <p>Non i riquadri: quelli restano buoni. Serve quando si riapre la mappa dopo che una risposta
	 * si e' persa — un riquadro chiesto e mai arrivato resterebbe marcato «gia' chiesto» per sempre,
	 * e sarebbe un buco nero permanente nella mappa che nessun gesto sa riempire.
	 */
	public static void retryPending() {
		ASKED.clear();
	}

	/** Si esce dal mondo: un altro mondo e' un altro terreno. */
	public static void clear() {
		TILES.clear();
		ASKED.clear();
	}
}
