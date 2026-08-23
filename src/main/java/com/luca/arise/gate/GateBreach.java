package com.luca.arise.gate;

import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.config.SpawnConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.Rank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Il Dungeon Break: quello che succede a un varco che nessuno ha chiuso.
 *
 * <p>Fino a ieri ignorare un varco costava zero. Il rombo compariva sulla mappa, restava cinque
 * minuti, spariva, e il mondo era esattamente come prima. Un gioco che premia soltanto chi agisce
 * e non fa mai niente a chi non agisce ha una mappa che e' un elenco di attivita' facoltative, e
 * un'attivita' facoltativa e' una che si smette di fare.
 *
 * <p>Adesso un varco su tre <strong>cede</strong>. Il portale collassa e cio' che c'era dentro esce:
 * i mob del tema e del rango che il pannello di analisi aveva dichiarato, in superficie, nel raggio
 * di quaranta blocchi. Non sono mob generici — sono <em>quelli</em>, gli stessi che il giocatore
 * avrebbe incontrato entrando, ed e' quello a rendere la rottura la conseguenza di una scelta
 * invece di un evento casuale.
 *
 * <p>Un minuto prima il varco comincia a cedere e lo dice: chi e' abbastanza vicino ha il tempo di
 * correre a chiuderlo. Senza il preavviso, una rottura sarebbe una tassa; con il preavviso e' una
 * decisione, ed e' la differenza fra difficolta' e fastidio.
 *
 * <p><strong>Cosa non fa, e deliberatamente.</strong> Non uccide gli NPC del mercato (sono
 * invulnerabili e restano tali), non distrugge blocchi, non tocca l'inventario di nessuno. La
 * minaccia deve costare tempo e fastidio, mai contenuto: perdere per sempre il Cartografo perche'
 * si era disconnessi e' esattamente il genere di punizione che fa disinstallare una mod.
 */
public final class GateBreach {

	/** Quanto in alto sopra il terreno nascono i mob, per non incastrarli nel suolo. */
	private static final int SPAWN_LIFT = 1;

	/** Quanti tentativi di piazzamento per mob prima di rinunciare a quello. */
	private static final int PLACEMENT_ATTEMPTS = 8;

	/** Entro quale raggio si avvisa chi sta giocando. Oltre, non farebbe in tempo comunque. */
	private static final double WARNING_RADIUS = 200.0;

	/** Entro quale raggio /arise gate breach cerca un varco da far cedere. */
	public static final double COMMAND_REACH = 128.0;

	private GateBreach() {
	}

	/**
	 * Decide, per questo varco, se scadere significhera' richiudersi o cedere.
	 *
	 * <p>Si tira <strong>una volta sola</strong>, alla nascita del varco, e non alla scadenza: cosi'
	 * il preavviso puo' essere onesto. Un varco che decidesse all'ultimo istante avrebbe dovuto
	 * mentire per un minuto intero, o non avvisare affatto.
	 */
	public static boolean rollWillBreach(RandomSource random) {
		SpawnConfig spawn = AriseConfig.get().gates().spawn();
		return spawn.enabled() && random.nextDouble() < spawn.breakChance();
	}

	/** Se manca poco e il varco cedera', avvisa chi e' abbastanza vicino da poterci fare qualcosa. */
	public static void warn(ServerLevel level, Vec3 position, Rank rank, int remainingTicks) {
		SpawnConfig spawn = AriseConfig.get().gates().spawn();

		if (remainingTicks != spawn.breakWarningTicks()) {
			return;
		}

		Component line = Component.translatable("arise.msg.gate.breaching", rank.label())
				.withStyle(ChatFormatting.RED);

		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(position) <= WARNING_RADIUS * WARNING_RADIUS) {
				player.sendSystemMessage(line);
				Overlay.actionBar(player, line);
			}
		}

		AriseFx.gateBreaching(level, position, rank.color());
	}

	/**
	 * Il varco cede: l'effetto, l'annuncio, e cio' che c'era dentro che esce.
	 *
	 * <p>La quantita' e' quella di una stanza moltiplicata per le ondate — tre, di default — perche'
	 * il metro giusto e' «quanto avresti dovuto affrontare comunque», non un numero inventato. Un
	 * varco di rango E riversa nove creature; uno di rango S ne riversa trentatre', e a quel punto
	 * il giocatore ha un rango S per affrontarle.
	 */
	public static void breach(ServerLevel level, Vec3 position, GateOffer offer) {
		GateConfig config = AriseConfig.get().gates();
		SpawnConfig spawn = config.spawn();
		Rank rank = offer.rank();

		AriseFx.gateBreak(level, position, rank.color());

		Component line = Component.translatable("arise.msg.gate.breached", rank.label())
				.withStyle(ChatFormatting.RED);

		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(position) <= WARNING_RADIUS * WARNING_RADIUS) {
				player.sendSystemMessage(line);
				Overlay.title(player, Component.translatable("arise.title.gate_breached"),
						Component.translatable("arise.subtitle.gate_breached"));
			}
		}

		List<Identifier> mobs = offer.mobs();
		if (mobs.isEmpty()) {
			return;
		}

		int total = Math.max(1, config.mobsPerRoom(rank) * Math.max(1, spawn.breakWaves()));
		RandomSource random = level.getRandom();

		for (int i = 0; i < total; i++) {
			release(level, position, spawn.breakRadius(), random,
					mobs.get(random.nextInt(mobs.size())));
		}
	}

	/**
	 * Fa uscire una creatura, in superficie e su terreno gia' caricato.
	 *
	 * <p>Il vincolo dei chunk gia' caricati e' lo stesso che regge la comparsa dei varchi, e per la
	 * stessa ragione: generare terreno sul thread del server per piazzare uno zombie costerebbe
	 * fra un decimo e mezzo secondo a mob, e qui i mob sono trenta.
	 */
	private static void release(ServerLevel level, Vec3 centre, int radius, RandomSource random,
			Identifier typeId) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);

		if (type == null) {
			return;
		}

		for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 4.0 + random.nextDouble() * Math.max(1, radius - 4);

			int x = (int) Math.round(centre.x() + Math.cos(angle) * distance);
			int z = (int) Math.round(centre.z() + Math.sin(angle) * distance);
			BlockPos ground = new BlockPos(x, 0, z);

			if (!level.hasChunkAt(ground)) {
				continue;
			}

			BlockPos spot = new BlockPos(x,
					level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + SPAWN_LIFT, z);

			if (!level.noCollision(type.getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))) {
				continue;
			}

			Entity entity = type.create(level, EntitySpawnReason.EVENT);

			if (entity == null) {
				return;
			}

			entity.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
					random.nextFloat() * 360.0F, 0.0F);

			// Persistenti come i mob dentro un Gate: una minaccia che sparisce da sola appena il
			// giocatore si allontana di qualche chunk non e' una minaccia, e chi torna dopo
			// dieci minuti troverebbe il prato pulito senza aver fatto niente.
			if (entity instanceof Mob mob) {
				mob.setPersistenceRequired();
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
						EntitySpawnReason.EVENT, null);
			}

			level.addFreshEntity(entity);
			AriseFx.gateBreachMob(level, entity.position());
			return;
		}
	}

	/** Vero se questo mondo e' un posto in cui ha senso far cedere un varco. */
	public static boolean canBreach(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}
}
