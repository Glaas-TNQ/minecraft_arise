package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.luca.arise.city.City;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: "portami a questa Associazione".
 *
 * <p>Viaggia solo il nome della città: che quella città esista davvero nel mondo lo verifica il
 * server, che è l'unico a poterlo sapere. Il pannello di viaggio si apre soltanto toccando un
 * terminale dentro un'Associazione, quindi è quel gesto — non il pacchetto — a essere la porta.
 */
public record CityTravelPayload(City city) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CityTravelPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("city_travel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CityTravelPayload> STREAM_CODEC =
			StreamCodec.composite(City.STREAM_CODEC, CityTravelPayload::city, CityTravelPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
