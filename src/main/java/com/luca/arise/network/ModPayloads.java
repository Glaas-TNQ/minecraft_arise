package com.luca.arise.network;

import java.util.List;

import com.luca.arise.ability.AbilityManager;
import com.luca.arise.city.CityManager;
import com.luca.arise.gate.GateEntity;
import com.luca.arise.map.WorldMap;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gem.GemManager;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModMenus;
import com.luca.arise.shop.ShopManager;
import com.luca.arise.shadow.ShadowManager;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ModPayloads {

	private ModPayloads() {
	}

	/** Registrazione dei tipi: deve girare su entrambi i lati, quindi sta nell'init comune. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SpendPointPayload.TYPE, SpendPointPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AriseActionPayload.TYPE, AriseActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShadowActionPayload.TYPE, ShadowActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GateActionPayload.TYPE, GateActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CityTravelPayload.TYPE, CityTravelPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShopActionPayload.TYPE, ShopActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GemActionPayload.TYPE, GemActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MapTileRequestPayload.TYPE,
				MapTileRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GateOfferPayload.TYPE, GateOfferPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CityListPayload.TYPE, CityListPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenScreenPayload.TYPE, OpenScreenPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapPayload.TYPE, MapPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapTilePayload.TYPE, MapTilePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SpendPointPayload.TYPE, (payload, context) -> {
			// Esecuzione esplicita sul thread del server: tocchiamo attributi e attachment, che
			// non sono thread-safe. Costa al massimo un tick di ritardo.
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				Component error = ProgressManager.spend(player, payload.stat(), payload.amount());

				if (error != null) {
					player.sendSystemMessage(error);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(AriseActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();

				// L'apertura di un menu non produce un messaggio: la risposta e' la finestra.
				if (payload.action() == AriseActionPayload.Action.OPEN_GEAR) {
					Component locked = QuestManager.require(player, Unlock.GEAR);

					if (locked != null) {
						player.sendSystemMessage(locked);
					} else {
						ModMenus.open(player);
					}

					return;
				}

				Component feedback = switch (payload.action()) {
					case EXTRACT -> ShadowManager.extract(player);
					case SURVEY -> ShadowManager.survey(player);
					case SUMMON -> ShadowManager.summon(player);
					case RECALL -> ShadowManager.recall(player);
					case STANCE -> ShadowManager.cycleStance(player);
					case ORDER_FOCUS -> ShadowManager.focus(player);
					case ORDER_HOLD -> ShadowManager.hold(player);
					case ABILITY_1, ABILITY_2, ABILITY_3, ABILITY_4, ABILITY_5 ->
							AbilityManager.use(player, payload.action().ability());
					case OPEN_GEAR -> null;
					// La risposta e' la mappa; un messaggio arriva solo se non si puo' avere.
					case OPEN_MAP -> WorldMap.open(player);
				};

				if (feedback == null) {
					return;
				}

				player.sendSystemMessage(feedback);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(GemActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();

				Component feedback = switch (payload.action()) {
					case SOCKET -> GemManager.socket(player, payload.gemId(), payload.pieceId());
					case EXTRACT -> GemManager.extract(player, payload.gemId());
					case SHATTER -> GemManager.shatter(player, payload.gemId());
				};

				player.sendSystemMessage(feedback);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();

				switch (payload.action()) {
					case OPEN -> ShopManager.announceOpen(player);
					case BUY -> player.sendSystemMessage(ShopManager.buy(player, payload.offerId()));
					case REFRESH -> player.sendSystemMessage(ShopManager.refresh(player));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ShadowActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();

				Component feedback = switch (payload.action()) {
					case SUMMON -> ShadowManager.summonOne(player, payload.shadowId());
					case RECALL -> ShadowManager.recallOne(player, payload.shadowId());
					case DISMISS -> ShadowManager.dismiss(player, payload.shadowId());
					case RENAME -> ShadowManager.rename(player, payload.shadowId(), payload.name());
					case RECOLOR -> ShadowManager.recolor(player, payload.shadowId(), payload.color());
					case UPGRADE -> ShadowManager.upgrade(player, payload.shadowId());
					case SQUAD -> ShadowManager.toggleSquad(player, payload.shadowId());
				};

				player.sendSystemMessage(feedback);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(GateActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();

				// L'id arriva dal client, quindi vale zero finché non si dimostra che indica un
				// varco vero, ancora vivo e a portata di mano. Senza questi tre controlli
				// basterebbe un pacchetto costruito a mano per entrare in un Gate di rango S.
				if (!(player.level().getEntity(payload.entityId()) instanceof GateEntity varco)
						|| varco.offer() == null
						|| varco.distanceToSqr(player) > MAX_GATE_REACH * MAX_GATE_REACH) {
					player.sendSystemMessage(Component.translatable("arise.msg.gate.varco_gone"));
					return;
				}

				switch (payload.action()) {
					case ENTER -> {
						Component feedback = GateManager.enter(player, varco.offer(), varco.isRed());
						player.sendSystemMessage(feedback);
						varco.discard();
					}
					case DISMISS -> {
						varco.discard();
						player.sendSystemMessage(Component.translatable("arise.msg.gate.varco_dismissed"));
					}
				}
			});
		});

		// I riquadri di terreno. Il gestore non fa niente di pesante: mette in coda e torna. Il
		// calcolo vero sta su un thread suo, vedi TerrainAtlas.
		ServerPlayNetworking.registerGlobalReceiver(MapTileRequestPayload.TYPE, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayer player = context.player();

					// La mappa si apre con i Gate, e i riquadri sono la mappa: senza questa riga un
					// client modificato potrebbe far dipingere il mondo al server prima ancora del
					// risveglio, che e' l'unica cosa cara di tutto il sistema.
					if (!com.luca.arise.quest.QuestManager.has(player,
							com.luca.arise.quest.Unlock.GATES)) {
						return;
					}

					List<Integer> tiles = payload.tiles();

					for (int i = 0; i + 1 < tiles.size(); i += 2) {
						com.luca.arise.map.TerrainAtlas.request(player, payload.lod(),
								tiles.get(i), tiles.get(i + 1));
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(CityTravelPayload.TYPE, (payload, context) ->
				context.server().execute(() -> {
					ServerPlayer player = context.player();
					player.sendSystemMessage(CityManager.travel(player, payload.city()));
				}));
	}

	/**
	 * Distanza massima fra giocatore e varco perché l'azione sia accettata.
	 *
	 * <p>Generosa di proposito: il pannello resta aperto mentre si pensa, e nessuno deve perdere
	 * l'ingresso per aver fatto due passi indietro. Serve a impedire di entrare in un varco
	 * dall'altra parte del mondo, non a misurare la buona fede.
	 */
	private static final double MAX_GATE_REACH = 16.0;
}
