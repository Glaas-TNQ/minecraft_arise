package com.luca.arise.shadow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.registry.ModEntities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Estrazione, evocazione, richiamo e crescita delle ombre. Solo lato server.
 *
 * <p>Due strutture transitorie, entrambe ricostruibili e mai salvate su disco:
 * <ul>
 *   <li>i <em>candidati</em>, cioè i cadaveri ancora estraibili — vivono pochi secondi;
 *   <li>le <em>evocate</em>, cioè quali ombre dell'esercito hanno ora un'entità nel mondo.
 * </ul>
 * La fonte di verità resta l'attachment del giocatore: se il server si riavvia, l'esercito è
 * intatto e semplicemente non c'è nulla di evocato.
 */
public final class ShadowManager {

	/** Un cadavere ancora estraibile. */
	private record Candidate(Identifier sourceType, Vec3 position, double maxHealth, double attackDamage,
			long expiresAtTick) {
	}

	private static final Map<UUID, List<Candidate>> CANDIDATES = new HashMap<>();

	/** giocatore → (id dell'ombra → id dell'entità evocata). */
	private static final Map<UUID, Map<UUID, UUID>> SUMMONED = new HashMap<>();

	private ShadowManager() {
	}

	public static ShadowArmy army(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.ARMY);
	}

	private static void setArmy(ServerPlayer player, ShadowArmy army) {
		player.setAttached(ModAttachments.ARMY, army);
	}

	public static int capacity(ServerPlayer player) {
		return AriseConfig.get().shadows().capacityAt(ProgressManager.get(player).level());
	}

	public static int summonedCount(ServerPlayer player) {
		return pruneSummoned(player).size();
	}

	// ---------------------------------------------------------------- estrazione

	/** Registra un cadavere come estraibile per qualche secondo. */
	public static void recordKill(ServerPlayer killer, LivingEntity victim) {
		ShadowConfig config = AriseConfig.get().shadows();
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());

		double attackDamage = 2.0;
		AttributeInstance instance = victim.getAttribute(Attributes.ATTACK_DAMAGE);
		if (instance != null) {
			attackDamage = instance.getValue();
		}

		CANDIDATES.computeIfAbsent(killer.getUUID(), key -> new ArrayList<>())
				.add(new Candidate(type, victim.position(), victim.getMaxHealth(), attackDamage,
						killer.level().getGameTime() + config.extractionWindowTicks()));
	}

	/** Tenta l'estrazione dal cadavere più vicino. Restituisce il messaggio da mostrare. */
	public static Component extract(ServerPlayer player) {
		Component locked = QuestManager.require(player, Unlock.ARMY);
		if (locked != null) {
			return locked;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		ServerLevel level = player.level();
		long now = level.getGameTime();

		Candidate best = takeNearestCandidate(player, now, config.extractionRange());
		if (best == null) {
			return Component.translatable("arise.msg.shadow.no_target");
		}

		ShadowArmy army = army(player);
		int capacity = capacity(player);
		if (army.size() >= capacity) {
			return Component.translatable("arise.msg.shadow.army_full", capacity);
		}

		double chance = config.extractionChanceAt(ProgressManager.get(player).level());

		AriseFx.extractionRitual(level, best.position());

		if (level.getRandom().nextDouble() > chance) {
			AriseFx.extractionFailed(level, best.position());
			return Component.translatable("arise.msg.shadow.extraction_failed",
					String.format("%.0f", chance * 100));
		}

		// Le ombre nascono sempre al livello 1: la loro forza viene dal mob d'origine e da quanto
		// hanno combattuto, non da quanto era avanti il giocatore quando le ha estratte.
		// Il contrario premierebbe l'accumulo di cadaveri in attesa di salire di livello.
		ShadowData shadow = new ShadowData(UUID.randomUUID(), best.sourceType(), 1, 0L,
				best.maxHealth() * config.healthFactor(),
				best.attackDamage() * config.damageFactor(),
				Optional.empty(), ShadowData.DEFAULT_COLOR);

		setArmy(player, army.with(shadow));

		AriseFx.extractionSuccess(level, best.position(), shadow.rank(config));
		QuestManager.advance(player, Objective.EXTRACT);

		return Component.translatable("arise.msg.shadow.extracted", shadow.displayName(),
				shadow.rank(config).label(), army.size() + 1, capacity);
	}

	/**
	 * Estrae dalla lista il candidato valido più vicino, scartando gli scaduti.
	 *
	 * <p>Il candidato viene <em>rimosso</em> a prescindere dall'esito del tiro: un cadavere si
	 * tenta una volta sola, altrimenti basterebbe premere il tasto a raffica per aggirare la
	 * probabilità.
	 */
	private static Candidate takeNearestCandidate(ServerPlayer player, long now, double range) {
		List<Candidate> candidates = CANDIDATES.get(player.getUUID());
		if (candidates == null) {
			return null;
		}

		double rangeSqr = range * range;
		Candidate best = null;
		double bestDistance = Double.MAX_VALUE;

		Iterator<Candidate> iterator = candidates.iterator();
		while (iterator.hasNext()) {
			Candidate candidate = iterator.next();

			if (candidate.expiresAtTick() < now) {
				iterator.remove();
				continue;
			}

			double distance = candidate.position().distanceToSqr(player.position());
			if (distance <= rangeSqr && distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}

		if (best != null) {
			candidates.remove(best);
		}

		if (candidates.isEmpty()) {
			CANDIDATES.remove(player.getUUID());
		}

		return best;
	}

	// ---------------------------------------------------------------- evocazione

	public static Component summon(ServerPlayer player) {
		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army(player);

		if (army.isEmpty()) {
			return Component.translatable("arise.msg.shadow.army_empty");
		}

		Map<UUID, UUID> summoned = pruneSummoned(player);
		int slots = config.maxSummoned() - summoned.size();

		if (slots <= 0) {
			return Component.translatable("arise.msg.shadow.summon_limit", config.maxSummoned());
		}

		int spawned = 0;
		for (ShadowData shadow : army.shadows()) {
			if (slots <= 0) {
				break;
			}
			if (summoned.containsKey(shadow.id())) {
				continue;
			}

			if (spawn(player, shadow, summoned)) {
				spawned++;
				slots--;
			}
		}

		if (spawned == 0) {
			return Component.translatable("arise.msg.shadow.already_summoned");
		}

		playSummonSound(player);
		syncSummoned(player, summoned);
		return Component.translatable("arise.msg.shadow.summoned", spawned);
	}

	/** Evoca una singola ombra, per la schermata dell'esercito. */
	public static Component summonOne(ServerPlayer player, UUID shadowId) {
		ShadowConfig config = AriseConfig.get().shadows();
		Optional<ShadowData> shadow = army(player).find(shadowId);

		if (shadow.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		Map<UUID, UUID> summoned = pruneSummoned(player);

		if (summoned.containsKey(shadowId)) {
			return Component.translatable("arise.msg.shadow.already_summoned");
		}

		if (summoned.size() >= config.maxSummoned()) {
			return Component.translatable("arise.msg.shadow.summon_limit", config.maxSummoned());
		}

		if (!spawn(player, shadow.get(), summoned)) {
			return Component.translatable("arise.msg.shadow.summon_failed");
		}

		playSummonSound(player);
		syncSummoned(player, summoned);
		return Component.translatable("arise.msg.shadow.summoned_one", shadow.get().displayName());
	}

	/** Richiama una singola ombra. */
	public static Component recallOne(ServerPlayer player, UUID shadowId) {
		Map<UUID, UUID> summoned = pruneSummoned(player);
		UUID entityId = summoned.remove(shadowId);

		if (entityId == null) {
			return Component.translatable("arise.msg.shadow.nothing_to_recall");
		}

		despawn(player, entityId);
		syncSummoned(player, summoned);

		return army(player).find(shadowId)
				.map(shadow -> Component.translatable("arise.msg.shadow.recalled_one", shadow.displayName()))
				.orElseGet(() -> Component.translatable("arise.msg.shadow.recalled", 1));
	}

	public static Component recall(ServerPlayer player) {
		Map<UUID, UUID> summoned = pruneSummoned(player);

		if (summoned.isEmpty()) {
			return Component.translatable("arise.msg.shadow.nothing_to_recall");
		}

		int recalled = 0;
		for (UUID entityId : List.copyOf(summoned.values())) {
			despawn(player, entityId);
			recalled++;
		}

		summoned.clear();
		syncSummoned(player, summoned);
		return Component.translatable("arise.msg.shadow.recalled", recalled);
	}

	private static boolean spawn(ServerPlayer player, ShadowData shadow, Map<UUID, UUID> summoned) {
		ServerLevel level = player.level();
		ShadowEntity entity = ModEntities.SHADOW.create(level, EntitySpawnReason.MOB_SUMMONED);

		if (entity == null) {
			return false;
		}

		// Sparse attorno al giocatore invece che tutte sullo stesso blocco: altrimenti si
		// spingono a vicenda e partono in una direzione casuale.
		double angle = level.getRandom().nextDouble() * Math.PI * 2;
		double distance = 1.5 + level.getRandom().nextDouble();
		entity.snapTo(player.getX() + Math.cos(angle) * distance, player.getY(),
				player.getZ() + Math.sin(angle) * distance, player.getYRot(), 0.0F);

		entity.applyData(shadow, player);

		if (!level.addFreshEntity(entity)) {
			return false;
		}

		summoned.put(shadow.id(), entity.getUUID());
		AriseFx.summon(level, entity.position(), shadow.color());
		return true;
	}

	private static void despawn(ServerPlayer player, UUID entityId) {
		Entity entity = player.level().getEntityInAnyDimension(entityId);

		if (entity != null) {
			int color = entity instanceof ShadowEntity shadow ? shadow.getColor() : ShadowData.DEFAULT_COLOR;
			AriseFx.recall(player.level(), entity.position(), color);
			entity.discard();
		}
	}

	private static void playSummonSound(ServerPlayer player) {
		AriseFx.summonSound(player.level(), player.position());
	}

	// ---------------------------------------------------------------- crescita

	/**
	 * Distribuisce l'XP di un'uccisione alle ombre evocate.
	 *
	 * <p>Chi ha inferto il colpo prende tutto, le altre presenti una quota: così le veterane si
	 * distinguono, ma un'ombra da supporto non resta indietro per sempre.
	 *
	 * @param killerShadowId l'ombra che ha ucciso, oppure {@code null} se è stato il giocatore
	 */
	public static void awardXp(ServerPlayer player, long amount, UUID killerShadowId) {
		Map<UUID, UUID> summoned = pruneSummoned(player);
		if (summoned.isEmpty() || amount <= 0) {
			return;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army(player);
		boolean changed = false;

		for (UUID shadowId : summoned.keySet()) {
			Optional<ShadowData> current = army.find(shadowId);
			if (current.isEmpty()) {
				continue;
			}

			long share = shadowId.equals(killerShadowId)
					? amount
					: (long) (amount * config.leveling().xpShare());

			ShadowData updated = current.get().withXpGained(share, config);
			if (updated == current.get()) {
				continue;
			}

			army = army.replace(updated);
			changed = true;

			if (updated.level() > current.get().level()) {
				onShadowLevelUp(player, current.get(), updated, summoned.get(shadowId), config);
			}
		}

		if (changed) {
			setArmy(player, army);
		}
	}

	private static void onShadowLevelUp(ServerPlayer player, ShadowData before, ShadowData shadow,
			UUID entityId, ShadowConfig config) {
		player.sendSystemMessage(Component.translatable("arise.msg.shadow.level_up",
				shadow.displayName(), shadow.level()));

		// L'entità già nel mondo va aggiornata subito: altrimenti il livello sale ma l'ombra
		// continua a colpire come prima fino al prossimo richiamo.
		if (!(player.level().getEntityInAnyDimension(entityId) instanceof ShadowEntity entity)) {
			return;
		}

		entity.applyData(shadow, player, false);
		celebrate(player.level(), entity.position(), before, shadow, config);
	}

	/**
	 * Il riscontro visivo di una crescita: fanfara solo quando cambia il rango.
	 *
	 * <p>Un'ombra sale di livello spesso, di rango raramente. Se i due momenti suonassero uguali,
	 * il secondo — che è quello che conta — passerebbe inosservato.
	 */
	private static void celebrate(ServerLevel level, Vec3 position, ShadowData before,
			ShadowData after, ShadowConfig config) {
		Rank rank = after.rank(config);

		if (rank != before.rank(config)) {
			AriseFx.rankUp(level, position, rank);
		} else {
			AriseFx.summon(level, position, after.color());
		}
	}

	/** L'ombra caduta smette di essere evocata, ma resta nell'esercito. */
	public static void onSummonedDied(ServerPlayer owner, ShadowEntity entity) {
		UUID shadowId = entity.getShadowId();
		Map<UUID, UUID> summoned = SUMMONED.get(owner.getUUID());

		if (summoned != null && shadowId != null) {
			summoned.remove(shadowId);
			syncSummoned(owner, summoned);
		}

		AriseFx.shadowFell(owner.level(), entity.position(), entity.getColor());

		army(owner).find(shadowId).ifPresent(shadow ->
				owner.sendSystemMessage(Component.translatable("arise.msg.shadow.fallen", shadow.displayName())));
	}

	/** All'uscita del giocatore le entità vanno rimosse: i dati bastano a ricostruirle. */
	public static void onPlayerLeave(ServerPlayer player) {
		Map<UUID, UUID> summoned = SUMMONED.remove(player.getUUID());

		if (summoned != null) {
			for (UUID entityId : summoned.values()) {
				despawn(player, entityId);
			}
		}

		CANDIDATES.remove(player.getUUID());
	}

	/** Ripulisce dalle evocazioni morte o sparite, e restituisce la mappa viva. */
	private static Map<UUID, UUID> pruneSummoned(ServerPlayer player) {
		Map<UUID, UUID> summoned = SUMMONED.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
		int before = summoned.size();

		summoned.values().removeIf(entityId -> {
			Entity entity = player.level().getEntityInAnyDimension(entityId);
			return entity == null || entity.isRemoved();
		});

		if (summoned.size() != before) {
			syncSummoned(player, summoned);
		}

		return summoned;
	}

	/**
	 * Manda al client l'elenco delle ombre evocate.
	 *
	 * <p>Il client non può dedurlo da solo — le entità possono essere fuori dai chunk caricati —
	 * e senza questo la schermata dell'esercito mostrerebbe il bottone sbagliato.
	 */
	private static void syncSummoned(ServerPlayer player, Map<UUID, UUID> summoned) {
		player.setAttached(ModAttachments.SUMMONED, new SummonedShadows(List.copyOf(summoned.keySet())));
	}

	// ---------------------------------------------------------------- postura

	public static ShadowStance stance(ServerPlayer player) {
		ShadowStance stance = player.getAttached(ModAttachments.STANCE);
		return stance == null ? ShadowStance.DEFENSIVE : stance;
	}

	public static Component setStance(ServerPlayer player, ShadowStance stance) {
		player.setAttached(ModAttachments.STANCE, stance);

		// In passiva le ombre già ingaggiate devono mollare la presa subito, altrimenti l'ordine
		// sembra ignorato finché il bersaglio non muore.
		if (stance == ShadowStance.PASSIVE) {
			forEachSummoned(player, entity -> entity.setTarget(null));
		}

		return Component.translatable("arise.msg.shadow.stance", stance.label());
	}

	public static Component cycleStance(ServerPlayer player) {
		return setStance(player, stance(player).next());
	}

	// ---------------------------------------------------------------- gestione

	/** Congeda un'ombra: sparisce dall'esercito e restituisce una parte del suo valore. */
	public static Component dismiss(ServerPlayer player, UUID shadowId) {
		Optional<ShadowData> shadow = army(player).find(shadowId);
		if (shadow.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		recallOne(player, shadowId);

		ShadowConfig config = AriseConfig.get().shadows();
		long refund = (long) (valueOf(shadow.get(), config) * config.costs().dismissRefund());

		setArmy(player, army(player).without(shadowId));
		ProgressManager.addSouls(player, refund);

		return Component.translatable("arise.msg.shadow.dismissed", shadow.get().displayName(), refund);
	}

	/** Quanto "vale" un'ombra in soul coin: serve solo a calcolare il rimborso del congedo. */
	private static long valueOf(ShadowData shadow, ShadowConfig config) {
		long spent = 0L;
		for (int level = 1; level < shadow.level(); level++) {
			spent += config.costs().upgradeCost(level);
		}
		return spent + (long) shadow.powerScore();
	}

	public static Component rename(ServerPlayer player, UUID shadowId, String name) {
		return modify(player, shadowId, AriseConfig.get().shadows().costs().rename(),
				shadow -> shadow.withName(name),
				updated -> Component.translatable("arise.msg.shadow.renamed", updated.displayName()));
	}

	public static Component recolor(ServerPlayer player, UUID shadowId, int color) {
		return modify(player, shadowId, AriseConfig.get().shadows().costs().recolor(),
				shadow -> shadow.withColor(color),
				updated -> Component.translatable("arise.msg.shadow.recolored", updated.displayName()));
	}

	public static Component upgrade(ServerPlayer player, UUID shadowId) {
		ShadowConfig config = AriseConfig.get().shadows();
		Optional<ShadowData> current = army(player).find(shadowId);

		if (current.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		if (current.get().isMaxLevel(config)) {
			return Component.translatable("arise.msg.shadow.max_level");
		}

		return modify(player, shadowId, config.costs().upgradeCost(current.get().level()),
				ShadowData::withLevelUp,
				updated -> Component.translatable("arise.msg.shadow.upgraded",
						updated.displayName(), updated.level()));
	}

	/**
	 * Applica una modifica a pagamento a una singola ombra.
	 *
	 * <p>Un solo punto in cui si paga, si aggiorna l'esercito e si rinfresca l'entità evocata:
	 * ogni operazione che dimenticasse uno dei tre passaggi produrrebbe un'incoerenza silenziosa
	 * fra i dati e ciò che si vede nel mondo.
	 */
	private static Component modify(ServerPlayer player, UUID shadowId, long cost,
			UnaryOperator<ShadowData> change, Function<ShadowData, Component> feedback) {
		Optional<ShadowData> current = army(player).find(shadowId);
		if (current.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		if (!ProgressManager.spendSouls(player, cost)) {
			return Component.translatable("arise.msg.shadow.not_enough_souls",
					cost, ProgressManager.souls(player));
		}

		ShadowData updated = change.apply(current.get());
		setArmy(player, army(player).replace(updated));

		UUID entityId = pruneSummoned(player).get(shadowId);
		Vec3 where = player.position();
		if (entityId != null
				&& player.level().getEntityInAnyDimension(entityId) instanceof ShadowEntity entity) {
			entity.applyData(updated, player, false);
			where = entity.position();
		}

		celebrate(player.level(), where, current.get(), updated, AriseConfig.get().shadows());

		return feedback.apply(updated);
	}

	private static void forEachSummoned(ServerPlayer player, java.util.function.Consumer<ShadowEntity> action) {
		for (UUID entityId : pruneSummoned(player).values()) {
			if (player.level().getEntityInAnyDimension(entityId) instanceof ShadowEntity entity) {
				action.accept(entity);
			}
		}
	}

	// ---------------------------------------------------------------- debug

	public static void clear(ServerPlayer player) {
		recall(player);
		setArmy(player, ShadowArmy.EMPTY);
	}
}
