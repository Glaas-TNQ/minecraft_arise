package com.luca.arise.event;

import com.luca.arise.gear.GearManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * Quello che succede all'equipaggiamento senza che nessuno lo chieda.
 *
 * <p>Due automatismi, e sono i due che fanno sentire lo spazio dimensionale come una cosa del
 * Sistema invece che come un baule in piu'.
 */
public final class GearEvents {

	private GearEvents() {
	}

	public static void register() {
		/*
		 * Il ritiro all'ultimo istante.
		 *
		 * ALLOW_DEATH scatta prima che la morte venga eseguita, quindi prima che l'inventario
		 * finisca per terra: e' l'unico punto, senza mixin, in cui si puo' ancora mettere al
		 * sicuro qualcosa. Si restituisce sempre true — non stiamo impedendo la morte, stiamo solo
		 * approfittando del momento.
		 */
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				GearManager.recover(player);
			}

			return true;
		});

		// Il giocatore che riappare non e' lo stesso oggetto di quello morto: l'attachment lo ha
		// seguito (copyOnDeath), gli oggetti no, e vanno rimessi su questo.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				GearManager.restore(newPlayer));

		// Chi entra puo' aver lasciato il mondo esattamente fra la morte e il respawn, o essere
		// stato spostato da un comando: il ritiro non deve restare in sospeso per sempre.
		ServerPlayerEvents.JOIN.register(GearManager::restore);
	}
}
