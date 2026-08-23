package com.luca.arise.city;

import java.util.List;

import com.luca.arise.city.CityPlan.Fill;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Le citta' come parte del mondo: nascono con i chunk, come un villaggio.
 *
 * <p>Prima le citta' si costruivano <em>dopo</em> — un cantiere a budget che partiva all'avvio
 * del server e metteva venti minuti a tirarle su tutte e cinque, mentre il giocatore era gia'
 * dentro. Per un mondo nuovo voleva dire entrare in un mondo senza Associazioni e vederle
 * arrivare. Qui invece la citta' e' una <em>feature</em> del generatore: ogni chunk che cade nel
 * perimetro di una citta' nasce gia' con il suo pezzo di citta' dentro, e il primo giocatore che
 * entra trova le cinque Associazioni al loro posto come trova le montagne.
 *
 * <p>Il costo e' zero all'avvio e si paga solo dove serve: un chunk di citta' costa un po' piu' di
 * un chunk di prato, e si genera quando qualcuno ci va — cioe' quando viaggia fin laggiu'.
 *
 * <p>La stessa classe gira in <strong>due passate</strong>, registrate come due feature:
 * <ul>
 *   <li>la <em>spianata</em>, al primo passo della decorazione ({@code RAW_GENERATION}): plinto,
 *       aria sopra e suolo. Serve a togliere la terra da sotto i piedi alla vegetazione vanilla,
 *       che altrimenti pianterebbe alberi a meta' chunk e li farebbe sporgere nel chunk accanto —
 *       dove la citta' e' magari gia' finita, e nessuno tornerebbe a togliere le foglie;
 *   <li>la <em>citta'</em>, all'ultimo passo ({@code TOP_LAYER_MODIFICATION}): tutti i volumi,
 *       spianata compresa. Dopo di lei non passa piu' nessuno, quindi un villaggio o una miniera
 *       che fossero capitati nel perimetro vengono semplicemente ricoperti.
 * </ul>
 *
 * <p>Ogni passata tocca <em>solo il proprio chunk</em>: i volumi della pianta vengono ritagliati
 * sui suoi sedici per sedici. E' la regola del generatore — scrivere lontano dal chunk che si
 * sta generando e' un errore — ed e' anche quello che rende il lavoro lo stesso in qualunque
 * ordine i chunk vengano fuori.
 */
public final class CityFeature extends Feature<NoneFeatureConfiguration> {

	/** Come nel cantiere: nessun aggiornamento ai vicini, la luce la calcola il passo successivo. */
	private static final int FLAGS = 2;

	private final boolean full;

	/**
	 * @param full vero per la citta' intera, falso per la sola spianata
	 */
	public CityFeature(boolean full) {
		super(NoneFeatureConfiguration.CODEC);
		this.full = full;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();

		if (!level.getLevel().dimension().equals(Level.OVERWORLD)) {
			return false;
		}

		CityConfig config = AriseConfig.get().cities();
		BlockPos origin = context.origin();
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		boolean placed = false;

		for (City city : City.values()) {
			// Il controllo che costa niente e scarta tutto: quasi ogni chunk del mondo esce da qui.
			if (!CityPlans.touches(config, city, chunkX, chunkZ)) {
				continue;
			}

			CityPlans.Planned planned = CityPlans.of(level.getLevel(), city);
			placed |= paint(level, full ? planned.fills() : planned.terrace(), chunkX, chunkZ);
		}

		return placed;
	}

	/** Applica i volumi, ritagliati sul chunk, nell'ordine della pianta. */
	private static boolean paint(WorldGenLevel level, List<Fill> fills, int chunkX, int chunkZ) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		int x1 = x0 + 15;
		int z1 = z0 + 15;
		int floor = level.getMinY();
		int ceiling = level.getMaxY();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean any = false;

		for (Fill fill : fills) {
			if (fill.maxX() < x0 || fill.minX() > x1 || fill.maxZ() < z0 || fill.minZ() > z1) {
				continue;
			}

			int fx0 = Math.max(fill.minX(), x0);
			int fx1 = Math.min(fill.maxX(), x1);
			int fz0 = Math.max(fill.minZ(), z0);
			int fz1 = Math.min(fill.maxZ(), z1);
			int fy0 = Math.max(fill.minY(), floor);
			int fy1 = Math.min(fill.maxY(), ceiling);

			for (int x = fx0; x <= fx1; x++) {
				for (int z = fz0; z <= fz1; z++) {
					for (int y = fy0; y <= fy1; y++) {
						level.setBlock(cursor.set(x, y, z), fill.state(), FLAGS);
					}
				}
			}

			any = true;
		}

		return any;
	}
}
