package com.luca.arise.npc;

import java.util.List;

import com.luca.arise.AriseMod;
import com.luca.arise.city.City;
import com.luca.arise.city.CityMarket;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * Chi mette le persone dentro le botteghe, e chi controlla che ci siano ancora.
 *
 * <p>Nessun elenco salvato da nessuna parte, come per le citta': si guarda il mondo. Prima di
 * mettere qualcuno dietro un bancone si controlla se dietro quel bancone c'e' gia' qualcuno, e la
 * risposta arriva dalle entita' che stanno li'. Un registro degli NPC vivi finirebbe in disaccordo
 * col mondo al primo {@code /kill}, e il disaccordo sarebbe silenzioso.
 *
 * <p>Il ripopolamento e' <strong>idempotente</strong> e questo e' il punto: si puo' richiamare
 * quante volte si vuole — a citta' finita, con un comando, dopo che qualcuno ha fatto una
 * sciocchezza — e ogni volta rimette solo quello che manca.
 */
public final class NpcManager {

	/** Quanto lontano si cerca chi dovrebbe stare dietro un bancone. */
	private static final double SEARCH_RADIUS = 4.0;

	private NpcManager() {
	}

	/**
	 * Popola le nove botteghe di una citta'.
	 *
	 * @return quante persone sono state messe adesso
	 */
	public static int populate(ServerLevel level, City city, int baseY) {
		if (!AriseConfig.get().market().npcs()) {
			return 0;
		}

		CityConfig config = AriseConfig.get().cities();
		int centreX = config.centreX(city);
		int centreZ = config.centreZ(city);
		int placed = 0;

		for (CityMarket.Stall stall : CityMarket.STALLS) {
			double[] spot = CityMarket.standing(stall, centreX, baseY, centreZ);

			if (occupied(level, stall, spot)) {
				continue;
			}

			if (spawn(level, stall, spot)) {
				placed++;
			}
		}

		if (placed > 0) {
			AriseMod.LOGGER.info("{}: {} botteghe popolate.", city.getSerializedName(), placed);
		}

		return placed;
	}

	/**
	 * C'e' gia' la persona giusta dietro questo bancone?
	 *
	 * <p>Si guarda il ruolo e non solo la presenza: se qualcuno ha ucciso il Fonditore e al suo
	 * posto e' rimasto un altro NPC finito li' per sbaglio, la bottega e' comunque da rifare.
	 */
	private static boolean occupied(ServerLevel level, CityMarket.Stall stall, double[] spot) {
		AABB box = new AABB(spot[0] - SEARCH_RADIUS, spot[1] - 3.0, spot[2] - SEARCH_RADIUS,
				spot[0] + SEARCH_RADIUS, spot[1] + 3.0, spot[2] + SEARCH_RADIUS);

		List<ShopkeeperEntity> found = level.getEntitiesOfClass(ShopkeeperEntity.class, box,
				npc -> npc.role() == stall.role());

		return !found.isEmpty();
	}

	private static boolean spawn(ServerLevel level, CityMarket.Stall stall, double[] spot) {
		ShopkeeperEntity npc = ModEntities.SHOPKEEPER.create(level, EntitySpawnReason.EVENT);

		if (npc == null) {
			return false;
		}

		npc.setRole(stall.role());
		npc.snapTo(spot[0], spot[1], spot[2], stall.facing(), 0.0F);

		// La testa guarda dove guarda il corpo. Senza, l'NPC nasce con il collo storto e resta
		// cosi' finche' qualcuno non gli passa davanti.
		npc.setYHeadRot(stall.facing());
		npc.setYBodyRot(stall.facing());

		return level.addFreshEntity(npc);
	}

	/**
	 * La quota del pavimento di una bottega, letta dal mondo.
	 *
	 * <p>Serve a ripopolare una citta' costruita in una sessione precedente, quando la quota su cui
	 * e' stata posata non e' piu' in memoria. Si guarda dove sta il segnaposto dell'Associazione,
	 * che e' l'unico punto della citta' di cui si conosce sempre la posizione orizzontale.
	 */
	public static int floorY(ServerLevel level, City city, BlockPos marker) {
		return marker.getY() - com.luca.arise.city.CityPlan.markerHeight();
	}
}
