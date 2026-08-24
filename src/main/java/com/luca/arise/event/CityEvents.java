package com.luca.arise.event;

import com.luca.arise.city.City;
import com.luca.arise.city.CityManager;
import com.luca.arise.gate.Abyss;
import com.luca.arise.gate.GateManager;
import com.luca.arise.network.CityListPayload;

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

		// Le città nascono con il mondo. Su un mondo nuovo questo è letteralmente il momento in cui
		// il mondo viene creato; su uno già fatto, cinque letture e via.
		ServerLifecycleEvents.SERVER_STARTED.register(CityManager::onServerStarted);

		// Le costruzioni a metà vivono in memoria: a server fermo non hanno più senso, e tenerle
		// significherebbe ripartire da uno stato che non corrisponde più a nessun mondo.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CityManager.clear();

			// Il terreno gia' dipinto per la mappa appartiene al seme di questo mondo. In
			// singleplayer si esce da un mondo e se ne apre un altro senza chiudere il gioco: senza
			// questa riga la mappa del secondo mostrerebbe il paesaggio del primo.
			com.luca.arise.map.TerrainAtlas.clear();
		});

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

			// Shift inverte il verso, come in tutto il Quartiere del Mercato: il segnaposto
			// dell'Associazione manda in viaggio, e tenendo shift manda in giu'. L'Abisso non ha
			// bisogno di un blocco suo — comincia dove i Cacciatori si radunano, che e' anche il
			// posto giusto perche' un giorno accanto ci sia il Monumento delle Ombre.
			if (serverPlayer.isShiftKeyDown()) {
				serverPlayer.sendSystemMessage(GateManager.descend(serverPlayer,
						Abyss.record(serverPlayer).next()));
				return InteractionResult.SUCCESS;
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
