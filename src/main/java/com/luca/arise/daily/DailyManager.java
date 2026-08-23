package com.luca.arise.daily;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.DailyConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * La giornata di un Cacciatore: quattro cose da fare, e un punto se le fai.
 *
 * <p>Ogni alba il Sistema chiede cento blocchi scavati, cento colpi a segno, cento salti e mille
 * blocchi di corsa. Sono i cento flessioni, cento addominali, cento squat e dieci chilometri del
 * canone, tradotti nei soli quattro gesti che Minecraft sa gia' contare. Sono tarati per essere
 * <strong>un promemoria di giocare</strong>, non un secondo lavoro: chi scava, combatte e cammina
 * li fa senza accorgersene.
 *
 * <h2>Non c'e' nessuna penalita'</h2>
 *
 * <p>C'era, ed e' stata tolta dopo averla provata. Il canone la prevede — una Zona di Penalita' in
 * cui il Sistema ti sposta se la sera non hai fatto — ed era stata costruita per intero: un deserto
 * sigillato, ondate di creature, un cronometro. In gioco non funzionava, e la ragione non e' la
 * taratura.
 *
 * <p>Una penalita' che <em>sposta</em> non e' una conseguenza: e' un'interruzione. E
 * un'interruzione arriva sempre nel momento sbagliato, perche' il momento non lo sceglie chi
 * gioca. Nessuna durata la salvava — accorciarla la rendeva solo un'interruzione piu' breve.
 *
 * <p>Cio' che resta e' la meta' che funzionava: il Sistema chiede, e se fai quello che chiede paga.
 * Chi non lo fa semplicemente non prende il punto, e domani il Sistema richiede. Un premio mancato
 * si sente quanto basta, e non toglie a nessuno la serata.
 *
 * <p>Il conto dei giorni e' quello di Minecraft, non quello del calendario di chi gioca: chi non
 * accende il gioco per una settimana non trova sette giornate arretrate ad aspettarlo.
 */
public final class DailyManager {

	/** Un giorno di Minecraft. */
	private static final long DAY_TICKS = 24000L;

	/** Chi ha gia' ricevuto l'avviso di tre quarti, per non ripeterlo quattro volte al secondo. */
	private static final Set<UUID> WARNED = new HashSet<>();

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
	 * Il battito della giornata: apre l'alba e avvisa a tre quarti.
	 *
	 * <p>Chiamato quattro volte al secondo, dallo stesso giro degli altri sistemi — e per questo
	 * tutto qui dentro confronta numeri di giorno e tick assoluti, mai conteggi di chiamate. Il
	 * ritmo con cui qualcuno decide di chiamare questo metodo non deve poter cambiare il gioco.
	 */
	public static void tick(ServerPlayer player) {
		DailyConfig config = AriseConfig.get().daily();

		if (!config.enabled() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		if (!QuestManager.has(player, Unlock.STATS)) {
			return;
		}

		long today = dayOf(level);
		DailyQuest daily = get(player);

		// Il giorno cambia e la pagina si volta. Non c'e' niente da saldare: una giornata lasciata
		// aperta e' un punto non preso, e un punto non preso non ha bisogno di essere riscosso.
		if (daily.day() != today) {
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
	 * L'avviso a tre quarti di giornata, se manca ancora qualcosa. Una volta sola.
	 *
	 * <p>E' rimasto anche senza la penalita', e la sua funzione e' cambiata: prima diceva «rimedia
	 * o ti prendo», adesso dice «hai ancora un quarto di giornata per prendere il punto». La prima
	 * era una minaccia, la seconda un promemoria — ed e' l'unica cosa che l'avviso e' mai riuscito
	 * a essere davvero. Per questo non e' piu' rosso.
	 */
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
				daily.remaining(config)).withStyle(ChatFormatting.YELLOW));
	}

	/** Dimentica un giocatore che se ne va: l'insieme non deve crescere per sempre. */
	public static void forget(UUID player) {
		WARNED.remove(player);
	}
}
