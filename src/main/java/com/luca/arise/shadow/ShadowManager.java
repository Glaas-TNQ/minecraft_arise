package com.luca.arise.shadow;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.AriseAdvancements;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.StatThreshold;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.registry.ModEntities;
import com.luca.arise.workshop.LooseSoul;
import com.luca.arise.workshop.SoulItems;
import com.luca.arise.workshop.WorkshopManager;

import net.minecraft.core.BlockPos;
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

	/** Un cadavere ancora estraibile. L'archetipo si decide qui, finche' il corpo c'e' ancora. */
	private record Candidate(Identifier sourceType, ShadowArchetype archetype, Vec3 position,
			double maxHealth, double attackDamage, long expiresAtTick) {
	}

	/**
	 * Quanto le altre ombre in campo rendono piu' forte questa. Moltiplicatori, non valori.
	 *
	 * <p>Zero e' il caso normale: l'aura la emana solo chi ha almeno il grado di Comandante, e
	 * Comandanti non se ne hanno finche' non si e' fatta molta strada. E' esattamente quello che
	 * deve essere — una ricompensa da fine partita che cambia il modo di comporre la squadra, non
	 * un numero che c'e' sempre.
	 */
	public record Aura(double damage, double health) {

		public static final Aura NONE = new Aura(0.0, 0.0);
	}

	private static final Map<UUID, List<Candidate>> CANDIDATES = new HashMap<>();

	/** giocatore → (id dell'ombra → id dell'entità evocata). */
	private static final Map<UUID, Map<UUID, UUID>> SUMMONED = new HashMap<>();

	/**
	 * giocatore → quanto valeva il Monarca l'ultima volta che l'esercito e' stato riallineato.
	 *
	 * <p>Serve al dono del Monarca (vedi {@code ShadowEntity.applyData}): una parte della vita e
	 * del danno del giocatore finisce in ogni ombra evocata, quindi quando il giocatore cambia —
	 * sale di livello, indossa un pezzo, incastona una gemma — le ombre gia' in campo vanno
	 * riscritte. Confrontare due numeri una volta al secondo costa niente; riscrivere quattro
	 * eserciti a ogni battito costerebbe, e per giunta per niente.
	 */
	private static final Map<UUID, Double> MONARCH_MARK = new HashMap<>();

	/**
	 * Quanto stretto e' il cono in cui si puo' indicare un bersaglio: coseno dell'angolo.
	 *
	 * <p>0,995 sono poco piu' di cinque gradi. Piu' largo e si indica il mob sbagliato in una
	 * stanza affollata, che e' l'unico errore davvero fastidioso in un ordine di questo tipo.
	 */
	private static final double AIM_CONE = 0.995;

	/**
	 * Il recupero delle ombre oltre il ventesimo gradino dell'Abisso: un'ora di gioco.
	 *
	 * <p>Non un tempo piu' lungo: un tempo che, dentro una discesa, non scorre mai abbastanza.
	 * L'uscita lo azzera comunque — la punizione e' la discesa, non la partita.
	 */
	private static final int UNFORGIVING_DOWNTIME = 72000;

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

	/**
	 * Quante ombre puo' tenere in campo <em>questo</em> Cacciatore.
	 *
	 * <p>Erano quattro per tutti, quanti sono gli archetipi, ed era un numero buono. Le soglie di
	 * Forza lo rendono una scelta: cento punti spesi li' valgono due posti in piu', cioe' sei
	 * ombre insieme — e sono cento punti che non sono andati in Vitalita'. E' la prima volta che
	 * una statistica cambia <em>quante</em> cose puoi fare invece di quanto forte le fai.
	 */
	public static int summonLimit(ServerPlayer player) {
		int limit = StatThreshold.summonLimit(ProgressManager.get(player),
				AriseConfig.get().shadows().maxSummoned());

		// L'Abisso puo' dimezzarlo, dal quindicesimo gradino in giu'. La regola vive li' e non qui:
		// chi legge l'esercito non deve trovarci dentro le condizioni di un dungeon.
		return com.luca.arise.gate.GateManager.summonLimitIn(player, limit);
	}

	// ---------------------------------------------------------------- estrazione

	/**
	 * Creature da cui non si estrae niente.
	 *
	 * <p>Un tag e non un elenco nel codice, ed e' la prima cosa dell'esercito che si estende da
	 * datapack: un pack che aggiunge un NPC amichevole non deve aspettare una versione della mod per
	 * impedire che qualcuno lo trasformi in un soldato. Dentro ci sono i tre casi che erano gia'
	 * sbagliati — i villager, i golem di ferro dei villaggi, e le ombre stesse — piu' i supporti da
	 * armatura, che non hanno un'ombra perche' non hanno mai avuto niente.
	 */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>
			NOT_EXTRACTABLE = net.minecraft.tags.TagKey.create(
					net.minecraft.core.registries.Registries.ENTITY_TYPE,
					com.luca.arise.AriseMod.id("not_extractable"));

	/** Registra un cadavere come estraibile per qualche secondo. */
	public static void recordKill(ServerPlayer killer, LivingEntity victim) {
		// EntityType non ha un `is(tag)`: la domanda si fa all'holder del registro, che e' lo stesso
		// oggetto a cui il datapack attacca il tag.
		if (victim.getType().builtInRegistryHolder().is(NOT_EXTRACTABLE)) {
			return;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());

		double attackDamage = 2.0;
		AttributeInstance instance = victim.getAttribute(Attributes.ATTACK_DAMAGE);
		if (instance != null) {
			attackDamage = instance.getValue();
		}

		CANDIDATES.computeIfAbsent(killer.getUUID(), key -> new ArrayList<>())
				.add(new Candidate(type, ShadowArchetype.of(victim, config.legion().behaviour()),
						victim.position(), victim.getMaxHealth(), attackDamage,
						killer.level().getGameTime() + config.extractionWindowTicks()));
	}

	/**
	 * Guarda il cadavere più vicino senza toccarlo: rango, archetipo, probabilità.
	 *
	 * <p>Il gemello innocuo di {@link #extract}. La differenza sta tutta in una parola:
	 * {@code peek} invece di {@code take} — il candidato <em>non</em> viene rimosso dalla lista, e
	 * quindi l'anteprima si puo' chiedere quante volte si vuole finche' il corpo e' fresco.
	 *
	 * <p>Serve perche' l'estrazione ha due proprieta' che insieme fanno male: e' a probabilita', e
	 * consuma il cadavere comunque. Sapere <em>prima</em> che quel corpo darebbe un Colosso di
	 * rango A e che si sta tirando al trenta per cento non toglie niente al tiro — cambia solo la
	 * qualita' della decisione, che e' esattamente cio' che un tiro di dado dovrebbe avere attorno.
	 */
	public static Component survey(ServerPlayer player) {
		Component locked = QuestManager.require(player, Unlock.ARMY);
		if (locked != null) {
			return locked;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		Candidate best = peekNearestCandidate(player, player.level().getGameTime(),
				config.extractionRange());

		if (best == null) {
			return Component.translatable("arise.msg.shadow.no_target");
		}

		// Il rango si legge dalla stessa formula che lo decidera' davvero, applicata ai numeri che
		// l'ombra avrebbe: se qui dicesse un rango e l'estrazione ne producesse un altro,
		// l'anteprima sarebbe peggio di niente.
		Rank rank = Rank.fromScore(
				best.maxHealth() * config.healthFactor()
						+ best.attackDamage() * config.damageFactor() * ShadowData.DAMAGE_WEIGHT,
				config.rankThresholds());

		double chance = config.extractionChanceAt(ProgressManager.get(player).level());

		return Component.translatable("arise.msg.shadow.survey",
				rank.label(), best.archetype().label(),
				String.format("%.0f", chance * 100));
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

		// A esercito pieno non si rinuncia piu' al cadavere: l'estrazione si tenta lo stesso, e
		// quello che ne esce e' un'anima da mettere a lavorare invece che un'ombra da comandare.
		// E' l'ingresso dell'Officina, ed e' il motivo per cui il tetto dell'esercito ha smesso
		// di essere una parete ed e' diventato un bivio.
		boolean full = army.size() >= capacity;

		// A esercito pieno l'anima si conserva solo se l'Officina e' gia' stata concessa: prima
		// della Via dell'Artigiano un'Anima Errante sarebbe un oggetto senza spiegazione.
		if (full && !QuestManager.has(player, Unlock.WORKSHOP)) {
			return Component.translatable("arise.msg.shadow.army_full", capacity);
		}

		if (full && !AriseConfig.get().workshop().overflowSoul()) {
			return Component.translatable("arise.msg.shadow.army_full", capacity);
		}

		double chance = config.extractionChanceAt(ProgressManager.get(player).level());

		AriseFx.extractionRitual(level, best.position());

		if (level.getRandom().nextDouble() > chance) {
			AriseFx.extractionFailed(level, best.position());
			return Component.translatable("arise.msg.shadow.extraction_failed",
					String.format("%.0f", chance * 100));
		}

		if (full) {
			LooseSoul soul = LooseSoul.of(best.sourceType(),
					best.maxHealth() * config.healthFactor(),
					best.attackDamage() * config.damageFactor());

			AriseFx.extractionSuccess(level, best.position(), SoulItems.rankOf(soul));
			QuestManager.advance(player, Objective.EXTRACT);
			WorkshopManager.grantSoul(player, soul);

			return Component.translatable("arise.msg.shadow.overflow", capacity);
		}

		// Le ombre nascono sempre al livello 1: la loro forza viene dal mob d'origine e da quanto
		// hanno combattuto, non da quanto era avanti il giocatore quando le ha estratte.
		// Il contrario premierebbe l'accumulo di cadaveri in attesa di salire di livello.
		ShadowData shadow = new ShadowData(UUID.randomUUID(), best.sourceType(), best.archetype(), 1, 0L,
				best.maxHealth() * config.healthFactor(),
				best.attackDamage() * config.damageFactor(),
				Optional.empty(), ShadowData.DEFAULT_COLOR);

		setArmy(player, army.with(shadow));

		AriseAdvancements.award(player, AriseAdvancements.FIRST_SHADOW);
		AriseFx.extractionSuccess(level, best.position(), shadow.rank(config));
		QuestManager.advance(player, Objective.EXTRACT);

		// L'archetipo nel messaggio non e' decorazione: e' l'unica volta in cui il gioco dice al
		// giocatore che quel cadavere ha prodotto un Mago invece di una Guardia, e da li' in poi
		// quella parola e' l'unica ragione per cui sceglierla in squadra.
		return Component.translatable("arise.msg.shadow.extracted", shadow.displayName(),
				shadow.archetype().label(), shadow.rank(config).label(), army.size() + 1, capacity);
	}

	/**
	 * Estrae dalla lista il candidato valido più vicino, scartando gli scaduti.
	 *
	 * <p>Il candidato viene <em>rimosso</em> a prescindere dall'esito del tiro: un cadavere si
	 * tenta una volta sola, altrimenti basterebbe premere il tasto a raffica per aggirare la
	 * probabilità.
	 */
	private static Candidate takeNearestCandidate(ServerPlayer player, long now, double range) {
		Candidate best = peekNearestCandidate(player, now, range);
		List<Candidate> candidates = CANDIDATES.get(player.getUUID());

		if (candidates == null) {
			return best;
		}

		if (best != null) {
			candidates.remove(best);
		}

		if (candidates.isEmpty()) {
			CANDIDATES.remove(player.getUUID());
		}

		return best;
	}

	/**
	 * Il candidato valido più vicino, <em>senza</em> toglierlo dalla lista.
	 *
	 * <p>Scarta comunque gli scaduti mentre cerca: la pulizia e' una conseguenza del passare del
	 * tempo, non del tentare l'estrazione, e farla anche in anteprima significa che l'elenco non
	 * cresce mai per un giocatore che guarda e basta.
	 */
	private static Candidate peekNearestCandidate(ServerPlayer player, long now, double range) {
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

		return best;
	}

	// ---------------------------------------------------------------- le nominate

	/**
	 * Consegna una delle sette ombre nominate, se il giocatore non ce l'ha gia'.
	 *
	 * <p>Non passa dall'estrazione e non tira nessun dado: una nominata o si e' guadagnata o no.
	 * Non rispetta nemmeno il tetto dell'esercito — arrivano da condizioni che si incontrano una
	 * volta sola in tutta la partita, e perderne una perche' quel giorno c'erano sei ombre in
	 * elenco sarebbe la cosa piu' crudele che questa mod possa fare.
	 *
	 * <p>Annuncia da sola, e con la regia giusta: un titolo a schermo e una riga che dice cosa
	 * quell'ombra fa di diverso. Una nominata che arrivasse in silenzio in fondo a un elenco
	 * sarebbe indistinguibile da un'estrazione fortunata.
	 *
	 * @return vero se l'ombra e' entrata adesso, falso se il giocatore l'aveva gia'
	 */
	public static boolean grantNamed(ServerPlayer player, NamedShadow which) {
		ShadowArmy army = army(player);

		if (army.hasNamed(which)) {
			return false;
		}

		ShadowData shadow = which.create();
		setArmy(player, army.with(shadow));

		AriseFx.extractionSuccess(player.level(), player.position(),
				shadow.rank(AriseConfig.get().shadows()));

		AriseAdvancements.award(player, AriseAdvancements.NAMED_SHADOW);
		Overlay.title(player, Component.translatable("arise.title.named_joined"), which.label());
		player.sendSystemMessage(Component.translatable("arise.msg.shadow.named_joined",
				which.label(), which.description()));

		return true;
	}

	/** Vero se questa nominata e' gia' nell'esercito del giocatore. */
	public static boolean hasNamed(ServerPlayer player, NamedShadow which) {
		return army(player).hasNamed(which);
	}

	/** Vero se questa nominata e' <em>in campo</em> adesso: e' cio' che conta per Greed e Beru. */
	public static boolean isNamedSummoned(ServerPlayer player, NamedShadow which) {
		Map<UUID, UUID> summoned = SUMMONED.get(player.getUUID());

		if (summoned == null || summoned.isEmpty()) {
			return false;
		}

		ShadowArmy army = army(player);

		return summoned.keySet().stream()
				.map(id -> army.find(id).orElse(null))
				.anyMatch(shadow -> shadow != null && shadow.named().orElse(null) == which);
	}

	// ---------------------------------------------------------------- evocazione

	public static Component summon(ServerPlayer player) {
		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army(player);

		if (army.isEmpty()) {
			return Component.translatable("arise.msg.shadow.army_empty");
		}

		Map<UUID, UUID> summoned = pruneSummoned(player);
		int slots = summonLimit(player) - summoned.size();

		if (slots <= 0) {
			return Component.translatable("arise.msg.shadow.summon_limit", summonLimit(player));
		}

		ShadowDowntime downtime = downtime(player);
		long now = player.level().getGameTime();

		int spawned = 0;
		int resting = 0;

		for (ShadowData shadow : callUp(player, army, config)) {
			if (summoned.containsKey(shadow.id())) {
				continue;
			}

			// Un'ombra caduta da poco si conta ma non si evoca. Il tasto non fallisce: manda in
			// campo quelle che ci sono. Rifiutare in blocco perche' una su dieci non e' pronta
			// sarebbe la peggiore delle due risposte possibili.
			if (downtime.isDown(shadow.id(), now)) {
				resting++;
				continue;
			}

			if (slots <= 0) {
				continue;
			}

			if (spawn(player, shadow, summoned)) {
				spawned++;
				slots--;
			}
		}

		if (spawned == 0) {
			return resting > 0
					? Component.translatable("arise.msg.shadow.all_resting", resting)
					: Component.translatable("arise.msg.shadow.already_summoned");
		}

		playSummonSound(player);
		syncSummoned(player, summoned);
		refreshLegion(player);

		return resting > 0
				? Component.translatable("arise.msg.shadow.summoned_partial", spawned, resting)
				: Component.translatable("arise.msg.shadow.summoned", spawned);
	}

	/**
	 * In che ordine chiamare l'esercito.
	 *
	 * <p>Prima la <strong>squadra</strong>, nell'ordine in cui e' stata composta; poi, se avanza
	 * posto, le altre <strong>dalla piu' forte alla piu' debole</strong>.
	 *
	 * <p>Prima di questo blocco l'ordine era quello della lista, cioe' quello di estrazione: il
	 * tasto mandava fuori le prime quattro ombre <em>mai estratte</em>, che dopo dieci ore sono le
	 * quattro piu' deboli che si possiedono. Era il difetto piu' grosso dell'esercito, e non si
	 * vedeva perche' il gioco non diceva mai quali stesse chiamando.
	 *
	 * <p>La squadra vuota non e' un errore: chi non l'ha composta riceve semplicemente le piu'
	 * forti, che e' quello che avrebbe scelto comunque.
	 */
	private static List<ShadowData> callUp(ServerPlayer player, ShadowArmy army, ShadowConfig config) {
		return callUpOrder(army, squad(player), config);
	}

	/**
	 * La stessa cosa senza giocatore: e' qui che vive la regola, ed e' l'unico modo di provarla.
	 *
	 * <p>Un ordine di chiamata sbagliato non da' un errore: da' un esercito che manda fuori le
	 * ombre sbagliate, cioe' esattamente il difetto che questo blocco esiste per correggere. Va
	 * sul banco di prova come i codec.
	 */
	public static List<ShadowData> callUpOrder(ShadowArmy army, ShadowSquad squad, ShadowConfig config) {
		List<ShadowData> order = new ArrayList<>(army.size());

		for (UUID id : squad.ids()) {
			army.find(id).ifPresent(order::add);
		}

		// Con il completamento spento la squadra e' un elenco chiuso — ma solo se esiste.
		if (!config.legion().fillFromRoster() && !order.isEmpty()) {
			return order;
		}

		List<ShadowData> rest = new ArrayList<>(army.shadows());
		rest.removeIf(shadow -> squad.contains(shadow.id()));
		rest.sort(Comparator.comparingDouble((ShadowData shadow) -> shadow.effectivePower(config)).reversed());

		order.addAll(rest);
		return order;
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

		if (summoned.size() >= summonLimit(player)) {
			return Component.translatable("arise.msg.shadow.summon_limit", summonLimit(player));
		}

		long remaining = downtime(player).remaining(shadowId, player.level().getGameTime());
		if (remaining > 0) {
			return Component.translatable("arise.msg.shadow.resting",
					shadow.get().displayName(), Math.max(1L, remaining / 20L));
		}

		if (!spawn(player, shadow.get(), summoned)) {
			return Component.translatable("arise.msg.shadow.summon_failed");
		}

		playSummonSound(player);
		syncSummoned(player, summoned);
		refreshLegion(player);
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
		refreshLegion(player);

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
		clearOrders(player);
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
		speak(player, shadow);
		return true;
	}

	/**
	 * Igris parla, quando e' abbastanza cresciuto. Nient'altro nell'esercito dice mai niente.
	 *
	 * <p>Una riga sola, e solo dal grado di Maresciallo in su, che vuol dire dopo molte ore. E'
	 * una battuta di dialogo e non una meccanica, ed e' esattamente per questo che vale: in una
	 * mod dove trenta ombre sono numeri in un elenco, una che a un certo punto risponde smette di
	 * essere un numero.
	 */
	private static void speak(ServerPlayer player, ShadowData shadow) {
		NamedShadow named = shadow.named().orElse(null);

		if (named != NamedShadow.IGRIS
				|| shadow.grade(AriseConfig.get().shadows()).ordinal() < ShadowGrade.MARSHAL.ordinal()) {
			return;
		}

		player.sendSystemMessage(Component.translatable("arise.msg.shadow.named_speaks",
				named.label(), named.line()));
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

		// Il traguardo guarda il grado e non il livello: un Gran Maresciallo e' un'ombra che ha
		// combattuto abbastanza da comandarne altre quattro, e il livello da solo non lo dice.
		if (shadow.grade(config) == ShadowGrade.GRAND_MARSHAL
				&& before.grade(config) != ShadowGrade.GRAND_MARSHAL) {
			AriseAdvancements.award(player, AriseAdvancements.GRAND_MARSHAL);
		}
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

		long now = owner.level().getGameTime();

		// La quarta regola dell'Abisso: dal ventesimo gradino in giu' un'ombra caduta non torna
		// fino all'uscita. Non e' un tempo piu' lungo, e' un tempo che non scorre — ed e' la
		// differenza che trasforma l'esercito da risorsa rinnovabile in risorsa finita.
		//
		// Un'ora di gioco e' un'ora: chi resta dentro cosi' a lungo ha comunque scelto di restarci,
		// e chi esce ritrova tutto pronto. La discesa e' la punizione, non la partita.
		int downtimeTicks = com.luca.arise.gate.GateManager.depthOf(owner)
						>= com.luca.arise.gate.AbyssRule.UNFORGIVING.depth()
				? UNFORGIVING_DOWNTIME
				: AriseConfig.get().shadows().downtimeTicks();

		if (shadowId != null && downtimeTicks > 0) {
			owner.setAttached(ModAttachments.DOWNTIME,
					downtime(owner).with(shadowId, now + downtimeTicks, now));
		}

		army(owner).find(shadowId).ifPresent(shadow ->
				owner.sendSystemMessage(Component.translatable("arise.msg.shadow.fallen",
						shadow.displayName(), Math.max(1, downtimeTicks / 20))));

		// Se la caduta era un Comandante, chi resta in campo perde l'aura: le statistiche vanno
		// riscritte subito, o l'esercito continuerebbe a colpire come se lui fosse ancora li'.
		refreshLegion(owner);
	}

	/** All'uscita del giocatore le entità vanno rimosse: i dati bastano a ricostruirle. */
	/**
	 * Vero se questa entita' e' una delle evocazioni vive di questo giocatore.
	 *
	 * <p>Serve all'ombra stessa: un'entita' che non risulta piu' evocata e' un residuo, e deve
	 * togliersi di mezzo da sola. Vedi {@code ShadowEntity.tick}.
	 */
	public static boolean isSummoned(ServerPlayer owner, UUID entityId) {
		Map<UUID, UUID> summoned = SUMMONED.get(owner.getUUID());
		return summoned != null && summoned.containsValue(entityId);
	}

	/**
	 * Il giocatore ha cambiato mondo: l'esercito rientra.
	 *
	 * <p>E' la correzione di un difetto visto in gioco, e vale la pena scrivere come nasceva.
	 * Le ombre evocate restavano nella dimensione da cui si usciva; il loro chunk si scaricava; da
	 * quel momento {@code getEntityInAnyDimension} rispondeva "non c'e'" e
	 * {@link #pruneSummoned} le toglieva dall'elenco. Alla successiva evocazione ne nascevano
	 * altrettante nuove — e le vecchie erano ancora li', salvate su disco, pronte a ricomparire
	 * appena il giocatore fosse tornato in zona. Un varco, dieci ombre.
	 *
	 * <p>Il richiamo si fa <em>qui</em> e non a ogni battito perche' qui le entita' sono ancora
	 * raggiungibili: il mondo di partenza arriva come argomento, e i suoi chunk sono ancora
	 * caricati nell'istante in cui l'evento scatta.
	 */
	public static void onLevelChange(ServerPlayer player, ServerLevel origin) {
		Map<UUID, UUID> summoned = SUMMONED.get(player.getUUID());

		if (summoned == null || summoned.isEmpty()) {
			return;
		}

		int recalled = 0;
		for (UUID entityId : summoned.values()) {
			Entity entity = origin.getEntity(entityId);

			if (entity != null) {
				entity.discard();
				recalled++;
			}
		}

		summoned.clear();
		syncSummoned(player, summoned);
		clearOrders(player);

		if (recalled > 0) {
			player.sendSystemMessage(Component.translatable("arise.msg.shadow.recalled_travel", recalled));
		}
	}

	public static void onPlayerLeave(ServerPlayer player) {
		Map<UUID, UUID> summoned = SUMMONED.remove(player.getUUID());

		if (summoned != null) {
			for (UUID entityId : summoned.values()) {
				despawn(player, entityId);
			}
		}

		CANDIDATES.remove(player.getUUID());
		MONARCH_MARK.remove(player.getUUID());
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

	// ---------------------------------------------------------------- squadra

	/** La squadra del giocatore, gia' ripulita da chi non fa piu' parte dell'esercito. */
	public static ShadowSquad squad(ServerPlayer player) {
		ShadowSquad current = player.getAttachedOrCreate(ModAttachments.SQUAD);
		ShadowSquad pruned = current.pruned(army(player));

		if (pruned != current) {
			player.setAttached(ModAttachments.SQUAD, pruned);
		}

		return pruned;
	}

	/** Mette o toglie un'ombra dalla squadra. Il tetto e' quello delle evocazioni contemporanee. */
	public static Component toggleSquad(ServerPlayer player, UUID shadowId) {
		Optional<ShadowData> shadow = army(player).find(shadowId);
		if (shadow.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		int capacity = summonLimit(player);
		ShadowSquad current = squad(player);
		ShadowSquad updated = current.toggled(shadowId, capacity);

		if (updated == current) {
			return Component.translatable("arise.msg.shadow.squad_full", capacity);
		}

		player.setAttached(ModAttachments.SQUAD, updated);

		return Component.translatable(updated.contains(shadowId)
						? "arise.msg.shadow.squad_added"
						: "arise.msg.shadow.squad_removed",
				shadow.get().displayName(), updated.size(), capacity);
	}

	// ---------------------------------------------------------------- ordini

	public static ShadowOrders orders(ServerPlayer player) {
		ShadowOrders orders = player.getAttached(ModAttachments.ORDERS);
		return orders == null ? ShadowOrders.NONE : orders;
	}

	private static void setOrders(ServerPlayer player, ShadowOrders orders) {
		player.setAttached(ModAttachments.ORDERS, orders);
	}

	public static void clearOrders(ServerPlayer player) {
		if (!orders(player).isIdle()) {
			setOrders(player, ShadowOrders.NONE);
		}
	}

	/**
	 * "Uccidetelo": l'esercito punta cio' che il Monarca sta guardando.
	 *
	 * <p>Premuto senza niente nel mirino, l'ordine si revoca invece di fallire. E' la stessa scelta
	 * di ogni tasto di questa mod: un comando che non fa niente e non spiega perche' e' peggio di
	 * un comando che fa la cosa opposta ma la dice.
	 */
	public static Component focus(ServerPlayer player) {
		if (summonedCount(player) == 0) {
			return Component.translatable("arise.msg.shadow.nothing_summoned");
		}

		LivingEntity target = aimedAt(player, AriseConfig.get().shadows().legion().behaviour().focusRange());

		if (target == null) {
			if (orders(player).hasFocus()) {
				setOrders(player, orders(player).withoutFocus());
				return Component.translatable("arise.msg.shadow.focus_cleared");
			}

			return Component.translatable("arise.msg.shadow.no_aim");
		}

		setOrders(player, orders(player).withFocus(target.getUUID()));
		AriseFx.shadowFocus(player.level(), target.getBoundingBox().getCenter());

		return Component.translatable("arise.msg.shadow.focus", target.getDisplayName());
	}

	/** "Restate qui": l'esercito tiene la posizione. Ripremuto, torna a seguire. */
	public static Component hold(ServerPlayer player) {
		ShadowOrders current = orders(player);

		if (current.isHolding()) {
			setOrders(player, current.withoutHold());
			return Component.translatable("arise.msg.shadow.hold_released");
		}

		if (summonedCount(player) == 0) {
			return Component.translatable("arise.msg.shadow.nothing_summoned");
		}

		BlockPos post = player.blockPosition();
		setOrders(player, current.withHold(post));
		AriseFx.shadowHold(player.level(), Vec3.atBottomCenterOf(post));

		return Component.translatable("arise.msg.shadow.hold", post.getX(), post.getY(), post.getZ());
	}

	/**
	 * L'essere vivente che il giocatore sta inquadrando, entro la distanza data.
	 *
	 * <p>Un cono attorno alla direzione dello sguardo invece di un raggio esatto: a venti blocchi
	 * un raggio pretende una mira che con un mob che si muove non si ha. Vince il piu' <em>centrato</em>,
	 * non il piu' vicino, che e' quello che si aspetta chiunque abbia un mirino davanti. Serve
	 * comunque la linea di vista: indicare un bersaglio attraverso un muro sarebbe un radar.
	 */
	private static LivingEntity aimedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F).normalize();

		LivingEntity best = null;
		double bestAim = AIM_CONE;

		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(range),
				entity -> entity != player && entity.isAlive()
						&& !ShadowGoals.isProtected(entity, player))) {

			Vec3 toward = candidate.getBoundingBox().getCenter().subtract(eye);
			double distance = toward.length();

			if (distance > range || distance < 1.0E-4) {
				continue;
			}

			double aim = toward.scale(1.0 / distance).dot(look);
			if (aim > bestAim && player.hasLineOfSight(candidate)) {
				bestAim = aim;
				best = candidate;
			}
		}

		return best;
	}

	// ---------------------------------------------------------------- aura di comando

	/**
	 * Quanto le <em>altre</em> ombre in campo potenziano questa.
	 *
	 * <p>L'esclusione non e' un dettaglio: senza, un Gran Maresciallo si darebbe il quaranta per
	 * cento di danno da solo, e la squadra migliore sarebbe sempre "il piu' forte, quattro volte".
	 * Cosi' invece un Comandante costa un posto e lo restituisce agli altri tre, che e' una scelta.
	 *
	 * <p>Legge la mappa grezza e non {@link #pruneSummoned}: viene chiamata da dentro
	 * {@code applyData}, cioe' nel mezzo di un'evocazione, e ripulire li' vorrebbe dire spedire un
	 * pacchetto di sincronia a meta' di un'operazione che non e' ancora finita.
	 */
	public static Aura auraFor(ServerPlayer owner, UUID exclude) {
		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army(owner);
		double damage = 0.0;
		double health = 0.0;

		// Bellion comanda da casa. E' l'unica eccezione alla regola "conta solo chi e' in campo", ed
		// e' anche l'unica cosa in tutta la mod che dia un senso alle ventisei ombre che restano
		// nell'esercito senza uscire mai: il Gran Maresciallo le tiene tutte, non le quattro che
		// hanno un posto. Per questo si eredita alla fine e non cade da nessun boss.
		ShadowData bellion = army.shadows().stream()
				.filter(shadow -> shadow.named().orElse(null) == NamedShadow.BELLION)
				.filter(shadow -> !shadow.id().equals(exclude))
				.findFirst()
				.orElse(null);

		if (bellion != null) {
			damage += bellion.auraDamage(config);
			health += bellion.auraHealth(config);
		}

		Map<UUID, UUID> summoned = SUMMONED.get(owner.getUUID());

		if (summoned == null || summoned.isEmpty()) {
			return damage == 0.0 && health == 0.0 ? Aura.NONE : new Aura(damage, health);
		}

		for (UUID shadowId : summoned.keySet()) {
			if (shadowId.equals(exclude)) {
				continue;
			}

			ShadowData other = army.find(shadowId).orElse(null);

			// Bellion e' gia' stato contato sopra: se e' anche in campo, la sua aura non vale due
			// volte. E' il genere di doppio conteggio che non da' nessun errore e che si nota solo
			// come "le ombre sono piu' forti di quanto dice la schermata".
			if (other == null || other.named().orElse(null) == NamedShadow.BELLION) {
				continue;
			}

			damage += other.auraDamage(config);
			health += other.auraHealth(config);
		}

		return new Aura(damage, health);
	}

	/**
	 * Riscrive le statistiche di tutte le ombre in campo.
	 *
	 * <p>Da chiamare ogni volta che cambia <em>chi</em> c'e' fuori — un'evocazione, un richiamo, una
	 * caduta — perche' l'aura di comando dipende dalla composizione, e ogni volta che cambia il
	 * Monarca, perche' una parte di lui e' dentro ognuna di loro.
	 */
	public static void refreshLegion(ServerPlayer player) {
		ShadowArmy army = army(player);

		for (Map.Entry<UUID, UUID> entry : pruneSummoned(player).entrySet()) {
			ShadowData data = army.find(entry.getKey()).orElse(null);

			if (data != null && player.level().getEntityInAnyDimension(entry.getValue())
					instanceof ShadowEntity entity) {
				entity.applyData(data, player, false);
			}
		}

		MONARCH_MARK.put(player.getUUID(), monarchMark(player));
	}

	/**
	 * Manutenzione al secondo: l'ordine che non ha piu' un bersaglio, e il Monarca che e' cambiato.
	 *
	 * <p>Girano insieme perche' sono le due sole cose dell'esercito che nessun evento annuncia. Un
	 * bersaglio muore e nessuno lo dice all'ordine; un pezzo d'equipaggiamento cambia il danno del
	 * giocatore e nessuno lo dice alle ombre.
	 */
	public static void tick(ServerPlayer player) {
		ShadowOrders orders = orders(player);

		if (orders.hasFocus() && orders.focus()
				.map(id -> !(player.level().getEntity(id) instanceof LivingEntity living) || !living.isAlive())
				.orElse(true)) {
			setOrders(player, orders.withoutFocus());
		}

		Map<UUID, UUID> summoned = SUMMONED.get(player.getUUID());
		if (summoned == null || summoned.isEmpty()) {
			return;
		}

		double mark = monarchMark(player);
		Double previous = MONARCH_MARK.get(player.getUUID());

		if (previous == null || Math.abs(previous - mark) > 1.0E-4) {
			refreshLegion(player);
		}
	}

	/** Il numero che riassume "quanto vale il Monarca adesso": vita e danno in uno. */
	private static double monarchMark(ServerPlayer player) {
		return player.getMaxHealth() * 1000.0 + player.getAttributeValue(Attributes.ATTACK_DAMAGE);
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

		// L'anima torna in mano, col livello che aveva. Congedare smette di essere una perdita e
		// diventa uno spostamento: dall'esercito all'officina, e volendo di nuovo indietro.
		if (AriseConfig.get().workshop().dismissReturnsSoul()) {
			WorkshopManager.grantSoul(player, WorkshopManager.soulOf(shadow.get()));
		}

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

	/**
	 * Da' un nome a un'ombra — se il grado glielo consente.
	 *
	 * <p>Da Cavaliere in su, come nel manhwa: il nome e' un privilegio del grado, non un acquisto.
	 * Il costo in soul coin resta, ma smette di essere l'unico requisito, e la scala dei gradi
	 * smette di essere una decorazione nella schermata.
	 */
	public static Component rename(ServerPlayer player, UUID shadowId, String name) {
		ShadowConfig config = AriseConfig.get().shadows();
		Optional<ShadowData> current = army(player).find(shadowId);

		if (current.isEmpty()) {
			return Component.translatable("arise.msg.shadow.unknown");
		}

		// Igris si chiama Igris. Le sette nominate sono l'unico posto della mod in cui il nome non
		// e' una scelta del giocatore, ed e' proprio quello a renderle personaggi invece che pedine.
		if (current.get().named().isPresent()) {
			return Component.translatable("arise.msg.shadow.named_keeps_name",
					current.get().displayName());
		}

		ShadowGrade grade = current.get().grade(config);
		if (!grade.canBeNamed()) {
			return Component.translatable("arise.msg.shadow.cannot_name",
					current.get().displayName(), ShadowGrade.KNIGHT.label(), grade.label());
		}

		return modify(player, shadowId, config.costs().rename(),
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

	// ---------------------------------------------------------------- recupero

	/**
	 * Le ombre ancora a terra, gia' ripulite dalle voci scadute.
	 *
	 * <p>La pulizia avviene alla lettura e non in un tick apposta: nessuno ha bisogno di sapere
	 * che un'ombra e' tornata disponibile finche' non prova a evocarla o non apre la schermata.
	 */
	public static ShadowDowntime downtime(ServerPlayer player) {
		ShadowDowntime current = player.getAttachedOrCreate(ModAttachments.DOWNTIME);
		ShadowDowntime pruned = current.pruned(player.level().getGameTime());

		if (pruned != current) {
			player.setAttached(ModAttachments.DOWNTIME, pruned);
		}

		return pruned;
	}

	// ---------------------------------------------------------------- arruolamento

	/**
	 * Un'anima dell'Officina entra nell'esercito.
	 *
	 * <p>E' la porta di ritorno dall'automazione al combattimento, e conserva tutto: mob
	 * d'origine, vita, danno e soprattutto il <em>livello</em> guadagnato nel Crogiolo. Un'anima
	 * fusa fino al livello dieci diventa un'ombra di livello dieci, non un'ombra appena nata: se
	 * cosi' non fosse, fondere per l'esercito non avrebbe alcun senso.
	 */
	public static Component enlist(ServerPlayer player, LooseSoul soul) {
		Component locked = QuestManager.require(player, Unlock.ENLIST);
		if (locked != null) {
			return locked;
		}

		ShadowConfig config = AriseConfig.get().shadows();
		ShadowArmy army = army(player);
		int capacity = capacity(player);

		if (army.size() >= capacity) {
			return Component.translatable("arise.msg.shadow.army_full", capacity);
		}

		// L'archetipo si ricava dal mob d'origine e dalla vita che l'anima ha congelato dentro di
		// se'. La vita e' gia' moltiplicata per il fattore di config, e la soglia dei Colossi
		// guarda il mob vivo: qui si torna indietro, o un'anima di zombie passerebbe per un
		// Colosso solo perche' l'ombra di uno zombie e' piu' robusta dello zombie.
		double sourceHealth = soul.health() / Math.max(1.0E-3, config.healthFactor());
		ShadowArchetype archetype = ShadowArchetype.ofSource(soul.sourceType(), sourceHealth,
				config.legion().behaviour());

		ShadowData shadow = new ShadowData(UUID.randomUUID(), soul.sourceType(), archetype,
				soul.level(), 0L, soul.health(), WorkshopManager.enlistedDamage(soul),
				Optional.empty(), ShadowData.DEFAULT_COLOR);

		setArmy(player, army.with(shadow));
		QuestManager.advance(player, Objective.ENLIST_SOUL);
		AriseFx.soulEnlisted(player.level(), player.position(), shadow.rank(config));

		return Component.translatable("arise.msg.shadow.enlisted", shadow.displayName(),
				shadow.level(), army.size() + 1, capacity);
	}

	// ---------------------------------------------------------------- debug

	public static void clear(ServerPlayer player) {
		recall(player);
		setArmy(player, ShadowArmy.EMPTY);
		player.setAttached(ModAttachments.SQUAD, ShadowSquad.EMPTY);
		clearOrders(player);
	}
}
