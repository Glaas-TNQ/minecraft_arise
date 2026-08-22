package com.luca.arise.progress;

import java.util.Map;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Tutta la logica del Sistema. Gira <strong>solo lato server</strong>: il client riceve lo stato
 * gia' calcolato tramite la sincronizzazione dell'attachment.
 */
public final class ProgressManager {

	private ProgressManager() {
	}

	public static PlayerProgress get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.PROGRESS);
	}

	private static void set(ServerPlayer player, PlayerProgress progress) {
		player.setAttached(ModAttachments.PROGRESS, progress);
	}

	/** XP che vale l'uccisione di questa creatura. */
	public static long xpFor(LivingEntity victim) {
		AriseConfig config = AriseConfig.get();
		double xp = config.xpFlatPerKill() + victim.getMaxHealth() * config.xpPerMobMaxHealth();
		return Math.max(1L, (long) xp);
	}

	/**
	 * Accredita XP e fa salire di livello quante volte serve.
	 *
	 * <p>Il ciclo e' necessario: un singolo guadagno grosso (o un {@code /arise xp}) puo' valere
	 * piu' livelli in una volta.
	 */
	public static void addXp(ServerPlayer player, long amount) {
		if (amount <= 0) {
			return;
		}

		AriseConfig config = AriseConfig.get();
		PlayerProgress progress = get(player);

		if (progress.level() >= config.maxLevel()) {
			return;
		}

		long xp = progress.xp() + amount;
		int level = progress.level();
		int points = progress.unspentPoints();
		int levelsGained = 0;

		long needed = config.xpForNextLevel(level);
		while (xp >= needed && level < config.maxLevel()) {
			xp -= needed;
			level++;
			points += config.pointsPerLevel();
			levelsGained++;
			needed = config.xpForNextLevel(level);
		}

		if (level >= config.maxLevel()) {
			xp = 0;
		}

		set(player, progress.withLevel(level, xp, points));

		if (levelsGained > 0) {
			onLevelUp(player, level, levelsGained * config.pointsPerLevel());
		}
	}

	private static void onLevelUp(ServerPlayer player, int newLevel, int pointsGained) {
		float healthBefore = player.getMaxHealth();
		applyAttributes(player);
		// La vita massima appena guadagnata viene regalata piena: salire di livello non deve
		// lasciare il giocatore con una barra piu' lunga ma mezza vuota.
		float healthGained = player.getMaxHealth() - healthBefore;
		if (healthGained > 0) {
			player.setHealth(player.getHealth() + healthGained);
		}

		player.sendSystemMessage(Component.translatable("arise.msg.level_up", newLevel, pointsGained));
		AriseFx.levelUp(player);
	}

	public static long souls(ServerPlayer player) {
		return get(player).souls();
	}

	public static void addSouls(ServerPlayer player, long amount) {
		if (amount != 0) {
			set(player, get(player).withSouls(get(player).souls() + amount));
		}
	}

	/**
	 * Scala il costo se il giocatore può permetterselo.
	 *
	 * @return {@code true} se la spesa è andata a buon fine
	 */
	public static boolean spendSouls(ServerPlayer player, long cost) {
		PlayerProgress progress = get(player);
		if (cost < 0 || progress.souls() < cost) {
			return false;
		}

		set(player, progress.withSouls(progress.souls() - cost));
		return true;
	}

	/** Imposta il livello a un valore preciso (comandi di debug). L'XP parziale viene azzerata. */
	public static void setLevel(ServerPlayer player, int level) {
		AriseConfig config = AriseConfig.get();
		int clamped = Math.clamp(level, 1, config.maxLevel());
		PlayerProgress progress = get(player);
		int points = (clamped - 1) * config.pointsPerLevel() - spentPoints(progress);
		set(player, progress.withLevel(clamped, 0L, Math.max(0, points)));
		applyAttributes(player);
	}

	public static int spentPoints(PlayerProgress progress) {
		int total = 0;
		for (Stat stat : Stat.SPENDABLE) {
			total += progress.stat(stat);
		}
		return total;
	}

	/**
	 * Spende punti su una statistica.
	 *
	 * @return {@code null} se l'operazione riesce, altrimenti il messaggio di errore da mostrare.
	 */
	public static Component spend(ServerPlayer player, Stat stat, int amount) {
		if (amount <= 0) {
			return Component.translatable("arise.msg.spend.invalid_amount");
		}

		PlayerProgress progress = get(player);
		if (progress.unspentPoints() < amount) {
			return Component.translatable("arise.msg.spend.not_enough", progress.unspentPoints());
		}

		int cap = AriseConfig.get().cap(stat);
		if (progress.stat(stat) + amount > cap) {
			return Component.translatable("arise.msg.spend.cap_reached",
					Component.translatable(stat.translationKey()), cap);
		}

		set(player, progress.withStatIncreased(stat, amount));
		applyAttributes(player);
		return null;
	}

	public static void reset(ServerPlayer player) {
		set(player, PlayerProgress.INITIAL);
		applyAttributes(player);
	}

	/**
	 * Rimette a posto i modificatori se non corrispondono alle statistiche.
	 *
	 * <p>Esiste perché applicarli agli eventi giusti <em>non basta</em>: dopo un respawn i
	 * modificatori risultavano assenti anche se {@code AFTER_RESPAWN} li aveva riapplicati, segno
	 * che qualcosa più a valle rifà la mappa degli attributi. Invece di rincorrere il momento
	 * esatto, ogni secondo si verifica che lo stato reale corrisponda ai dati e lo si corregge.
	 *
	 * <p>Costa una somma delle sorgenti per giocatore al secondo, e nel caso normale non
	 * scrive nulla: il prezzo giusto per una classe di bug che altrimenti riappare a ogni
	 * cambiamento di Minecraft.
	 */
	public static void reconcile(ServerPlayer player) {
		Map<Stat, Double> totals = StatSources.totals(player);

		for (Stat stat : Stat.values()) {
			AttributeInstance instance = player.getAttribute(stat.attribute());
			if (instance == null) {
				continue;
			}

			double expected = totals.getOrDefault(stat, 0.0);
			AttributeModifier actual = instance.getModifier(stat.modifierId());

			boolean missing = actual == null && expected != 0.0;
			boolean stale = actual != null && actual.amount() != expected;

			if (missing || stale) {
				applyAttributes(player);
				return;
			}
		}
	}

	/**
	 * Riscrive i modificatori di attributo a partire dalle statistiche correnti.
	 *
	 * <p>Un solo modificatore per statistica, con id fisso, <em>rimpiazzato</em> a ogni ricalcolo:
	 * accumularne uno per livello e' l'errore che moltiplica la vita per quaranta. Il valore e' la
	 * somma di tutte le sorgenti (punti spesi, equipaggiamento, gemme): vedi {@link StatSources}.
	 *
	 * <p>I modificatori sono transitori (non salvati col giocatore) proprio perche' vengono
	 * riapplicati al login e a ogni cambiamento: la fonte di verita' resta l'attachment.
	 */
	public static void applyAttributes(ServerPlayer player) {
		Map<Stat, Double> totals = StatSources.totals(player);

		for (Stat stat : Stat.values()) {
			AttributeInstance instance = player.getAttribute(stat.attribute());
			if (instance == null) {
				continue;
			}

			instance.removeModifier(stat.modifierId());

			double amount = totals.getOrDefault(stat, 0.0);
			if (amount != 0.0) {
				instance.addOrUpdateTransientModifier(
						new AttributeModifier(stat.modifierId(), amount, stat.operation()));
			}
		}

		// La vita massima puo' essere scesa (reset, config cambiata): senza questo il giocatore
		// resterebbe con piu' vita del suo massimo.
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}
}
