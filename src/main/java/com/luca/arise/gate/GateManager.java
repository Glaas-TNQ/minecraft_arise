package com.luca.arise.gate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.AriseMod;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.config.SpawnConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.AriseAdvancements;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.shadow.NamedShadow;
import com.luca.arise.shadow.ShadowManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.registry.ModEntities;
import com.luca.arise.workshop.WorkshopManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
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
	/**
	 * @param essenceTarget quante creature vanno abbattute, se l'obiettivo e' la Raccolta
	 */
	/**
	 * @param depth     la profondita' dell'Abisso, o zero se questo e' un varco qualunque
	 * @param startedAt il tick in cui si e' entrati: serve solo all'Abisso, che ha un cronometro
	 */
	private record Instance(GateOffer offer, GateLayout layout, int originX, int originZ,
			int regionIndex, UUID bossId, boolean red, int essenceTarget, int depth, long startedAt) {

		boolean isAbyss() {
			return depth > 0;
		}

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
		return enter(player, offer, false);
	}

	/**
	 * Attraversa il varco, sapendo se era rosso.
	 *
	 * <p>Il colore arriva dall'entita' e non dal preventivo, e viene passato qui invece di essere
	 * letto: cosi' il pannello di analisi puo' continuare a non saperlo, che e' il punto.
	 */
	public static Component enter(ServerPlayer player, GateOffer offer, boolean red) {
		return enter(player, offer, red, 0);
	}

	/**
	 * Attraversa il varco sapendo anche a che profondita' dell'Abisso ci si sta calando.
	 *
	 * <p>Zero vuol dire «non e' l'Abisso», ed e' il caso di tutti i varchi del mondo. La discesa
	 * passa dallo stesso codice di un varco qualunque perche' <em>e'</em> un varco qualunque: quello
	 * che cambia sono il rango, le regole e il cronometro, e nessuna delle tre e' una ragione per
	 * avere un secondo generatore da tenere allineato al primo.
	 */
	public static Component enter(ServerPlayer player, GateOffer offer, boolean red, int depth) {
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

		UUID bossId = populate(gate, config, layout, offer, originX, originZ, random, depth);

		// Il bersaglio si conta adesso, una volta sola. Chiederlo al preventivo a ogni uccisione
		// vorrebbe dire rigenerare la pianta del varco a ogni mob che cade — un lavoro che
		// nessuno vede e che si moltiplica per il numero di creature che ci sono dentro.
		int essenceTarget = offer.objective().essenceTarget(offer.inhabitants(config));

		ACTIVE.put(player.getUUID(), new Instance(offer, layout, originX, originZ, regionIndex,
				bossId, red, essenceTarget, depth, gate.getGameTime()));

		// Il punto di ritorno prima del teletrasporto: se qualcosa va storto dopo, la via di casa
		// è già scritta su disco.
		player.setAttached(ModAttachments.RETURN_POINT,
				new ReturnPoint(player.level().dimension(), player.position()));

		Vec3 entrance = roomCenter(config, layout.entrance(), originX, originZ);
		player.teleportTo(gate, entrance.x(), entrance.y(), entrance.z(), Set.of(), player.getYRot(), 0.0F, true);
		AriseFx.gateEntered(gate, entrance, offer.rank());

		if (depth > 0) {
			Abyss.brief(player, depth).forEach(player::sendSystemMessage);
		}

		if (offer.objective() == GateObjective.HUNT) {
			HUNT_DEADLINE.put(player.getUUID(),
					gate.getGameTime() + GateObjective.HUNT_TICKS);
		}

		if (offer.objective() != GateObjective.SOVEREIGN) {
			// L'obiettivo si e' gia' letto nel pannello, ma il pannello si chiude: la riga
			// all'ingresso e' quella che il giocatore ha davanti mentre decide da che parte andare.
			player.sendSystemMessage(Component.translatable("arise.msg.gate.objective",
					offer.objective().label(), offer.objective().description()));
		}

		if (red) {
			// Il varco si chiude adesso, e il giocatore lo scopre adesso. E' l'unico momento di
			// tutta la mod in cui il gioco toglie qualcosa invece di darla, e va detto forte.
			Overlay.title(player, Component.translatable("arise.title.red_gate"),
					Component.translatable("arise.subtitle.red_gate"));
			player.sendSystemMessage(Component.translatable("arise.msg.gate.red_sealed")
					.withStyle(net.minecraft.ChatFormatting.RED));
			AriseFx.redGateSealed(gate, entrance);
		}

		return Component.translatable("arise.msg.gate.entered", offer.rank().label(), layout.rooms().size());
	}

	/**
	 * Cosa scalda, dentro un varco rosso.
	 *
	 * <p>Un tag e non un elenco nel codice: un pack che voglia aggiungere il suo braciere lo mette
	 * qui senza toccare Java, ed e' la prima cosa di Arise che si estende da datapack.
	 */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> HEAT =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
					AriseMod.id("heat_sources"));

	/**
	 * Quanti posti in campo restano a questo giocatore, tenuto conto di dove si trova.
	 *
	 * <p>Dentro l'Abisso, dal quindicesimo gradino in giu', sono la meta'. Sta qui e non in
	 * {@code ShadowManager} perche' e' l'unico posto che sa in che varco e' il giocatore, e perche'
	 * una regola dell'Abisso scritta dentro l'esercito sarebbe una regola che chi legge l'esercito
	 * non si aspetta.
	 */
	public static int summonLimitIn(ServerPlayer player, int limit) {
		Instance instance = ACTIVE.get(player.getUUID());

		return instance != null && instance.isAbyss() && isInGate(player)
				? Abyss.summonLimitAt(instance.depth(), limit)
				: limit;
	}

	/** La profondita' dell'Abisso in cui si trova, o zero. */
	public static int depthOf(ServerPlayer player) {
		Instance instance = ACTIVE.get(player.getUUID());
		return instance != null && isInGate(player) ? instance.depth() : 0;
	}

	/**
	 * Apre una discesa nell'Abisso al gradino indicato.
	 *
	 * <p>Un gradino per volta: si puo' scendere al massimo a uno piu' in giu' di quello gia' chiuso.
	 * L'Abisso e' una scala, non un menu di difficolta' — scendere al quindicesimo senza aver visto
	 * il quattordicesimo significherebbe incontrare tre regole insieme senza averne imparata
	 * nessuna, perdere, e non sapere quale delle tre ha vinto.
	 */
	public static Component descend(ServerPlayer player, int depth) {
		Component locked = QuestManager.require(player, Unlock.GATES);
		if (locked != null) {
			return locked;
		}

		int allowed = Abyss.record(player).next();
		int target = Math.clamp(depth, 1, allowed);

		if (depth > allowed) {
			return Component.translatable("arise.msg.abyss.too_deep", allowed);
		}

		return enter(player, Abyss.offer(AriseConfig.get().gates(), target), false, target);
	}

	/** Vero se questo giocatore e' dentro un varco che si e' chiuso alle sue spalle. */
	public static boolean isSealedIn(ServerPlayer player) {
		Instance instance = ACTIVE.get(player.getUUID());
		return instance != null && instance.red() && isInGate(player);
	}

	/**
	 * Chi ha gia' completato il varco in cui si trova.
	 *
	 * <p>Perche' le fini sono tre e possono capitare due volte: un giocatore che riempie la barra
	 * dell'Essenza e poi decide di uccidere comunque il Sovrano ha diritto a farlo, e non a essere
	 * pagato due volte.
	 */
	private static final java.util.Set<UUID> COMPLETED = new java.util.HashSet<>();

	/** giocatore → quante creature ha abbattuto in questo varco. Serve alla Raccolta d'Essenza. */
	private static final Map<UUID, Integer> SLAIN = new HashMap<>();

	/** giocatore → a che tick di gioco scade la Caccia. Assente se questo varco non la chiede. */
	private static final Map<UUID, Long> HUNT_DEADLINE = new HashMap<>();

	/** Chi si e' gia' sentito dire che la Caccia e' scaduta. Serve a dirglielo una volta sola. */
	private static final java.util.Set<UUID> HUNT_EXPIRED = new java.util.HashSet<>();

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
			int originX, int originZ, RandomSource random, int depth) {
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
				Mob spawned = spawnIn(gate, config, entry.getKey(), kind, originX, originZ, random,
						mobs.get(random.nextInt(mobs.size())), 1.0, 1.0, null);

				// Solo il primo di ogni stanza, ed e' cosi' che la regola «mai piu' di un mob con
				// affisso per stanza» resta vera senza doverla ricordare da nessun'altra parte.
				if (i == 0 && spawned != null) {
					// Dal quinto gradino dell'Abisso l'affisso c'e' comunque, anche a rango basso:
					// e' la prima regola della discesa, e la prima che si impara a temere.
					if (depth >= AbyssRule.AFFIXED.depth()) {
						GateAffixes.apply(spawned, MobAffix.random(random));
					} else {
						GateAffixes.apply(spawned, rank, random);
					}
				}
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
			countTowardsEssence(player, instance);
			return;
		}

		complete(player, instance);
	}

	/**
	 * Una creatura in meno, e forse il varco e' finito.
	 *
	 * <p>Solo per la Raccolta d'Essenza: negli altri due obiettivi ripulire una stanza e' una scelta
	 * tattica, non un progresso, e un contatore che salisse comunque direbbe al giocatore che sta
	 * facendo la cosa giusta mentre perde tempo.
	 */
	private static void countTowardsEssence(ServerPlayer player, Instance instance) {
		GateObjective objective = instance.offer().objective();

		if (objective != GateObjective.ESSENCE) {
			return;
		}

		int target = instance.essenceTarget();
		int slain = SLAIN.merge(player.getUUID(), 1, Integer::sum);

		if (slain < target) {
			Overlay.actionBar(player, Component.translatable("arise.msg.gate.essence",
					slain, target));
			return;
		}

		complete(player, instance);
	}

	/**
	 * Il varco e' finito: paga, apre l'uscita, e lo fa una volta sola.
	 *
	 * <p>Prima stava dentro la morte del boss, perche' quella era l'unica fine possibile. Adesso le
	 * fini sono tre — il Sovrano, la barra piena, il Sovrano entro il tempo — e il pagamento sta
	 * fuori da tutte e tre: se restasse attaccato a una, le altre due chiuderebbero un varco senza
	 * ricompensarlo.
	 */
	private static void complete(ServerPlayer player, Instance instance) {
		if (!ACTIVE.containsKey(player.getUUID()) || COMPLETED.contains(player.getUUID())) {
			return;
		}

		COMPLETED.add(player.getUUID());

		GateObjective objective = instance.offer().objective();
		boolean inTime = objective != GateObjective.HUNT
				|| player.level().getGameTime() <= HUNT_DEADLINE.getOrDefault(player.getUUID(),
						Long.MAX_VALUE);

		// Il premio della fretta e della fatica: mezzo in piu' su XP e soul coin. Un obiettivo
		// diverso che pagasse uguale sarebbe una variazione senza motivo di sceglierla.
		double bonus = objective == GateObjective.SOVEREIGN || !inTime
				? 1.0
				: 1.0 + GateObjective.BONUS;

		// La profondita' moltiplica quello che gia' c'era: al decimo gradino una discesa vale il
		// doppio di un varco del suo rango. Il premio deve crescere quanto le regole che si
		// accumulano, o scendere piu' in giu' diventa una cosa che si fa solo per il numero.
		double depthBonus = instance.isAbyss() ? Abyss.reward(instance.depth()) : 1.0;

		long xp = Math.round(instance.offer().xp() * bonus * depthBonus);
		long souls = Math.round(instance.offer().souls() * bonus * depthBonus);

		if (objective == GateObjective.HUNT && !inTime) {
			player.sendSystemMessage(Component.translatable("arise.msg.gate.hunt_late")
					.withStyle(net.minecraft.ChatFormatting.GRAY));
		}

		ProgressManager.addXp(player, xp);
		ProgressManager.addSouls(player, souls);

		QuestManager.advance(player, Objective.CLEAR_GATE);
		AriseAdvancements.award(player, AriseAdvancements.FIRST_GATE);

		if (instance.red()) {
			AriseAdvancements.award(player, AriseAdvancements.RED_GATE);
		}

		AriseFx.gateClear(player, instance.rank());
		player.sendSystemMessage(Component.translatable("arise.msg.gate.cleared",
				instance.rank().label(), xp, souls));

		// Il bottino dopo il messaggio di completamento: prima si sa di aver vinto, poi si vede
		// cosa si e' vinto. Ogni riga se la scrive il gestore che assegna il pezzo, che e' l'unico
		// a sapere se lo zaino era pieno.
		GateLoot.award(player, instance.rank()).forEach(player::sendSystemMessage);

		// Il varco rosso paga il doppio, ed e' l'unico posto della mod dove il bottino si tira due
		// volte: non e' generosita', e' il prezzo di un'ora in cui non si poteva uscire.
		if (instance.red()) {
			GateLoot.award(player, instance.rank()).forEach(player::sendSystemMessage);
			ShadowManager.grantNamed(player, NamedShadow.IRON);
		}

		if (instance.isAbyss()) {
			Abyss.completed(player, instance.depth(),
							player.level().getGameTime() - instance.startedAt())
					.forEach(player::sendSystemMessage);

			AriseAdvancements.award(player, AriseAdvancements.ABYSS);

			if (instance.depth() >= 10) {
				AriseAdvancements.award(player, AriseAdvancements.ABYSS_TEN);
			}
		}

		// Un cubo per varco chiuso, sempre. Non e' bottino a probabilita': e' la scelta che chiude
		// la run, e una scelta che a volte non ti viene offerta non e' una scelta ricorrente.
		WorkshopManager.give(player, AbyssCubeItem.of(instance.rank()));
		player.sendSystemMessage(Component.translatable("arise.msg.cube.found",
				instance.rank().label()));

		awardNamedShadow(player, instance);

		openExit(player, instance);
	}

	/**
	 * Se questo varco era <em>quel</em> varco, il Sovrano lascia una delle ombre nominate.
	 *
	 * <p>Cinque delle sette si prendono qui, e ognuna ha una condizione che il gioco non produce
	 * per caso: un tema preciso e un rango minimo. Non e' bottino — non c'e' nessun tiro di dado —
	 * ed e' voluto: un'ombra che il giocatore puo' <em>andare a cercare</em> vale piu' di una che
	 * gli capita. Chi vuole Beru sa cosa deve fare, e sa che gli serviranno mesi.
	 *
	 * <p>Le altre due non passano da qui. Igris arriva dall'esame di rango, Bellion si eredita alla
	 * fine della catena: sono le due che segnano un passaggio invece di premiare una battaglia.
	 */
	private static void awardNamedShadow(ServerPlayer player, Instance instance) {
		GateTheme theme = instance.offer().theme();
		Rank rank = instance.rank();

		// Igris arriva al primo varco di rango C, qualunque tema. Non e' un premio di percorso: e'
		// il rango in cui il Cacciatore smette di essere uno che sopravvive ai varchi e comincia a
		// chiuderli, e serve un'ombra che lo dica. Prima delle cinque legate al tema, perche' il
		// rango C viene prima del B.
		if (rank.ordinal() >= Rank.C.ordinal()
				&& ShadowManager.grantNamed(player, NamedShadow.IGRIS)) {
			return;
		}

		NamedShadow prize = switch (theme) {
			case FROST -> rank.ordinal() >= Rank.B.ordinal() ? NamedShadow.TANK : null;
			case RUIN -> rank.ordinal() >= Rank.B.ordinal() ? NamedShadow.TUSK : null;
			case ASH -> rank.ordinal() >= Rank.A.ordinal() ? NamedShadow.GREED : null;
			case SCULK -> rank == Rank.S ? NamedShadow.BERU : null;
			// Iron esce solo da un varco sigillato, e i varchi sigillati non esistono ancora: il
			// caso c'e' perche' la tabella sia completa, non perche' oggi produca qualcosa.
			case VOID, DEPTHS -> null;
		};

		if (prize != null) {
			ShadowManager.grantNamed(player, prize);
		}
	}

	/**
	 * La via di casa, che compare dove il guardiano e' caduto.
	 *
	 * <p>C'era gia' — {@code /arise leave} — e non bastava: un comando scritto in una riga di chat
	 * che nel frattempo e' scorsa via non e' una porta. Chi ha appena vinto si guarda intorno e
	 * cerca <em>qualcosa</em>, e non trovando niente conclude di essere rimasto chiuso dentro.
	 *
	 * <p>Una pietra magnetica su un lastrico di vetro illuminato: la si vede da tutta la sala,
	 * vanilla la conosce gia' come "il punto a cui si torna", e si tocca invece di ricordarsela.
	 */
	private static void openExit(ServerPlayer player, Instance instance) {
		if (!(player.level() instanceof ServerLevel gate)) {
			return;
		}

		GateConfig config = AriseConfig.get().gates();
		Vec3 centre = roomCenter(config, instance.layout().bossRoom(),
				instance.originX(), instance.originZ());

		int x = (int) Math.floor(centre.x());
		int z = (int) Math.floor(centre.z());
		int floor = config.floorY();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				// La luce sta *sotto* il vetro: dentro la sala si vede un lastrico che brilla, e
				// non una lampada da guardare.
				gate.setBlock(new BlockPos(x + dx, floor - 2, z + dz),
						Blocks.SEA_LANTERN.defaultBlockState(), 2);
				gate.setBlock(new BlockPos(x + dx, floor - 1, z + dz),
						Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState(), 2);
			}
		}

		BlockPos stone = new BlockPos(x, floor, z);
		gate.setBlock(stone, Blocks.LODESTONE.defaultBlockState(), 2);

		AriseFx.gateVarcoOpened(gate, Vec3.atCenterOf(stone), instance.rank());
		player.sendSystemMessage(Component.translatable("arise.msg.gate.exit_open")
				.withStyle(net.minecraft.ChatFormatting.AQUA));
	}

	/** Vero se questo blocco e' la via di casa di un varco. */
	public static boolean isExit(Level level, BlockPos pos) {
		return level.dimension().equals(GATE_DIMENSION)
				&& level.getBlockState(pos).is(Blocks.LODESTONE);
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

			// Un Volatile caduto un istante prima dell'uscita aveva ancora il suo scoppio in coda,
			// e sarebbe maturato in una stanza che non esiste piu'. Non farebbe male a nessuno —
			// non c'e' nessuno — ma resterebbe in memoria per sempre, una voce per ogni run.
			DelayedStrike.forget(gate);
		}

		// Chi esce dall'Abisso ritrova l'esercito pronto: la quarta regola dice che le ombre cadute
		// non tornano *fino all'uscita*, e questa e' l'uscita. Un recupero da un'ora che
		// sopravvivesse alla discesa sarebbe una punizione che segue il giocatore fuori.
		if (instance.isAbyss()) {
			player.setAttached(ModAttachments.DOWNTIME, com.luca.arise.shadow.ShadowDowntime.EMPTY);
		}

		USED_REGIONS.remove(instance.regionIndex());
		BOSS_GREETED.remove(player.getUUID());
		GateBoss.forget(player.getUUID());
		COMPLETED.remove(player.getUUID());
		SLAIN.remove(player.getUUID());
		HUNT_DEADLINE.remove(player.getUUID());
		HUNT_EXPIRED.remove(player.getUUID());
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

		if (instance.red()) {
			bite(player);
		}

		countdown(player, instance);

		// Il Sovrano batte dentro il battito del giocatore che lo sta combattendo: non ha un tick
		// suo, e cosi' non ne consuma nessuno quando nella sua sala non c'e' nessuno.
		if (player.level() instanceof ServerLevel gate) {
			GateBoss.tick(player, GateBoss.find(gate, instance.bossId()), instance.rank(),
					GateBoss.reach(config),
					Abyss.hasRule(instance.depth(), AbyssRule.RELENTLESS));
		}

		if (player.getRandom().nextFloat() < AMBIENCE_CHANCE) {
			AriseFx.gateAmbience(player);
		}
	}

	/**
	 * Il tempo che resta alla Caccia, scritto sopra la hotbar.
	 *
	 * <p>Un obiettivo a tempo senza il tempo visibile e' un obiettivo a tradimento. Sta nella barra
	 * d'azione e non in chat per lo stesso motivo per cui ci sta il contatore degli incarichi: e'
	 * un numero che cambia, e i numeri che cambiano in chat diventano venti righe.
	 */
	private static void countdown(ServerPlayer player, Instance instance) {
		Long deadline = HUNT_DEADLINE.get(player.getUUID());

		if (deadline == null || COMPLETED.contains(player.getUUID())) {
			return;
		}

		long left = deadline - player.level().getGameTime();

		if (left <= 0) {
			// La scadenza NON si toglie dalla mappa. Toglierla sembrava pulito e regalava il
			// premio: complete() legge questa stessa mappa per decidere se il giocatore e' stato
			// in tempo, e una voce assente vale "nessuna scadenza", cioe' sempre in tempo. Chi
			// avesse lasciato scadere la caccia e poi ucciso il Sovrano con calma avrebbe preso
			// il bonus della fretta. Si annuncia una volta sola, e la scadenza resta scritta.
			if (HUNT_EXPIRED.add(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("arise.msg.gate.hunt_over")
						.withStyle(net.minecraft.ChatFormatting.GRAY));
			}

			return;
		}

		Overlay.actionBar(player, Component.translatable("arise.msg.gate.hunt_left",
				left / 1200, (left / 20) % 60));
	}

	/**
	 * Il gelo di un varco rosso: morde chi resta lontano da una fonte di calore.
	 *
	 * <p>E' la meta' di questo blocco che cambia come si gioca. Un dungeon in cui l'ambiente fa
	 * male smette di essere un corridoio da percorrere e diventa una traversata da gestire: si
	 * pianta un falo', si va avanti fin dove si arriva, si pianta il successivo. Ed e' la prima
	 * volta che quello che ci si e' portati nello zaino conta piu' di quello che si ha addosso.
	 *
	 * <p>Il calore lo porti tu. Le lampade del tema non scaldano — sono luce, e la differenza fra
	 * luce e calore e' esattamente la lezione che questo varco insegna.
	 */
	private static void bite(ServerPlayer player) {
		SpawnConfig spawn = AriseConfig.get().gates().spawn();

		if (player.tickCount % Math.max(1, spawn.frostIntervalTicks()) != 0
				|| player.isCreative() || player.isSpectator()) {
			return;
		}

		// Tank annulla il gelo. E' l'unica ombra che rende un intero tipo di varco piu' facile, ed
		// e' anche il motivo per cui vale la pena andarla a prendere in un varco Gelo di rango B.
		if (ShadowManager.isNamedSummoned(player, NamedShadow.TANK)) {
			return;
		}

		if (nearHeat(player, spawn.frostHeatRadius())) {
			return;
		}

		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, spawn.frostIntervalTicks() + 20, 0));
		player.hurtServer(player.level(), player.damageSources().freeze(),
				(float) spawn.frostDamage());

		AriseFx.frostBite(player.level(), player.position());
	}

	/**
	 * Se c'e' qualcosa che scalda qui attorno.
	 *
	 * <p>Un cubo di lato undici, una volta ogni due secondi, per il solo giocatore che sta dentro
	 * un varco rosso: e' il genere di scansione che sarebbe inaccettabile a ogni tick e che qui
	 * non si nota. E si ferma al primo blocco trovato, che nella pratica e' subito — chi ha capito
	 * il varco si tiene addosso il suo falo'.
	 */
	private static boolean nearHeat(ServerPlayer player, int radius) {
		BlockPos centre = player.blockPosition();

		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
				centre.offset(radius, radius, radius))) {
			if (player.level().getBlockState(pos).is(HEAT)) {
				return true;
			}
		}

		return false;
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
		// La Sala del Risveglio sta in questa dimensione ma non e' un'istanza di nessuno: chi si e'
		// disconnesso mentre parlava con l'Araldo deve ritrovarcisi, non essere rispedito a casa a
		// meta' discorso.
		if (com.luca.arise.tutorial.AwakeningManager.onJoin(player)) {
			return;
		}

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
