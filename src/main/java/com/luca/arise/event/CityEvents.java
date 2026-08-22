package com.luca.arise.event;

import com.luca.arise.city.City;
import com.luca.arise.city.CityManager;
import com.luca.arise.network.CityListPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Gli agganci delle città: il battito che le costruisce e il terminale che le collega.
 *
 * <p>Il battito è a ogni tick e non ogni venti come le altre cose della mod: una costruzione a
 * budget ha senso solo se il budget viene speso di continuo. Quando non c'è niente da costruire il
 * metodo esce sulla prima riga, quindi non costa nulla.
 */
public final class CityEvents {

	private CityEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CityManager::tick);

		// Il mondo già pronto: alla prima entrata, le Associazioni che mancano si tirano su da sole.
		ServerPlayerEvents.JOIN.register(player ->
				CityManager.onFirstJoin(player.level().getServer(), player));

		// Le costruzioni a metà vivono in memoria: a server fermo non hanno più senso, e tenerle
		// significherebbe ripartire da uno stato che non corrisponde più a nessun mondo.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CityManager.clear());

		// Il terminale non è un'entità né un blocco nostro: è la pietra al centro
		// dell'Associazione. Un blocco in meno da registrare, e niente che possa sparire quando
		// il chunk si scarica.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			City city = CityManager.terminalAt(level, hit.getBlockPos());
			if (city == null) {
				return InteractionResult.PASS;
			}

			openHub(serverPlayer);
			return InteractionResult.SUCCESS;
		});
	}

	/** Manda l'elenco delle Associazioni esistenti e fa aprire il pannello di viaggio. */
	public static void openHub(ServerPlayer player) {
		ServerPlayNetworking.send(player,
				new CityListPayload(CityManager.built(player.level().getServer().overworld())));
	}
}
