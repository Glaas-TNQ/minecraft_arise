package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.luca.arise.map.MapTiles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: un riquadro di terreno gia' dipinto.
 *
 * <p>Il client non potrebbe calcolarselo: il terreno di un mondo lo sa il generatore, e il
 * generatore sta sul server. Non potrebbe nemmeno leggerlo dai chunk che ha caricato — quelli sono
 * un cerchio di poche centinaia di blocchi attorno a lui, e questa mappa deve saper mostrare una
 * citta' a duecentomila.
 *
 * <h2>Tre byte per campione, e non quattro</h2>
 *
 * <p>Un campione e' un colore opaco: l'alfa e' sempre {@code FF} e mandarlo sarebbe un quarto del
 * pacchetto buttato. Quattromilanovantasei campioni per tre byte fanno dodici kilobyte a riquadro,
 * che il protocollo comprime prima di metterli sul filo — un terreno ha pochissimi colori davvero
 * diversi, e si comprime bene. Il client rimette l'alfa da solo, perche' un riquadro trasparente non
 * esiste: o e' arrivato, o non c'e'.
 */
public record MapTilePayload(int lod, int tileX, int tileZ, byte[] rgb) implements CustomPacketPayload {

	/** La dimensione esatta, l'unica accettabile. Vedi il costruttore compatto. */
	private static final int BYTES = MapTiles.TILE * MapTiles.TILE * 3;

	public static final CustomPacketPayload.Type<MapTilePayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("map_tile"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MapTilePayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, MapTilePayload::lod,
					ByteBufCodecs.VAR_INT, MapTilePayload::tileX,
					ByteBufCodecs.VAR_INT, MapTilePayload::tileZ,
					ByteBufCodecs.byteArray(BYTES), MapTilePayload::rgb,
					MapTilePayload::new);

	/**
	 * Rifiuta un riquadro della misura sbagliata.
	 *
	 * <p>Il tetto del codec vale in ricezione; questo vale anche in scrittura, e serve a far
	 * scoppiare subito un errore di programmazione invece di mandare in rete un riquadro che il
	 * client leggerebbe come una striscia di colori sfalsati.
	 */
	public MapTilePayload {
		if (rgb.length != BYTES) {
			throw new IllegalArgumentException(
					"riquadro di " + rgb.length + " byte invece di " + BYTES);
		}
	}

	/** Impacchetta i colori: tre byte per campione, l'alfa si butta. */
	public static MapTilePayload of(int lod, int tileX, int tileZ, int[] colours) {
		byte[] rgb = new byte[BYTES];

		for (int i = 0; i < colours.length; i++) {
			rgb[i * 3] = (byte) (colours[i] >> 16);
			rgb[i * 3 + 1] = (byte) (colours[i] >> 8);
			rgb[i * 3 + 2] = (byte) colours[i];
		}

		return new MapTilePayload(lod, tileX, tileZ, rgb);
	}

	/** Rimette i colori come li vuole il client: opachi. */
	public int[] colours() {
		int[] colours = new int[MapTiles.TILE * MapTiles.TILE];

		for (int i = 0; i < colours.length; i++) {
			colours[i] = 0xFF000000
					| ((rgb[i * 3] & 0xFF) << 16)
					| ((rgb[i * 3 + 1] & 0xFF) << 8)
					| (rgb[i * 3 + 2] & 0xFF);
		}

		return colours;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
