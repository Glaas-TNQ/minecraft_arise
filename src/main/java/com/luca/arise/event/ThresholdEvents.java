package com.luca.arise.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.fx.AriseFx;
import com.luca.arise.gate.DelayedStrike;
import com.luca.arise.gate.GateAffixes;
import com.luca.arise.fx.Overlay;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.StatThreshold;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Le soglie che si vedono solo quando qualcosa prova a farti male.
 *
 * <p>Otto delle dodici soglie sono numeri letti altrove — quante ombre stanno in campo, quanto
 * lontano arriva il Passo d'ombra. Le altre quattro sono <em>rifiuti</em>: la fame che non uccide,
 * la caduta breve che non fa danno, il fuoco che non brucia, il veleno che non tocca. Tutte e
 * quattro passano da un evento solo, che risponde a una domanda sola: questo colpo arriva?
 *
 * <p><strong>Perche' rifiuti e non riduzioni.</strong> Fabric offre {@code ALLOW_DAMAGE}, che dice
 * si' o no. Ridurre della meta' vorrebbe dire lasciar passare il colpo e poi ricucire la vita
 * subito dopo, e si vedrebbe: la barra scende e risale, la schermata rossa lampeggia lo stesso, e
 * il giocatore conclude che la sua statistica non funziona. Una soglia che si <em>vede</em>
 * funzionare vale piu' di una che funziona meglio, quindi ogni soglia difensiva e' assoluta —
 * e per questo costa cento punti, non venti.
 */
public final class ThresholdEvents {

	/**
	 * Quando l'ultima difesa di ciascun giocatore tornera' disponibile.
	 *
	 * <p>Non persistente, e la scelta e' la stessa dei cooldown delle abilita': e' un tick assoluto,
	 * e dopo un riavvio confronterebbe istanti che non vogliono piu' dire la stessa cosa. Ripartire
	 * con la difesa pronta e' il comportamento sensato — e comunque un riavvio non e' un modo
	 * praticabile di aggirarla, visto che chi lo facesse per questo avrebbe gia' perso il
	 * combattimento in cui gli sarebbe servita.
	 */
	private static final Map<UUID, Long> LAST_STAND_READY = new HashMap<>();

	private ThresholdEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}

			return allowed(player, source, amount);
		});

		// L'ultima difesa sta su ALLOW_DEATH e non su ALLOW_DAMAGE, ed e' una correzione, non una
		// preferenza: l'importo che arriva ad ALLOW_DAMAGE e' il danno *prima* di armatura,
		// assorbimento e incantesimi. Un colpo da venti su un Cacciatore con quindici cuori e
		// un'armatura di diamante non lo uccide affatto — ma li' sembrava letale, e l'ultima
		// difesa gli avrebbe messo la vita a mezzo cuore. Avrebbe *tolto* vita per salvarlo, e
		// bruciato dieci minuti di ricarica per farlo.
		//
		// ALLOW_DEATH scatta quando la morte sta davvero per avvenire, che e' l'unica domanda a
		// cui questa soglia deve rispondere. E' lo stesso evento da cui passa il risveglio.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}

			return !lastStand(player, source);
		});

		// Gli affissi dei mob dei Gate stanno qui e non in un evento loro per una ragione sola: e'
		// lo stesso colpo. Due gestori registrati sullo stesso evento vorrebbero dire due passate
		// sulla stessa entita' a ogni danno del mondo, per una cosa che riguarda i mob di un varco.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			GateAffixes.onDamaged(entity, source.getEntity(), taken);
			GateAffixes.onDealt(source.getEntity(), entity, taken);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> GateAffixes.onDeath(entity));

		// La mappa dell'ultima difesa non deve crescere con il numero di giocatori mai passati di
		// qui: chi se ne va la lascia com'era, e chi torna riparte con la difesa pronta — che e'
		// esattamente cio' che succede gia' dopo un riavvio.
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> forget(handler.getPlayer().getUUID()));

		// Un solo battito per il server, non uno per mondo: la coda dei colpi ritardati porta il
		// proprio mondo dentro ogni voce, e cosi' non serve chiedersi dove si sta girando.
		ServerTickEvents.END_SERVER_TICK.register(
				server -> DelayedStrike.tick(server.overworld().getGameTime()));
	}

	/** Falso se una delle soglie difensive del giocatore rifiuta questo colpo. */
	private static boolean allowed(ServerPlayer player, DamageSource source, float amount) {
		if (source.is(DamageTypes.STARVE)
				&& ProgressManager.reached(player, StatThreshold.VITALITY_HUNGER)) {
			return false;
		}

		if (source.is(DamageTypeTags.IS_FALL) && forgivesFall(player, amount)) {
			return false;
		}

		if (source.is(DamageTypeTags.IS_FIRE) && forgivesFire(player, source)) {
			return false;
		}

		if ((source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER))
				&& ProgressManager.reached(player, StatThreshold.ENDURANCE_POISON)) {
			return false;
		}

		return true;
	}

	/**
	 * Le cadute brevi non contano piu'; correre le annulla tutte.
	 *
	 * <p>Due soglie diverse, due statistiche diverse, e la seconda non e' un doppione della prima:
	 * la Vitalita' perdona <em>l'altezza</em>, l'Agilita' perdona <em>il modo</em>. Un Cacciatore
	 * agile che salta da trenta blocchi mentre corre atterra intero; lo stesso che si lascia cadere
	 * da fermo no.
	 */
	private static boolean forgivesFall(ServerPlayer player, float amount) {
		if (player.isSprinting() && ProgressManager.reached(player, StatThreshold.AGILITY_SPRINT)) {
			return true;
		}

		return amount <= StatThreshold.FALL_FORGIVEN
				&& ProgressManager.reached(player, StatThreshold.VITALITY_FALL);
	}

	/**
	 * Il fuoco a venticinque punti, la lava a cento.
	 *
	 * <p>La lava sta nel tag del fuoco, quindi va distinta prima: perdonare tutto il tag alla prima
	 * soglia regalerebbe con venticinque punti la cosa che dovrebbe costarne cento.
	 */
	private static boolean forgivesFire(ServerPlayer player, DamageSource source) {
		boolean lava = source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR);

		if (lava) {
			return ProgressManager.reached(player, StatThreshold.ENDURANCE_LAVA);
		}

		return ProgressManager.reached(player, StatThreshold.ENDURANCE_FIRE);
	}

	/**
	 * L'ultima difesa: un colpo che ucciderebbe lascia mezzo cuore, una volta ogni dieci minuti.
	 *
	 * <p>E' l'unica soglia che tocca la morte, e per questo ha tre paletti insieme: costa cento
	 * punti in Vitalita', ha un tempo di ricarica lungo, e <strong>non salva dal vuoto</strong> —
	 * altrimenti sarebbe un modo di sopravvivere a una caduta nel nulla, che non e' un colpo
	 * incassato ma un errore di navigazione.
	 *
	 * @return vero se la morte va annullata perche' l'ultima difesa e' intervenuta
	 */
	private static boolean lastStand(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| !ProgressManager.reached(player, StatThreshold.VITALITY_LAST_STAND)) {
			return false;
		}

		long now = player.level().getGameTime();
		Long ready = LAST_STAND_READY.get(player.getUUID());

		if (ready != null && now < ready) {
			return false;
		}

		LAST_STAND_READY.put(player.getUUID(), now + StatThreshold.LAST_STAND_COOLDOWN);

		player.setHealth(StatThreshold.LAST_STAND_HEALTH);
		player.clearFire();

		AriseFx.lastStand(player.level(), player.position());
		Overlay.title(player, Component.translatable("arise.title.last_stand"),
				StatThreshold.VITALITY_LAST_STAND.label());
		player.sendSystemMessage(Component.translatable("arise.msg.threshold.last_stand")
				.withStyle(ChatFormatting.GOLD));

		return true;
	}

	/** Dimentica il giocatore che se ne va: la mappa non deve crescere per sempre. */
	public static void forget(UUID player) {
		LAST_STAND_READY.remove(player);
	}
}
