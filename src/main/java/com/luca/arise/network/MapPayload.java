package com.luca.arise.network;

import java.util.List;

import com.luca.arise.AriseMod;
import com.luca.arise.city.City;
import com.luca.arise.gate.GateTheme;
import com.luca.arise.progress.Rank;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: cosa c'è sulla mappa del mondo, e aprila.
 *
 * <p>Il client non può saperlo da solo: le città stanno a duecentomila blocchi, i varchi in chunk
 * che non ha mai visto, e l'indice dei varchi è un dato del server. Arriva tutto insieme, già
 * riconciliato, quando si preme il tasto — non si tiene sincronizzato di continuo, perché una
 * mappa aperta dieci secondi al giorno non vale un pacchetto al secondo.
 */
public record MapPayload(
		List<CityMark> cities,
		List<GateMark> gates,
		/**
		 * Il lato di una citta', in blocchi.
		 *
		 * <p>Viaggia in rete invece di essere letto dalla config del client, per una ragione che
		 * vale per tutta la mod: la config che conta e' quella del server. Su un server con le
		 * citta' ingrandite, un client con i valori di serie disegnerebbe cinque quadrati della
		 * misura sbagliata — e li disegnerebbe con la stessa sicurezza di quelli giusti. Un intero
		 * in piu' nel pacchetto costa un byte.
		 */
		int citySize) implements CustomPacketPayload {

	/** Una città sulla mappa: dov'è il centro, e se è già sorta. */
	public record CityMark(City city, int x, int z, boolean built) {

		public static final StreamCodec<ByteBuf, CityMark> STREAM_CODEC = StreamCodec.composite(
				City.STREAM_CODEC, CityMark::city,
				ByteBufCodecs.VAR_INT, CityMark::x,
				ByteBufCodecs.VAR_INT, CityMark::z,
				ByteBufCodecs.BOOL, CityMark::built,
				CityMark::new);
	}

	/**
	 * Un varco sulla mappa.
	 *
	 * @param remainingTicks quanto gli resta, o quanto gli restava l'ultima volta che era sveglio
	 * @param awake          falso se il suo chunk è scaricato: dorme, e il tempo non scorre
	 */
	public record GateMark(int x, int y, int z, Rank rank, GateTheme theme, int remainingTicks, boolean awake) {

		private static final StreamCodec<ByteBuf, Rank> RANK = ByteBufCodecs.fromCodec(Rank.CODEC);

		public static final StreamCodec<ByteBuf, GateMark> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, GateMark::x,
				ByteBufCodecs.VAR_INT, GateMark::y,
				ByteBufCodecs.VAR_INT, GateMark::z,
				RANK, GateMark::rank,
				GateTheme.STREAM_CODEC, GateMark::theme,
				ByteBufCodecs.VAR_INT, GateMark::remainingTicks,
				ByteBufCodecs.BOOL, GateMark::awake,
				GateMark::new);
	}

	public static final CustomPacketPayload.Type<MapPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("map"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MapPayload> STREAM_CODEC =
			StreamCodec.composite(
					CityMark.STREAM_CODEC.apply(ByteBufCodecs.list()), MapPayload::cities,
					GateMark.STREAM_CODEC.apply(ByteBufCodecs.list()), MapPayload::gates,
					ByteBufCodecs.VAR_INT, MapPayload::citySize,
					MapPayload::new);

	public MapPayload {
		cities = List.copyOf(cities);
		gates = List.copyOf(gates);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
