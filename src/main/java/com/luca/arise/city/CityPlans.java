package com.luca.arise.city;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.luca.arise.AriseMod;
import com.luca.arise.city.CityPlan.Fill;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Le piante delle citta', calcolate una volta sola per mondo e condivise.
 *
 * <p>La stessa pianta la leggono due cose diverse: il generatore del mondo, un chunk alla volta e
 * da piu' thread insieme, e il cantiere a budget di {@link CityBuild} quando si ricostruisce una
 * citta' con un comando. Se ognuno si calcolasse la sua, basterebbe una virgola diversa — una
 * quota, un seme — per avere due citta' sovrapposte che non combaciano. Qui la pianta e' una, e
 * chi la vuole la chiede.
 *
 * <p>La cache si svuota all'arresto del server: la quota dipende dal terreno, e il terreno
 * dipende dal mondo. Un mondo diverso e' una pianta diversa.
 */
public final class CityPlans {

	/**
	 * La pianta di una citta' posata sul suo terreno.
	 *
	 * @param baseY   la quota di calpestio
	 * @param fills   tutti i volumi, nell'ordine in cui vanno applicati
	 * @param terrace i soli volumi della spianata: plinto, aria sopra, suolo. E' quello che il
	 *                generatore posa <em>prima</em> della vegetazione, perche' su un marciapiede
	 *                gli alberi non nascono
	 */
	public record Planned(int baseY, List<Fill> fills, List<Fill> terrace) {
	}

	private static final Map<City, Planned> CACHE = new ConcurrentHashMap<>();

	private CityPlans() {
	}

	/** La pianta della citta', calcolandola se e' la prima volta. Sicuro da piu' thread. */
	public static Planned of(ServerLevel level, City city) {
		return CACHE.computeIfAbsent(city, wanted -> compute(level, wanted));
	}

	private static Planned compute(ServerLevel level, City city) {
		CityConfig config = AriseConfig.get().cities();
		int baseY = groundLevel(level, config, city);
		RandomSource random = RandomSource.create(city.index() * 31L + 7L);
		List<Fill> fills = CityPlan.of(city, config, baseY, random);

		// Una riga per citta' e per avvio: e' la traccia che il generatore e' passato di li'.
		AriseMod.LOGGER.info("Pianta di {} calcolata: quota {}, {} volumi, angolo {} {}",
				city.getSerializedName(), baseY, fills.size(), config.cityX(city), config.cityZ(city));

		return new Planned(baseY, fills, CityPlan.terraceOnly(city, config, baseY));
	}

	/** Vero se questo chunk cade, anche solo in parte, dentro il perimetro della citta'. */
	public static boolean touches(CityConfig config, City city, int chunkX, int chunkZ) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		int cityX0 = config.cityX(city);
		int cityZ0 = config.cityZ(city);
		int cityX1 = cityX0 + config.size() - 1;
		int cityZ1 = cityZ0 + config.size() - 1;

		return x0 + 15 >= cityX0 && x0 <= cityX1 && z0 + 15 >= cityZ0 && z0 <= cityZ1;
	}

	/**
	 * La quota su cui posare la citta'.
	 *
	 * <p>Si campiona il terreno su una griglia di venticinque punti e si prende la media. Il solo
	 * centro basterebbe se il mondo fosse piatto; con quattro angoli soli una collina in mezzo
	 * sposta la media di parecchio. Su cinquecento blocchi di lato la differenza fra una citta'
	 * posata bene e una mezza sepolta sta tutta qui.
	 *
	 * <p><strong>Si chiede al generatore, non al mondo.</strong> {@code level.getHeight} legge la
	 * mappa delle altezze di un chunk, e per leggerla il chunk deve <em>esistere</em>: venticinque
	 * campioni sparsi su mezzo chilometro erano venticinque chunk generati di colpo, cioe' tredici
	 * secondi di server fermo prima ancora di posare un blocco. {@code getBaseHeight} interroga il
	 * rumore del generatore e risponde senza costruire niente — ed e' anche l'unica domanda che si
	 * puo' fare dal thread del generatore, mentre il chunk sta ancora nascendo.
	 */
	public static int groundLevel(ServerLevel level, CityConfig config, City city) {
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

		// Mai sotto il livello del mare: una citta' sul fondale sarebbe un acquario.
		return Math.max(level.getSeaLevel() + 1, sum / samples);
	}

	/** All'arresto del server: un altro mondo e' un altro terreno. */
	public static void clear() {
		CACHE.clear();
	}
}
