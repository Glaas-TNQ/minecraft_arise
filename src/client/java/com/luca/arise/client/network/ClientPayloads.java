package com.luca.arise.client.network;

import com.luca.arise.client.screen.CityHubScreen;
import com.luca.arise.client.screen.GateOfferScreen;
import com.luca.arise.network.CityListPayload;
import com.luca.arise.network.GateOfferPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * I pacchetti che arrivano dal server e aprono qualcosa sullo schermo.
 *
 * <p>Fin qui il client non riceveva niente: lo stato del giocatore viaggia da solo con gli
 * attachment sincronizzati. Il preventivo di un varco no — è un dato che ha senso solo nel momento
 * in cui qualcuno lo chiede, e tenerlo sincronizzato di continuo sarebbe traffico sprecato.
 */
public final class ClientPayloads {

	private ClientPayloads() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(GateOfferPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().setScreenAndShow(
						new GateOfferScreen(payload.entityId(), payload.offer(), payload.remainingTicks()))));

		ClientPlayNetworking.registerGlobalReceiver(CityListPayload.TYPE, (payload, context) ->
				context.client().execute(() -> Minecraft.getInstance().setScreenAndShow(
						new CityHubScreen(payload.cities()))));
	}
}
