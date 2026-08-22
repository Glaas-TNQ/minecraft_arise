package com.luca.arise.gate;

import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.config.SpawnConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gear.GearManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * I varchi che si aprono da soli.
 *
 * <p>Finche' un Gate esisteva solo scrivendo un comando, nel mondo non succedeva mai niente da
 * solo. Qui il mondo comincia a muoversi: ogni tanto, vicino a chi sta giocando, si apre un varco
 * che non ha chiesto nessuno.
 *
 * <p>Tre vincoli guidano tutto il resto:
 * <ul>
 *   <li><strong>mai generare chunk</strong>. Un varco si apre solo dove il mondo e' gia' caricato:
 *       cercare un posto bello a duecento blocchi obbligherebbe il server a generare terreno che
 *       nessuno ha chiesto, e a farlo ogni trenta secondi per ogni giocatore;
 *   <li><strong>nessuna contabilita'</strong>. Quanti varchi ci sono gia' si scopre guardando il
 *       mondo, non tenendo una lista: una lista sopravvivrebbe ai varchi che descrive, e prima o
 *       poi andrebbe in disaccordo con la realta';
 *   <li><strong>ne' addosso ne' irraggiungibile</strong>. Troppo vicino e' un agguato, troppo
 *       lontano e non ci va nessuno.
 * </ul>
 */
public final class GateSpawner {

	/** Un varco e' alto poco piu' di due blocchi e largo poco meno di due. */
	private static final double WIDTH = 0.8;
	private static final double HEIGHT = 2.6;

	private GateSpawner() {
	}

	/**
	 * Il battito del generatore.
	 *
	 * <p>Un evento proprio e non un aggancio a quello della progressione, che gira ogni venti tick:
	 * il conto "ogni seicento tick" appeso a un ciclo che ne salta diciannove su venti sarebbe vero
	 * solo se i due contatori fossero in fase, e non c'e' motivo per cui debbano esserlo. Qui si
	 * guarda {@code getTickCount}, che parte da zero con il server e non ha sorprese.
	 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SpawnConfig spawn = AriseConfig.get().gates().spawn();

			if (!spawn.enabled() || spawn.checkIntervalTicks() <= 0
					|| server.getTickCount() % spawn.checkIntervalTicks() != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				roll(player, spawn);
			}
		});
	}

	/** Il tiro per un giocatore. */
	private static void roll(ServerPlayer player, SpawnConfig spawn) {
		if (!(player.level() instanceof ServerLevel level) || !isEligible(level, player)) {
			return;
		}

		if (level.getRandom().nextDouble() >= spawn.chancePerCheck()) {
			return;
		}

		trySpawn(level, player, spawn);
	}

	/**
	 * Apre un varco spontaneo adesso, se si trova posto. Restituisce il messaggio da mostrare.
	 *
	 * <p>E' la forma che serve al comando di prova: senza, verificare questo sistema vorrebbe dire
	 * restare fermi in un prato per un quarto d'ora.
	 */
	public static Component spawnNow(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return Component.translatable("arise.msg.gate.spawn_failed");
		}

		SpawnConfig spawn = AriseConfig.get().gates().spawn();
		return trySpawn(level, player, spawn)
				? Component.translatable("arise.msg.gate.spawn_forced")
				: Component.translatable("arise.msg.gate.spawn_failed");
	}

	/** Un giocatore idoneo: nell'Overworld, vivo, e senza gia' troppi varchi attorno. */
	private static boolean isEligible(ServerLevel level, ServerPlayer player) {
		if (!level.dimension().equals(Level.OVERWORLD) || player.isSpectator() || player.isDeadOrDying()) {
			return false;
		}

		SpawnConfig spawn = AriseConfig.get().gates().spawn();
		return nearbyVarchi(level, player, spawn.nearbyRadius()) < spawn.maxNearby();
	}

	/** Quanti varchi ci sono gia' attorno. Si contano nel mondo, non in una lista. */
	private static int nearbyVarchi(ServerLevel level, ServerPlayer player, int radius) {
		AABB box = player.getBoundingBox().inflate(radius);
		List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, box);
		return found.size();
	}

	private static boolean trySpawn(ServerLevel level, ServerPlayer player, SpawnConfig spawn) {
		GateConfig gates = AriseConfig.get().gates();
		RandomSource random = level.getRandom();

		Vec3 where = findSpot(level, player, spawn, random);
		if (where == null) {
			return false;
		}

		Rank rank = rollRank(GearManager.hunterRank(player), random, spawn);
		GateOffer offer = GateOffer.roll(gates, rank, random.nextLong());

		GateEntity varco = ModEntities.GATE.create(level, EntitySpawnReason.EVENT);
		if (varco == null) {
			return false;
		}

		// Rivolto verso il giocatore: un varco visto di taglio si legge come un errore grafico.
		float yaw = (float) Math.toDegrees(Math.atan2(
				player.getZ() - where.z(), player.getX() - where.x())) - 90.0F;

		varco.snapTo(where.x(), where.y(), where.z(), yaw, 0.0F);
		varco.configure(offer, spawn.lifetimeTicks());

		if (!level.addFreshEntity(varco)) {
			return false;
		}

		AriseFx.gateVarcoOpened(level, where, rank);
		announce(player, where, rank);
		return true;
	}

	/**
	 * Cerca un posto dove aprirlo.
	 *
	 * <p>Tira una direzione e una distanza, e verifica. Ripetere il tiro e' molto piu' economico
	 * che ragionare sul terreno: la maggior parte dei tentativi finisce contro un chunk non
	 * caricato, e quel controllo costa una lettura.
	 */
	private static Vec3 findSpot(ServerLevel level, ServerPlayer player, SpawnConfig spawn,
			RandomSource random) {
		int min = Math.max(1, spawn.minDistance());
		int max = Math.max(min + 1, spawn.maxDistance());

		for (int attempt = 0; attempt < spawn.placementAttempts(); attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = min + random.nextDouble() * (max - min);

			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);

			// Il controllo che tiene in piedi tutto il resto: mai chiedere al mondo di generarsi.
			if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
				continue;
			}

			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);

			// Sopra l'acqua, sopra la lava o su una foglia il varco resterebbe sospeso o affogato.
			if (!level.getBlockState(ground).isSolidRender()) {
				continue;
			}

			Vec3 candidate = new Vec3(x + 0.5, y, z + 0.5);
			AABB box = new AABB(candidate.x() - WIDTH, candidate.y(), candidate.z() - WIDTH,
					candidate.x() + WIDTH, candidate.y() + HEIGHT, candidate.z() + WIDTH);

			if (level.noCollision(box) && level.getFluidState(BlockPos.containing(candidate)).isEmpty()) {
				return candidate;
			}
		}

		return null;
	}

	/** Il rango del varco: quello del Cacciatore, con qualche scarto. */
	private static Rank rollRank(Rank base, RandomSource random, SpawnConfig spawn) {
		double roll = random.nextDouble();
		int delta = 0;

		if (roll < spawn.rankUpChance()) {
			delta = 1;
		} else if (roll < spawn.rankUpChance() + spawn.rankDownChance()) {
			delta = -1;
		}

		return Rank.values()[Math.clamp(base.ordinal() + delta, 0, Rank.values().length - 1)];
	}

	/**
	 * Avvisa il giocatore, con distanza e direzione approssimative.
	 *
	 * <p>Non le coordinate: un varco da cercare vale piu' di un varco da raggiungere seguendo tre
	 * numeri. "Circa centoventi blocchi a nord-est" e' abbastanza per andarci e non abbastanza per
	 * non guardarsi intorno.
	 */
	private static void announce(ServerPlayer player, Vec3 where, Rank rank) {
		int distance = (int) Math.round(player.position().distanceTo(where));

		player.sendSystemMessage(Component.translatable("arise.msg.gate.spawned",
				rank.label(), distance, direction(player.position(), where)));

		AriseFx.gateVarcoDistant(player);
	}

	/** Il punto cardinale, in ottavi. */
	private static Component direction(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();

		// Angolo da nord in senso orario: nord è -Z, est è +X.
		double degrees = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
		String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		int octant = (int) Math.floor((degrees + 22.5) / 45.0) % 8;

		return Component.translatable("arise.direction." + names[octant]);
	}
}
