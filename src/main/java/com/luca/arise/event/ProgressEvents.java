package com.luca.arise.event;

import java.util.UUID;

import com.luca.arise.ability.AbilityManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gate.GateLoot;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemType;
import com.luca.arise.gear.GearManager;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.shadow.ShadowEntity;
import com.luca.arise.shadow.NamedShadow;
import com.luca.arise.shadow.ShadowManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public final class ProgressEvents {

	/**
	 * Ogni quanti tick si verifica che gli attributi corrispondano alle statistiche.
	 *
	 * <p>Un quarto di secondo, non uno intero: da quando l'equipaggiamento sta nelle caselle di
	 * vanilla, indossare un elmo non passa piu' da nessun codice nostro, e questo controllo e' cio'
	 * che se ne accorge. Un secondo di ritardo fra il click e i cuori che salgono si vede.
	 */
	private static final int RECONCILE_INTERVAL_TICKS = 5;

	private ProgressEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, damageSource) -> {
			if (victim.level().isClientSide()) {
				return;
			}

			// Niente XP dai giocatori: aprirebbe la porta al farming in PvP prima ancora di avere
			// un bilanciamento.
			if (victim instanceof Player) {
				return;
			}

			// Un'ombra caduta non è una preda: né XP né estrazione, o si aprirebbe un ciclo in cui
			// il proprio esercito si riproduce da solo.
			if (victim instanceof ShadowEntity) {
				return;
			}

			Entity killer = damageSource.getEntity();
			ServerPlayer player = resolveOwner(killer);

			if (player != null) {
				// L'uccisione conta per gli incarichi anche prima del risveglio: e' cosi' che si
				// arriva al Sistema, non un premio che il Sistema concede.
				QuestManager.advance(player, Objective.KILL);

				// Prima del risveglio non si guadagna niente: non c'e' nessun Sistema che misuri.
				if (!QuestManager.has(player, Unlock.SYSTEM)) {
					return;
				}

				// Le gemme moltiplicano il bottino della singola uccisione: ametista l'XP, zaffiro
				// i soul coin. Il tetto lo ha gia' applicato GemManager, qui si legge e basta.
				long xp = Math.round(ProgressManager.xpFor(victim)
						* (1.0 + GemManager.effect(player, GemType.AMETHYST)));
				// Greed e' l'unica ombra che paga invece di combattere, e per farlo deve essere
				// in campo: chi la tiene in squadra sta rinunciando a un posto di potenza per una
				// scelta economica, ed e' esattamente la decisione che deve costare qualcosa.
				double greed = ShadowManager.isNamedSummoned(player, NamedShadow.GREED)
						? NamedShadow.GREED_SOUL_FACTOR
						: 1.0;

				long souls = Math.round(AriseConfig.get().soulsFor(victim.getMaxHealth())
						* (1.0 + GemManager.effect(player, GemType.SAPPHIRE)) * greed);

				ProgressManager.addXp(player, xp);
				ProgressManager.addSouls(player, souls);
				ShadowManager.recordKill(player, victim);

				// L'ombra che ha inferto il colpo prende tutta l'XP, le altre evocate una quota.
				UUID killerShadow = killer instanceof ShadowEntity shadow ? shadow.getShadowId() : null;
				ShadowManager.awardXp(player, xp, killerShadow);
				GateManager.onEntityDied(player, victim);

				// Fuori da un varco il bottino lo assegna nessun altro: GateManager si ferma
				// subito se il giocatore non e' dentro un'istanza.
				if (!GateManager.isInGate(player) && QuestManager.has(player, Unlock.GEAR)) {
					GateLoot.worldDrop(player, victim.position());
				}

				// Dopo recordKill: l'ossidiana estrae dal cadavere appena registrato.
				GemEvents.onKill(player);
			}
		});

		// I modificatori di attributo sono transitori: vanno riapplicati ogni volta che il
		// giocatore entra nel mondo o viene ricreato.
		ServerPlayerEvents.JOIN.register(player -> {
			// Prima gli slot, poi gli attributi: se il rango non regge piu' quello che il
			// giocatore ha addosso, i pezzi in eccesso vanno tolti *prima* di contarli.
			GearManager.enforce(player);
			ProgressManager.applyAttributes(player);
			GateManager.onPlayerJoin(player);
			ModAttachments.resync(player);
		});
		// Cambiare dimensione richiama l'esercito. Senza, le ombre restavano nel mondo di prima e
		// diventavano copie: vedi ShadowManager.onLevelChange.
		//
		// E rimanda al client tutto quello che sa il server: dall'altra parte del viaggio c'e'
		// un'entita' nuova, che nasce senza niente addosso. Vedi ModAttachments.resync — e' la
		// causa dell'HUD che spariva e non tornava.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			ShadowManager.onLevelChange(player, origin);
			ModAttachments.resync(player);
		});

		ServerPlayerEvents.LEAVE.register(player -> {
			ShadowManager.onPlayerLeave(player);
			GateManager.onPlayerLeave(player);
		});

		// La via di casa di un varco: la pietra che compare quando il guardiano cade. Sta qui
		// insieme all'altra regola della dimensione dei varchi — chi tocca cosa, e chi non puo'
		// rompere niente — invece che dentro GateManager, che di eventi non ne registra.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer serverPlayer)
					|| !GateManager.isExit(level, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}

			serverPlayer.sendSystemMessage(GateManager.leave(serverPlayer));
			return InteractionResult.SUCCESS;
		});

		// Nessuno scava dentro un Gate. Il deepslate rinforzato regge le esplosioni, ma un
		// giocatore con gli attrezzi giusti aprirebbe comunque un buco sul vuoto: qui la strada si
		// chiude del tutto.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!level.dimension().equals(GateManager.GATE_DIMENSION));

		// Rete di sicurezza per gli attributi: vedi ProgressManager.reconcile. Una volta al
		// secondo, non a ogni tick — il controllo è economico ma non gratuito.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % RECONCILE_INTERVAL_TICKS != 0) {
				return;
			}

			long now = server.overworld().getGameTime();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				GearManager.tick(player);
				ProgressManager.reconcile(player);
				ShadowManager.tick(player);
				AbilityManager.prune(player, now);
				GateManager.tick(player);
			}
		});

		// Morire dentro un Gate significa risvegliarsi altrove: l'istanza va smontata, o resterebbe
		// una regione occupata da mob e blocchi che nessuno rivedrà mai.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ProgressManager.applyAttributes(newPlayer);
			GateManager.closeInstance(newPlayer);

			// Morire costruisce un ServerPlayer nuovo, e sul client ne nasce uno nuovo pure li'.
			// Senza questa riga il giocatore risorge senza HUD, e l'HUD non torna finche' il
			// server non riscrive gli incarichi — cioe' potenzialmente mai.
			ModAttachments.resync(newPlayer);
		});
	}

	/**
	 * Risale al giocatore a cui accreditare l'uccisione.
	 *
	 * <p>Un nemico abbattuto dalle proprie ombre conta come proprio: è il punto del sistema —
	 * l'esercito che si alimenta da solo.
	 */
	private static ServerPlayer resolveOwner(Entity killer) {
		if (killer instanceof ServerPlayer player) {
			return player;
		}

		if (killer instanceof ShadowEntity shadow && shadow.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}

		return null;
	}
}
