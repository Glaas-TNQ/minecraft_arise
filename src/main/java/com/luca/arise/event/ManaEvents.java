package com.luca.arise.event;

import com.luca.arise.ability.FlightManager;
import com.luca.arise.mana.ManaManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * Il battito del Mana e le uscite dal volo.
 *
 * <p>Un giro suo e non un pezzo di {@code ProgressEvents} perche' ha un ritmo diverso: la
 * riconciliazione degli attributi si accontenta di una volta al secondo, il Mana no. La barra deve
 * muoversi in modo continuo, e soprattutto il volo deve spegnersi <em>quando</em> il Mana finisce
 * e non fino a un secondo dopo — un secondo di volo gratuito e' poco, ma un secondo di ritardo
 * nella caduta si vede.
 *
 * <p>Le quattro righe che spengono il volo sono la parte che conta davvero. {@code mayfly} e' un
 * permesso scritto sul giocatore: se non lo si toglie non se ne va da solo, e ogni via d'uscita che
 * non passa di qui sarebbe un modo di tenerselo. Vedi {@link FlightManager}.
 */
public final class ManaEvents {

	/** Ogni quanti tick si rigenera e si addebita il volo: quattro volte al secondo. */
	private static final int EVERY = 5;

	private ManaEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				FlightManager.tick(player);
				ManaManager.tick(player);
			}
		});

		// Le quattro uscite. Morire e cambiare mondo costruiscono un ServerPlayer nuovo, e il
		// permesso di volare non lo segue: spegnerlo sul vecchio e' l'unico momento in cui si puo'.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			FlightManager.stop(oldPlayer);
			FlightManager.forget(newPlayer.getUUID());
			ManaManager.refill(newPlayer);
		});

		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
				(player, origin, destination) -> FlightManager.stop(player));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FlightManager.stop(handler.getPlayer());
			FlightManager.forget(handler.getPlayer().getUUID());
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FlightManager.clear());
	}
}
