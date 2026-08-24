package com.luca.arise.map;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.luca.arise.AriseMod;
import com.luca.arise.network.MapTilePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Il terreno della mappa, dipinto interrogando il rumore invece del mondo.
 *
 * <h2>Il problema</h2>
 *
 * <p>La mappa di questa mod ha sempre disegnato un reticolo e quattro punti, e c'era scritto nel
 * commento che era voluto — «non disegna il terreno, sarebbe un'altra mod». Era una scelta
 * sbagliata: un rettangolo nero con sopra dei segni non e' una mappa, e' una legenda senza la
 * mappa. Quello che mancava non erano i segni, era il <em>sotto</em>.
 *
 * <p>Il motivo per cui non c'era e' vero, e resta vero: leggere il terreno vuol dire leggere i
 * chunk, e le citta' stanno a duecentomila blocchi da chiunque. Chiedere il terreno attorno a una
 * citta' obbligherebbe il server a generare qualche migliaio di chunk che nessuno ha chiesto — che
 * e' esattamente la cosa che il collaudo del cantiere ha insegnato a non fare mai.
 *
 * <h2>La via d'uscita, e quella sbagliata provata prima</h2>
 *
 * <p>La prima versione chiedeva le quote al generatore, con
 * {@code ChunkGenerator.getBaseHeight}. Sulla carta era la risposta giusta — interroga il rumore,
 * non tocca un chunk — e infatti nel commento c'era scritto che costava «un battito di ciglia».
 * Misurandola sono venuti fuori <strong>sette secondi e mezzo per riquadro</strong>: sette
 * millesimi per colonna, perche' quel metodo costruisce una colonna di rumore intera, con falde e
 * interpolazione, per restituire un numero. E' fatto per piazzare una struttura ogni tanto, non per
 * campionare a migliaia. Il primo comando di prova ha fatto scattare il watchdog del server.
 *
 * <p>La strada buona e' un piano piu' sotto: {@code Climate.Sampler.sample}, cioe' i sei rumori del
 * clima — temperatura, umidita', continentalita', erosione, profondita', stranezza. Sono gli stessi
 * numeri con cui il gioco decide quale bioma mettere dove, si leggono in una chiamata, e da soli
 * bastano a disegnare una mappa:
 *
 * <ul>
 *   <li>il <strong>bioma</strong> viene dai sei valori senza doverli ricampionare
 *       ({@code MultiNoiseBiomeSource.getNoiseBiome(TargetPoint)}), e da' la tinta;
 *   <li>la <strong>continentalita'</strong> dice quanto e' fondo il mare;
 *   <li>la <strong>profondita'</strong>, letta a quota fissa, cresce dove il terreno e' piu' alto:
 *       non e' una quota in blocchi, ma il suo <em>dislivello</em> fra due campioni vicini e'
 *       esattamente cio' che serve per ombreggiare un rilievo.
 * </ul>
 *
 * <p>Nessuno di questi tocca un chunk, nessuno scrive niente, e tutti sono gia' chiamati da piu'
 * thread insieme dalla generazione vera — quindi si possono chiamare da un thread nostro.
 *
 * <p>Le due regole che restano:
 *
 * <ol>
 *   <li><strong>Fuori dal thread del server.</strong> Anche a rumore economico, un riquadro sono
 *       quattromila campioni: sul thread principale si vedrebbe.
 *   <li><strong>Si tiene quello che si e' calcolato.</strong> Il rumore di un mondo non cambia mai:
 *       un riquadro calcolato una volta vale per tutta la vita del server, e il piu' delle volte la
 *       mappa si riapre sullo stesso posto.
 * </ol>
 */
public final class TerrainAtlas {

	/**
	 * Quanti riquadri si tengono in memoria.
	 *
	 * <p>Ognuno e' {@code 64 x 64} interi, cioe' sedici kilobyte: mille e ventiquattro riquadri sono
	 * sedici megabyte, che per un server e' poco e per una mappa e' tantissimo — a livello zero sono
	 * sessantasette milioni di metri quadrati di terreno gia' pronto.
	 */
	private static final int CACHE_SIZE = 1024;

	/**
	 * Quanti riquadri si dipingono insieme.
	 *
	 * <p>Due, e non e' un numero da spremere: questi thread rubano tempo agli stessi core su cui
	 * gira il server, e una mappa che si riempie in tre secondi invece che in cinque non vale un
	 * battito che salta.
	 */
	private static final int WORKERS = 2;

	private static final Map<Long, int[]> CACHE = Collections.synchronizedMap(
			new LinkedHashMap<Long, int[]>(256, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
					return size() > CACHE_SIZE;
				}
			});

	/** I riquadri gia' in lavorazione: senza, dieci richieste dello stesso sono dieci calcoli. */
	private static final Set<Long> WORKING = ConcurrentHashMap.newKeySet();

	private static ExecutorService pool;

	/** Finche' e' falso non si e' ancora misurato quanto costa un riquadro: si scrive una riga sola. */
	private static volatile boolean timed;

	/** Il grigio verso cui vira una montagna, e la sabbia verso cui vira una spiaggia. */
	private static final int ROCK = 0x8A8A8A;
	private static final int SAND = 0xDCD3A0;

	/** Il blu del mare aperto: il colore del bioma vira qui man mano che il fondale scende. */
	private static final int DEEP_WATER = 0x081830;

	private TerrainAtlas() {
	}

	// ---------------------------------------------------------------- richiesta

	/**
	 * Manda al giocatore il riquadro chiesto, dipingendolo se non c'e'.
	 *
	 * <p>Se e' gia' in cache parte subito, sullo stesso battito. Se no la richiesta finisce in coda
	 * e la risposta arriva quando arriva: il client lascia il buco vuoto e lo riempie quando lo
	 * riceve, che e' l'unico comportamento onesto per una mappa che si costruisce mentre la guardi.
	 */
	public static void request(ServerPlayer player, int lod, int tileX, int tileZ) {
		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return;
		}

		ServerLevel overworld = server.overworld();
		long key = MapTiles.key(lod, tileX, tileZ);
		int[] cached = CACHE.get(key);

		if (cached != null) {
			ServerPlayNetworking.send(player, MapTilePayload.of(lod, tileX, tileZ, cached));
			return;
		}

		if (!WORKING.add(key)) {
			return;
		}

		UUID who = player.getUUID();

		pool().execute(() -> {
			int[] colours;

			try {
				colours = paint(overworld, lod, tileX, tileZ);
			} catch (RuntimeException failure) {
				AriseMod.LOGGER.warn("Riquadro di mappa non dipingibile ({} {} {}): {}",
						lod, tileX, tileZ, failure.toString());
				WORKING.remove(key);
				return;
			}

			CACHE.put(key, colours);
			WORKING.remove(key);

			// Il pacchetto si manda dal thread del server e non da qui: la rete non e' nostra, e
			// scriverci da un thread qualunque e' il genere di cosa che funziona novantanove volte
			// e la centesima chiude la connessione.
			server.execute(() -> {
				ServerPlayer target = server.getPlayerList().getPlayer(who);

				if (target != null) {
					ServerPlayNetworking.send(target, MapTilePayload.of(lod, tileX, tileZ, colours));
				}
			});
		});
	}

	// ---------------------------------------------------------------- il calcolo

	/**
	 * Dipinge un riquadro. Gira su un thread nostro, e non tocca niente del mondo.
	 *
	 * <p>Un campione, una chiamata: {@code sampler.sample} da' i sei rumori del clima in un colpo, e
	 * da quei sei escono sia il bioma sia il rilievo. Sono {@value MapTiles#TILE} per
	 * {@value MapTiles#TILE} campioni, alla distanza che il livello di dettaglio comanda.
	 *
	 * <p>La quota a cui si campiona e' <strong>fissa</strong>, ed e' il livello del mare. Non e' un
	 * dettaglio: la profondita' del clima e' una funzione di quanto si e' sotto la superficie, quindi
	 * letta a quota costante diventa una funzione del terreno. Campionarla all'altezza vera di ogni
	 * colonna — che e' la cosa che verrebbe in mente — darebbe lo stesso numero dappertutto, e una
	 * mappa perfettamente piatta.
	 */
	private static int[] paint(ServerLevel level, int lod, int tileX, int tileZ) {
		long began = System.nanoTime();

		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		BiomeSource biomes = generator.getBiomeSource();
		Climate.Sampler sampler = random.sampler();

		int step = MapTiles.step(lod);
		int originX = MapTiles.originOf(tileX, lod);
		int originZ = MapTiles.originOf(tileZ, lod);

		// Il clima si campiona a quarti di blocco, e a quota fissa: vedi sopra.
		int quartY = level.getSeaLevel() >> 2;

		int[] colours = new int[MapTiles.TILE * MapTiles.TILE];

		// Il dislivello si misura rispetto al campione a nord-ovest, quindi serve tenere in mano la
		// riga precedente e il campione precedente. Una riga in piu' di memoria invece di
		// ricampionare: campionare due volte lo stesso punto raddoppierebbe il costo del riquadro.
		long[] previousRow = new long[MapTiles.TILE];
		long[] currentRow = new long[MapTiles.TILE];

		for (int z = 0; z < MapTiles.TILE; z++) {
			for (int x = 0; x < MapTiles.TILE; x++) {
				int quartX = (originX + x * step) >> 2;
				int quartZ = (originZ + z * step) >> 2;

				Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
				currentRow[x] = point.depth();

				// Il campione a nord-ovest. Sul bordo del riquadro non c'e', e si ripiega su se
				// stesso: la riga di pixel piu' a nord e quella piu' a ovest restano senza
				// ombreggiatura invece di prenderne una inventata.
				long before = z == 0 || x == 0 ? currentRow[x] : previousRow[x - 1];

				Holder<Biome> biome = biomeAt(biomes, point, quartX, quartY, quartZ, sampler);
				colours[z * MapTiles.TILE + x] = colour(biome, point, currentRow[x] - before);
			}

			System.arraycopy(currentRow, 0, previousRow, 0, MapTiles.TILE);
		}

		if (!timed) {
			timed = true;
			AriseMod.LOGGER.info("Primo riquadro di mappa dipinto in {} ms (livello {}).",
					(System.nanoTime() - began) / 1_000_000L, lod);
		}

		return colours;
	}

	/**
	 * Il bioma di un campione gia' preso.
	 *
	 * <p>La sorgente dei biomi di un mondo normale e' {@code MultiNoiseBiomeSource}, che sa
	 * rispondere partendo dai sei valori del clima: sono gli stessi che abbiamo appena letto, e
	 * ripassarglieli evita di campionare due volte lo stesso punto — cioe' dimezza il costo del
	 * riquadro.
	 *
	 * <p>Un mondo con un generatore diverso (bioma singolo, scacchiera, una mod) non ha quel
	 * metodo. Li' si passa dalla strada lunga, che funziona sempre. Costa il doppio, e su un mondo
	 * a bioma unico il doppio di niente e' niente.
	 */
	private static Holder<Biome> biomeAt(BiomeSource biomes, Climate.TargetPoint point,
			int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
		if (biomes instanceof MultiNoiseBiomeSource multi) {
			return multi.getNoiseBiome(point);
		}

		return biomes.getNoiseBiome(quartX, quartY, quartZ, sampler);
	}

	/** Il colore di un campione: il bioma da' la tinta, il mare la scurisce, il dislivello la ombreggia. */
	private static int colour(Holder<Biome> holder, Climate.TargetPoint point, long slope) {
		Biome biome = holder.value();
		int base;

		if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_DEEP_OCEAN)
				|| holder.is(BiomeTags.IS_RIVER)) {
			// La continentalita' e' fortemente negativa in mezzo all'oceano e vicina a zero sotto
			// costa: e' cio' che distingue il mare aperto da una baia. Senza, il mare e' una tinta
			// piatta, ed e' meta' del mondo.
			double deep = Math.clamp(-point.continentalness() / 10000.0, 0.0, 1.0);
			base = mix(biome.getWaterColor(), DEEP_WATER, deep * 0.8);
		} else {
			// Le coordinate del colore dell'erba servono solo alle sfumature di rumore che vanilla
			// mette dentro certi biomi; zero va benissimo, e per una mappa e' anzi meglio — due
			// campioni vicini nello stesso bioma non devono avere due verdi diversi.
			base = biome.getGrassColor(0.0, 0.0);

			if (holder.is(BiomeTags.IS_MOUNTAIN)) {
				base = mix(base, ROCK, 0.45);
			} else if (holder.is(BiomeTags.IS_BEACH)) {
				base = mix(base, SAND, 0.7);
			}
		}

		// L'ombreggiatura si ferma presto: un versante ripido non deve diventare nero, o le
		// montagne sembrano buchi. La soglia e' in unita' di profondita' del clima, che sono
		// decimillesimi — un dislivello vero fra due campioni vicini sta nelle centinaia.
		double light = Math.clamp(slope / 900.0, -0.4, 0.4);
		int shaded = light >= 0 ? mix(base, 0xFFFFFF, light) : mix(base, 0x000000, -light);

		return 0xFF000000 | shaded;
	}

	private static int mix(int from, int to, double amount) {
		double t = Math.clamp(amount, 0.0, 1.0);

		int r = (int) (((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
		int g = (int) (((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
		int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);

		return (r << 16) | (g << 8) | b;
	}

	/**
	 * Dipinge qualche riquadro e scrive nel log quanto ci ha messo. Serve al comando di prova.
	 *
	 * <p>Esiste per una ragione precisa, ed e' la ragione per cui questa classe e' stata riscritta
	 * una volta: tutto il sistema poggia sull'affermazione che interrogare il rumore sia economico,
	 * e un'affermazione del genere o si misura o e' una speranza. La prima versione la dava per
	 * buona e costava mille volte tanto.
	 *
	 * <p><strong>Non gira sul thread del server</strong>, e ci e' voluto un watchdog scattato per
	 * imparare: la misura di quattro riquadri fatta in linea ha bloccato il battito per trentun
	 * secondi e il server si e' dichiarato morto. La risposta arriva nel log, non nella chat, perche'
	 * quando finisce chi ha lanciato il comando potrebbe non essere piu' collegato.
	 */
	public static void bench(ServerLevel level, int lod, int tiles) {
		int side = Math.max(1, (int) Math.ceil(Math.sqrt(tiles)));
		net.minecraft.core.BlockPos spawn = level.getRespawnData().pos();
		int fromX = MapTiles.tileOf(spawn.getX(), lod);
		int fromZ = MapTiles.tileOf(spawn.getZ(), lod);

		pool().execute(() -> {
			long began = System.nanoTime();
			int painted = 0;

			for (int z = 0; z < side; z++) {
				for (int x = 0; x < side; x++) {
					CACHE.put(MapTiles.key(lod, fromX + x, fromZ + z),
							paint(level, lod, fromX + x, fromZ + z));
					painted++;
				}
			}

			long millis = (System.nanoTime() - began) / 1_000_000L;
			AriseMod.LOGGER.info("Misura mappa: {} riquadri al livello {} ({} blocchi l'uno) in {} ms"
					+ " — {} ms l'uno.", painted, lod, MapTiles.span(lod), millis, millis / painted);
		});
	}

	// ---------------------------------------------------------------- il ciclo di vita

	private static synchronized ExecutorService pool() {
		if (pool == null || pool.isShutdown()) {
			pool = Executors.newFixedThreadPool(WORKERS, task -> {
				Thread thread = new Thread(task, "arise-mappa");

				// Demone: se il server si ferma mentre un riquadro e' a meta', il processo deve
				// poter uscire lo stesso. Un riquadro perso e' un riquadro che si ridipinge.
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY);
				return thread;
			});
		}

		return pool;
	}

	/**
	 * All'arresto del server: la cache appartiene a un mondo, e il mondo dopo ha un altro seme.
	 *
	 * <p>Non svuotarla sarebbe il difetto piu' silenzioso di tutti: in singleplayer si esce da un
	 * mondo e se ne apre un altro senza chiudere il gioco, e la mappa del secondo mostrerebbe il
	 * terreno del primo.
	 */
	public static void clear() {
		CACHE.clear();
		WORKING.clear();
		timed = false;

		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}
}
