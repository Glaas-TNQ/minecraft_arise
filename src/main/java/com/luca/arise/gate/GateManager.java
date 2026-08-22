package com.luca.arise.gate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.AriseMod;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.registry.ModEntities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Apertura, popolamento, completamento e smontaggio dei Gate.
 *
 * <p>Ogni Gate è un'<em>istanza</em>: una porzione della dimensione {@code arise:gate} assegnata a
 * un giocatore, distante migliaia di blocchi dalle altre. Non è una dimensione per giocatore —
 * sarebbero centinaia di mondi da salvare — ma una sola dimensione spartita in regioni, come fanno
 * le mod di dungeon istanziati.
 *
 * <p>Le istanze vivono solo in memoria: sono ricostruibili e non hanno senso dopo un riavvio. Il
 * <em>punto di ritorno</em>, invece, è salvato su disco: è l'unica cosa che serve per non lasciare
 * nessuno intrappolato.
 */
public final class GateManager {

	public static final ResourceKey<Level> GATE_DIMENSION =
			ResourceKey.create(Registries.DIMENSION, AriseMod.id("gate"));

	/** Un Gate aperto, con tutto ciò che serve a chiuderlo. */
	private record Instance(GateOffer offer, GateLayout layout, int originX, int originZ,
			int regionIndex, UUID bossId) {

		Rank rank() {
			return offer.rank();
		}
	}

	private static final Map<UUID, Instance> ACTIVE = new HashMap<>();

	/** Chi ha già visto la stanza del boss: l'entrata in scena si fa una volta sola. */
	private static final Set<UUID> BOSS_GREETED = new java.util.HashSet<>();

	/** Probabilità, a ogni battito del server, che si senta il respiro del Gate. */
	private static final float AMBIENCE_CHANCE = 0.03F;

	/** Indici di regione liberi, riusati appena un Gate si chiude. */
	private static final Set<Integer> USED_REGIONS = new java.util.HashSet<>();

	private GateManager() {
	}

	public static boolean isInGate(ServerPlayer player) {
		return player.level().dimension().equals(GATE_DIMENSION);
	}

	// ---------------------------------------------------------------- apertura

	/**
	 * Fa comparire un <em>varco</em> davanti al giocatore. Non costruisce niente e non teletrasporta
	 * nessuno: mette nel mondo qualcosa da guardare e da decidere.
	 *
	 * <p>Il preventivo si tira adesso perché il pannello di analisi deve dire il vero. Il dungeon
	 * nasce solo se qualcuno attraversa — e nasce identico a com'è stato annunciato, perché
	 * ricostruito dallo stesso seme.
	 */
	public static Component offer(ServerPlayer player, Rank rank) {
		Component locked = QuestManager.require(player, Unlock.GATES);
		if (locked != null) {
			return locked;
		}

		ServerLevel level = player.level();
		GateConfig config = AriseConfig.get().gates();

		GateOffer offer = GateOffer.roll(config, rank, level.getRandom().nextLong());

		GateEntity varco = ModEntities.GATE.create(level, EntitySpawnReason.EVENT);
		if (varco == null) {
			return Component.translatable("arise.msg.gate.varco_failed");
		}

		Vec3 where = varcoPosition(player);
		varco.snapTo(where.x(), where.y(), where.z(), player.getYRot() + 180.0F, 0.0F);
		varco.configure(offer);

		if (!level.addFreshEntity(varco)) {
			return Component.translatable("arise.msg.gate.varco_failed");
		}

		AriseFx.gateVarcoOpened(level, where, rank);

		return Component.translatable("arise.msg.gate.varco_opened", rank.label());
	}

	/**
	 * Tre blocchi davanti al naso, all'altezza degli occhi, se c'è posto.
	 *
	 * <p>Se davanti c'è un muro il varco finisce addosso a chi lo ha chiamato: è brutto ma sempre
	 * meglio che murarlo dentro la roccia dove non si può cliccare.
	 */
	private static Vec3 varcoPosition(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		if (look.lengthSqr() < 1.0E-4) {
			return player.position();
		}

		Vec3 candidate = player.position().add(look.scale(3.0));
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
				candidate.x() - 0.8, candidate.y(), candidate.z() - 0.8,
				candidate.x() + 0.8, candidate.y() + 2.6, candidate.z() + 0.8);

		return player.level().noCollision(box) ? candidate : player.position();
	}

	/** Attraversa il varco: <em>qui</em> il dungeon viene costruito. */
	public static Component enter(ServerPlayer player, GateOffer offer) {
		if (ACTIVE.containsKey(player.getUUID())) {
			return Component.translatable("arise.msg.gate.already_open");
		}

		ServerLevel gate = player.level().getServer().getLevel(GATE_DIMENSION);
		if (gate == null) {
			// La dimensione arriva da un file di datapack: se manca, il mondo è stato creato
			// prima che la mod la definisse, oppure il JSON non è stato accettato.
			return Component.translatable("arise.msg.gate.no_dimension");
		}

		GateConfig config = AriseConfig.get().gates();
		RandomSource random = gate.getRandom();

		int regionIndex = allocateRegion();
		int originX = regionIndex * config.regionSpacing();
		int originZ = 0;

		// La stessa pianta annunciata dal pannello, ricostruita dal seme del preventivo.
		GateLayout layout = offer.layout(config);
		GateBuilder.build(gate, config, layout, offer.theme(), originX, originZ);

		UUID bossId = populate(gate, config, layout, offer, originX, originZ, random);

		ACTIVE.put(player.getUUID(), new Instance(offer, layout, originX, originZ, regionIndex, bossId));

		// Il punto di ritorno prima del teletrasporto: se qualcosa va storto dopo, la via di casa
		// è già scritta su disco.
		player.setAttached(ModAttachments.RETURN_POINT,
				new ReturnPoint(player.level().dimension(), player.position()));

		Vec3 entrance = roomCenter(config, layout.entrance(), originX, originZ);
		player.teleportTo(gate, entrance.x(), entrance.y(), entrance.z(), Set.of(), player.getYRot(), 0.0F, true);
		AriseFx.gateEntered(gate, entrance, offer.rank());

		return Component.translatable("arise.msg.gate.entered", offer.rank().label(), layout.rooms().size());
	}

	private static int allocateRegion() {
		int index = 0;
		while (USED_REGIONS.contains(index)) {
			index++;
		}
		USED_REGIONS.add(index);
		return index;
	}

	/** Riempie le stanze di mob e mette il boss in fondo. Restituisce l'id del boss. */
	private static UUID populate(ServerLevel gate, GateConfig config, GateLayout layout, GateOffer offer,
			int originX, int originZ, RandomSource random) {
		Rank rank = offer.rank();
		List<Identifier> mobs = config.mobsFor(rank);
		int perRoom = config.mobsPerRoom(rank);

		for (Map.Entry<GateLayout.Cell, GateLayout.Kind> entry : layout.rooms().entrySet()) {
			GateLayout.Kind kind = entry.getValue();

			// L'ingresso resta sgombro: comparire in mezzo a quattro nemici non è una sfida, è
			// un'imboscata a cui non si può reagire.
			if (kind == GateLayout.Kind.ENTRANCE || kind == GateLayout.Kind.BOSS) {
				continue;
			}

			// Le sale ampie ne reggono di più, le stanze laterali meno: chi devia deve trovare una
			// sfida proporzionata, non un secondo dungeon.
			int count = switch (kind) {
				case HALL -> perRoom + 2;
				case SIDE -> Math.max(1, perRoom - 1);
				default -> perRoom;
			};

			for (int i = 0; i < count; i++) {
				spawnIn(gate, config, entry.getKey(), kind, originX, originZ, random,
						mobs.get(random.nextInt(mobs.size())), 1.0, 1.0, null);
			}
		}

		// Il boss è quello annunciato dal pannello, non uno estratto adesso: chi ha deciso di
		// entrare lo ha fatto sapendo cosa lo aspettava in fondo.
		Mob boss = spawnIn(gate, config, layout.bossRoom(), GateLayout.Kind.BOSS, originX, originZ, random,
				offer.boss(),
				config.bossHealthMultiplier(), config.bossDamageMultiplier(),
				Component.translatable("arise.gate.boss", rank.label()));

		return boss == null ? null : boss.getUUID();
	}

	private static Mob spawnIn(ServerLevel gate, GateConfig config, GateLayout.Cell room,
			GateLayout.Kind kind, int originX, int originZ, RandomSource random, Identifier typeId,
			double healthMultiplier, double damageMultiplier, Component name) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
		if (type == null) {
			return null;
		}

		Entity entity = type.create(gate, EntitySpawnReason.EVENT);
		if (!(entity instanceof Mob mob)) {
			return null;
		}

		Vec3 center = roomCenter(config, room, originX, originZ);
		int spread = config.halfRoom(kind) - 2;
		mob.snapTo(center.x() + random.nextInt(spread * 2 + 1) - spread, center.y(),
				center.z() + random.nextInt(spread * 2 + 1) - spread, random.nextFloat() * 360.0F, 0.0F);

		scale(mob, Attributes.MAX_HEALTH, healthMultiplier);
		scale(mob, Attributes.ATTACK_DAMAGE, damageMultiplier);
		mob.setHealth(mob.getMaxHealth());

		if (name != null) {
			mob.setCustomName(name);
			mob.setCustomNameVisible(true);
		}

		// Senza questo il gioco li considera spawn naturali e li cancella appena il giocatore si
		// allontana di qualche chunk: si tornerebbe in una stanza vuota.
		mob.setPersistenceRequired();

		return gate.addFreshEntity(mob) ? mob : null;
	}

	private static void scale(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double multiplier) {
		if (multiplier == 1.0) {
			return;
		}

		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() * multiplier);
		}
	}

	private static Vec3 roomCenter(GateConfig config, GateLayout.Cell room, int originX, int originZ) {
		return new Vec3(originX + room.x() * config.cellSize() + 0.5,
				config.floorY(),
				originZ + room.z() * config.cellSize() + 0.5);
	}

	// ---------------------------------------------------------------- completamento

	/**
	 * Chiamato alla morte di ogni creatura uccisa da un giocatore.
	 *
	 * <p>Due esiti diversi: il boss chiude il Gate e paga tutto, un mob qualunque ogni tanto lascia
	 * un pezzo lungo la strada. Il controllo su {@code isInGate} non è ridondante rispetto alla
	 * mappa delle istanze: quella resta aperta anche nell'istante in cui il giocatore ne sta
	 * uscendo, e senza, un mob ucciso fuori conterebbe lo stesso.
	 */
	public static void onEntityDied(ServerPlayer player, LivingEntity victim) {
		Instance instance = ACTIVE.get(player.getUUID());
		if (instance == null || !isInGate(player)) {
			return;
		}

		if (instance.bossId() == null || !victim.getUUID().equals(instance.bossId())) {
			GateLoot.mobDrop(player, instance.rank(), victim.position());
			return;
		}

		long xp = instance.offer().xp();
		long souls = instance.offer().souls();

		ProgressManager.addXp(player, xp);
		ProgressManager.addSouls(player, souls);

		QuestManager.advance(player, Objective.CLEAR_GATE);
		AriseFx.gateClear(player, instance.rank());
		player.sendSystemMessage(Component.translatable("arise.msg.gate.cleared",
				instance.rank().label(), xp, souls));

		// Il bottino dopo il messaggio di completamento: prima si sa di aver vinto, poi si vede
		// cosa si e' vinto. Ogni riga se la scrive il gestore che assegna il pezzo, che e' l'unico
		// a sapere se lo zaino era pieno.
		GateLoot.award(player, instance.rank()).forEach(player::sendSystemMessage);
	}

	// ---------------------------------------------------------------- uscita

	public static Component leave(ServerPlayer player) {
		if (!ACTIVE.containsKey(player.getUUID()) && !isInGate(player)) {
			return Component.translatable("arise.msg.gate.not_inside");
		}

		sendHome(player);
		closeInstance(player);
		return Component.translatable("arise.msg.gate.left");
	}

	/**
	 * Riporta il giocatore al punto di ingresso.
	 *
	 * <p>Se il punto di ritorno manca — vecchio salvataggio, o dato perso — si ripiega sullo spawn
	 * del mondo: meglio comparire in un posto sbagliato che restare bloccati in una dimensione
	 * vuota senza comandi.
	 */
	public static void sendHome(ServerPlayer player) {
		ReturnPoint point = player.getAttached(ModAttachments.RETURN_POINT);
		ServerLevel target = point == null ? null : player.level().getServer().getLevel(point.dimension());

		if (target == null) {
			target = player.level().getServer().overworld();
			Vec3 spawn = Vec3.atBottomCenterOf(target.getRespawnData().pos());
			player.teleportTo(target, spawn.x(), spawn.y(), spawn.z(), Set.of(), player.getYRot(), 0.0F, true);
			return;
		}

		Vec3 position = point.position();
		player.teleportTo(target, position.x(), position.y(), position.z(), Set.of(),
				player.getYRot(), player.getXRot(), true);
	}

	/** Smonta l'istanza: rimuove i mob rimasti, cancella la geometria e libera la regione. */
	public static void closeInstance(ServerPlayer player) {
		Instance instance = ACTIVE.remove(player.getUUID());
		if (instance == null) {
			return;
		}

		ServerLevel gate = player.level().getServer().getLevel(GATE_DIMENSION);
		if (gate != null) {
			GateConfig config = AriseConfig.get().gates();
			removeMobs(gate, config, instance);
			GateBuilder.clear(gate, config, instance.layout(), instance.originX(), instance.originZ());
		}

		USED_REGIONS.remove(instance.regionIndex());
		BOSS_GREETED.remove(player.getUUID());
	}

	/**
	 * Battito del Gate per un giocatore che ci sta dentro, chiamato una volta al secondo.
	 *
	 * <p>Due cose che non possono succedere al momento della generazione: l'entrata in scena del
	 * boss — costruire il Gate e suonarla subito significherebbe sprecarla a stanze vuote — e il
	 * respiro d'ambiente, che per definizione va nel tempo e non nello spazio.
	 */
	public static void tick(ServerPlayer player) {
		Instance instance = ACTIVE.get(player.getUUID());
		if (instance == null || !isInGate(player)) {
			return;
		}

		GateConfig config = AriseConfig.get().gates();
		Vec3 bossRoom = roomCenter(config, instance.layout().bossRoom(), instance.originX(), instance.originZ());
		double reach = config.halfRoom(GateLayout.Kind.BOSS);

		if (!BOSS_GREETED.contains(player.getUUID()) && player.distanceToSqr(bossRoom) < reach * reach) {
			BOSS_GREETED.add(player.getUUID());
			AriseFx.gateBoss(player.level(), bossRoom, instance.rank());
			player.sendSystemMessage(Component.translatable("arise.msg.gate.boss_room", instance.rank().label()));
			return;
		}

		if (player.getRandom().nextFloat() < AMBIENCE_CHANCE) {
			AriseFx.gateAmbience(player);
		}
	}

	private static void removeMobs(ServerLevel gate, GateConfig config, Instance instance) {
		int[] bounds = instance.layout().boundsInCells();
		int margin = config.halfRoom(GateLayout.Kind.BOSS) + 3;

		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
				instance.originX() + bounds[0] * config.cellSize() - margin,
				config.floorY() - 2,
				instance.originZ() + bounds[2] * config.cellSize() - margin,
				instance.originX() + bounds[1] * config.cellSize() + margin,
				config.floorY() + config.roomHeight() + 2,
				instance.originZ() + bounds[3] * config.cellSize() + margin);

		for (Mob mob : gate.getEntitiesOfClass(Mob.class, area)) {
			mob.discard();
		}
	}

	/**
	 * Rete di sicurezza al login: chi si ritrova nella dimensione dei Gate senza un'istanza aperta
	 * viene rispedito a casa. Succede dopo ogni riavvio del server, perché le istanze non si
	 * salvano.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		if (isInGate(player) && !ACTIVE.containsKey(player.getUUID())) {
			sendHome(player);
			player.sendSystemMessage(Component.translatable("arise.msg.gate.recovered"));
		}
	}

	public static void onPlayerLeave(ServerPlayer player) {
		if (ACTIVE.containsKey(player.getUUID())) {
			sendHome(player);
			closeInstance(player);
		}
	}
}
