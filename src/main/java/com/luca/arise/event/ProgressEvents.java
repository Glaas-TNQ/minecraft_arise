package com.luca.arise.event;

import java.util.UUID;

import com.luca.arise.ability.AbilityManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemType;
import com.luca.arise.gear.GearManager;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.shadow.ShadowEntity;
import com.luca.arise.shadow.ShadowManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ProgressEvents {

	/** Ogni quanti tick si verifica che gli attributi corrispondano alle statistiche. */
	private static final int RECONCILE_INTERVAL_TICKS = 20;

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
				// Le gemme moltiplicano il bottino della singola uccisione: ametista l'XP, zaffiro
				// i soul coin. Il tetto lo ha gia' applicato GemManager, qui si legge e basta.
				long xp = Math.round(ProgressManager.xpFor(victim)
						* (1.0 + GemManager.effect(player, GemType.AMETHYST)));
				long souls = Math.round(AriseConfig.get().soulsFor(victim.getMaxHealth())
						* (1.0 + GemManager.effect(player, GemType.SAPPHIRE)));

				ProgressManager.addXp(player, xp);
				ProgressManager.addSouls(player, souls);
				ShadowManager.recordKill(player, victim);

				// L'ombra che ha inferto il colpo prende tutta l'XP, le altre evocate una quota.
				UUID killerShadow = killer instanceof ShadowEntity shadow ? shadow.getShadowId() : null;
				ShadowManager.awardXp(player, xp, killerShadow);
				GateManager.onEntityDied(player, victim);

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
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			ShadowManager.onPlayerLeave(player);
			GateManager.onPlayerLeave(player);
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
				GearManager.enforce(player);
				ProgressManager.reconcile(player);
				AbilityManager.prune(player, now);
				GateManager.tick(player);
			}
		});

		// Morire dentro un Gate significa risvegliarsi altrove: l'istanza va smontata, o resterebbe
		// una regione occupata da mob e blocchi che nessuno rivedrà mai.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ProgressManager.applyAttributes(newPlayer);
			GateManager.closeInstance(newPlayer);
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
