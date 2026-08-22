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

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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

	/** Le costruzioni in corso, una per città. */
	private static final Map<City, CityBuild> RUNNING = new EnumMap<>(City.class);

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
	 * Tira su le città che mancano, la prima volta che qualcuno entra nel mondo.
	 *
	 * <p>All'ingresso di un giocatore e non all'avvio del server, per due motivi: un server dove
	 * non entra nessuno non ha motivo di costruire niente, e chi entra vede l'avanzamento invece di
	 * trovarsi il lavoro già fatto senza sapere da chi.
	 */
	public static void onFirstJoin(MinecraftServer server, ServerPlayer player) {
		if (autoBuildChecked || !AriseConfig.get().cities().autoBuild()) {
			return;
		}

		autoBuildChecked = true;
		int started = setup(server, null);

		if (started > 0) {
			player.sendSystemMessage(Component.translatable("arise.msg.city.auto_build", started));
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
			if (!exists(level, city) && !RUNNING.containsKey(city)) {
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

		if (exists(level, city)) {
			return Component.translatable("arise.msg.city.already_built", city.label());
		}

		CityConfig config = AriseConfig.get().cities();
		int baseY = groundLevel(level, config, city);
		RandomSource random = RandomSource.create(city.index() * 31L + 7L);

		List<Fill> fills = CityPlan.of(city, config, baseY, random);
		CityBuild build = new CityBuild(level, city, fills, baseY);

		RUNNING.put(city, build);
		if (requester != null) {
			REQUESTERS.put(city, requester.getUUID());
		}
		ANNOUNCED.put(city, 0);

		AriseMod.LOGGER.info("Costruzione di {} avviata a {} {} (quota {})",
				city.getSerializedName(), config.cityX(city), config.cityZ(city), baseY);

		return Component.translatable("arise.msg.city.building", city.label(),
				config.centreX(city), config.centreZ(city));
	}

	/**
	 * La quota su cui posare la città.
	 *
	 * <p>Si campiona il terreno in cinque punti — centro e quattro angoli — e si prende la media.
	 * Il solo centro basterebbe se il mondo fosse piatto: su un pendio darebbe una città metà
	 * sepolta e metà sospesa su un plinto di venti blocchi.
	 */
	private static int groundLevel(ServerLevel level, CityConfig config, City city) {
		int x0 = config.cityX(city);
		int z0 = config.cityZ(city);
		int x1 = x0 + config.size() - 1;
		int z1 = z0 + config.size() - 1;

		int sum = level.getHeight(Heightmap.Types.WORLD_SURFACE, config.centreX(city), config.centreZ(city))
				+ level.getHeight(Heightmap.Types.WORLD_SURFACE, x0, z0)
				+ level.getHeight(Heightmap.Types.WORLD_SURFACE, x1, z0)
				+ level.getHeight(Heightmap.Types.WORLD_SURFACE, x0, z1)
				+ level.getHeight(Heightmap.Types.WORLD_SURFACE, x1, z1);

		// Mai sotto il livello del mare: una città sul fondale sarebbe un acquario.
		return Math.max(level.getSeaLevel() + 1, sum / 5);
	}

	/** Un passo di ogni costruzione in corso. Chiamato dal battito del server. */
	public static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}

		int budget = AriseConfig.get().cities().blocksPerTick();

		for (City city : List.copyOf(RUNNING.keySet())) {
			CityBuild build = RUNNING.get(city);
			boolean finished = build.advance(budget);

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
		ServerLevel level = player.level().getServer().overworld();

		if (!exists(level, city)) {
			return Component.translatable("arise.msg.city.not_built", city.label());
		}

		CityConfig config = AriseConfig.get().cities();
		BlockPos marker = markerPos(config, city, level);

		// Davanti all'ingresso, a sud della piazza, rivolto verso il portone.
		double x = marker.getX() + 0.5;
		double z = marker.getZ() + 14.5;
		double y = marker.getY();

		AriseFx.cityTravel(player.level(), player.position(), city.color());
		player.teleportTo(level, x, y, z, Set.of(), 180.0F, 0.0F, true);
		AriseFx.cityTravel(level, new Vec3(x, y, z), city.color());

		return Component.translatable("arise.msg.city.arrived", city.label());
	}

	/** All'arresto del server le costruzioni a metà non hanno senso di sopravvivere in memoria. */
	public static void clear() {
		autoBuildChecked = false;
		RUNNING.clear();
		REQUESTERS.clear();
		ANNOUNCED.clear();
		KNOWN.clear();
	}
}
