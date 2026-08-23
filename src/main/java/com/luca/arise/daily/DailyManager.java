package com.luca.arise.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.DailyConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gate.ReturnPoint;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * La Quest Giornaliera, e cosa succede se il giorno finisce senza averla chiusa.
 *
 * <p>Ogni alba il Sistema chiede quattro cose — scavare, colpire, saltare, correre — e ogni
 * tramonto fa i conti. Chiusa: un punto statistica e la vita piena. Aperta: <strong>ti sposta</strong>.
 *
 * <p>I quattro obiettivi sono i cento flessioni, cento addominali, cento squat e dieci chilometri
 * del canone, tradotti negli unici quattro verbi che Minecraft misura gia' da solo. E sono tarati
 * per essere <em>un promemoria di giocare</em>: chi passa una giornata a scavare, combattere e
 * camminare li fa senza accorgersene. La penalita' deve capitare a chi ha passato la giornata fermo
 * in una fattoria, non a chi ha giocato.
 *
 * <h2>La penalita' non toglie niente</h2>
 *
 * <p>Ed e' la sola regola non negoziabile di tutto il blocco. Non leva livelli, non brucia
 * l'inventario, non cancella l'esercito, e <strong>non si puo' morire nella Zona</strong>: chi
 * arriva a zero riparte dal centro a meta' vita, col cronometro azzerato. Costa otto minuti e la
 * faccia.
 *
 * <p>Una penalita' che sottraesse sarebbe la ricetta per disinstallare la mod. Una che interrompe
 * e' la ricetta per ricordarsela — ed e' il momento in cui il Sistema smette di essere
 * un'interfaccia e diventa un carceriere, che e' esattamente cio' che e' nel materiale d'origine.
 */
public final class DailyManager {

	/** Un tick di gioco vale un giorno ogni ventiquattromila. */
	private static final long DAY_TICKS = 24000L;

	/** giocatore → a che tick di gioco finisce la sua Sopravvivenza. */
	private static final Map<UUID, Long> SURVIVING = new HashMap<>();

	/** giocatore → a che tick arriva la prossima ondata. */
	private static final Map<UUID, Long> NEXT_WAVE = new HashMap<>();

	/** giocatore → quante ondate ha gia' incassato in questa penalita'. */
	private static final Map<UUID, Integer> WAVES = new HashMap<>();

	/** Chi si e' gia' sentito dire che il giorno sta per finire. Una volta per giorno. */
	private static final Set<UUID> WARNED = new java.util.HashSet<>();

	/** giocatore → a che tick riscrivere il conto alla rovescia della Sopravvivenza. */
	private static final Map<UUID, Long> NEXT_TICK_LINE = new HashMap<>();

	private DailyManager() {
	}

	public static DailyQuest get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.DAILY);
	}

	private static void set(ServerPlayer player, DailyQuest daily) {
		player.setAttached(ModAttachments.DAILY, daily);
	}

	/** Il giorno di Minecraft in cui si trova questo mondo. */
	public static long dayOf(ServerLevel level) {
		return level.getGameTime() / DAY_TICKS;
	}

	// ---------------------------------------------------------------- i contatori

	/**
	 * Un obiettivo avanza.
	 *
	 * <p>Silenziosa finche' un contatore non arriva al suo bersaglio: quattro contatori che
	 * annunciassero ogni passo sarebbero quattrocento righe al giorno. Quando uno si chiude lo
	 * dice, e quando si chiudono tutti e quattro lo dice il Sistema.
	 */
	public static void advance(ServerPlayer player, DailyTask task, int amount) {
		DailyConfig config = AriseConfig.get().daily();

		if (!config.enabled() || amount <= 0 || !QuestManager.has(player, Unlock.STATS)) {
			return;
		}

		DailyQuest daily = get(player);

		if (daily.settled() || daily.progress(task) >= task.target(config)) {
			return;
		}

		DailyQuest updated = daily.with(task, amount, config);
		set(player, updated);

		if (updated.progress(task) < task.target(config)) {
			return;
		}

		player.sendSystemMessage(Component.translatable("arise.msg.daily.task_done",
				task.label(), updated.remaining(config)).withStyle(ChatFormatting.GRAY));

		if (updated.complete(config)) {
			reward(player, config);
		}
	}

	private static void reward(ServerPlayer player, DailyConfig config) {
		set(player, get(player).withSettled());

		ProgressManager.addPoints(player, config.reward());
		player.setHealth(player.getMaxHealth());

		Overlay.title(player, Component.translatable("arise.title.daily_done"),
				Component.translatable("arise.subtitle.daily_done"));
		player.sendSystemMessage(Component.translatable("arise.msg.daily.done", config.reward())
				.withStyle(ChatFormatting.GOLD));

		AriseFx.levelUp(player);
	}

	// ---------------------------------------------------------------- il giorno

	/**
	 * Il battito della giornata. Apre l'alba, avvisa a tre quarti, e al tramonto fa i conti.
	 *
	 * <p>Chiamato quattro volte al secondo, dallo stesso giro degli altri sistemi — e per questo
	 * tutto qui dentro confronta numeri di giorno e tick assoluti, mai conteggi di chiamate. Il
	 * ritmo con cui qualcuno decide di chiamare questo metodo non deve poter cambiare il gioco.
	 */
	public static void tick(ServerPlayer player) {
		DailyConfig config = AriseConfig.get().daily();

		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		// Chi si trova nella Zona senza essere nell'elenco di chi la sta scontando e' un residuo, e
		// va rimandato a casa prima di ogni altra cosa. La Zona non ha porte: se il battito non lo
		// libera, non lo libera nessuno.
		//
		// Il caso non e' ipotetico, e' garantito. L'elenco vive in memoria e il mondo no, quindi
		// basta un riavvio del server con qualcuno dentro. Ci arrivano anche due strade piu' corte:
		// spegnere `daily.enabled` mentre uno sconta — il battito usciva subito, e la penalita'
		// diventava un ergastolo — e un teletrasporto arrivato da fuori. Tre cause, una regola:
		// nell'elenco o fuori dalla Zona, mai nessuna delle due.
		//
		// Ha un rovescio dichiarato: `forget` svuota l'elenco quando qualcuno si disconnette, quindi
		// uscire dal gioco durante la penalita' ora fa uscire anche dalla Zona. Non e' un buco
		// lasciato per distrazione, e' il lato meno grave di una scelta a due lati — la giornata e'
		// gia' saldata quando la penalita' parte, quindi non si ripete, e chi chiude il gioco per
		// evitare otto minuti li ha comunque interrotti. Il rimedio vero e' rendere persistente la
		// scadenza invece di tenerla in memoria, e non e' il lavoro di stasera.
		if (PenaltyZone.contains(player) && !SURVIVING.containsKey(player.getUUID())) {
			GateManager.sendHome(player);
			return;
		}

		if (!config.enabled()) {
			return;
		}

		if (surviving(player, level, config)) {
			return;
		}

		if (!QuestManager.has(player, Unlock.STATS)) {
			return;
		}

		long today = dayOf(level);
		DailyQuest daily = get(player);

		if (daily.day() != today) {
			settle(player, level, daily, config);
			open(player, today, config);
			return;
		}

		warn(player, level, daily, config);
	}

	/** L'alba: contatori azzerati, e il Sistema che chiede. */
	private static void open(ServerPlayer player, long today, DailyConfig config) {
		set(player, DailyQuest.forDay(today));
		WARNED.remove(player.getUUID());

		player.sendSystemMessage(Component.translatable("arise.msg.daily.opened")
				.withStyle(ChatFormatting.AQUA));

		for (DailyTask task : DailyTask.values()) {
			player.sendSystemMessage(Component.translatable("arise.msg.daily.line",
					task.label(), task.target(config)).withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * Il tramonto del giorno prima.
	 *
	 * <p>Il giorno {@code -1} di {@link DailyQuest#NONE} non si salda: e' il segnaposto di chi non
	 * ha mai visto un'alba, e mandarlo nella Zona di Penalita' al primo login sarebbe punirlo per
	 * una giornata che non gli e' mai stata chiesta.
	 */
	private static void settle(ServerPlayer player, ServerLevel level, DailyQuest daily,
			DailyConfig config) {
		if (daily.day() < 0 || daily.settled() || daily.complete(config)) {
			return;
		}

		penalise(player, level, config);
	}

	/** L'avviso a tre quarti di giornata, se manca ancora qualcosa. Una volta sola. */
	private static void warn(ServerPlayer player, ServerLevel level, DailyQuest daily,
			DailyConfig config) {
		if (daily.settled() || WARNED.contains(player.getUUID())) {
			return;
		}

		long into = level.getGameTime() % DAY_TICKS;

		if (into < DAY_TICKS * config.warnAt()) {
			return;
		}

		WARNED.add(player.getUUID());

		player.sendSystemMessage(Component.translatable("arise.msg.daily.warning",
				daily.remaining(config)).withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------- la penalita'

	/** Il Sistema ti sposta. */
	private static void penalise(ServerPlayer player, ServerLevel level, DailyConfig config) {
		set(player, get(player).withSettled());

		ServerLevel gate = level.getServer().getLevel(GateManager.GATE_DIMENSION);

		if (gate == null) {
			// Senza la dimensione dei varchi non c'e' nessun posto dove mandarlo. Il Sistema si
			// limita a dirlo, come fa il risveglio nello stesso caso.
			player.sendSystemMessage(Component.translatable("arise.msg.daily.failed")
					.withStyle(ChatFormatting.RED));
			return;
		}

		// Il punto di ritorno prima del viaggio, su disco: e' la stessa rete di sicurezza del
		// risveglio e dei Gate, e serve alla stessa cosa — un crash qui non deve lasciare nessuno
		// chiuso in un deserto.
		player.setAttached(ModAttachments.RETURN_POINT,
				new ReturnPoint(player.level().dimension(), player.position()));

		PenaltyZone.ensure(gate);

		Vec3 centre = PenaltyZone.centre();
		player.teleportTo(gate, centre.x(), centre.y(), centre.z(), java.util.Set.of(),
				player.getYRot(), 0.0F, true);

		long now = gate.getGameTime();
		SURVIVING.put(player.getUUID(), now + config.penaltyTicks());
		NEXT_WAVE.put(player.getUUID(), now + config.waveTicks());
		WAVES.put(player.getUUID(), 0);

		Overlay.title(player, Component.translatable("arise.title.penalty")
						.withStyle(ChatFormatting.RED),
				Component.translatable("arise.subtitle.penalty"));

		player.sendSystemMessage(Component.translatable("arise.msg.daily.penalty",
				Math.max(1, config.penaltyTicks() / 20)).withStyle(ChatFormatting.RED));

		AriseFx.penalty(gate, centre);
	}

	/**
	 * Il battito della Sopravvivenza: il tempo che scorre e le ondate che arrivano.
	 *
	 * @return vero se il giocatore sta scontando la penalita', e quindi non ha una giornata da
	 *         portare avanti
	 */
	private static boolean surviving(ServerPlayer player, ServerLevel level, DailyConfig config) {
		Long until = SURVIVING.get(player.getUUID());

		if (until == null) {
			return false;
		}

		// Chi esce dalla Zona in qualunque modo — un comando, una morte gestita altrove, un
		// riavvio — smette di scontare. La Zona e' il posto, non un flag: se non ci sei, e' finita.
		if (!PenaltyZone.contains(player)) {
			release(player, false);
			return false;
		}

		long now = level.getGameTime();

		if (now >= until) {
			release(player, true);
			return true;
		}

		// Il conto alla rovescia mostra i secondi: riscriverlo quattro volte per lo stesso numero
		// sono tre pacchetti su quattro spesi per niente. La stessa cura del conto della Caccia.
		Long nextWrite = NEXT_TICK_LINE.get(player.getUUID());

		if (nextWrite == null || now >= nextWrite) {
			NEXT_TICK_LINE.put(player.getUUID(), now + 20L);
			Overlay.actionBar(player, Component.translatable("arise.msg.daily.surviving",
					Math.max(1, (until - now) / 20)));
		}

		Long wave = NEXT_WAVE.get(player.getUUID());

		if (wave != null && now >= wave) {
			NEXT_WAVE.put(player.getUUID(), now + config.waveTicks());
			spawnWave(player, level, config);
		}

		return true;
	}

	/**
	 * Un'ondata di millepiedi.
	 *
	 * <p>Silverfish riscalati: veloci, numerosi, e da soli innocui — quello che fa male e' che non
	 * smettono di arrivare. Non lasciano bottino e non danno esperienza, perche' la Zona non e'
	 * contenuto: e' tempo tolto.
	 */
	private static void spawnWave(ServerPlayer player, ServerLevel level, DailyConfig config) {
		int wave = WAVES.merge(player.getUUID(), 1, Integer::sum);
		int count = config.waveSize() + wave;

		for (int i = 0; i < count; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			double distance = 8.0 + level.getRandom().nextDouble() * 8.0;

			Mob mob = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.EVENT);

			if (mob == null) {
				return;
			}

			mob.snapTo(player.getX() + Math.cos(angle) * distance, PenaltyZone.FLOOR,
					player.getZ() + Math.sin(angle) * distance,
					level.getRandom().nextFloat() * 360.0F, 0.0F);

			scale(mob, Attributes.SCALE, 2.2);
			scale(mob, Attributes.MAX_HEALTH, 2.0);
			mob.setHealth(mob.getMaxHealth());
			mob.setPersistenceRequired();
			mob.setTarget(player);

			level.addFreshEntity(mob);
		}

		AriseFx.penaltyWave(level, player.position());
	}

	private static void scale(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			double factor) {
		AttributeInstance instance = mob.getAttribute(attribute);

		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() * factor);
		}
	}

	/** Fine della penalita': si torna dove si era, e la Zona resta vuota. */
	private static void release(ServerPlayer player, boolean survived) {
		SURVIVING.remove(player.getUUID());
		NEXT_WAVE.remove(player.getUUID());
		WAVES.remove(player.getUUID());
		NEXT_TICK_LINE.remove(player.getUUID());

		if (!survived) {
			return;
		}

		clear(player);
		GateManager.sendHome(player);

		Overlay.title(player, Component.translatable("arise.title.penalty_over"),
				Component.translatable("arise.subtitle.penalty_over"));
		player.sendSystemMessage(Component.translatable("arise.msg.daily.survived")
				.withStyle(ChatFormatting.AQUA));
	}

	/** Toglie di mezzo i millepiedi rimasti: la Zona deve essere vuota per il prossimo. */
	private static void clear(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 centre = PenaltyZone.centre();
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(centre, centre)
				.inflate(PenaltyZone.RADIUS + 4);

		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityTypes.SILVERFISH)) {
			mob.discard();
		}
	}

	/**
	 * Un colpo che ucciderebbe dentro la Zona non uccide.
	 *
	 * <p>E' la regola che rende la penalita' accettabile: si riparte dal centro a meta' vita e il
	 * cronometro <strong>riparte da capo</strong>. Il prezzo di morire e' rifare gli otto minuti,
	 * ed e' un prezzo pagato in tempo — l'unica valuta che questa penalita' spende.
	 *
	 * @return vero se la morte va annullata
	 */
	public static boolean catchDeath(ServerPlayer player) {
		if (!SURVIVING.containsKey(player.getUUID()) || !PenaltyZone.contains(player)) {
			return false;
		}

		DailyConfig config = AriseConfig.get().daily();
		long now = player.level().getGameTime();

		SURVIVING.put(player.getUUID(), now + config.penaltyTicks());
		NEXT_WAVE.put(player.getUUID(), now + config.waveTicks());
		WAVES.put(player.getUUID(), 0);

		clear(player);

		Vec3 centre = PenaltyZone.centre();
		player.teleportTo(centre.x(), centre.y(), centre.z());
		player.setHealth(player.getMaxHealth() / 2.0F);
		player.removeAllEffects();
		player.clearFire();

		player.sendSystemMessage(Component.translatable("arise.msg.daily.again")
				.withStyle(ChatFormatting.RED));

		return true;
	}

	/** Manda subito qualcuno nella Zona. Per il collaudo: aspettare un tramonto costa dieci minuti. */
	public static Component force(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return Component.translatable("arise.msg.daily.failed");
		}

		penalise(player, level, AriseConfig.get().daily());
		return Component.translatable("arise.msg.daily.forced");
	}

	/** Dimentica un giocatore che se ne va: le tre mappe non devono crescere per sempre. */
	public static void forget(UUID player) {
		SURVIVING.remove(player);
		NEXT_WAVE.remove(player);
		WAVES.remove(player);
		WARNED.remove(player);
		NEXT_TICK_LINE.remove(player);
	}
}
