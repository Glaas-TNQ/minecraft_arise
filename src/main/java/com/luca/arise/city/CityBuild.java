package com.luca.arise.city;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.luca.arise.city.CityPlan.Fill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * La costruzione di una città in corso: un po' per battito del server, finché non è finita.
 *
 * <p>Una città è decine di milioni di blocchi, e sta a duecentomila blocchi dallo spawn — dove il
 * mondo <em>non esiste ancora</em>. Da questa seconda cosa discende tutto il resto: il costo vero
 * non sono i blocchi, sono i <strong>chunk</strong>. Un {@code setBlock} che cade in un chunk mai
 * visto lo fa generare da zero — rumore, superficie, caverne, strutture — e quello costa fra un
 * decimo e mezzo secondo. Contro un battito che ne dura cinquanta millesimi.
 *
 * <p>Tre regole tengono in piedi il server, e ognuna e' stata pagata con un difetto vero:
 *
 * <ul>
 *   <li><strong>i chunk si generano fuori dal thread del server.</strong> Quando il cursore arriva
 *       in un chunk che non c'e', non lo si pretende: lo si <em>chiede</em> con un ticket e si
 *       lascia perdere il battito. Il generatore lavora sul suo pool, e al battito dopo il chunk
 *       e' li' pronto da riempire. Senza questo il server restava venti-trenta secondi indietro
 *       per tutti i primi minuti di un mondo nuovo;
 *   <li><strong>si lavora un chunk per volta.</strong> Il cursore percorre il volume a blocchi di
 *       sedici per sedici, non a colonne. Percorrere per colonne — com'era prima — attraversa
 *       trentadue chunk per passata, e un solo battito ne toccava quasi duecento: sessanta secondi
 *       in un tick, e il watchdog spegneva il server dichiarandolo bloccato;
 *   <li><strong>c'e' una scadenza, non solo un budget.</strong> Il conteggio dei blocchi non dice
 *       niente sul tempo. Si guarda l'orologio, e appena la fetta e' finita si smette.
 * </ul>
 *
 * <p>Il prezzo è che la città cresce sotto gli occhi invece di apparire — che poi è anche meglio.
 */
public final class CityBuild {

	/** Come nei Gate: nessun aggiornamento ai vicini, o il costo si moltiplica. */
	private static final int FLAGS = 2;

	/**
	 * Il ticket con cui si tiene fermo un chunk mentre lo si riempie.
	 *
	 * <p>Ognuna delle tre scelte e' stata pagata da un errore vero:
	 * <ul>
	 *   <li><strong>nessuna scadenza</strong>, perche' un ticket che puo' scadere prima di aver
	 *       finito di caricare viene <em>rifiutato</em>: {@code addTicketAndLoadWithRadius} lancia
	 *       "can expire before it loads, cannot fetch asynchronously". E' esattamente cio' che
	 *       succedeva usando {@code TicketType.UNKNOWN};
	 *   <li><strong>carica ma non simula</strong>: qui si sta costruendo, non giocando. Un cantiere
	 *       che facesse girare mob, redstone e crescita delle piante costerebbe quanto il lavoro;
	 *   <li><strong>non persistente</strong>: se il server cade a meta' costruzione, i ticket
	 *       muoiono con lui. Uno persistente lascerebbe mille chunk bloccati per sempre in un mondo
	 *       che nessuno sa piu' come sbloccare.
	 * </ul>
	 */
	private static final TicketType BUILDING =
			new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

	/**
	 * Ogni quanti blocchi si guarda l'orologio.
	 *
	 * <p>{@code nanoTime} costa poco ma non zero, e chiamarlo per ogni blocco su decine di milioni
	 * di blocchi sarebbe una tassa inutile. Mille {@code setBlock} dentro un chunk già caricato
	 * sono meno di un millesimo di secondo, quindi il controllo resta abbastanza fitto.
	 */
	private static final int CLOCK_INTERVAL = 1024;

	/**
	 * Dopo quanti battiti d'attesa si smette di aspettare un chunk e lo si pretende.
	 *
	 * <p>È una rete di sicurezza, non un'ottimizzazione: se una richiesta andasse persa — un
	 * generatore di un'altra mod, un errore nel pool — senza questo la città resterebbe ferma per
	 * sempre a un chunk dalla fine, e nessuno saprebbe perché. Duecento battiti sono dieci secondi.
	 */
	private static final int PATIENCE = 200;

	/**
	 * Quanti chunk si chiedono in anticipo.
	 *
	 * <p>E' la voce che decide se questa classe e' lenta o veloce, e la prima versione non ce
	 * l'aveva: chiedendo un chunk per volta si aspettava il generatore a ogni chunk, cioe' uno per
	 * battito, venti al secondo. Una citta' ne vuole piu' di mille solo per il basamento.
	 *
	 * <p>Chiedendone ventiquattro alla volta il pool lavora in parallelo mentre il thread del
	 * server riempie quelli gia' pronti, e la coda si svuota alla velocita' del generatore invece
	 * che a quella dei battiti. Ventiquattro chunk in attesa sono pochi megabyte.
	 */
	private static final int PREFETCH = 24;

	private final ServerLevel level;
	private final City city;
	private final List<Fill> fills;
	private final long total;
	private final int baseY;

	/** I chunk già richiesti al generatore, per non chiederli due volte. */
	private final Set<Long> requested = new HashSet<>();

	/** Quale volume si sta riempiendo. */
	private int index;

	/** Se il cursore è già posizionato dentro il volume corrente. */
	private boolean cursorReady;

	/** Il chunk in lavorazione, in coordinate di chunk. */
	private int chunkX;
	private int chunkZ;

	/** La posizione dentro la porzione di volume che cade in questo chunk. */
	private int x;
	private int y;
	private int z;

	/** Da quanti battiti si sta aspettando lo stesso chunk. */
	private int waiting;

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
	 * Costruisce finché non finisce il budget, non scade il tempo, o non manca un chunk.
	 *
	 * @param budget       quanti blocchi al massimo piazzare in questo battito
	 * @param deadlineNano il momento, in {@link System#nanoTime()}, oltre il quale ci si ferma
	 * @return vero quando non è rimasto più niente da costruire
	 */
	public boolean advance(int budget, long deadlineNano) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int remaining = budget;
		int untilClock = CLOCK_INTERVAL;

		while (remaining > 0 && index < fills.size()) {
			Fill fill = fills.get(index);

			if (!cursorReady) {
				chunkX = fill.minX() >> 4;
				chunkZ = fill.minZ() >> 4;
				enterChunk(fill);
				cursorReady = true;
			}

			// Il chunk c'è? Se no si chiede — insieme a quelli che verranno dopo — e si lascia
			// perdere il battito: generarlo qui vorrebbe dire fermare il server per mezzo secondo,
			// e nessun budget può impedirlo.
			if (!ready(fill)) {
				return false;
			}

			int x1 = Math.min(fill.maxX(), (chunkX << 4) + 15);
			int z1 = Math.min(fill.maxZ(), (chunkZ << 4) + 15);

			cursor.set(x, y, z);
			level.setBlock(cursor, fill.state(), FLAGS);
			remaining--;
			placed++;

			// Dentro il chunk si sale per colonne: l'altezza è l'asse più corto, e finirla tutta
			// prima di spostarsi tiene il lavoro dentro la stessa sezione verticale.
			if (++y > fill.maxY()) {
				y = fill.minY();

				if (++z > z1) {
					z = Math.max(fill.minZ(), chunkZ << 4);

					if (++x > x1) {
						release(chunkX, chunkZ);
						nextChunk(fill);
					}
				}
			}

			if (--untilClock <= 0) {
				untilClock = CLOCK_INTERVAL;

				if (System.nanoTime() >= deadlineNano) {
					break;
				}
			}
		}

		return done();
	}

	// ---------------------------------------------------------------- i chunk

	/**
	 * Il chunk corrente è pronto da riempire?
	 *
	 * <p>Se non c'è lo si chiede una volta sola e si risponde di no. Il battito finisce lì: il
	 * generatore lavora per conto suo e al prossimo giro, quasi sempre, il chunk c'è.
	 */
	private boolean ready(Fill fill) {
		if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
			waiting = 0;
			return true;
		}

		// La pazienza finita: si genera qui, sul thread del server, e si accetta lo scatto. È
		// preferibile a una città che non finisce mai.
		if (++waiting > PATIENCE) {
			waiting = 0;
			return true;
		}

		prefetch(fill);
		return false;
	}

	/**
	 * Chiede al generatore il chunk corrente e i successivi della fila.
	 *
	 * <p>Percorre la griglia dei chunk in avanti nello stesso ordine in cui la percorrera' il
	 * cursore, cosi' quando ci arrivera' li trovera' gia' fatti. Si ferma al bordo del volume: oltre
	 * non si sa ancora dove andra' il cursore, e chiedere chunk che nessuno riempira' e' lavoro
	 * regalato al generatore.
	 */
	private void prefetch(Fill fill) {
		int lastChunkX = fill.maxX() >> 4;
		int lastChunkZ = fill.maxZ() >> 4;
		int firstChunkZ = fill.minZ() >> 4;

		int cx = chunkX;
		int cz = chunkZ;

		for (int i = 0; i < PREFETCH; i++) {
			if (requested.add(key(cx, cz))) {
				level.getChunkSource().addTicketAndLoadWithRadius(
						BUILDING, new ChunkPos(cx, cz), 0);
			}

			if (++cz > lastChunkZ) {
				cz = firstChunkZ;

				if (++cx > lastChunkX) {
					return;
				}
			}
		}
	}

	/**
	 * Lascia andare un chunk finito.
	 *
	 * <p>Senza, i mille e passa chunk di una città resterebbero tutti caricati fino alla fine della
	 * costruzione: un modo elegante di far finire la memoria proprio mentre si sta lavorando.
	 */
	private void release(int cx, int cz) {
		if (requested.remove(key(cx, cz))) {
			level.getChunkSource().removeTicketWithRadius(
					BUILDING, new ChunkPos(cx, cz), 0);
		}
	}

	/**
	 * Le due coordinate di un chunk impacchettate in un {@code long}.
	 *
	 * <p>Scritta a mano perche' {@code ChunkPos.asLong(int, int)} non esiste piu' in 26.2, e un
	 * {@code Set} di oggetti {@code ChunkPos} vorrebbe dire allocarne uno a ogni controllo.
	 */
	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	/** Lascia andare tutti i chunk ancora trattenuti. */
	private void releaseAll() {
		for (long held : requested) {
			level.getChunkSource().removeTicketWithRadius(
					BUILDING, new ChunkPos((int) (held >> 32), (int) held), 0);
		}

		requested.clear();
	}

	/** Posiziona il cursore all'angolo della porzione di volume che cade nel chunk corrente. */
	private void enterChunk(Fill fill) {
		x = Math.max(fill.minX(), chunkX << 4);
		z = Math.max(fill.minZ(), chunkZ << 4);
		y = fill.minY();
		waiting = 0;
	}

	/**
	 * Passa al chunk successivo del volume, o al volume successivo se erano finiti.
	 *
	 * <p>Si percorre per righe di chunk, come si legge una pagina: tutta la fila lungo Z, poi si
	 * scende di uno lungo X. Due chunk consecutivi restano vicini, e quello appena generato resta
	 * caldo per il volume successivo che ci passerà sopra.
	 */
	private void nextChunk(Fill fill) {
		int lastChunkX = fill.maxX() >> 4;
		int lastChunkZ = fill.maxZ() >> 4;

		if (++chunkZ > lastChunkZ) {
			chunkZ = fill.minZ() >> 4;

			if (++chunkX > lastChunkX) {
				// Il volume e' finito: i chunk chiesti in anticipo che il cursore non ha mai
				// raggiunto vanno liberati adesso, o su ventottomila volumi diventano un mondo
				// intero tenuto in memoria.
				releaseAll();

				index++;
				cursorReady = false;
				return;
			}
		}

		enterChunk(fill);
	}
}
