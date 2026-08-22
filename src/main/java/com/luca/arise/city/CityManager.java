package com.luca.arise.city;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.luca.arise.AriseMod;
import com.luca.arise.city.CityPlan.Fill;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.npc.NpcManager;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

/**
 * Costruzione, riconoscimento e viaggio fra le città.
 *
 * <p>Non c'è nessun file di salvataggio delle città, ed è voluto: la prova che una città esiste è
 * la città stessa — si guarda se al centro della sua Associazione c'è la pietra segnaposto. Un
 * elenco tenuto a parte finirebbe prima o poi in disaccordo col mondo (un backup, un
 * {@code /fill} di troppo, un mondo copiato) e il disaccordo sarebbe silenzioso.
 */
public final class CityManager {

	/** Le costruzioni in corso. Ne parte una sola per volta: vedi {@link #QUEUE}. */
	private static final Map<City, CityBuild> RUNNING = new EnumMap<>(City.class);

	/**
	 * Le città in attesa del proprio turno.
	 *
	 * <p>Costruirle tutte e cinque insieme moltiplicherebbe per cinque il lavoro di ogni battito, e
	 * il numero che sta in config — quanti blocchi al colpo — smetterebbe di voler dire qualcosa.
	 * Una alla volta il costo per battito è sempre quello, che il mondo sia nuovo o che si stia
	 * ricostruendo una sola città con un comando.
	 */
	private static final java.util.Deque<City> QUEUE = new java.util.ArrayDeque<>();

	/** Chi ha chiesto la costruzione, per mandargli l'avanzamento. */
	private static final Map<City, java.util.UUID> REQUESTERS = new EnumMap<>(City.class);

	/** Ultima percentuale annunciata: senza, l'avanzamento sarebbe un muro di messaggi. */
	private static final Map<City, Integer> ANNOUNCED = new EnumMap<>(City.class);

	/**
	 * Città già viste esistere.
	 *
	 * <p>Serve perché la verifica costa: il segnaposto sta a duecentomila blocchi dallo spawn, e
	 * leggerlo obbliga il server a generare quel chunk. Una volta per avvio basta e avanza — una
	 * città non sparisce da sola.
	 */
	private static final Set<City> KNOWN = EnumSet.noneOf(City.class);

	/** Ogni quanti punti percentuali si dà notizia. */
	private static final int ANNOUNCE_STEP = 10;

	/**
	 * Sopra quanti nanosecondi di battito medio il cantiere si ferma per un giro.
	 *
	 * <p>Quaranta millesimi su cinquanta. Non è bilanciamento, è una valvola: costruire una città
	 * significa far generare mille chunk vergini, e buona parte di quel lavoro Minecraft la fa
	 * comunque sul thread principale, per quanto lo si chieda in anticipo. Senza questa riga il
	 * server passa i primi minuti di un mondo nuovo a dichiararsi sovraccarico.
	 *
	 * <p>L'effetto è un ciclo: si costruisce finché il server regge, ci si ferma quando arranca, e
	 * la media si assesta appena sotto la soglia. La città ci mette un po' di più e il gioco resta
	 * giocabile — che è il baratto giusto per qualcosa che succede in sottofondo.
	 */
	private static final long TICK_BUDGET_NANOS = 40_000_000L;

	/**
	 * Se il controllo di "mondo già pronto" è già stato fatto in questa sessione.
	 *
	 * <p>Verificare l'esistenza delle cinque città costa cinque chunk generati a duecentomila
	 * blocchi dallo spawn: una volta per avvio è il prezzo giusto, a ogni ingresso non lo sarebbe.
	 */
	private static boolean autoBuildChecked;

	private CityManager() {
	}

	// ---------------------------------------------------------------- mondo già pronto

	/**
	 * Tira su le città che mancano, all'avvio del server.
	 *
	 * <p><strong>Su un mondo nuovo questo <em>è</em> la creazione del mondo</strong>, ed è il
	 * momento giusto. Prima si costruiva alla prima entrata di un giocatore, e il risultato era che
	 * chi apriva un mondo nuovo si trovava addosso un contatore di avanzamento mentre stava ancora
	 * capendo dove guardare. Su un mondo che le città ce le ha già, questo controllo costa cinque
	 * letture e non fa niente.
	 *
	 * <p>Nessuna bandiera salvata da nessuna parte: la prova che una città esiste resta la città
	 * stessa. Una bandiera prima o poi finirebbe in disaccordo col mondo, e in silenzio.
	 */
	public static void onServerStarted(MinecraftServer server) {
		if (autoBuildChecked || !AriseConfig.get().cities().autoBuild()) {
			return;
		}

		autoBuildChecked = true;
		int started = setup(server, null);

		if (started > 0) {
			AriseMod.LOGGER.info("Mondo nuovo: {} città da costruire.", started);
		}
	}

	/**
	 * Avvia la costruzione di tutte le città che non ci sono ancora.
	 *
	 * @return quante ne sono state avviate
	 */
	public static int setup(MinecraftServer server, ServerPlayer requester) {
		ServerLevel level = server.overworld();
		int started = 0;

		for (City city : City.values()) {
			if (!exists(level, city) && !RUNNING.containsKey(city) && !QUEUE.contains(city)) {
				build(server, requester, city);
				started++;
			}
		}

		return started;
	}

	// ---------------------------------------------------------------- costruzione

	/**
	 * Avvia la costruzione di una città.
	 *
	 * <p>{@code requester} può mancare: si costruisce anche dalla console del server, dove non c'è
	 * nessun giocatore a cui mandare l'avanzamento. In quel caso finisce nel log, che è comunque
	 * il posto dove lo si va a cercare quando si lancia un lavoro da minuti.
	 */
	public static Component build(MinecraftServer server, ServerPlayer requester, City city) {
		ServerLevel level = server.overworld();

		if (RUNNING.containsKey(city)) {
			return Component.translatable("arise.msg.city.already_building",
					city.label(), RUNNING.get(city).percent());
		}

		if (QUEUE.contains(city)) {
			return Component.translatable("arise.msg.city.queued", city.label());
		}

		if (exists(level, city)) {
			return Component.translatable("arise.msg.city.already_built", city.label());
		}

		CityConfig config = AriseConfig.get().cities();
		QUEUE.add(city);

		if (requester != null) {
			REQUESTERS.put(city, requester.getUUID());
		}

		if (!RUNNING.isEmpty() || QUEUE.size() > 1) {
			return Component.translatable("arise.msg.city.queued", city.label());
		}

		return Component.translatable("arise.msg.city.building", city.label(),
				config.centreX(city), config.centreZ(city));
	}

	/**
	 * Prende in carico la prossima città della coda.
	 *
	 * <p>La pianta si calcola qui e non quando la città viene messa in coda: leggere il terreno
	 * costa la generazione di qualche chunk a duecentomila blocchi dallo spawn, e farlo cinque
	 * volte tutte insieme sarebbe esattamente la scossa che la coda esiste per evitare.
	 */
	private static void start(MinecraftServer server, City city) {
		ServerLevel level = server.overworld();
		CityConfig config = AriseConfig.get().cities();
		int baseY = groundLevel(level, config, city);
		RandomSource random = RandomSource.create(city.index() * 31L + 7L);

		List<Fill> fills = CityPlan.of(city, config, baseY, random);

		RUNNING.put(city, new CityBuild(level, city, fills, baseY));
		ANNOUNCED.put(city, 0);

		AriseMod.LOGGER.info("Costruzione di {} avviata a {} {} (quota {}, {} volumi)",
				city.getSerializedName(), config.cityX(city), config.cityZ(city), baseY, fills.size());

		message(server, city, Component.translatable("arise.msg.city.building", city.label(),
				config.centreX(city), config.centreZ(city)));
	}

	/**
	 * La quota su cui posare la città.
	 *
	 * <p>Si campiona il terreno su una griglia di venticinque punti e si prende la media. Il solo
	 * centro basterebbe se il mondo fosse piatto; con quattro angoli soli una collina in mezzo
	 * sposta la media di parecchio. Su cinquecento blocchi di lato la differenza fra una città
	 * posata bene e una mezza sepolta sta tutta qui.
	 *
	 * <p><strong>Si chiede al generatore, non al mondo.</strong> {@code level.getHeight} legge la
	 * mappa delle altezze di un chunk, e per leggerla il chunk deve <em>esistere</em>: venticinque
	 * campioni sparsi su mezzo chilometro erano venticinque chunk generati di colpo, cioè tredici
	 * secondi di server fermo prima ancora di posare un blocco. {@code getBaseHeight} interroga il
	 * rumore del generatore e risponde senza costruire niente.
	 */
	private static int groundLevel(ServerLevel level, CityConfig config, City city) {
		int x0 = config.cityX(city);
		int z0 = config.cityZ(city);
		int step = Math.max(1, (config.size() - 1) / 4);
		int sum = 0;
		int samples = 0;

		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();

		for (int dx = 0; dx <= 4; dx++) {
			for (int dz = 0; dz <= 4; dz++) {
				sum += generator.getBaseHeight(x0 + dx * step, z0 + dz * step,
						Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
				samples++;
			}
		}

		// Mai sotto il livello del mare: una città sul fondale sarebbe un acquario.
		return Math.max(level.getSeaLevel() + 1, sum / samples);
	}

	/** Un passo della costruzione in corso, e il turno della prossima. Chiamato a ogni battito. */
	public static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			if (QUEUE.isEmpty()) {
				return;
			}

			start(server, QUEUE.poll());
		}

		// Il server è già in affanno: questo giro si salta. Vedi TICK_BUDGET_NANOS.
		if (server.getAverageTickTimeNanos() > TICK_BUDGET_NANOS) {
			return;
		}

		CityConfig config = AriseConfig.get().cities();
		int budget = config.blocksPerTick();

		// La scadenza si calcola qui e vale per il battito intero: se un giorno si costruissero due
		// città insieme, si spartirebbero la stessa fetta invece di prendersene una a testa.
		long deadline = System.nanoTime() + Math.max(1, config.msPerTick()) * 1_000_000L;

		for (City city : List.copyOf(RUNNING.keySet())) {
			CityBuild build = RUNNING.get(city);
			boolean finished = build.advance(budget, deadline);

			if (finished) {
				complete(server, build);
			} else {
				announce(server, build);
			}
		}
	}

	private static void announce(MinecraftServer server, CityBuild build) {
		City city = build.city();
		int percent = build.percent();
		int last = ANNOUNCED.getOrDefault(city, 0);

		if (percent < last + ANNOUNCE_STEP) {
			return;
		}

		ANNOUNCED.put(city, percent - percent % ANNOUNCE_STEP);
		message(server, city, Component.translatable("arise.msg.city.progress", city.label(), percent));
	}

	private static void complete(MinecraftServer server, CityBuild build) {
		City city = build.city();
		RUNNING.remove(city);
		ANNOUNCED.remove(city);
		KNOWN.add(city);

		CityConfig config = AriseConfig.get().cities();
		Vec3 centre = new Vec3(config.centreX(city) + 0.5, build.baseY() + 1.0, config.centreZ(city) + 0.5);
		AriseFx.cityRaised(build.level(), centre, city.color());

		// Le botteghe si popolano adesso, non prima: i chunk della piazza sono appena stati
		// toccati dal costruttore, quindi sono caricati e le entita' ci si possono appoggiare.
		NpcManager.populate(build.level(), city, build.baseY());

		message(server, city, Component.translatable("arise.msg.city.built", city.label(),
				config.centreX(city), config.centreZ(city)));
		REQUESTERS.remove(city);
	}

	private static void message(MinecraftServer server, City city, Component text) {
		java.util.UUID requester = REQUESTERS.get(city);
		ServerPlayer player = requester == null ? null : server.getPlayerList().getPlayer(requester);

		if (player != null) {
			player.sendSystemMessage(text);
		} else {
			AriseMod.LOGGER.info("{}", text.getString());
		}
	}

	// ---------------------------------------------------------------- esistenza e viaggio

	/** Vero se la città è già nel mondo. La prima verifica genera il chunk del centro. */
	public static boolean exists(ServerLevel level, City city) {
		if (KNOWN.contains(city)) {
			return true;
		}

		CityConfig config = AriseConfig.get().cities();
		BlockPos marker = markerPos(config, city, level);

		if (level.getBlockState(marker).is(CityPlan.marker().getBlock())) {
			KNOWN.add(city);
			return true;
		}

		return false;
	}

	/**
	 * La posizione del segnaposto.
	 *
	 * <p>La quota non è nota senza rileggere il terreno, quindi si cerca la pietra in una finestra
	 * verticale attorno alla quota probabile invece che in un punto solo. Costa qualche lettura in
	 * più una volta sola, e rende il riconoscimento indipendente da come era fatto il terreno il
	 * giorno in cui la città è stata costruita.
	 */
	private static BlockPos markerPos(CityConfig config, City city, ServerLevel level) {
		int x = config.centreX(city);
		int z = config.centreZ(city);
		int probable = Math.max(level.getSeaLevel() + 1,
				level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));

		for (int y = probable + 8; y >= probable - 32; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (level.getBlockState(pos).is(CityPlan.marker().getBlock())) {
				return pos;
			}
		}

		return new BlockPos(x, probable, z);
	}

	/** Le città che esistono davvero, per la schermata di viaggio. */
	public static List<City> built(ServerLevel level) {
		List<City> result = new ArrayList<>();

		for (City city : City.values()) {
			if (exists(level, city)) {
				result.add(city);
			}
		}

		return result;
	}

	/**
	 * Vero se il giocatore è dentro il perimetro di un'Associazione dei Cacciatori.
	 *
	 * <p>Il confronto è sulla distanza dal centro pianificato, non sul segnaposto: leggere il
	 * segnaposto obbliga a generare il chunk, e questa domanda arriva ogni volta che qualcuno prova
	 * a estrarre una gemma. Solo quando la distanza torna si va a verificare che la città esista
	 * davvero.
	 */
	public static boolean atAssociation(ServerPlayer player, int radius) {
		ServerLevel level = player.level() instanceof ServerLevel server ? server : null;

		if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
			return false;
		}

		CityConfig config = AriseConfig.get().cities();
		double limit = (double) radius * radius;

		for (City city : City.values()) {
			double dx = player.getX() - config.centreX(city);
			double dz = player.getZ() - config.centreZ(city);

			if (dx * dx + dz * dz <= limit) {
				return exists(level, city);
			}
		}

		return false;
	}

	/** Il terminale di viaggio di una città, se questa posizione è il suo segnaposto. */
	public static City terminalAt(Level level, BlockPos pos) {
		if (!level.getBlockState(pos).is(CityPlan.marker().getBlock())) {
			return null;
		}

		CityConfig config = AriseConfig.get().cities();
		for (City city : City.values()) {
			if (pos.getX() == config.centreX(city) && pos.getZ() == config.centreZ(city)) {
				return city;
			}
		}

		return null;
	}

	/** Viaggio verso una città: si arriva davanti all'ingresso dell'Associazione. */
	public static Component travel(ServerPlayer player, City city) {
		Component locked = QuestManager.require(player, Unlock.CITIES);
		if (locked != null) {
			return locked;
		}

		ServerLevel level = player.level().getServer().overworld();

		if (!exists(level, city)) {
			return Component.translatable("arise.msg.city.not_built", city.label());
		}

		CityConfig config = AriseConfig.get().cities();
		BlockPos marker = markerPos(config, city, level);

		// Davanti all'ingresso, a sud della piazza, rivolto verso il portone. La distanza e la
		// quota le decide la pianta: l'Associazione può diventare più larga, e chi arriva non deve
		// per questo ritrovarsi dentro un muro o due blocchi sopra il selciato.
		double x = marker.getX() + 0.5;
		double z = marker.getZ() + CityPlan.entranceOffset() + 0.5;
		double y = marker.getY() - CityPlan.markerHeight();

		AriseFx.cityTravel(player.level(), player.position(), city.color());
		player.teleportTo(level, x, y, z, Set.of(), 180.0F, 0.0F, true);
		AriseFx.cityTravel(level, new Vec3(x, y, z), city.color());

		// Ripopolare all'arrivo e non all'avvio del server: qui i chunk sono caricati perche' c'e'
		// appena arrivato un giocatore, e il controllo e' idempotente — rimette solo cio' che
		// manca. E' anche il modo in cui una citta' costruita prima che il mercato esistesse si
		// ritrova le sue nove botteghe abitate senza doverla ricostruire.
		NpcManager.populate(level, city, (int) y);

		return Component.translatable("arise.msg.city.arrived", city.label());
	}

	/** All'arresto del server le costruzioni a metà non hanno senso di sopravvivere in memoria. */
	public static void clear() {
		autoBuildChecked = false;
		RUNNING.clear();
		QUEUE.clear();
		REQUESTERS.clear();
		ANNOUNCED.clear();
		KNOWN.clear();
	}
}
