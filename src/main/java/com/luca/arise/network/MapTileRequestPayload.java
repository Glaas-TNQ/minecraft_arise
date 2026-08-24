package com.luca.arise.network;

import java.util.List;

import com.luca.arise.AriseMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: i riquadri di terreno che servono adesso.
 *
 * <p>Li chiede il client e non li manda il server di sua iniziativa, perche' quali servano dipende
 * da dove la mappa e' inquadrata — e dov'e' inquadrata lo sa solo chi ha il mouse in mano. Un
 * server che mandasse il terreno attorno al giocatore manderebbe la cosa sbagliata nel momento
 * esatto in cui si guarda una citta'.
 *
 * <p>Pochi per volta, e con un tetto scritto nel codec: e' l'unica difesa contro un client
 * modificato che chieda diecimila riquadri per far calcolare al server mezzo pianeta. Ogni riquadro
 * costa migliaia di campioni di rumore, quindi la richiesta e' la parte cara del sistema, non la
 * risposta.
 *
 * @param lod   il livello di dettaglio, uguale per tutti i riquadri della richiesta
 * @param tiles gli indici, a coppie: {@code x0, z0, x1, z1, ...}
 */
public record MapTileRequestPayload(int lod, List<Integer> tiles) implements CustomPacketPayload {

	/** Quanti riquadri al massimo per richiesta. Otto e' gia' mezza schermata. */
	public static final int MAX_TILES = 8;

	public static final CustomPacketPayload.Type<MapTileRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("map_tile_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MapTileRequestPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, MapTileRequestPayload::lod,
					ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_TILES * 2)),
					MapTileRequestPayload::tiles,
					MapTileRequestPayload::new);

	public MapTileRequestPayload {
		tiles = List.copyOf(tiles);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
